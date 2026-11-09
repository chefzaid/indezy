package dev.swirlit.indezy.service;

import dev.swirlit.indezy.dto.ComplianceDtos;
import dev.swirlit.indezy.dto.TrainingDtos;
import dev.swirlit.indezy.exception.ResourceNotFoundException;
import dev.swirlit.indezy.model.Training;
import dev.swirlit.indezy.model.enums.FundingStatus;
import dev.swirlit.indezy.repository.TrainingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

/**
 * The training record (see docs/features.md#training-record): trainings with their certification and the funding
 * asked to the training fund, each workspace's own.
 */
@Service
@RequiredArgsConstructor
public class TrainingService {

    static final int MAX_TRAININGS = 200;
    /** A funding request due within this many days, or past due, is pointed out. */
    static final int FUNDING_DUE_DAYS = 14;
    /** A certification within this many days of its end is expiring, as a compliance document is. */
    static final int EXPIRING_DAYS = 30;
    private static final ZoneId PARIS = ZoneId.of("Europe/Paris");

    private final TrainingRepository trainings;
    private Clock clock = Clock.systemUTC();

    @Autowired(required = false)
    void setClock(Clock clock) {
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<TrainingDtos.Training> list(Long freelanceId) {
        LocalDate today = today();
        return trainings.findByFreelanceIdOrderByStartsOnDescIdDesc(freelanceId).stream().map(training -> dto(training, today)).toList();
    }

    @Transactional
    public TrainingDtos.Training create(Long freelanceId, TrainingDtos.Details details) {
        if (trainings.findByFreelanceIdOrderByStartsOnDescIdDesc(freelanceId).size() >= MAX_TRAININGS) {
            throw new IllegalArgumentException("A training record holds " + MAX_TRAININGS + " trainings at most");
        }
        Training training = new Training();
        training.setFreelanceId(freelanceId);
        return dto(trainings.save(apply(training, details)), today());
    }

    @Transactional
    public TrainingDtos.Training update(Long freelanceId, Long id, TrainingDtos.Details details) {
        return dto(trainings.save(apply(owned(freelanceId, id), details)), today());
    }

    @Transactional
    public void delete(Long freelanceId, Long id) {
        trainings.delete(owned(freelanceId, id));
    }

    /** The certifications still valid, for the public profile: name and end, most recent first. */
    @Transactional(readOnly = true)
    public List<String> validCertifications(Long freelanceId) {
        LocalDate today = today();
        return trainings.findByFreelanceIdOrderByStartsOnDescIdDesc(freelanceId).stream()
            .filter(training -> training.getCertification() != null)
            .filter(training -> training.getCertificationExpiresOn() == null || !training.getCertificationExpiresOn().isBefore(today))
            .map(Training::getCertification)
            .distinct()
            .toList();
    }

    private Training owned(Long freelanceId, Long id) {
        return trainings.findByIdAndFreelanceId(id, freelanceId).orElseThrow(() -> new ResourceNotFoundException("Training not found"));
    }

    private static Training apply(Training training, TrainingDtos.Details details) {
        if (details.startsOn() != null && details.endsOn() != null && details.endsOn().isBefore(details.startsOn())) {
            throw new IllegalArgumentException("A training ends on or after its start");
        }
        training.setTitle(details.title().strip());
        training.setProvider(blankToNull(details.provider()));
        training.setStartsOn(details.startsOn());
        training.setEndsOn(details.endsOn());
        training.setHours(details.hours());
        training.setCost(details.cost());
        training.setCertification(blankToNull(details.certification()));
        training.setCertifiedOn(details.certifiedOn());
        training.setCertificationExpiresOn(details.certificationExpiresOn());
        training.setFund(details.fund());
        training.setFundingStatus(details.fund() == null ? null : details.fundingStatus() != null ? details.fundingStatus() : FundingStatus.TO_REQUEST);
        training.setFundingDeadline(details.fund() == null ? null : details.fundingDeadline());
        training.setFundedAmount(details.fund() == null ? null : details.fundedAmount());
        training.setNotes(blankToNull(details.notes()));
        return training;
    }

    static TrainingDtos.Training dto(Training training, LocalDate today) {
        boolean fundingDue = training.getFundingStatus() == FundingStatus.TO_REQUEST && training.getFundingDeadline() != null &&
            !training.getFundingDeadline().isAfter(today.plusDays(FUNDING_DUE_DAYS));
        return new TrainingDtos.Training(training.getId(), training.getTitle(), training.getProvider(), training.getStartsOn(),
            training.getEndsOn(), training.getHours(), training.getCost(), training.getCertification(), training.getCertifiedOn(),
            training.getCertificationExpiresOn(), validity(training, today), training.getFund(), training.getFundingStatus(),
            training.getFundingDeadline(), fundingDue, training.getFundedAmount(), training.getNotes());
    }

    private static ComplianceDtos.Validity validity(Training training, LocalDate today) {
        LocalDate end = training.getCertificationExpiresOn();
        if (training.getCertification() == null) {
            return null;
        }
        if (end == null) {
            return ComplianceDtos.Validity.NO_EXPIRY;
        }
        if (end.isBefore(today)) {
            return ComplianceDtos.Validity.EXPIRED;
        }
        return end.isAfter(today.plusDays(EXPIRING_DAYS)) ? ComplianceDtos.Validity.VALID : ComplianceDtos.Validity.EXPIRING;
    }

    private LocalDate today() {
        return LocalDate.now(clock.withZone(PARIS));
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }
}
