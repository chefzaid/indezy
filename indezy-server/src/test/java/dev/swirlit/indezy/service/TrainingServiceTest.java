package dev.swirlit.indezy.service;

import dev.swirlit.indezy.dto.ComplianceDtos.Validity;
import dev.swirlit.indezy.dto.TrainingDtos;
import dev.swirlit.indezy.model.Training;
import dev.swirlit.indezy.model.enums.FundingStatus;
import dev.swirlit.indezy.model.enums.TrainingFund;
import dev.swirlit.indezy.repository.TrainingRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TrainingServiceTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 11, 9);

    private final TrainingRepository repository = mock(TrainingRepository.class);
    private final TrainingService service = new TrainingService(repository);
    private final List<Training> stored = new ArrayList<>();

    @BeforeEach
    void setUp() {
        service.setClock(Clock.fixed(Instant.parse("2026-11-09T10:00:00Z"), ZoneOffset.UTC));
        when(repository.findByFreelanceIdOrderByStartsOnDescIdDesc(1L)).thenReturn(stored);
        when(repository.save(any(Training.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    private static TrainingDtos.Details details(String title, TrainingFund fund, FundingStatus status, LocalDate deadline,
                                                String certification, LocalDate expires) {
        return new TrainingDtos.Details(title, " Kubernetes Academy ", LocalDate.of(2026, 11, 16), LocalDate.of(2026, 11, 18), 21,
            new BigDecimal("1800.00"), certification, null, expires, fund, status, deadline, null, "  ");
    }

    private static Training training(String certification, LocalDate expires) {
        Training training = new Training();
        training.setTitle("T");
        training.setCertification(certification);
        training.setCertificationExpiresOn(expires);
        return training;
    }

    @Test
    void recordsATrainingAskingItsFundingByDefault() {
        TrainingDtos.Training saved = service.create(1L, details(" CKA ", TrainingFund.FIF_PL, null, TODAY.plusDays(10),
            "Certified Kubernetes Administrator", TODAY.plusYears(2)));

        assertThat(saved.title()).isEqualTo("CKA");
        assertThat(saved.provider()).isEqualTo("Kubernetes Academy");
        assertThat(saved.notes()).isNull();
        assertThat(saved.fundingStatus()).isEqualTo(FundingStatus.TO_REQUEST);
        assertThat(saved.fundingDue()).isTrue();
        assertThat(saved.certificationValidity()).isEqualTo(Validity.VALID);
    }

    @Test
    void forgetsTheFundingWithoutAFund() {
        TrainingDtos.Training saved = service.create(1L, details("Scrum", null, FundingStatus.REQUESTED, TODAY, null, null));
        assertThat(saved.fundingStatus()).isNull();
        assertThat(saved.fundingDeadline()).isNull();
        assertThat(saved.fundingDue()).isFalse();
        assertThat(saved.certificationValidity()).isNull();
    }

    @Test
    void pointsOutFundingDueOnlyWhenStillToAskAndNear() {
        assertThat(service.create(1L, details("A", TrainingFund.AGEFICE, null, TODAY.plusDays(15), null, null)).fundingDue()).isFalse();
        assertThat(service.create(1L, details("B", TrainingFund.AGEFICE, null, TODAY.minusDays(1), null, null)).fundingDue()).isTrue();
        assertThat(service.create(1L, details("C", TrainingFund.AGEFICE, FundingStatus.REQUESTED, TODAY, null, null)).fundingDue())
            .isFalse();
        assertThat(service.create(1L, details("D", TrainingFund.OPCO, null, null, null, null)).fundingDue()).isFalse();
    }

    @Test
    void tellsACertificationsValidityLikeADocuments() {
        assertThat(TrainingService.dto(training("X", null), TODAY).certificationValidity()).isEqualTo(Validity.NO_EXPIRY);
        assertThat(TrainingService.dto(training("X", TODAY.minusDays(1)), TODAY).certificationValidity()).isEqualTo(Validity.EXPIRED);
        assertThat(TrainingService.dto(training("X", TODAY.plusDays(30)), TODAY).certificationValidity()).isEqualTo(Validity.EXPIRING);
        assertThat(TrainingService.dto(training("X", TODAY.plusDays(31)), TODAY).certificationValidity()).isEqualTo(Validity.VALID);
    }

    @Test
    void pointsOutTheFundingRequestsDueSoonestFirst() {
        Training later = training(null, null);
        later.setId(1L);
        later.setFund(TrainingFund.FIF_PL);
        later.setFundingStatus(FundingStatus.TO_REQUEST);
        later.setFundingDeadline(TODAY.plusDays(10));
        Training sooner = training(null, null);
        sooner.setId(2L);
        sooner.setFund(TrainingFund.AGEFICE);
        sooner.setFundingStatus(FundingStatus.TO_REQUEST);
        sooner.setFundingDeadline(TODAY.plusDays(2));
        Training asked = training(null, null);
        asked.setId(3L);
        asked.setFund(TrainingFund.AGEFICE);
        asked.setFundingStatus(FundingStatus.REQUESTED);
        asked.setFundingDeadline(TODAY);
        stored.addAll(List.of(later, sooner, asked));

        assertThat(service.fundingDue(1L)).extracting(dev.swirlit.indezy.dto.DashboardStatsDto.TrainingFundingDue::trainingId)
            .containsExactly(2L, 1L);
    }

    @Test
    void listsTheValidCertificationsOnceEach() {
        stored.addAll(List.of(training("AWS SAA", TODAY.plusYears(1)), training("PSM I", null), training("Old", TODAY.minusDays(1)),
            training(null, null), training("AWS SAA", TODAY.plusYears(2))));
        assertThat(service.validCertifications(1L)).containsExactly("AWS SAA", "PSM I");
    }

    @Test
    void refusesAnEndBeforeTheStartTooManyTrainingsAndAnotherWorkspacesTraining() {
        assertThatThrownBy(() -> service.create(1L, new TrainingDtos.Details("X", null, TODAY, TODAY.minusDays(1), null, null, null,
            null, null, null, null, null, null, null))).isInstanceOf(IllegalArgumentException.class);
        when(repository.findByIdAndFreelanceId(9L, 1L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.delete(1L, 9L)).isInstanceOf(dev.swirlit.indezy.exception.ResourceNotFoundException.class);
        stored.addAll(Collections.nCopies(TrainingService.MAX_TRAININGS, training(null, null)));
        assertThatThrownBy(() -> service.create(1L, details("Y", null, null, null, null, null)))
            .isInstanceOf(IllegalArgumentException.class);
    }
}
