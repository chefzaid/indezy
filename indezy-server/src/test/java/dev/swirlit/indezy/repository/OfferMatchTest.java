package dev.swirlit.indezy.repository;

import dev.swirlit.indezy.dto.OfferMatchDto;
import dev.swirlit.indezy.dto.OfferMatchDto.AvailabilityFit;
import dev.swirlit.indezy.dto.OfferMatchDto.PlaceFit;
import dev.swirlit.indezy.dto.OfferMatchDto.RateFit;
import dev.swirlit.indezy.model.JobOffer;
import dev.swirlit.indezy.model.enums.WorkMode;
import dev.swirlit.indezy.repository.JobOfferSpecifications.Origin;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

/** The match score of the cards (the SQL one is checked against it in {@link JobOfferSpecificationsTest}). */
class OfferMatchTest {

    private static final Origin PARIS = new Origin(48.8589, 2.347);
    private static final OfferMatch.Profile JAVA_DEV = new OfferMatch.Profile(List.of("Java", "Spring Boot", "Kafka", "Go"), 600, PARIS);

    @Test
    void countsTheProfileSkillsTheOfferMentionsAsWholeWords() {
        JobOffer offer = offer(o -> {
            o.setSearchText("developpeur java spring boot exemple conseil. vous maitrisez kafka.");
            o.setSkills(List.of("Java", "Spring Boot"));
        });

        assertThat(OfferMatch.score(offer, offer.getSkills(), JAVA_DEV).skills()).containsExactly("Java", "Spring Boot", "Kafka");
        // "javascript" is not Java, and "go" in a text is a word, not the language: short skills come from the skill list.
        JobOffer javascript = offer(o -> o.setSearchText("developpeur javascript on y go"));
        assertThat(OfferMatch.score(javascript, javascript.getSkills(), JAVA_DEV).skills()).isEmpty();
        JobOffer golang = offer(o -> o.setSkills(List.of("Go")));
        assertThat(OfferMatch.score(golang, golang.getSkills(), JAVA_DEV).skills()).containsExactly("Go");
        assertThat(OfferMatch.mentions(null, List.of(), "java")).isFalse();
    }

    @Test
    void givesFullSkillPointsFromThreeSkillsAndScalesToWhatTheProfileStates() {
        JobOffer threeSkills = offer(o -> o.setSearchText("java spring boot kafka"));
        JobOffer oneSkill = offer(o -> o.setSearchText("java"));

        // 60 skill points + rate not stated (10) + place not stated (5), over 100.
        assertThat(OfferMatch.score(threeSkills, threeSkills.getSkills(), JAVA_DEV).score()).isEqualTo(75);
        assertThat(OfferMatch.score(oneSkill, oneSkill.getSkills(), JAVA_DEV).score()).isEqualTo(35);
        // Skills only: 60 points available.
        OfferMatch.Profile skillsOnly = new OfferMatch.Profile(List.of("Java"), null, null);
        OfferMatchDto match = OfferMatch.score(oneSkill, oneSkill.getSkills(), skillsOnly);
        assertThat(match.score()).isEqualTo(100);
        assertThat(match.rate()).isNull();
        assertThat(match.place()).isNull();
        assertThat(OfferMatch.score(oneSkill, oneSkill.getSkills(), new OfferMatch.Profile(List.of(), 600, PARIS))).isNull();
        assertThat(OfferMatch.score(oneSkill, oneSkill.getSkills(), OfferMatch.Profile.at(PARIS))).isNull();
    }

    @Test
    void comparesTheBestRateTheOfferPaysWithTheTarget() {
        assertThat(OfferMatch.rateFit(offer(o -> o.setRateMax(650)), 600)).isEqualTo(RateFit.MEETS);
        assertThat(OfferMatch.rateFit(offer(o -> o.setRateMin(600)), 600)).isEqualTo(RateFit.MEETS);
        assertThat(OfferMatch.rateFit(offer(o -> o.setRateMax(540)), 600)).isEqualTo(RateFit.CLOSE);
        assertThat(OfferMatch.rateFit(offer(o -> o.setRateMax(539)), 600)).isEqualTo(RateFit.BELOW);
        assertThat(OfferMatch.rateFit(offer(o -> { }), 600)).isEqualTo(RateFit.UNKNOWN);
    }

    @Test
    void ratesThePlaceFromTheWorkModeAndTheDistance() {
        assertThat(OfferMatch.placeFit(offer(o -> o.setWorkMode(WorkMode.REMOTE)), PARIS)).isEqualTo(PlaceFit.REMOTE);
        assertThat(OfferMatch.placeFit(offer(o -> at(o, 48.8976, 2.2574)), PARIS)).isEqualTo(PlaceFit.NEAR);
        assertThat(OfferMatch.placeFit(offer(o -> at(o, 49.4175, 2.8261)), PARIS)).isEqualTo(PlaceFit.REACHABLE);
        assertThat(OfferMatch.placeFit(offer(o -> at(o, 45.764, 4.8357)), PARIS)).isEqualTo(PlaceFit.FAR);
        assertThat(OfferMatch.placeFit(offer(o -> {
            at(o, 45.764, 4.8357);
            o.setWorkMode(WorkMode.HYBRID);
            o.setRemoteDaysPerWeek(3);
        }), PARIS)).isEqualTo(PlaceFit.MOSTLY_REMOTE);
        assertThat(OfferMatch.placeFit(offer(o -> o.setWorkMode(WorkMode.HYBRID)), PARIS)).isEqualTo(PlaceFit.UNKNOWN);
    }

    @Test
    void comparesWhenTheMissionStartsWithWhenTheFreelanceIsAvailable() {
        LocalDate today = LocalDate.of(2026, 10, 20);
        OfferMatch.Profile freeInNovember = new OfferMatch.Profile(List.of("Java"), null, null, LocalDate.of(2026, 11, 16), today);

        assertThat(OfferMatch.availabilityFit(offer(o -> o.setStartDate(LocalDate.of(2026, 11, 16))), freeInNovember))
            .isEqualTo(AvailabilityFit.READY);
        assertThat(OfferMatch.availabilityFit(offer(o -> o.setStartDate(LocalDate.of(2026, 11, 2))), freeInNovember))
            .isEqualTo(AvailabilityFit.SOON);
        assertThat(OfferMatch.availabilityFit(offer(o -> o.setStartDate(LocalDate.of(2026, 11, 1))), freeInNovember))
            .isEqualTo(AvailabilityFit.TOO_EARLY);
        assertThat(OfferMatch.availabilityFit(offer(o -> o.setStartAsap(true)), freeInNovember)).isEqualTo(AvailabilityFit.TOO_EARLY);
        assertThat(OfferMatch.availabilityFit(offer(o -> { }), freeInNovember)).isEqualTo(AvailabilityFit.UNKNOWN);

        // Available since a day already past: available today, as soon as possible and on a start already passed.
        OfferMatch.Profile freeNow = new OfferMatch.Profile(List.of("Java"), null, null, LocalDate.of(2026, 9, 1), today);
        assertThat(freeNow.availableFrom()).isEqualTo(today);
        assertThat(OfferMatch.availabilityFit(offer(o -> o.setStartAsap(true)), freeNow)).isEqualTo(AvailabilityFit.READY);
        assertThat(OfferMatch.availabilityFit(offer(o -> o.setStartDate(LocalDate.of(2026, 10, 1))), freeNow)).isEqualTo(AvailabilityFit.READY);
        OfferMatch.Profile freeSoon = new OfferMatch.Profile(List.of("Java"), null, null, LocalDate.of(2026, 10, 30), today);
        assertThat(OfferMatch.availabilityFit(offer(o -> o.setStartAsap(true)), freeSoon)).isEqualTo(AvailabilityFit.SOON);
    }

    @Test
    void scoresTheAvailabilityAsAFourthPartWhenTheProfileStatesIt() {
        LocalDate today = LocalDate.of(2026, 10, 20);
        JobOffer starting = offer(o -> {
            o.setSearchText("java spring boot kafka");
            o.setStartDate(LocalDate.of(2026, 11, 2));
        });
        OfferMatch.Profile available = new OfferMatch.Profile(JAVA_DEV.skills(), 600, PARIS, LocalDate.of(2026, 11, 16), today);

        // 60 skill points + rate not stated (10) + place not stated (5) + starting two weeks early (5), over 110.
        OfferMatchDto match = OfferMatch.score(starting, starting.getSkills(), available);
        assertThat(match.score()).isEqualTo(73);
        assertThat(match.availability()).isEqualTo(AvailabilityFit.SOON);
        assertThat(OfferMatch.score(starting, starting.getSkills(), JAVA_DEV).availability()).isNull();
        // An availability date without a today to read it against counts for nothing.
        assertThat(new OfferMatch.Profile(List.of("Java"), null, null, LocalDate.of(2026, 11, 16), null).availableFrom()).isNull();
    }

    @Test
    void findsASkillUnderAnyOfItsSpellings() {
        OfferMatch.Profile profile = new OfferMatch.Profile(List.of("JS", "JavaScript", "Postgres", "Spring-Boot"), null, null);
        JobOffer offer = offer(o -> {
            o.setSearchText("developpeur fullstack javascript. base postgresql et springboot.");
            o.setSkills(List.of("TS"));
        });

        assertThat(profile.skills()).containsExactly("JS", "Postgres", "Spring-Boot");
        assertThat(OfferMatch.score(offer, offer.getSkills(), profile).skills()).containsExactly("JS", "Postgres", "Spring-Boot");
        JobOffer typed = offer(o -> o.setSkills(List.of("TS")));
        assertThat(OfferMatch.score(typed, typed.getSkills(), new OfferMatch.Profile(List.of("TypeScript"), null, null)).skills())
            .containsExactly("TypeScript");
    }

    @Test
    void keepsEachSkillOnceAndAtMostTwenty() {
        OfferMatch.Profile profile = new OfferMatch.Profile(List.of(" Java ", "JAVA", "Éclair", "eclair", " "), 0, null);

        assertThat(profile.skills()).containsExactly("Java", "Éclair");
        assertThat(profile.targetRate()).isNull();
        assertThat(new OfferMatch.Profile(IntStream.range(0, 30).mapToObj(i -> "skill" + i).toList(), null, null).skills())
            .hasSize(OfferMatch.MAX_SKILLS);
        assertThat(new OfferMatch.Profile(null, null, null).canMatch()).isFalse();
    }

    private static JobOffer offer(Consumer<JobOffer> setup) {
        JobOffer offer = new JobOffer();
        offer.setSearchText("");
        setup.accept(offer);
        return offer;
    }

    private static void at(JobOffer offer, double latitude, double longitude) {
        offer.setLatitude(latitude);
        offer.setLongitude(longitude);
    }
}
