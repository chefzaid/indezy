package dev.swirlit.indezy.service;

import dev.swirlit.indezy.dto.YearlyReviewDto;
import dev.swirlit.indezy.dto.YearlyReviewDto.Mission;
import dev.swirlit.indezy.dto.YearlyReviewDto.Totals;
import dev.swirlit.indezy.model.ActivityReport;
import dev.swirlit.indezy.model.BaseEntity;
import dev.swirlit.indezy.model.Client;
import dev.swirlit.indezy.model.Project;
import dev.swirlit.indezy.model.enums.DocumentLanguage;
import dev.swirlit.indezy.model.enums.LostReason;
import dev.swirlit.indezy.model.enums.ProjectStatus;
import dev.swirlit.indezy.repository.ActivityReportRepository;
import dev.swirlit.indezy.repository.ClientRepository;
import dev.swirlit.indezy.repository.ContactInteractionRepository;
import dev.swirlit.indezy.repository.ContactRepository;
import dev.swirlit.indezy.repository.FreelanceRepository;
import dev.swirlit.indezy.pdf.YearlyReviewPdfRenderer;
import dev.swirlit.indezy.repository.ProjectRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/** Builds a workspace's yearly review, shown in the app and exported as a PDF. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class YearlyReviewService {

    /** Working days a year assumed for a mission that states none, as the projects list does. */
    static final int DEFAULT_DAYS_PER_YEAR = Project.DEFAULT_DAYS_PER_YEAR;
    static final int TOP_SKILLS = 5;
    private static final ZoneId PARIS = ZoneId.of("Europe/Paris");

    private final ProjectRepository projects;
    private final ActivityReportRepository reports;
    private final ClientRepository clients;
    private final ContactRepository contacts;
    private final ContactInteractionRepository interactions;
    private final FreelanceRepository freelances;
    private final YearlyReviewPdfRenderer pdfRenderer;
    private final Clock clock;

    public YearlyReviewDto review(Long freelanceId, int year) {
        List<Project> all = projects.findByFreelanceId(freelanceId);
        List<ActivityReport> workspaceReports = reports.findByProjectFreelanceIdOrderByReportYearDescReportMonthDesc(freelanceId);
        List<Mission> missions = missions(all, workspaceReports, year);
        List<Project> opened = all.stream().filter(project -> inYear(project, year)).toList();
        List<Client> newClients = clients.findByFreelanceId(freelanceId).stream().filter(client -> inYear(client, year)).toList();
        String name = freelances.findById(freelanceId)
            .map(freelance -> String.join(" ", Objects.requireNonNullElse(freelance.getFirstName(), ""),
                Objects.requireNonNullElse(freelance.getLastName(), "")).strip())
            .orElse("");
        return new YearlyReviewDto(year, name, missions, totals(missions), totals(missions(all, workspaceReports, year - 1)),
            pipeline(opened),
            DashboardAnalytics.buildSkillTrends(opened).stream().limit(TOP_SKILLS)
                .map(trend -> new YearlyReviewDto.SkillCount(trend.getSkill(), trend.getCount())).toList(),
            new YearlyReviewDto.Network(
                (int) newClients.stream().filter(client -> !Boolean.FALSE.equals(client.getIsFinal())).count(),
                (int) newClients.stream().filter(client -> Boolean.FALSE.equals(client.getIsFinal())).count(),
                (int) contacts.findByFreelanceId(freelanceId).stream().filter(contact -> inYear(contact, year)).count(),
                (int) interactions.countByContactFreelanceIdAndOccurredOnBetween(freelanceId, LocalDate.of(year, 1, 1),
                    LocalDate.of(year, 12, 31))));
    }

    /** The review as a PDF in {@code language}, named after its year. */
    public ActivityReportService.PdfFile renderPdf(Long freelanceId, int year, DocumentLanguage language) {
        byte[] content = pdfRenderer.render(review(freelanceId, year), language, LocalDate.now(clock.withZone(PARIS)));
        return new ActivityReportService.PdfFile((language == DocumentLanguage.EN ? "indezy-year-in-review-" : "indezy-bilan-") + year + ".pdf", content);
    }

    /** The won missions worked in {@code year}, earliest first. */
    static List<Mission> missions(List<Project> projects, List<ActivityReport> reports, int year) {
        LocalDate first = LocalDate.of(year, 1, 1);
        LocalDate last = LocalDate.of(year, 12, 31);
        Map<Long, Double> reported = reports.stream()
            .filter(report -> report.getReportYear() == year && report.getProject() != null)
            .collect(Collectors.groupingBy(report -> report.getProject().getId(), Collectors.summingDouble(ActivityReport::getWorkedDays)));
        return projects.stream()
            .filter(project -> project.getStatus() == ProjectStatus.WON && project.getStartDate() != null)
            .filter(project -> !project.getStartDate().isAfter(last) && (end(project) == null || !end(project).isBefore(first)))
            .sorted(Comparator.comparing(Project::getStartDate))
            .map(project -> {
                boolean fromReports = reported.containsKey(project.getId());
                double days = fromReports ? reported.get(project.getId()) : estimatedDays(project, first, last);
                Long revenue = project.getDailyRate() == null ? null : Math.round(days * project.getDailyRate());
                return new Mission(project.getId(), project.getRole(),
                    project.getClient() == null ? null : project.getClient().getCompanyName(), project.getDailyRate(),
                    project.getStartDate(), end(project), days, fromReports, revenue);
            })
            .toList();
    }

    /** The mission's last day: the day it was ended, else its start plus its duration, or none while it is open-ended. */
    static LocalDate end(Project project) {
        return project.lastDay();
    }

    /** Its working days within the year, from the calendar days it covers there and its days per year, by halves. */
    static double estimatedDays(Project project, LocalDate first, LocalDate last) {
        LocalDate from = project.getStartDate().isAfter(first) ? project.getStartDate() : first;
        LocalDate end = end(project);
        LocalDate to = end == null || end.isAfter(last) ? last : end;
        double covered = ChronoUnit.DAYS.between(from, to) + 1;
        int perYear = Objects.requireNonNullElse(project.getDaysPerYear(), DEFAULT_DAYS_PER_YEAR);
        return Math.round(covered / first.lengthOfYear() * perYear * 2) / 2.0;
    }

    static Totals totals(List<Mission> missions) {
        long revenue = missions.stream().map(Mission::revenue).filter(Objects::nonNull).mapToLong(Long::longValue).sum();
        double paidDays = missions.stream().filter(mission -> mission.revenue() != null).mapToDouble(Mission::days).sum();
        double days = missions.stream().mapToDouble(Mission::days).sum();
        return new Totals(revenue, days, paidDays > 0 ? (int) Math.round(revenue / paidDays) : null, missions.size());
    }

    static YearlyReviewDto.Pipeline pipeline(List<Project> opened) {
        int won = (int) opened.stream().filter(project -> project.getStatus() == ProjectStatus.WON).count();
        int lost = (int) opened.stream().filter(project -> project.getStatus() == ProjectStatus.LOST).count();
        Map<LostReason, Integer> reasons = new EnumMap<>(LostReason.class);
        opened.stream().filter(project -> project.getStatus() == ProjectStatus.LOST && project.getLostReason() != null)
            .forEach(project -> reasons.merge(project.getLostReason(), 1, Integer::sum));
        return new YearlyReviewDto.Pipeline(opened.size(), won, lost,
            won + lost == 0 ? null : Math.round(won * 100f / (won + lost)),
            reasons.entrySet().stream()
                .sorted(Map.Entry.<LostReason, Integer>comparingByValue().reversed())
                .map(entry -> new YearlyReviewDto.ReasonCount(entry.getKey(), entry.getValue())).toList());
    }

    private static boolean inYear(BaseEntity entity, int year) {
        return entity.getCreatedAt() != null && entity.getCreatedAt().getYear() == year;
    }
}
