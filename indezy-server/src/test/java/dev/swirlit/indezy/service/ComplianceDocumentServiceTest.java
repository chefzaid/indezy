package dev.swirlit.indezy.service;

import dev.swirlit.indezy.dto.ComplianceDtos;
import dev.swirlit.indezy.exception.InvalidStateException;
import dev.swirlit.indezy.model.enums.ComplianceDocumentType;
import dev.swirlit.indezy.repository.ComplianceDocumentRepository;
import dev.swirlit.indezy.repository.CompliancePackRepository;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ComplianceDocumentServiceTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 27);

    private final ComplianceDocumentRepository documents = mock(ComplianceDocumentRepository.class);
    private final ComplianceDocumentService service = new ComplianceDocumentService(documents, mock(CompliancePackRepository.class),
        Clock.fixed(TODAY.atTime(10, 0).toInstant(ZoneOffset.UTC), ZoneOffset.UTC));
    private final ComplianceDtos.Details urssaf = new ComplianceDtos.Details(ComplianceDocumentType.URSSAF_VIGILANCE, null, TODAY, null);

    @Test
    void refusesAnEmptyFileATooLargeOneAndAFullVault() {
        assertThatThrownBy(() -> service.upload(7L, null, urssaf)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.upload(7L, new MockMultipartFile("file", new byte[0]), urssaf))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.upload(7L, new MockMultipartFile("file", "big.pdf", "application/pdf", new byte[10 * 1024 * 1024 + 1]),
            urssaf)).isInstanceOf(IllegalArgumentException.class).hasMessage("A document is 10 MB at most");
        when(documents.countByFreelanceId(7L)).thenReturn(30L);
        assertThatThrownBy(() -> service.upload(7L, new MockMultipartFile("file", "a.pdf", "application/pdf", "%PDF-1.7".getBytes()), urssaf))
            .isInstanceOf(InvalidStateException.class);
    }

    @Test
    void tellsEachValidity() {
        assertThat(ComplianceDocumentService.validity(null, TODAY)).isEqualTo(ComplianceDtos.Validity.NO_EXPIRY);
        assertThat(ComplianceDocumentService.validity(TODAY.minusDays(1), TODAY)).isEqualTo(ComplianceDtos.Validity.EXPIRED);
        assertThat(ComplianceDocumentService.validity(TODAY, TODAY)).isEqualTo(ComplianceDtos.Validity.EXPIRING);
        assertThat(ComplianceDocumentService.validity(TODAY.plusDays(30), TODAY)).isEqualTo(ComplianceDtos.Validity.EXPIRING);
        assertThat(ComplianceDocumentService.validity(TODAY.plusDays(31), TODAY)).isEqualTo(ComplianceDtos.Validity.VALID);
    }

    @Test
    void keepsAStoredFileApartFromItsCaller() {
        byte[] content = {1, 2};
        ComplianceDocumentService.StoredFile file = new ComplianceDocumentService.StoredFile("a.pdf", "application/pdf", content);
        content[0] = 9;
        file.content()[1] = 9;
        assertThat(file.content()).containsExactly(1, 2);
    }
}
