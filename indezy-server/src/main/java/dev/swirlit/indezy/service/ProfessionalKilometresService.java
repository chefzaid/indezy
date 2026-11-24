package dev.swirlit.indezy.service;

import dev.swirlit.indezy.model.Freelance;
import dev.swirlit.indezy.model.User;
import dev.swirlit.indezy.repository.FreelanceRepository;
import dev.swirlit.indezy.repository.UserRepository;
import dev.swirlit.indezy.tax.KilometreScale;
import org.springframework.beans.factory.annotation.Autowired;
import dev.swirlit.indezy.model.ActivityDay;
import dev.swirlit.indezy.model.ActivityReport;
import dev.swirlit.indezy.model.Project;
import dev.swirlit.indezy.model.enums.ActivityDayType;
import dev.swirlit.indezy.model.enums.ActivityReportStatus;
import dev.swirlit.indezy.model.enums.DocumentLanguage;
import dev.swirlit.indezy.repository.ActivityReportRepository;
import dev.swirlit.indezy.util.CsvText;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

/**
 * The year's professional kilometres (see docs/features.md#professional-kilometres): each validated activity report's
 * days on site times its mission's round trip by car, a row per month and mission, then a total per client and the
 * year's total, for the accountant to claim mileage.
 */
@Service
@RequiredArgsConstructor
public class ProfessionalKilometresService {

    static final String SEPARATOR = ";";
    private static final BigDecimal METRES_PER_KM = BigDecimal.valueOf(1000);
    private static final String[] FRENCH_HEADERS = {"Mois", "Client", "Mission", "Jours sur site", "Aller-retour (km)", "Kilomètres"};
    private static final String[] ENGLISH_HEADERS = {"Month", "Client", "Mission", "Days on site", "Round trip (km)", "Kilometres"};

    private final ActivityReportRepository reports;
    private final CommuteService commutes;

    private FreelanceRepository freelances;
    private UserRepository users;

    /** The workspace's account, for its car: without them, the CSV gives no amount. */
    @Autowired(required = false)
    void setAccounts(FreelanceRepository freelanceRepository, UserRepository userRepository) {
        this.freelances = freelanceRepository;
        this.users = userRepository;
    }

    @Transactional(readOnly = true)
    public String csv(Long freelanceId, int year, DocumentLanguage language) {
        boolean french = language != DocumentLanguage.EN;
        List<ActivityReport> validated = reports.findByProjectFreelanceIdOrderByReportYearDescReportMonthDesc(freelanceId).stream()
            .filter(report -> report.getReportYear() == year && report.getStatus() == ActivityReportStatus.VALIDATED)
            .sorted(Comparator.comparingInt(ActivityReport::getReportMonth).thenComparing(report -> report.getProject().getId()))
            .toList();
        Map<Long, Optional<Integer>> metres = new HashMap<>();
        String[] headers = french ? FRENCH_HEADERS : ENGLISH_HEADERS;
        StringBuilder csv = new StringBuilder(CsvText.row(SEPARATOR, headers)).append("\r\n");
        BigDecimal total = BigDecimal.ZERO;
        // Per client, by name: its days on site and its kilometres (when some trip is known).
        Map<String, BigDecimal[]> byClient = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        for (ActivityReport report : validated) {
            Project mission = report.getProject();
            BigDecimal days = BigDecimal.valueOf(ActivityDay.daysOf(report.getDays(), ActivityDayType.ONSITE));
            Optional<BigDecimal> roundTrip = metres.computeIfAbsent(mission.getId(), id -> commutes.drivingMeters(mission))
                .map(oneWay -> BigDecimal.valueOf(2L * oneWay).divide(METRES_PER_KM, 1, RoundingMode.HALF_UP));
            BigDecimal kilometres = roundTrip.map(trip -> trip.multiply(days).setScale(1, RoundingMode.HALF_UP)).orElse(null);
            total = kilometres == null ? total : total.add(kilometres);
            String client = mission.getClient() == null ? null : mission.getClient().getCompanyName();
            if (client != null && !client.isBlank() && kilometres != null) {
                BigDecimal[] sums = byClient.computeIfAbsent(client.strip(), name -> new BigDecimal[] {BigDecimal.ZERO, BigDecimal.ZERO});
                sums[0] = sums[0].add(days);
                sums[1] = sums[1].add(kilometres);
            }
            csv.append(CsvText.row(SEPARATOR, String.format("%d-%02d", year, report.getReportMonth()),
                CsvText.text(mission.getClient() == null ? null : mission.getClient().getCompanyName()), CsvText.text(mission.getRole()),
                number(days, french), roundTrip.map(trip -> number(trip, french)).orElse(""),
                kilometres == null ? "" : number(kilometres, french))).append("\r\n");
        }
        byClient.forEach((client, sums) -> csv.append(CsvText.row(SEPARATOR, "Total", CsvText.text(client), "", number(sums[0], french), "",
            number(sums[1].setScale(1, RoundingMode.HALF_UP), french))).append("\r\n"));
        csv.append(CsvText.row(SEPARATOR, "Total", "", "", "", "", number(total.setScale(1, RoundingMode.HALF_UP), french))).append("\r\n");
        // With the car's horsepower given, the amount the kilometre scale gives for the year's distance.
        BigDecimal driven = total;
        car(freelanceId).ifPresent(car -> {
            BigDecimal amount = KilometreScale.amount(car.getVehicleHorsepower(), driven, Boolean.TRUE.equals(car.getVehicleElectric()));
            String label = (french ? "Barème kilométrique " : "Kilometre scale ") + horsepower(car.getVehicleHorsepower(), french) +
                (Boolean.TRUE.equals(car.getVehicleElectric()) ? (french ? ", électrique (+20 %)" : ", electric (+20%)") : "") + " (€)";
            csv.append(CsvText.row(SEPARATOR, label, "", "", "", "", number(amount, french))).append("\r\n");
        });
        return csv.toString();
    }

    private Optional<User> car(Long freelanceId) {
        if (freelances == null || users == null) {
            return Optional.empty();
        }
        return freelances.findById(freelanceId).map(Freelance::getEmail).flatMap(users::findByEmail)
            .filter(user -> user.getVehicleHorsepower() != null);
    }

    private static String horsepower(int horsepower, boolean french) {
        if (horsepower <= KilometreScale.MIN_HORSEPOWER) {
            return french ? "3 CV et moins" : "3 CV or less";
        }
        return horsepower >= KilometreScale.MAX_HORSEPOWER ? (french ? "7 CV et plus" : "7 CV or more") : horsepower + " CV";
    }

    private static String number(BigDecimal value, boolean french) {
        BigDecimal stripped = value.stripTrailingZeros();
        String plain = (stripped.scale() < 0 ? stripped.setScale(0, RoundingMode.UNNECESSARY) : stripped).toPlainString();
        return french ? plain.replace('.', ',') : plain;
    }
}
