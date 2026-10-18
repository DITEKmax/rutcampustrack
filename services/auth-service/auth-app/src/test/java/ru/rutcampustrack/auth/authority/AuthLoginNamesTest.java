package ru.rutcampustrack.auth.authority;

import static org.assertj.core.api.Assertions.*;

import io.grpc.StatusRuntimeException;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

class AuthLoginNamesTest {
    @Test
    void preservesAcceptedStemDigitsAndCollisionSuffixes() {
        Set<String> taken = new HashSet<>(Set.of("ivanov-ivan-1234", "ivanov-ivan-1234-2"));
        assertThat(AuthLoginNames.reserve("ivanov-ivan", "1234", taken::contains))
                .isEqualTo("ivanov-ivan-1234-3");
    }

    @Test
    void longNameKeepsFourIdentityDigitsBeforeEveryCollisionSuffix() {
        String stem = "a".repeat(32);
        String first = AuthLoginNames.reserve(stem, "0042", s -> false);
        String second = AuthLoginNames.reserve(stem, "0042", first::equals);
        assertThat(first).hasSize(32).endsWith("-0042");
        assertThat(second).hasSize(32).endsWith("-0042-2");
        assertThat(second).isNotEqualTo(first);
    }

    @Test
    void emptyUnicodeTransliterationPreservesDigitsOnlyLoginWithoutInventedFallback() {
        assertThat(AuthLoginNames.reserve("", "0042", s -> false)).isEqualTo("0042");
        assertThat(AuthLoginNames.reserve("", "0042", "0042"::equals)).isEqualTo("0042-2");
    }

    @Test
    void cannotSilentlyCollapseDistinctAccountIntentsOnNamingCollision() {
        Set<String> taken = new HashSet<>();
        String first = AuthLoginNames.reserve("same-name", "0001", taken::contains);
        taken.add(first);
        String second = AuthLoginNames.reserve("same-name", "0001", taken::contains);
        assertThat(second).isEqualTo("same-name-0001-2");
        assertThatThrownBy(() -> AuthLoginNames.reserve("same-name", "0001", s -> true))
                .isInstanceOf(StatusRuntimeException.class);
    }
}
