package dev.swirlit.indezy.service;

import dev.swirlit.indezy.dto.ProjectReferralDtos;
import dev.swirlit.indezy.exception.ResourceNotFoundException;
import dev.swirlit.indezy.model.ActivityReport;
import dev.swirlit.indezy.model.Project;
import dev.swirlit.indezy.model.ProjectReferral;
import dev.swirlit.indezy.model.enums.ReferralBonusKind;
import dev.swirlit.indezy.model.enums.ReferralDirection;
import dev.swirlit.indezy.model.enums.ReferralStatus;
import dev.swirlit.indezy.repository.ActivityReportRepository;
import dev.swirlit.indezy.repository.ProjectReferralRepository;
import dev.swirlit.indezy.repository.ProjectRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Missions' co-optations (see docs/features.md#co-optation): who referred a mission to the freelancer, or whom they
 * referred, and the bonus that goes with it, fixed, by day worked or a share of what the mission earns, counted from its
 * timesheets; and what is still to pay and to receive across the account's missions.
 */
@Service
@RequiredArgsConstructor
public class ProjectReferralService {

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private final ProjectRepository projects;
    private final ProjectReferralRepository referrals;
    private final ActivityReportRepository reports;

    /** The mission's co-optation, if one was saved. */
    @Transactional(readOnly = true)
    public Optional<ProjectReferralDtos.View> get(Long projectId) {
        Project project = project(projectId);
        return referrals.findByProjectId(projectId).map(referral -> view(project, referral));
    }

    /** Saves the mission's co-optation; a bonus needs both its kind and its amount, and a paid one its day. */
    @Transactional
    public ProjectReferralDtos.View save(Long projectId, ProjectReferralDtos.Referral request) {
        Project project = project(projectId);
        if ((request.bonusKind() == null) != (request.bonusAmount() == null)) {
            throw new IllegalArgumentException("A bonus has a kind and an amount");
        }
        if (request.bonusKind() == ReferralBonusKind.PERCENT && request.bonusAmount().compareTo(HUNDRED) > 0) {
            throw new IllegalArgumentException("A share is 100% at most");
        }
        ProjectReferral referral = referrals.findByProjectId(projectId).orElseGet(ProjectReferral::new);
        referral.setProjectId(projectId);
        referral.setReferrer(request.referrer().strip());
        referral.setDirection(request.direction());
        referral.setBonusKind(request.bonusKind());
        referral.setBonusAmount(request.bonusAmount());
        referral.setStatus(request.status());
        referral.setPaidOn(request.status() == ReferralStatus.PAID ? request.paidOn() : null);
        referral.setNote(request.note() == null || request.note().isBlank() ? null : request.note().strip());
        return view(project, referrals.save(referral));
    }

    @Transactional
    public void delete(Long projectId) {
        project(projectId);
        referrals.findByProjectId(projectId).ifPresent(referrals::delete);
    }

    /** The account's co-optations, the latest mission first, and the bonuses not paid yet, each way. */
    @Transactional(readOnly = true)
    public ProjectReferralDtos.Summary summary(Long freelanceId) {
        Map<Long, Project> mine = projects.findByFreelanceId(freelanceId).stream().collect(Collectors.toMap(Project::getId, Function.identity()));
        List<ProjectReferralDtos.View> views = referrals.findByProjectIdIn(mine.keySet()).stream()
            .map(referral -> view(mine.get(referral.getProjectId()), referral))
            .sorted(Comparator.comparing(ProjectReferralDtos.View::projectId).reversed()).toList();
        return new ProjectReferralDtos.Summary(views, owed(views, ReferralDirection.I_PAY), owed(views, ReferralDirection.I_RECEIVE));
    }

    private static BigDecimal owed(List<ProjectReferralDtos.View> views, ReferralDirection direction) {
        return views.stream().filter(view -> view.referral().direction() == direction && view.referral().status() != ReferralStatus.PAID)
            .map(ProjectReferralDtos.View::bonus).filter(Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private ProjectReferralDtos.View view(Project project, ProjectReferral referral) {
        double worked = reports.findByProjectIdOrderByReportYearDescReportMonthDesc(project.getId()).stream()
            .mapToDouble(ActivityReport::getWorkedDays).sum();
        BigDecimal days = BigDecimal.valueOf(worked);
        BigDecimal earned = project.getDailyRate() == null ? BigDecimal.ZERO : days.multiply(BigDecimal.valueOf(project.getDailyRate()));
        BigDecimal bonus = referral.getBonusKind() == null ? null : switch (referral.getBonusKind()) {
            case FIXED -> referral.getBonusAmount();
            case PER_DAY -> referral.getBonusAmount().multiply(days);
            case PERCENT -> earned.multiply(referral.getBonusAmount()).divide(HUNDRED, 2, RoundingMode.HALF_UP);
        };
        ProjectReferralDtos.Referral saved = new ProjectReferralDtos.Referral(referral.getReferrer(), referral.getDirection(), referral.getBonusKind(),
            referral.getBonusAmount(), referral.getStatus(), referral.getPaidOn(), referral.getNote());
        return new ProjectReferralDtos.View(project.getId(), project.getRole(), project.getClient() == null ? null : project.getClient().getCompanyName(),
            saved, worked, earned.setScale(2, RoundingMode.HALF_UP), bonus == null ? null : bonus.setScale(2, RoundingMode.HALF_UP));
    }

    private Project project(Long projectId) {
        return projects.findById(projectId).orElseThrow(() -> new ResourceNotFoundException("Project not found with id: " + projectId));
    }
}
