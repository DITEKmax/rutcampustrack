package ru.rutcampustrack.attendance.geofence;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * Unit tests for GeoUtils Haversine distance calculation.
 * No Spring context — pure math unit tests.
 * GeoUtils is package-private, so test is in same package.
 */
class GeoUtilsTest {

    @Test
    void distanceMeters_samePoint_returnsZero() {
        double distance = GeoUtils.distanceMeters(55.7558, 37.6173, 55.7558, 37.6173);
        assertThat(distance).isCloseTo(0.0, within(0.001));
    }

    @Test
    void distanceMeters_nearbyMoscowPoints_returnsApprox22to24Meters() {
        // Two nearby points in Moscow — expected ~22-24m
        double distance = GeoUtils.distanceMeters(55.7558, 37.6173, 55.7560, 37.6175);
        assertThat(distance).isBetween(15.0, 35.0);
    }

    @Test
    void distanceMeters_moscowToStPetersburg_returnsApprox634km() {
        // Moscow: 55.7558, 37.6173 -> St. Petersburg: 59.9343, 30.3351
        double distance = GeoUtils.distanceMeters(55.7558, 37.6173, 59.9343, 30.3351);
        // ~634 km, tolerance 5 km = 5000 m
        assertThat(distance).isBetween(629_000.0, 639_000.0);
    }

    @Test
    void isWithinRadius_pointInsideRadius_returnsTrue() {
        // Center: 55.7558, 37.6173 — point very close (within 500m)
        boolean result = GeoUtils.isWithinRadius(55.7559, 37.6174, 55.7558, 37.6173, 500.0);
        assertThat(result).isTrue();
    }

    @Test
    void isWithinRadius_pointOutsideRadius_returnsFalse() {
        // St. Petersburg is far from Moscow — not within 500m
        boolean result = GeoUtils.isWithinRadius(59.9343, 30.3351, 55.7558, 37.6173, 500.0);
        assertThat(result).isFalse();
    }
}
