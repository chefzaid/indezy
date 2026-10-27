package dev.swirlit.indezy.service;

import dev.swirlit.indezy.dto.WebhookDtos;
import dev.swirlit.indezy.exception.InvalidStateException;
import dev.swirlit.indezy.model.ActivityReport;
import dev.swirlit.indezy.model.Invoice;
import dev.swirlit.indezy.model.Project;
import dev.swirlit.indezy.model.Webhook;
import dev.swirlit.indezy.model.enums.ActivityReportStatus;
import dev.swirlit.indezy.model.enums.WebhookEvent;
import dev.swirlit.indezy.repository.WebhookDeliveryRepository;
import dev.swirlit.indezy.repository.WebhookRepository;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WebhookServiceTest {

    private final WebhookRepository webhooks = mock(WebhookRepository.class);
    private final WebhookDeliveryRepository deliveries = mock(WebhookDeliveryRepository.class);
    private final WebhookService service = new WebhookService(webhooks, deliveries, new WebhookAddresses(true),
        Clock.fixed(Instant.parse("2026-10-27T10:00:00Z"), ZoneOffset.UTC));

    @Test
    void keepsFiveWebhooksAtMostAndOneEventAtLeast() {
        when(webhooks.countByFreelanceId(7L)).thenReturn(5L);
        assertThatThrownBy(() -> service.create(7L, new WebhookDtos.Write("https://h.example/x", Set.of(WebhookEvent.INVOICE_PAID), null)))
            .isInstanceOf(InvalidStateException.class);
        when(webhooks.countByFreelanceId(7L)).thenReturn(0L);
        assertThatThrownBy(() -> service.create(7L, new WebhookDtos.Write("https://h.example/x", Set.of(WebhookEvent.PING), null)))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void turnsAWebhookBackOnWithItsFailuresForgotten() {
        Webhook webhook = webhook();
        webhook.setActive(false);
        webhook.setConsecutiveFailures(20);
        when(webhooks.findByIdAndFreelanceId(3L, 7L)).thenReturn(Optional.of(webhook));
        WebhookDtos.Write on = new WebhookDtos.Write("https://h.example/x", Set.of(WebhookEvent.INVOICE_PAID), true);

        assertThat(service.update(7L, 3L, on)).satisfies(dto -> {
            assertThat(dto.active()).isTrue();
            assertThat(dto.consecutiveFailures()).isZero();
        });
        webhook.setConsecutiveFailures(2);
        service.update(7L, 3L, on);
        assertThat(webhook.getConsecutiveFailures()).isEqualTo(2);
        service.update(7L, 3L, new WebhookDtos.Write("https://h.example/y", Set.of(WebhookEvent.INVOICE_PAID), null));
        assertThat(webhook.isActive()).isTrue();
        assertThat(webhook.getUrl()).isEqualTo("https://h.example/y");
    }

    @Test
    void tellsOnlyTheWebhooksListeningToTheEvent() {
        Webhook listening = webhook();
        Webhook other = webhook();
        other.setEvents(Set.of(WebhookEvent.INVOICE_ISSUED));
        when(webhooks.findByFreelanceIdAndActiveTrue(7L)).thenReturn(List.of(listening, other));
        when(deliveries.save(any())).thenAnswer(call -> call.getArgument(0));

        service.publish(7L, WebhookEvent.INVOICE_PAID, Map.of());
        service.publish(null, WebhookEvent.INVOICE_PAID, Map.of());

        verify(deliveries, times(1)).save(any());
        verify(webhooks, never()).findByFreelanceIdAndActiveTrue(null);
    }

    @Test
    void describesObjectsWithoutTheirMissingParts() {
        Project project = new Project();
        project.setId(1L);
        assertThat(WebhookPayloads.project(project, null)).containsEntry("clientName", null).containsEntry("previousStatus", null);
        Invoice invoice = new Invoice();
        invoice.setId(2L);
        assertThat(WebhookPayloads.invoice(invoice)).containsEntry("clientName", null).containsEntry("issueDate", null)
            .containsEntry("dueDate", null).containsEntry("paidOn", null).containsEntry("kind", "INVOICE");
        ActivityReport report = new ActivityReport();
        report.setProject(project);
        report.setStatus(ActivityReportStatus.SUBMITTED);
        assertThat(WebhookPayloads.report(report)).containsEntry("workedDays", 0.0).containsEntry("projectId", 1L);
    }

    private static Webhook webhook() {
        Webhook webhook = new Webhook();
        webhook.setId(3L);
        webhook.setUrl("https://h.example/x");
        webhook.setSecret("s");
        webhook.setEvents(Set.of(WebhookEvent.INVOICE_PAID));
        return webhook;
    }
}
