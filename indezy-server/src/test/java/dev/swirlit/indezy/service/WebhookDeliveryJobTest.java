package dev.swirlit.indezy.service;

import dev.swirlit.indezy.model.Webhook;
import dev.swirlit.indezy.model.WebhookDelivery;
import dev.swirlit.indezy.model.enums.WebhookDeliveryStatus;
import dev.swirlit.indezy.model.enums.WebhookEvent;
import dev.swirlit.indezy.repository.WebhookDeliveryRepository;
import dev.swirlit.indezy.repository.WebhookRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WebhookDeliveryJobTest {

    private static final Instant NOW = Instant.parse("2026-10-27T10:00:00Z");

    /** Runs the work at once, as if in a transaction. */
    private static final class Immediate extends TransactionTemplate {
        @Override
        public <T> T execute(TransactionCallback<T> action) {
            return action.doInTransaction(mock(TransactionStatus.class));
        }
    }

    private final WebhookDeliveryRepository deliveries = mock(WebhookDeliveryRepository.class);
    private final WebhookRepository webhooks = mock(WebhookRepository.class);
    private final WebhookDeliveryJob job = new WebhookDeliveryJob(deliveries, webhooks, new WebhookAddresses(true),
        Clock.fixed(NOW, ZoneOffset.UTC), new Immediate());
    private WebhookDelivery delivery;
    private Webhook webhook;

    @BeforeEach
    void due() {
        webhook = new Webhook();
        webhook.setId(3L);
        // Nothing listens there: the connection is refused.
        webhook.setUrl("http://127.0.0.1:9/hook");
        webhook.setSecret("secret");
        webhook.setEvents(Set.of(WebhookEvent.INVOICE_PAID));
        delivery = new WebhookDelivery();
        delivery.setId(8L);
        delivery.setWebhookId(3L);
        delivery.setEvent(WebhookEvent.INVOICE_PAID);
        delivery.setPayload("{}");
        delivery.setNextAttemptAt(NOW);
        when(deliveries.findByStatusAndNextAttemptAtLessThanEqualOrderByNextAttemptAtAsc(eq(WebhookDeliveryStatus.PENDING), eq(NOW), any()))
            .thenReturn(List.of(delivery));
        when(deliveries.findById(8L)).thenReturn(Optional.of(delivery));
        when(webhooks.findById(3L)).thenReturn(Optional.of(webhook));
    }

    @Test
    void triesAgainLaterWhenNoAnswerCame_thenGivesUp() {
        assertThat(job.deliverDue()).isEqualTo(1);
        assertThat(delivery.getStatus()).isEqualTo(WebhookDeliveryStatus.PENDING);
        assertThat(delivery.getResponseStatus()).isZero();
        assertThat(delivery.getError()).startsWith("No answer");
        assertThat(delivery.getNextAttemptAt()).isEqualTo(NOW.plusSeconds(60));
        assertThat(webhook.getConsecutiveFailures()).isEqualTo(1);

        delivery.setAttempts(5);
        delivery.setNextAttemptAt(NOW);
        job.deliverDue();
        assertThat(delivery.getStatus()).isEqualTo(WebhookDeliveryStatus.FAILED);
    }

    @Test
    void turnsAWebhookOffAfterTwentyFailuresInARow() {
        webhook.setConsecutiveFailures(19);
        job.deliverDue();
        assertThat(webhook.isActive()).isFalse();
    }

    @Test
    void failsTheDeliveriesOfAWebhookTurnedOffOrGone() {
        webhook.setActive(false);
        job.deliverDue();
        assertThat(delivery.getStatus()).isEqualTo(WebhookDeliveryStatus.FAILED);
        assertThat(delivery.getError()).isEqualTo("The webhook is off or deleted");
        assertThat(webhook.getConsecutiveFailures()).isZero();
    }

    @Test
    void refusesAHostThatTurnedPrivate() {
        WebhookDeliveryJob guarded = new WebhookDeliveryJob(deliveries, webhooks, new WebhookAddresses(false), Clock.fixed(NOW, ZoneOffset.UTC),
            new Immediate());
        guarded.deliverDue();
        assertThat(delivery.getError()).isEqualTo("A webhook cannot post to a private or local address");
    }

    @Test
    void sendsNothingWhenTurnedOff_andSkipsWhatAnotherReplicaClaimed() {
        ReflectionTestUtils.setField(job, "enabled", false);
        job.run();
        verify(deliveries, never()).findByStatusAndNextAttemptAtLessThanEqualOrderByNextAttemptAtAsc(any(), any(), any());

        when(deliveries.findByStatusAndNextAttemptAtLessThanEqualOrderByNextAttemptAtAsc(any(), any(), any()))
            .thenThrow(new IllegalStateException("version changed"));
        assertThat(job.deliverDue()).isZero();
    }

    @Test
    void forgetsSettledDeliveries() {
        job.forgetOld();
        verify(deliveries).deleteSettledBefore(java.time.LocalDateTime.of(2026, 9, 27, 10, 0));
    }
}
