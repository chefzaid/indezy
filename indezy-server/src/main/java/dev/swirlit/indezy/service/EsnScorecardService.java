package dev.swirlit.indezy.service;

import dev.swirlit.indezy.dto.InvoiceDtos;
import dev.swirlit.indezy.model.Client;
import dev.swirlit.indezy.model.Freelance;
import dev.swirlit.indezy.model.JobOffer;
import dev.swirlit.indezy.model.User;
import dev.swirlit.indezy.repository.ClientRepository;
import dev.swirlit.indezy.repository.FreelanceRepository;
import dev.swirlit.indezy.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * The ESN scorecards across members (docs/adr/0016-esn-scorecards-across-members.md): each ESN's offer rate, process
 * length, margin range, answer time and payment delay, from the private scorecards of the members who did not turn it off,
 * one point per freelancer and ESN, a figure shown only once {@value #THRESHOLD} freelancers or more back it. The ESN (a
 * company) is named; no freelancer, client or contact is.
 */
@Service
@RequiredArgsConstructor
public class EsnScorecardService {

    static final int THRESHOLD = 5;
    static final int SHOWN = 50;
    private static final double LOW = 0.25;
    private static final double MIDDLE = 0.5;
    private static final double HIGH = 0.75;

    private final NetworkService network;
    private final UserRepository users;
    private final FreelanceRepository freelances;
    private final ClientRepository clients;
    private final CompanyScorecardService scorecards;
    private final InvoicePaymentDelays paymentDelays;

    /** One freelancer's record of one ESN, each figure null when their records do not give it. */
    record Point(Long userId, String key, String name, int opportunities, Integer offerRate, Integer processDays, Integer margin,
                 Integer answerDays, Integer paymentDays) {
    }

    /**
     * An ESN's figures across members: how many freelancers back it, and each figure (the median, the margin as its middle
     * half) only once {@value #THRESHOLD} of them give it, else null; {@code sharing} whether the reader's records count.
     */
    public record Figures(String company, int freelancers, int threshold, Integer offerRate, Integer processDays, Integer marginLow,
                          Integer marginHigh, Integer answerDays, Integer paymentDays, boolean sharing) {
    }

    /** The ESNs enough freelancers back, the most backed first. */
    public record Board(List<Figures> companies, int threshold, boolean sharing) {

        public Board {
            companies = List.copyOf(companies);
        }
    }

    @Transactional(readOnly = true)
    public Board board(Long userId) {
        network.requireMember(userId);
        boolean sharing = sharing(userId);
        Map<String, List<Point>> byCompany = points().stream().collect(Collectors.groupingBy(Point::key));
        List<Figures> shown = byCompany.values().stream().filter(points -> points.size() >= THRESHOLD)
            .map(points -> figures(points, sharing))
            .sorted(Comparator.comparingInt(Figures::freelancers).reversed().thenComparing(Figures::company, String.CASE_INSENSITIVE_ORDER))
            .limit(SHOWN).toList();
        return new Board(shown, THRESHOLD, sharing);
    }

    /** The figures of the workspace's company {@code companyId} across members, matched by its name. */
    @Transactional(readOnly = true)
    public Figures company(Long userId, Long companyId) {
        network.requireMember(userId);
        Client company = clients.findById(companyId).orElseThrow();
        String key = JobOffer.companyKeyOf(company.getCompanyName());
        List<Point> points = key == null ? List.of() : points().stream().filter(point -> point.key().equals(key)).toList();
        if (points.isEmpty()) {
            return new Figures(company.getCompanyName(), 0, THRESHOLD, null, null, null, null, null, null, sharing(userId));
        }
        return figures(points, sharing(userId));
    }

    private boolean sharing(Long userId) {
        return users.findById(userId).map(User::getShareEsnRecords).orElse(false);
    }

    private static Figures figures(List<Point> points, boolean sharing) {
        String name = points.stream().collect(Collectors.groupingBy(Point::name, Collectors.counting())).entrySet().stream()
            .max(Map.Entry.<String, Long>comparingByValue().thenComparing(Map.Entry.comparingByKey())).map(Map.Entry::getKey).orElse("");
        int backers = points.size();
        List<Integer> margins = values(points, Point::margin);
        boolean margined = backers >= THRESHOLD && margins.size() >= THRESHOLD;
        return new Figures(name, backers, THRESHOLD, median(points, Point::offerRate), median(points, Point::processDays),
            margined ? percentile(margins, LOW) : null, margined ? percentile(margins, HIGH) : null, median(points, Point::answerDays),
            median(points, Point::paymentDays), sharing);
    }

    private static Integer median(List<Point> points, Function<Point, Integer> figure) {
        List<Integer> values = values(points, figure);
        return points.size() >= THRESHOLD && values.size() >= THRESHOLD ? percentile(values, MIDDLE) : null;
    }

    private static List<Integer> values(List<Point> points, Function<Point, Integer> figure) {
        return points.stream().map(figure).filter(Objects::nonNull).sorted().toList();
    }

    /** The value at a share of the sorted values, between its two neighbours. */
    static int percentile(List<Integer> sorted, double share) {
        double position = share * (sorted.size() - 1);
        int below = (int) Math.floor(position);
        int above = Math.min(below + 1, sorted.size() - 1);
        return (int) Math.round(sorted.get(below) + (sorted.get(above) - sorted.get(below)) * (position - below));
    }

    /** One point per freelancer sharing and ESN of their workspace with an opportunity: their private scorecard of it. */
    List<Point> points() {
        List<Point> points = new ArrayList<>();
        for (User user : users.findByShareEsnRecordsIsNullOrShareEsnRecordsTrue()) {
            if (user.getDeletedAt() != null) {
                continue;
            }
            Optional<Freelance> freelance = freelances.findByEmail(user.getEmail());
            if (freelance.isEmpty()) {
                continue;
            }
            Map<Long, Long> paid = paymentDelays.byClient(freelance.get().getId()).stream()
                .collect(Collectors.toMap(InvoiceDtos.PaymentDelay::clientId, InvoiceDtos.PaymentDelay::averageDays));
            Map<String, Point> byKey = new HashMap<>();
            for (Client company : clients.findByFreelanceId(freelance.get().getId())) {
                String key = JobOffer.companyKeyOf(company.getCompanyName());
                if (Boolean.TRUE.equals(company.getIsFinal()) || key == null) {
                    continue;
                }
                CompanyScorecardService.Scorecard card = scorecards.scorecard(company.getId());
                if (card.opportunities() == 0) {
                    continue;
                }
                Integer margin = card.marginMin() == null ? null : (int) Math.round((card.marginMin() + card.marginMax()) / 2.0);
                Long days = paid.get(company.getId());
                Point point = new Point(user.getId(), key, company.getCompanyName().strip(), card.opportunities(), card.offerRate(),
                    card.processDays(), margin, card.answerDays(), days == null ? null : days.intValue());
                // A company entered twice in one workspace counts once, by the record with the most opportunities.
                byKey.merge(key, point, (kept, other) -> other.opportunities() > kept.opportunities() ? other : kept);
            }
            points.addAll(byKey.values());
        }
        return points;
    }
}
