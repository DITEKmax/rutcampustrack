package ru.rutcampustrack.academic.map;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CampusMapOwnerHmacProviderTest {
    @Test
    void missingDedicatedKeyFailsClosedWithoutAProductionFallback() {
        CampusMapOwnerHmacProvider provider = new CampusMapOwnerHmacProvider(
                new CampusMapUsageProperties(""));

        assertThatThrownBy(() -> provider.forUser(100L))
                .isInstanceOf(CampusMapReadException.class)
                .extracting(error -> ((CampusMapReadException) error).code())
                .isEqualTo(CampusMapReadException.Code.UNAVAILABLE);
    }

    @Test
    void configuredKeyProducesAStablePseudonymousOwnerDigest() {
        CampusMapOwnerHmacProvider provider = new CampusMapOwnerHmacProvider(
                new CampusMapUsageProperties("01234567890123456789012345678901"));

        byte[] first = provider.forUser(100L);
        byte[] repeat = provider.forUser(100L);
        byte[] other = provider.forUser(101L);

        assertThat(first).hasSize(32).containsExactly(repeat);
        assertThat(first).isNotEqualTo(other);
    }
}
