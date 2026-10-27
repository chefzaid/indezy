package dev.swirlit.indezy.service;

import dev.swirlit.indezy.dto.ComplianceDtos;
import dev.swirlit.indezy.exception.InvalidStateException;
import dev.swirlit.indezy.exception.ResourceNotFoundException;
import dev.swirlit.indezy.model.BillingIdentity;
import dev.swirlit.indezy.model.CompliancePack;
import dev.swirlit.indezy.model.Freelance;
import dev.swirlit.indezy.repository.ComplianceDocumentRepository;
import dev.swirlit.indezy.repository.CompliancePackRepository;
import dev.swirlit.indezy.repository.FreelanceRepository;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CompliancePackServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-27T10:00:00Z");

    private final CompliancePackRepository packs = mock(CompliancePackRepository.class);
    private final ComplianceDocumentRepository documents = mock(ComplianceDocumentRepository.class);
    private final FreelanceRepository freelances = mock(FreelanceRepository.class);
    private final CompliancePackService service = new CompliancePackService(packs, documents, freelances, Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    void opensForFourteenDaysWhenNotSaid() {
        ComplianceDocumentRepository.Summary summary = mock(ComplianceDocumentRepository.Summary.class);
        when(documents.findSummaries(eq(7L), anyCollection())).thenReturn(List.of(summary));
        when(packs.save(any())).thenAnswer(call -> call.getArgument(0));

        ComplianceDtos.CreatedPack created = service.create(7L, new ComplianceDtos.PackRequest(" ESN ", List.of(4L, 4L), null));

        assertThat(created.pack().expiresAt()).isEqualTo(NOW.plus(Duration.ofDays(14)));
        assertThat(created.pack().documentIds()).containsExactly(4L);
        assertThat(created.token()).hasSize(43);
        assertThat(created.toString()).doesNotContain(created.token());
    }

    @Test
    void keepsTwentyLinksOpenAtMost() {
        ComplianceDocumentRepository.Summary summary = mock(ComplianceDocumentRepository.Summary.class);
        when(documents.findSummaries(eq(7L), anyCollection())).thenReturn(List.of(summary));
        List<CompliancePack> open = IntStream.range(0, 20).mapToObj(index -> pack(NOW.plusSeconds(60), null)).toList();
        when(packs.findByFreelanceIdOrderByCreatedAtDesc(7L)).thenReturn(open);

        assertThatThrownBy(() -> service.create(7L, new ComplianceDtos.PackRequest("ESN", List.of(4L), 7)))
            .isInstanceOf(InvalidStateException.class);
    }

    @Test
    void revokesOnceAndKeepsTheFirstTime() {
        Instant first = NOW.minusSeconds(3600);
        CompliancePack revoked = pack(NOW.plusSeconds(60), first);
        when(packs.findByIdAndFreelanceId(3L, 7L)).thenReturn(Optional.of(revoked));

        assertThat(service.revoke(7L, 3L).revokedAt()).isEqualTo(first);
    }

    @Test
    void opensNoLinkWithoutAToken_expiredOrRevoked() {
        assertThatThrownBy(() -> service.open(" ")).isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> service.open(null)).isInstanceOf(ResourceNotFoundException.class);
        when(packs.findByTokenHash(anyString())).thenReturn(Optional.of(pack(NOW, null)));
        assertThatThrownBy(() -> service.open("expired")).isInstanceOf(ResourceNotFoundException.class);
        when(packs.findByTokenHash(anyString())).thenReturn(Optional.of(pack(NOW.plusSeconds(60), NOW.minusSeconds(1))));
        assertThatThrownBy(() -> service.open("revoked")).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void namesWhoSharesByTheBillingIdentityOrElseTheWorkspace() {
        CompliancePack pack = pack(NOW.plusSeconds(60), null);
        when(packs.findByTokenHash(anyString())).thenReturn(Optional.of(pack));
        when(documents.findSummaries(eq(7L), anyCollection())).thenReturn(List.of());
        Freelance freelance = new Freelance();
        freelance.setFirstName("Alice");
        freelance.setLastName(" ");
        when(freelances.findById(7L)).thenReturn(Optional.of(freelance));
        assertThat(service.open("token").sharedBy()).isEqualTo("Alice");

        BillingIdentity billing = new BillingIdentity();
        freelance.setBilling(billing);
        assertThat(service.open("token").sharedBy()).isEqualTo("Alice");
        billing.setLegalName("Alice Martin Conseil");
        assertThat(service.open("token").sharedBy()).isEqualTo("Alice Martin Conseil");
        assertThat(pack.getOpenCount()).isEqualTo(3);

        when(freelances.findById(7L)).thenReturn(Optional.empty());
        assertThat(service.open("token").sharedBy()).isEmpty();
    }

    private static CompliancePack pack(Instant expiresAt, Instant revokedAt) {
        CompliancePack pack = new CompliancePack();
        pack.setFreelanceId(7L);
        pack.setRecipient("ESN");
        pack.setExpiresAt(expiresAt);
        pack.setRevokedAt(revokedAt);
        pack.setDocumentIds(Set.copyOf(Collections.singleton(4L)));
        return pack;
    }
}
