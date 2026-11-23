package dev.swirlit.indezy.integration;

import dev.swirlit.indezy.model.JobOffer;
import dev.swirlit.indezy.model.enums.OfferDatePrecision;
import dev.swirlit.indezy.model.enums.OfferSite;
import dev.swirlit.indezy.model.enums.Seniority;
import dev.swirlit.indezy.model.enums.WorkMode;
import dev.swirlit.indezy.repository.JobOfferRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** The quarterly market report, read without an account: figures from ten missions or more, each counted once. */
class MarketReportIntegrationTest extends TwoAccountsIntegrationTest {

    @Autowired
    private JobOfferRepository offers;

    private JobOffer offer(int rate, LocalDate published, Seniority seniority, Long copyGroup) {
        JobOffer offer = new JobOffer();
        offer.setSite(OfferSite.FREE_WORK);
        offer.setExternalId("report-" + UUID.randomUUID());
        offer.setUrl("https://www.free-work.com/fr/tech-it/job-mission/zig/" + UUID.randomUUID());
        offer.setTitle("Développeur Zigreport");
        offer.setCompany("Conseil R");
        offer.setRateMin(rate - 50);
        offer.setRateMax(rate);
        offer.setWorkMode(WorkMode.HYBRID);
        offer.setSkills(List.of("Zigreport"));
        offer.setSearchText("zigreport");
        offer.setSeniority(seniority);
        offer.setCopyGroup(copyGroup);
        var at = published.atStartOfDay(ZoneId.of("Europe/Paris")).plusHours(10).toInstant();
        offer.setPublishedAt(at);
        offer.setDatePrecision(OfferDatePrecision.EXACT);
        offer.setFirstSeenAt(at);
        offer.setLastSeenAt(at);
        return offer;
    }

    @Test
    @SuppressWarnings("unchecked")
    void reportsTheRatesOfLastQuarterOpenly() {
        LocalDate today = LocalDate.now(ZoneId.of("Europe/Paris"));
        LocalDate thisQuarter = LocalDate.of(today.getYear(), (today.getMonthValue() - 1) / 3 * 3 + 1, 1);
        LocalDate lastQuarter = thisQuarter.minusMonths(3);
        for (int index = 0; index < 11; index++) {
            offers.save(offer(500 + index * 10, lastQuarter.plusDays(index), index < 6 ? Seniority.SENIOR : Seniority.JUNIOR, null));
        }
        // A copy of the same mission on another site does not count twice.
        offers.save(offer(900, lastQuarter.plusDays(20), Seniority.SENIOR, 777L));
        offers.save(offer(900, lastQuarter.plusDays(21), Seniority.SENIOR, 777L));

        Map<String, Object> report = exchange("/public/market-report", HttpMethod.GET, HttpEntity.EMPTY, Map.class).getBody();
        assertThat(report).containsEntry("threshold", 10);
        Map<String, Object> zig = ((List<Map<String, Object>>) report.get("skills")).stream()
            .filter(skill -> "Zigreport".equals(skill.get("skill"))).findFirst().orElseThrow();
        assertThat((Map<String, Object>) zig.get("figure")).containsEntry("offers", 12).containsEntry("median", 550);
        // Six senior and five junior missions: neither reaches ten, so neither shows.
        assertThat((Map<String, Object>) zig.get("bySeniority")).isEmpty();

        assertThat(exchange("/public/market-report?quarter=1999-Q1", HttpMethod.GET, HttpEntity.EMPTY, String.class).getStatusCode())
            .isEqualTo(HttpStatus.BAD_REQUEST);
    }
}
