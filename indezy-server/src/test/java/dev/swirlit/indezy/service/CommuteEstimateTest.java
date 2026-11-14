package dev.swirlit.indezy.service;

import dev.swirlit.indezy.dto.OfferCommuteDto;
import dev.swirlit.indezy.model.enums.TravelMode;
import dev.swirlit.indezy.repository.JobOfferSpecifications;
import dev.swirlit.indezy.scraping.GeoLocator;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/** The trip estimated from the distance alone, without Google: its speeds, its rush hour and the farthest a trip reaches. */
class CommuteEstimateTest {

    @Test
    void estimatesByCarAndTransitWithTheirSpeeds() {
        // 10 km straight: 13 km of road at 50 km/h plus 5 minutes by car; at 18 km/h plus 15 minutes by transit.
        // 13 road km: 5 at 30 km/h, 8 at 70, and parking; by transit 10 at 18 km/h, 3 at 45, and the walk.
        assertThat(CommuteService.estimated(10, TravelMode.DRIVING, false).seconds()).isEqualTo((int) Math.round((10 + 8 / 70.0 * 60 + 5) * 60));
        assertThat(CommuteService.estimated(10, TravelMode.TRANSIT, false).seconds()).isEqualTo((int) Math.round((10 / 18.0 * 60 + 4 + 15) * 60));
        assertThat(CommuteService.estimated(2, TravelMode.DRIVING, false).seconds()).isEqualTo((int) Math.round((2.6 / 30 * 60 + 5) * 60));
        // 130 road km by transit: 10 at 18 km/h, 30 at 45 and 90 at 70.
        assertThat(CommuteService.estimated(100, TravelMode.TRANSIT, false).seconds())
            .isEqualTo((int) Math.round((10 / 18.0 * 60 + 40 + 90 / 70.0 * 60 + 15) * 60));
        assertThat(CommuteService.estimated(10, TravelMode.DRIVING, false).meters()).isEqualTo(13000);
    }

    @Test
    void estimatesAnOffersTripInMinutesWithTheRushHourOfItsRegion() {
        OfferCommuteDto paris = CommuteService.offerCommute(30, "92");
        OfferCommuteDto lyon = CommuteService.offerCommute(30, "69");
        assertThat(paris.carMinutes()).isEqualTo(lyon.carMinutes())
            .isEqualTo((int) Math.round(CommuteService.estimated(30, TravelMode.DRIVING, true).seconds() / 60.0));
        assertThat(paris.carRushMinutes()).isGreaterThan(lyon.carRushMinutes());
        assertThat(paris.transitRushMinutes()).isEqualTo(lyon.transitRushMinutes()).isGreaterThan(paris.transitMinutes());
        assertThat(CommuteService.offerCommute(30, null)).isEqualTo(lyon);
    }

    @Test
    void findsTheFarthestDistanceATripFitsIn() {
        for (TravelMode mode : TravelMode.values()) {
            double km = CommuteService.reachKm(60, mode, true);
            assertThat(CommuteService.estimated(km, mode, true).rushSeconds()).isLessThanOrEqualTo(3600);
            assertThat(CommuteService.estimated(km + 0.5, mode, true).rushSeconds()).isGreaterThan(3600);
        }
        // Rush hour weighs more in Île-de-France: the same hour reaches less far there.
        assertThat(CommuteService.reachKm(60, TravelMode.DRIVING, true)).isLessThan(CommuteService.reachKm(60, TravelMode.DRIVING, false));
        assertThat(CommuteService.reachKm(1, TravelMode.TRANSIT, false)).isZero();
    }

    @Test
    void neverTakesLessTimeForALongerTrip() {
        for (TravelMode mode : TravelMode.values()) {
            for (boolean ileDeFrance : new boolean[] {false, true}) {
                CommuteService.Trip previous = CommuteService.estimated(0, mode, ileDeFrance);
                for (double km = 0.1; km <= 150; km += 0.1) {
                    CommuteService.Trip trip = CommuteService.estimated(km, mode, ileDeFrance);
                    assertThat(trip.seconds()).as("%s %.1f km", mode, km).isGreaterThanOrEqualTo(previous.seconds());
                    assertThat(trip.rushSeconds()).as("%s %.1f km at rush hour", mode, km).isGreaterThanOrEqualTo(previous.rushSeconds());
                    previous = trip;
                }
            }
        }
    }

    @Test
    void takesLongerAtRushHourAboveAllByCarInIleDeFrance() {
        // Issou (78) to Montrouge (92): about 43 km straight, some 50 minutes off-peak and 1 h 45 at 8:30 by car.
        GeoLocator.Place issou = new GeoLocator.Place("Issou", "78", null, 48.9890, 1.7920);
        GeoLocator.Place montrouge = new GeoLocator.Place("Montrouge", "92", null, 48.8160, 2.3180);
        double km = Math.sqrt(JobOfferSpecifications.squaredDistanceKm(new JobOfferSpecifications.Origin(issou.latitude(), issou.longitude()),
            montrouge.latitude(), montrouge.longitude()));
        CommuteService.Trip byCar = CommuteService.estimated(km, TravelMode.DRIVING, CommuteService.ileDeFrance(issou));
        assertThat(byCar.seconds()).isBetween(45 * 60, 52 * 60);
        assertThat(byCar.rushSeconds()).isBetween(100 * 60, 110 * 60);
        assertThat(byCar.commuteSeconds()).isEqualTo(byCar.rushSeconds());

        assertThat(CommuteService.ileDeFrance(new GeoLocator.Place("Lyon", "69", null, 45.76, 4.83))).isFalse();
        assertThat(CommuteService.ileDeFrance(new GeoLocator.Place("Somewhere", null, null, 45.76, 4.83))).isFalse();
        CommuteService.Trip lyon = CommuteService.estimated(20, TravelMode.DRIVING, false);
        assertThat(lyon.rushSeconds()).isBetween((int) Math.round(lyon.seconds() * 1.2), (int) Math.round(lyon.seconds() * 1.4));
        CommuteService.Trip shortDrive = CommuteService.estimated(5, TravelMode.DRIVING, true);
        assertThat(shortDrive.rushSeconds()).isCloseTo((int) Math.round(shortDrive.seconds() * 1.2), within(1));
        CommuteService.Trip byTrain = CommuteService.estimated(km, TravelMode.TRANSIT, true);
        assertThat(byTrain.rushSeconds()).isEqualTo((int) Math.round(byTrain.seconds() * 1.15));
    }

    @Test
    void measuresTheRushHourOfTheNextWeekday() {
        // Friday 30 October 2026 at noon UTC: the next weekday is Monday 2 November, 8:30 in Paris (7:30 UTC, winter time).
        assertThat(CommuteService.rushHour(Instant.parse("2026-10-30T12:00:00Z"))).isEqualTo(Instant.parse("2026-11-02T07:30:00Z"));
        assertThat(CommuteService.rushHour(Instant.parse("2026-10-27T10:00:00Z"))).isEqualTo(Instant.parse("2026-10-28T07:30:00Z"));
    }
}
