package dev.swirlit.indezy.scraping;

import dev.swirlit.indezy.model.JobOffer;
import dev.swirlit.indezy.model.enums.OfferDatePrecision;
import dev.swirlit.indezy.model.enums.OfferSite;
import dev.swirlit.indezy.model.enums.WorkMode;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OfferNormalizerTest {

    private static final Instant NOW = Instant.parse("2026-10-19T10:00:00Z");

    private final GeoLocator geoLocator = mock(GeoLocator.class);
    private final OfferNormalizer normalizer = new OfferNormalizer(geoLocator);

    @Test
    void completesAFreelanceOffer() {
        when(geoLocator.locate("Courbevoie", "92400"))
            .thenReturn(Optional.of(new GeoLocator.Place("Courbevoie", "92", "11", 48.8976, 2.2574)));
        ScrapedOffer scraped = base()
            .company("  Agence   Exemple ")
            .description("  Ligne 1\nLigne 2  ")
            .rateText("TJM HT max 600 €")
            .remoteText("2 jours de télétravail par semaine")
            .city("Courbevoie")
            .postalCode("92400")
            .skills(List.of("Java", "java", " ", "Spring"))
            .publishedAt(Instant.parse("2026-10-18T09:00:00Z"))
            .build();

        JobOffer offer = normalizer.normalize(OfferSite.COLLECTIVE, scraped, NOW).orElseThrow();

        assertThat(offer.getSite()).isEqualTo(OfferSite.COLLECTIVE);
        assertThat(offer.getCompany()).isEqualTo("Agence Exemple");
        assertThat(offer.getDescription()).isEqualTo("Ligne 1\nLigne 2");
        assertThat(offer.getRateMin()).isNull();
        assertThat(offer.getRateMax()).isEqualTo(600);
        assertThat(offer.getWorkMode()).isEqualTo(WorkMode.HYBRID);
        assertThat(offer.getRemoteDaysPerWeek()).isEqualTo(2);
        assertThat(offer.getDepartmentCode()).isEqualTo("92");
        assertThat(offer.getRegionCode()).isEqualTo("11");
        assertThat(offer.getCountryCode()).isEqualTo("FR");
        assertThat(offer.getLatitude()).isEqualTo(48.8976);
        assertThat(offer.getSkills()).containsExactly("Java", "Spring");
        assertThat(offer.getPublishedAt()).isEqualTo(Instant.parse("2026-10-18T09:00:00Z"));
        assertThat(offer.getDatePrecision()).isEqualTo(OfferDatePrecision.EXACT);
        assertThat(offer.getFirstSeenAt()).isEqualTo(NOW);
        assertThat(offer.getSearchText()).isEqualTo("developpeur java agence exemple courbevoie hauts de seine java spring ligne 1 ligne 2");
    }

    @Test
    void dropsOffersClosedToFreelancersOrWithoutTitleLinkOrId() {
        assertThat(normalizer.normalize(OfferSite.FREE_WORK, base().freelance(false).build(), NOW)).isEmpty();
        assertThat(normalizer.normalize(OfferSite.FREE_WORK, base().title("  ").build(), NOW)).isEmpty();
        assertThat(normalizer.normalize(OfferSite.FREE_WORK, base().url("javascript:alert(1)").build(), NOW)).isEmpty();
        assertThat(normalizer.normalize(OfferSite.FREE_WORK, base().url("/relative").build(), NOW)).isEmpty();
        assertThat(normalizer.normalize(OfferSite.FREE_WORK, base().url("https://exa mple.com").build(), NOW)).isEmpty();
        assertThat(normalizer.normalize(OfferSite.FREE_WORK, base().externalId(null).build(), NOW)).isEmpty();
    }

    @Test
    void keepsSiteCoordinatesAndPlacesByPostalCodeWithoutLookingUp() {
        ScrapedOffer scraped = base().city("Paris").postalCode("75008").countryCode("fr").latitude(48.85).longitude(2.35).build();

        JobOffer offer = normalizer.normalize(OfferSite.FREE_WORK, scraped, NOW).orElseThrow();

        assertThat(offer.getDepartmentCode()).isEqualTo("75");
        assertThat(offer.getRegionCode()).isEqualTo("11");
        assertThat(offer.getCountryCode()).isEqualTo("FR");
        assertThat(offer.getLongitude()).isEqualTo(2.35);
        verify(geoLocator, never()).locate(any(), any());
    }

    @Test
    void placesByDepartmentOrRegionNameAndLeavesForeignCitiesAlone() {
        when(geoLocator.locate(any(), any())).thenReturn(Optional.empty());
        JobOffer byName = normalizer.normalize(OfferSite.LEHIBOU,
            base().city("Meudon").departmentName("Hauts-de-Seine").build(), NOW).orElseThrow();
        JobOffer byRegion = normalizer.normalize(OfferSite.FREE_WORK, base().regionName("Occitanie").build(), NOW).orElseThrow();
        JobOffer foreign = normalizer.normalize(OfferSite.COLLECTIVE, base().city("Bruxelles").countryCode("BE").build(), NOW).orElseThrow();

        assertThat(byName.getDepartmentCode()).isEqualTo("92");
        assertThat(byName.getCountryCode()).isEqualTo("FR");
        assertThat(byRegion.getRegionCode()).isEqualTo("76");
        assertThat(byRegion.getDepartmentCode()).isNull();
        assertThat(foreign.getCountryCode()).isEqualTo("BE");
        assertThat(foreign.getDepartmentCode()).isNull();
        verify(geoLocator, never()).locate("Bruxelles", null);
    }

    @Test
    void placesCitiesThatAreAreasOrBusinessDistricts() {
        when(geoLocator.locate(any(), any())).thenReturn(Optional.empty());
        when(geoLocator.locate("Puteaux", null)).thenReturn(Optional.of(new GeoLocator.Place("Puteaux", "92", "11", 48.88, 2.24)));
        when(geoLocator.locate("Paris", null)).thenReturn(Optional.of(new GeoLocator.Place("Paris", "75", "11", 48.8589, 2.347)));

        JobOffer department = normalizer.normalize(OfferSite.COLLECTIVE, base().city("Hauts-de-Seine").build(), NOW).orElseThrow();
        JobOffer region = normalizer.normalize(OfferSite.COLLECTIVE, base().city("Île-de-France").build(), NOW).orElseThrow();
        JobOffer district = normalizer.normalize(OfferSite.COLLECTIVE, base().city("La Défense").build(), NOW).orElseThrow();
        JobOffer paris = normalizer.normalize(OfferSite.COLLECTIVE, base().city("Paris").build(), NOW).orElseThrow();

        assertThat(department.getDepartmentCode()).isEqualTo("92");
        assertThat(department.getCountryCode()).isEqualTo("FR");
        assertThat(region.getRegionCode()).isEqualTo("11");
        assertThat(region.getDepartmentCode()).isNull();
        verify(geoLocator, never()).locate("Hauts-de-Seine", null);
        verify(geoLocator, never()).locate("Île-de-France", null);
        assertThat(district.getDepartmentCode()).isEqualTo("92");
        assertThat(district.getLatitude()).isEqualTo(48.88);
        assertThat(district.getCity()).isEqualTo("La Défense");
        assertThat(paris.getDepartmentCode()).isEqualTo("75");
        assertThat(paris.getLatitude()).isEqualTo(48.8589);
    }

    @Test
    void readsTheDurationFromTheTitleOrTheDescription() {
        JobOffer fromTitle = normalizer.normalize(OfferSite.COLLECTIVE, base().title("PMO - freelance - 7 mois").build(), NOW).orElseThrow();
        JobOffer fromText = normalizer.normalize(OfferSite.COLLECTIVE,
            base().description("Contexte\nDurée : 6 mois renouvelables").build(), NOW).orElseThrow();
        JobOffer stated = normalizer.normalize(OfferSite.COLLECTIVE,
            base().title("PMO 7 mois").durationMonths(12).build(), NOW).orElseThrow();
        JobOffer none = normalizer.normalize(OfferSite.COLLECTIVE, base().description("5 ans d'expérience").build(), NOW).orElseThrow();

        assertThat(fromTitle.getDurationMonths()).isEqualTo(7);
        assertThat(fromText.getDurationMonths()).isEqualTo(6);
        assertThat(stated.getDurationMonths()).isEqualTo(12);
        assertThat(none.getDurationMonths()).isNull();
    }

    @Test
    void rejectsImplausibleValues() {
        ScrapedOffer scraped = base().rateMin(900).rateMax(450).durationMonths(120).remoteDaysPerWeek(9)
            .latitude(0.0).longitude(200.0).build();

        JobOffer offer = normalizer.normalize(OfferSite.YEETS, scraped, NOW).orElseThrow();

        assertThat(offer.getRateMin()).isEqualTo(450);
        assertThat(offer.getRateMax()).isEqualTo(900);
        assertThat(offer.getDurationMonths()).isNull();
        assertThat(offer.getRemoteDaysPerWeek()).isNull();
        assertThat(offer.getLatitude()).isNull();
        assertThat(offer.getLongitude()).isNull();

        JobOffer outOfRange = normalizer.normalize(OfferSite.YEETS, base().rateMin(50000).build(), NOW).orElseThrow();
        assertThat(outOfRange.getRateMin()).isNull();
    }

    @Test
    void forcesRemoteDaysToMatchFullRemoteAndOnSite() {
        JobOffer remote = normalizer.normalize(OfferSite.YEETS, base().workMode(WorkMode.REMOTE).build(), NOW).orElseThrow();
        JobOffer onsite = normalizer.normalize(OfferSite.YEETS, base().workMode(WorkMode.ONSITE).remoteDaysPerWeek(2).build(), NOW)
            .orElseThrow();

        assertThat(remote.getRemoteDaysPerWeek()).isEqualTo(5);
        assertThat(onsite.getRemoteDaysPerWeek()).isZero();
    }

    @Test
    void datesUndatedOffersWhenFirstSeenAndNeverInTheFuture() {
        JobOffer undated = normalizer.normalize(OfferSite.FREE_WORK, base().build(), NOW).orElseThrow();
        JobOffer future = normalizer.normalize(OfferSite.FREE_WORK, base().publishedAt(NOW.plusSeconds(3600)).build(), NOW).orElseThrow();
        JobOffer day = normalizer.normalize(OfferSite.FREE_WORK,
            base().publishedAt(Instant.parse("2026-10-18T00:00:00Z")).publishedDayOnly(true).build(), NOW).orElseThrow();

        assertThat(undated.getPublishedAt()).isEqualTo(NOW);
        assertThat(undated.getDatePrecision()).isEqualTo(OfferDatePrecision.FIRST_SEEN);
        assertThat(future.getPublishedAt()).isEqualTo(NOW);
        assertThat(day.getDatePrecision()).isEqualTo(OfferDatePrecision.DAY);
    }

    @Test
    void capsTextsSkillsAndDescriptions() {
        List<String> skills = IntStream.range(0, 40).mapToObj(i -> "skill-" + i).toList();
        ScrapedOffer scraped = base().title("t".repeat(400)).skills(skills)
            .skills(Collections.singletonList("s".repeat(80))).description("d".repeat(HtmlText.MAX_LENGTH + 10)).build();

        JobOffer offer = normalizer.normalize(OfferSite.FREE_WORK, scraped, NOW).orElseThrow();

        assertThat(offer.getTitle()).hasSize(300);
        assertThat(offer.getSkills()).hasSize(OfferNormalizer.MAX_SKILLS);
        assertThat(offer.getDescription()).hasSize(HtmlText.MAX_LENGTH);
        assertThat(OfferNormalizer.cap("s".repeat(80), OfferNormalizer.MAX_SKILL_LENGTH)).hasSize(60);
        assertThat(OfferNormalizer.cap(null, 10)).isNull();
        assertThat(OfferNormalizer.webLink(null)).isNull();
        assertThat(OfferNormalizer.webLink("https://x.fr/" + "a".repeat(1000))).isNull();
    }

    private static ScrapedOffer.ScrapedOfferBuilder base() {
        return ScrapedOffer.builder().externalId("42").url("https://www.example.com/offre/42").title("Développeur Java");
    }
}
