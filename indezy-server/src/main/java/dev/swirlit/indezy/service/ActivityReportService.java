package dev.swirlit.indezy.service;

import dev.swirlit.indezy.constants.ErrorMessages;
import dev.swirlit.indezy.dto.ActivityDayDto;
import dev.swirlit.indezy.dto.ActivityReportDecisionRequest;
import dev.swirlit.indezy.dto.ActivityReportDto;
import dev.swirlit.indezy.dto.ActivityReportLinkDto;
import dev.swirlit.indezy.dto.ActivityReportUpdateRequest;
import dev.swirlit.indezy.dto.PublicActivityReportDto;
import dev.swirlit.indezy.dto.PublicHolidayDto;
import dev.swirlit.indezy.exception.InvalidStateException;
import dev.swirlit.indezy.exception.ResourceNotFoundException;
import dev.swirlit.indezy.model.ActivityDay;
import dev.swirlit.indezy.model.ActivityReport;
import dev.swirlit.indezy.model.Client;
import dev.swirlit.indezy.model.Freelance;
import dev.swirlit.indezy.model.Project;
import dev.swirlit.indezy.model.enums.ActivityDayType;
import dev.swirlit.indezy.model.enums.ActivityReportStatus;
import dev.swirlit.indezy.model.enums.ProjectStatus;
import dev.swirlit.indezy.pdf.ActivityReportDocument;
import dev.swirlit.indezy.pdf.ActivityReportPdfRenderer;
import dev.swirlit.indezy.pdf.ActivityReportTemplate;
import dev.swirlit.indezy.repository.ActivityReportRepository;
import dev.swirlit.indezy.repository.ProjectRepository;
import dev.swirlit.indezy.util.FrenchPublicHolidays;
import lombok.extern.slf4j.Slf4j;
import dev.swirlit.indezy.model.enums.WebhookEvent;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.text.Normalizer;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Base64;
import java.util.Comparator;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Monthly activity reports (CRA) of accepted missions. The freelance records the month's days, then submits the report:
 * the client or ESN opens the validation link it receives and approves or rejects it. Only the SHA-256 of a link token
 * is stored, the link expires after {@link #LINK_VALIDITY}, and reopening a report revokes it.
 */
@Service
@Slf4j
@Transactional
public class ActivityReportService {

    static final Duration LINK_VALIDITY = Duration.ofDays(30);
    private static final int TOKEN_BYTES = 32;
    private static final int MAX_TOKEN_LENGTH = 128;
    private static final int MAX_FILE_NAME_ROLE = 40;

    private final ActivityReportRepository activityReportRepository;
    private final ProjectRepository projectRepository;
    private final ActivityReportPdfRenderer pdfRenderer;
    private final Clock clock;
    private final SecureRandom secureRandom = new SecureRandom();
    private WebhookPublisher webhooks = WebhookPublisher.NONE;

    /** A rendered report: the file name to offer and the PDF bytes. */
    public record PdfFile(String fileName, byte[] content) {
        public PdfFile {
            content = content.clone();
        }

        @Override
        public byte[] content() {
            return content.clone();
        }
    }

    @Autowired
    public ActivityReportService(ActivityReportRepository activityReportRepository, ProjectRepository projectRepository,
                                 ActivityReportPdfRenderer pdfRenderer) {
        this(activityReportRepository, projectRepository, pdfRenderer, Clock.systemUTC());
    }

    ActivityReportService(ActivityReportRepository activityReportRepository, ProjectRepository projectRepository,
                          ActivityReportPdfRenderer pdfRenderer, Clock clock) {
        this.activityReportRepository = activityReportRepository;
        this.projectRepository = projectRepository;
        this.pdfRenderer = pdfRenderer;
        this.clock = clock;
    }

    /** Tells the workspace's webhooks of the changes; none until Spring sets it (unit tests build the service by hand). */
    @Autowired
    void setWebhooks(WebhookPublisher webhooks) {
        this.webhooks = webhooks;
    }

    /** A project's saved reports, latest month first. */
    @Transactional(readOnly = true)
    public List<ActivityReportDto> getReports(Long projectId) {
        Project project = findProject(projectId);
        return activityReportRepository.findByProjectIdOrderByReportYearDescReportMonthDesc(projectId).stream()
            .map(report -> toDto(project, report, true))
            .toList();
    }

    /** The month's report, or an empty draft with the month's calendar when nothing was saved yet. */
    @Transactional(readOnly = true)
    public ActivityReportDto getReport(Long projectId, YearMonth month) {
        Project project = findProject(projectId);
        return findReport(projectId, month)
            .map(report -> toDto(project, report, true))
            .orElseGet(() -> calendar(project, month, true));
    }

    /** Replaces the month's days and comment, creating the report on first save; only accepted missions get one. */
    public ActivityReportDto saveReport(Long projectId, YearMonth month, ActivityReportUpdateRequest request) {
        Project project = findProject(projectId);
        ActivityReport report = findReport(projectId, month).orElseGet(() -> newReport(project, month));
        if (!report.getStatus().isEditable()) {
            throw new InvalidStateException(ErrorMessages.ACTIVITY_REPORT_LOCKED);
        }
        report.setDays(toDays(month, request.getDays()));
        report.setComment(trimToNull(request.getComment()));
        ActivityReport saved = activityReportRepository.save(report);
        log.info("Saved activity report {} of project {} ({} days)", saved.getId(), projectId, saved.getDays().size());
        return toDto(project, saved, true);
    }

    /** Locks the report and issues its validation link, replacing an earlier decision. */
    public ActivityReportLinkDto submit(Long projectId, YearMonth month) {
        Project project = findProject(projectId);
        ActivityReport report = requireReport(projectId, month);
        if (!report.getStatus().isEditable()) {
            throw new InvalidStateException(ErrorMessages.ACTIVITY_REPORT_LOCKED);
        }
        report.clearValidation();
        report.setStatus(ActivityReportStatus.SUBMITTED);
        report.setSubmittedAt(clock.instant());
        ActivityReportLinkDto link = issueLink(project, report);
        webhooks.publish(workspaceOf(project), WebhookEvent.ACTIVITY_REPORT_SUBMITTED, WebhookPayloads.report(report));
        return link;
    }

    /** Replaces the validation link of a submitted report, for instance when the first one was lost. */
    public ActivityReportLinkDto renewLink(Long projectId, YearMonth month) {
        Project project = findProject(projectId);
        ActivityReport report = requireReport(projectId, month);
        if (report.getStatus() != ActivityReportStatus.SUBMITTED) {
            throw new InvalidStateException(ErrorMessages.ACTIVITY_REPORT_NOT_SUBMITTED);
        }
        return issueLink(project, report);
    }

    /** Back to draft: the link stops working and any approval or rejection is forgotten. */
    public ActivityReportDto reopen(Long projectId, YearMonth month) {
        Project project = findProject(projectId);
        ActivityReport report = requireReport(projectId, month);
        report.clearValidation();
        report.setSubmittedAt(null);
        report.setStatus(ActivityReportStatus.DRAFT);
        ActivityReport saved = activityReportRepository.save(report);
        log.info("Reopened activity report {} of project {}", saved.getId(), projectId);
        return toDto(project, saved, true);
    }

    @Transactional(readOnly = true)
    public PdfFile renderPdf(Long projectId, YearMonth month) {
        findProject(projectId);
        return renderPdf(requireReport(projectId, month));
    }

    /** The report behind a validation link, as the validator sees it. */
    @Transactional(readOnly = true)
    public PublicActivityReportDto getPublicReport(String token) {
        return toPublicDto(findByToken(token));
    }

    @Transactional(readOnly = true)
    public PdfFile renderPublicPdf(String token) {
        return renderPdf(findByToken(token));
    }

    /** Records the validator's approval or rejection; a submitted report can be decided once. */
    public PublicActivityReportDto decide(String token, ActivityReportDecisionRequest request) {
        ActivityReport report = findByToken(token);
        if (report.getStatus() != ActivityReportStatus.SUBMITTED) {
            throw new InvalidStateException(ErrorMessages.ACTIVITY_REPORT_NOT_SUBMITTED);
        }
        boolean approved = Boolean.TRUE.equals(request.getApproved());
        String comment = trimToNull(request.getComment());
        if (!approved && comment == null) {
            throw new IllegalArgumentException(ErrorMessages.ACTIVITY_REPORT_REJECTION_REASON_REQUIRED);
        }
        report.setStatus(approved ? ActivityReportStatus.VALIDATED : ActivityReportStatus.REJECTED);
        report.setValidatorName(request.getValidatorName().trim());
        report.setDecidedAt(clock.instant());
        report.setDecisionComment(comment);
        ActivityReport saved = activityReportRepository.save(report);
        webhooks.publish(workspaceOf(saved.getProject()), approved ? WebhookEvent.ACTIVITY_REPORT_APPROVED : WebhookEvent.ACTIVITY_REPORT_REJECTED,
            WebhookPayloads.report(saved));
        log.info("Activity report {} {} through its validation link", saved.getId(), saved.getStatus());
        return toPublicDto(saved);
    }

    /** Every report of a workspace, for the account data export. */
    @Transactional(readOnly = true)
    public List<ActivityReportDto> findByFreelanceId(Long freelanceId) {
        return activityReportRepository.findByProjectFreelanceIdOrderByReportYearDescReportMonthDesc(freelanceId).stream()
            .map(report -> toDto(report.getProject(), report, true))
            .toList();
    }

    public void deleteByProjectId(Long projectId) {
        activityReportRepository.deleteByProjectId(projectId);
    }

    private ActivityReportLinkDto issueLink(Project project, ActivityReport report) {
        byte[] bytes = new byte[TOKEN_BYTES];
        secureRandom.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        Instant expiresAt = clock.instant().plus(LINK_VALIDITY);
        report.setValidationTokenHash(hash(token));
        report.setValidationExpiresAt(expiresAt);
        ActivityReport saved = activityReportRepository.save(report);
        log.info("Issued a validation link for activity report {} of project {}", saved.getId(), project.getId());
        return new ActivityReportLinkDto(token, expiresAt, toDto(project, saved, true));
    }

    private ActivityReport findByToken(String token) {
        if (token == null || token.isBlank() || token.length() > MAX_TOKEN_LENGTH) {
            throw new ResourceNotFoundException(ErrorMessages.ACTIVITY_REPORT_LINK_INVALID);
        }
        return activityReportRepository.findByValidationTokenHash(hash(token.trim()))
            .filter(report -> report.getValidationExpiresAt() != null && report.getValidationExpiresAt().isAfter(clock.instant()))
            .orElseThrow(() -> new ResourceNotFoundException(ErrorMessages.ACTIVITY_REPORT_LINK_INVALID));
    }

    private ActivityReport newReport(Project project, YearMonth month) {
        if (project.getStatus() != ProjectStatus.WON) {
            throw new InvalidStateException(ErrorMessages.ACTIVITY_REPORT_MISSION_REQUIRED);
        }
        ActivityReport report = new ActivityReport();
        report.setProject(project);
        report.setReportYear(month.getYear());
        report.setReportMonth(month.getMonthValue());
        return report;
    }

    private Project findProject(Long projectId) {
        return projectRepository.findById(projectId)
            .orElseThrow(() -> new ResourceNotFoundException(String.format(ErrorMessages.PROJECT_NOT_FOUND, projectId)));
    }

    private Optional<ActivityReport> findReport(Long projectId, YearMonth month) {
        return activityReportRepository.findByProjectIdAndReportYearAndReportMonth(projectId, month.getYear(), month.getMonthValue());
    }

    private ActivityReport requireReport(Long projectId, YearMonth month) {
        return findReport(projectId, month).orElseThrow(() ->
            new ResourceNotFoundException(String.format(ErrorMessages.ACTIVITY_REPORT_NOT_FOUND, projectId, month)));
    }

    private static List<ActivityDay> toDays(YearMonth month, List<ActivityDayDto> requested) {
        Set<LocalDate> seen = new HashSet<>();
        return requested.stream()
            .map(day -> {
                if (!YearMonth.from(day.getDate()).equals(month)) {
                    throw new IllegalArgumentException(ErrorMessages.ACTIVITY_REPORT_DAY_OUTSIDE_MONTH);
                }
                if (!seen.add(day.getDate())) {
                    throw new IllegalArgumentException(ErrorMessages.ACTIVITY_REPORT_DUPLICATE_DAY);
                }
                return new ActivityDay(day.getDate(), day.getType(), day.countsAsHalfDay());
            })
            .sorted(Comparator.comparing(ActivityDay::getDate))
            .toList();
    }

    /** The month's calendar for the mission: holidays, business days, mission dates and rate, without recorded days. */
    private ActivityReportDto calendar(Project project, YearMonth month, boolean forFreelance) {
        ActivityReportDto dto = new ActivityReportDto();
        dto.setYear(month.getYear());
        dto.setMonth(month.getMonthValue());
        dto.setStatus(ActivityReportStatus.DRAFT);
        dto.setDays(List.of());
        dto.setPublicHolidays(FrenchPublicHolidays.in(month).entrySet().stream()
            .map(entry -> new PublicHolidayDto(entry.getKey(), entry.getValue()))
            .toList());
        dto.setBusinessDays(FrenchPublicHolidays.businessDays(month));
        dto.setMissionStart(project.getStartDate());
        dto.setMissionEnd(project.lastDay());
        if (forFreelance) {
            dto.setProjectId(project.getId());
            dto.setDailyRate(project.getDailyRate());
            dto.setAmount(project.getDailyRate() != null ? 0.0 : null);
        }
        return dto;
    }

    private ActivityReportDto toDto(Project project, ActivityReport report, boolean forFreelance) {
        ActivityReportDto dto = calendar(project, report.getPeriod(), forFreelance);
        dto.setStatus(report.getStatus());
        dto.setComment(report.getComment());
        dto.setDays(report.getDays().stream()
            .map(day -> new ActivityDayDto(day.getDate(), day.getType(), day.isHalfDay()))
            .toList());
        dto.setWorkedDays(report.getWorkedDays());
        dto.setOnsiteDays(report.getDaysOf(ActivityDayType.ONSITE));
        dto.setRemoteDays(report.getDaysOf(ActivityDayType.REMOTE));
        dto.setAbsenceDays(report.getDaysOf(ActivityDayType.ABSENCE));
        dto.setSubmittedAt(report.getSubmittedAt());
        dto.setValidatorName(report.getValidatorName());
        dto.setDecidedAt(report.getDecidedAt());
        dto.setDecisionComment(report.getDecisionComment());
        if (forFreelance) {
            dto.setId(report.getId());
            dto.setAmount(project.getDailyRate() != null ? report.getWorkedDays() * project.getDailyRate() : null);
            dto.setLinkExpiresAt(report.getValidationExpiresAt());
            dto.setUpdatedAt(report.getUpdatedAt());
        }
        return dto;
    }

    private PublicActivityReportDto toPublicDto(ActivityReport report) {
        Project project = report.getProject();
        PublicActivityReportDto dto = new PublicActivityReportDto();
        dto.setConsultantName(fullName(project.getFreelance()));
        dto.setClientName(project.getClient().getCompanyName());
        dto.setMiddlemanName(project.getMiddleman() != null ? project.getMiddleman().getCompanyName() : null);
        dto.setMissionRole(project.getRole());
        dto.setShowsLocation(templateOf(project).showsLocation());
        dto.setReport(toDto(project, report, false));
        return dto;
    }

    private PdfFile renderPdf(ActivityReport report) {
        Project project = report.getProject();
        ActivityReportDocument document = new ActivityReportDocument(templateOf(project), fullName(project.getFreelance()),
            project.getClient().getCompanyName(), project.getMiddleman() != null ? project.getMiddleman().getCompanyName() : null,
            project.getRole(), report.getPeriod(), report.getDays(), report.getComment(), report.getStatus(),
            report.getValidatorName(), report.getDecidedAt(), report.getDecisionComment());
        return new PdfFile(fileName(project, report.getPeriod()), pdfRenderer.render(document));
    }

    /** The template of the mission's ESN when it has one, otherwise of its final client. */
    private static ActivityReportTemplate templateOf(Project project) {
        Client templateOwner = project.getMiddleman() != null ? project.getMiddleman() : project.getClient();
        return ActivityReportTemplate.of(templateOwner);
    }

    /** {@code CRA-2026-10-java-developer.pdf}: ASCII only, so every browser and mail client keeps it. */
    private static String fileName(Project project, YearMonth month) {
        String role = Normalizer.normalize(project.getRole() == null ? "" : project.getRole(), Normalizer.Form.NFD)
            .replaceAll("\\p{M}", "")
            .toLowerCase(Locale.ROOT)
            .replaceAll("[^a-z0-9]+", "-")
            .replaceAll("(^-+|-+$)", "");
        if (role.length() > MAX_FILE_NAME_ROLE) {
            int lastWord = role.lastIndexOf('-', MAX_FILE_NAME_ROLE);
            role = role.substring(0, lastWord > 0 ? lastWord : MAX_FILE_NAME_ROLE);
        }
        return "CRA-" + month + (role.isEmpty() ? "" : "-" + role) + ".pdf";
    }

    private static String fullName(Freelance freelance) {
        return Stream.of(freelance.getFirstName(), freelance.getLastName())
            .filter(part -> part != null && !part.isBlank())
            .collect(Collectors.joining(" "));
    }

    private static String trimToNull(String text) {
        return text == null || text.isBlank() ? null : text.trim();
    }

    private static Long workspaceOf(Project project) {
        return project.getFreelance() == null ? null : project.getFreelance().getId();
    }

    static String hash(String token) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }
}
