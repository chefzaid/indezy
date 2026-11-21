package dev.swirlit.indezy.service;

import dev.swirlit.indezy.dto.RateBenchmarkDtos;
import dev.swirlit.indezy.model.Freelance;
import dev.swirlit.indezy.model.Project;
import dev.swirlit.indezy.model.SkillDetail;
import dev.swirlit.indezy.model.User;
import dev.swirlit.indezy.model.enums.ProjectStatus;
import dev.swirlit.indezy.model.enums.RateRegion;
import dev.swirlit.indezy.model.enums.Seniority;
import dev.swirlit.indezy.model.enums.WorkMode;
import dev.swirlit.indezy.repository.FreelanceRepository;
import dev.swirlit.indezy.repository.ProjectRepository;
import dev.swirlit.indezy.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The network's rate benchmark (docs/adr/0014-aggregated-market-data.md): the daily rates of the won missions of the
 * accounts that opted in, one figure per freelancer (their latest mission naming the skill, in the last two years), shown
 * only once {@value #THRESHOLD} freelancers or more back it, never with a client, a contact or a freelancer's identity.
 */
@Service
@RequiredArgsConstructor
public class RateBenchmarkService {

    static final int THRESHOLD = 5;
    static final int MONTHS = 24;
    static final int SKILLS_SHOWN = 30;
    private static final ZoneId PARIS = ZoneId.of("Europe/Paris");
    private static final Set<String> ILE_DE_FRANCE = Set.of("75", "77", "78", "91", "92", "93", "94", "95");
    private static final Pattern POSTAL_CODE = Pattern.compile("\\b(\\d{2})\\d{3}\\b");
    private static final Pattern SKILL_SEPARATOR = Pattern.compile("[,;/|\\n]+");
    private static final int CONFIRMED_YEARS = 3;
    private static final int SENIOR_YEARS = 8;
    private static final int EXPERT_YEARS = 15;
    private static final double LOW = 0.25;
    private static final double MIDDLE = 0.5;
    private static final double HIGH = 0.75;

    private final NetworkService network;
    private final UserRepository users;
    private final FreelanceRepository freelances;
    private final ProjectRepository projects;
    private final Clock clock;

    /** One freelancer's latest rate for a skill: its seniority on it (null when unknown) and where the mission was worked. */
    record Point(Long userId, String skill, Seniority seniority, RateRegion region, int rate, LocalDate on) {
    }

    /** The figure of a skill, for a seniority and a region, or for all of them when null. */
    @Transactional(readOnly = true)
    public RateBenchmarkDtos.Figure figure(Long userId, String skill, Seniority seniority, RateRegion region) {
        network.requireMember(userId);
        String wanted = skill.strip();
        List<Integer> rates = points().stream()
            .filter(point -> point.skill().equalsIgnoreCase(wanted) && (seniority == null || seniority == point.seniority()) &&
                (region == null || region == point.region()))
            .map(Point::rate).sorted().toList();
        boolean shown = rates.size() >= THRESHOLD;
        return new RateBenchmarkDtos.Figure(wanted, seniority, region, rates.size(), THRESHOLD, shown ? percentile(rates, MIDDLE) : null,
            shown ? percentile(rates, LOW) : null, shown ? percentile(rates, HIGH) : null, sharing(userId));
    }

    /** The skills enough freelancers back to give a figure, the most backed first. */
    @Transactional(readOnly = true)
    public RateBenchmarkDtos.Skills skills(Long userId) {
        network.requireMember(userId);
        Map<String, String> names = new HashMap<>();
        Map<String, Integer> counts = new HashMap<>();
        for (Point point : points()) {
            String key = point.skill().toLowerCase(Locale.ROOT);
            names.putIfAbsent(key, point.skill());
            counts.merge(key, 1, Integer::sum);
        }
        List<RateBenchmarkDtos.Skill> skills = counts.entrySet().stream().filter(entry -> entry.getValue() >= THRESHOLD)
            .map(entry -> new RateBenchmarkDtos.Skill(names.get(entry.getKey()), entry.getValue()))
            .sorted(Comparator.comparingInt(RateBenchmarkDtos.Skill::freelancers).reversed().thenComparing(RateBenchmarkDtos.Skill::skill))
            .limit(SKILLS_SHOWN).toList();
        return new RateBenchmarkDtos.Skills(skills, THRESHOLD, sharing(userId));
    }

    private boolean sharing(Long userId) {
        return users.findById(userId).map(User::getShareRates).orElse(false);
    }

    /** One point per freelancer who opted in and skill: their latest won mission naming it in the last two years. */
    List<Point> points() {
        LocalDate since = LocalDate.now(clock.withZone(PARIS)).minusMonths(MONTHS);
        List<Point> points = new ArrayList<>();
        for (User user : users.findByShareRatesTrue()) {
            if (user.getDeletedAt() != null) {
                continue;
            }
            Optional<Freelance> freelance = freelances.findByEmail(user.getEmail());
            if (freelance.isEmpty()) {
                continue;
            }
            Map<String, Point> latest = new LinkedHashMap<>();
            for (Project project : projects.findByFreelanceId(freelance.get().getId())) {
                LocalDate on = project.getStartDate() != null ? project.getStartDate() :
                    project.getCreatedAt() == null ? null : project.getCreatedAt().toLocalDate();
                if (project.getStatus() != ProjectStatus.WON || project.getDailyRate() == null || project.getDailyRate() <= 0 ||
                        on == null || on.isBefore(since) || project.getTechStack() == null) {
                    continue;
                }
                RateRegion region = region(project);
                for (String skill : SKILL_SEPARATOR.split(project.getTechStack())) {
                    String name = skill.strip();
                    if (name.isEmpty()) {
                        continue;
                    }
                    Point point = new Point(user.getId(), name, seniority(user, name), region, project.getDailyRate(), on);
                    latest.merge(name.toLowerCase(Locale.ROOT), point, (kept, other) -> other.on().isAfter(kept.on()) ? other : kept);
                }
            }
            points.addAll(latest.values());
        }
        return points;
    }

    /** Remote when worked from home, else Île-de-France or the regions by the postal code of the place or the client. */
    static RateRegion region(Project project) {
        if (project.getWorkMode() == WorkMode.REMOTE) {
            return RateRegion.REMOTE;
        }
        String place = Objects.toString(project.getWorkAddress(), "") + " " +
            (project.getClient() == null ? "" : Objects.toString(project.getClient().getAddress(), "") + " " +
                Objects.toString(project.getClient().getCity(), ""));
        Matcher postal = POSTAL_CODE.matcher(place);
        if (postal.find()) {
            return ILE_DE_FRANCE.contains(postal.group(1)) ? RateRegion.ILE_DE_FRANCE : RateRegion.REGIONS;
        }
        return place.toLowerCase(Locale.ROOT).contains("paris") ? RateRegion.ILE_DE_FRANCE : null;
    }

    /** The seniority on a skill from the years the profile gives for it: under 3 junior, under 8 confirmed, under 15 senior. */
    static Seniority seniority(User user, String skill) {
        Integer years = user.getSkillDetails().entrySet().stream().filter(entry -> entry.getKey().equalsIgnoreCase(skill))
            .map(Map.Entry::getValue).map(SkillDetail::getYears).filter(Objects::nonNull).findFirst().orElse(null);
        if (years == null) {
            return null;
        }
        return years < CONFIRMED_YEARS ? Seniority.JUNIOR : years < SENIOR_YEARS ? Seniority.CONFIRMED :
            years < EXPERT_YEARS ? Seniority.SENIOR : Seniority.EXPERT;
    }

    /** The nearest-rank percentile of sorted rates. */
    static int percentile(List<Integer> sorted, double share) {
        int rank = (int) Math.ceil(share * sorted.size());
        return sorted.get(Math.max(0, Math.min(sorted.size() - 1, rank - 1)));
    }
}
