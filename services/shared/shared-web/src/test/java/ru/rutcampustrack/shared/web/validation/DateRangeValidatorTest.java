package ru.rutcampustrack.shared.web.validation;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.time.LocalDate;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class DateRangeValidatorTest {

    private static ValidatorFactory factory;
    private static Validator validator;

    @BeforeAll
    static void setup() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll
    static void cleanup() {
        factory.close();
    }

    @DateRangeValid
    record ReportRange(LocalDate from, LocalDate to) {}

    static Stream<Arguments> validCases() {
        return Stream.of(
                Arguments.of(new ReportRange(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 2)), "from < to"),
                Arguments.of(new ReportRange(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 1)), "from == to (inclusive)"),
                Arguments.of(new ReportRange(null, LocalDate.of(2026, 1, 2)), "from null"),
                Arguments.of(new ReportRange(LocalDate.of(2026, 1, 1), null), "to null")
        );
    }

    static Stream<Arguments> invalidCases() {
        return Stream.of(
                Arguments.of(new ReportRange(LocalDate.of(2026, 1, 2), LocalDate.of(2026, 1, 1)), "from > to")
        );
    }

    @ParameterizedTest(name = "valid: {1}")
    @MethodSource("validCases")
    @DisplayName("valid — нет violation'ов")
    void valid(ReportRange range, String description) {
        assertThat(validator.validate(range)).isEmpty();
    }

    @ParameterizedTest(name = "invalid: {1}")
    @MethodSource("invalidCases")
    @DisplayName("invalid — ровно один violation")
    void invalid(ReportRange range, String description) {
        assertThat(validator.validate(range)).hasSize(1);
    }
}
