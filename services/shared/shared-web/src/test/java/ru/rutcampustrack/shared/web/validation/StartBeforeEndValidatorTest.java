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
import java.time.LocalDateTime;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class StartBeforeEndValidatorTest {

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

    @StartBeforeEnd
    record DateRange(LocalDate start, LocalDate end) {}

    @StartBeforeEnd(start = "begin", end = "finish")
    record CustomNames(LocalDateTime begin, LocalDateTime finish) {}

    static Stream<Arguments> validCases() {
        return Stream.of(
                Arguments.of(new DateRange(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 2)), "start < end"),
                Arguments.of(new DateRange(null, LocalDate.of(2026, 1, 2)), "start null — пропускается"),
                Arguments.of(new DateRange(LocalDate.of(2026, 1, 1), null), "end null — пропускается"),
                Arguments.of(new DateRange(null, null), "оба null")
        );
    }

    static Stream<Arguments> invalidCases() {
        return Stream.of(
                Arguments.of(new DateRange(LocalDate.of(2026, 1, 2), LocalDate.of(2026, 1, 1)), "start > end"),
                Arguments.of(new DateRange(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 1)), "start == end (строго <)")
        );
    }

    @ParameterizedTest(name = "valid: {1}")
    @MethodSource("validCases")
    @DisplayName("valid — нет violation'ов")
    void valid(DateRange range, String description) {
        assertThat(validator.validate(range)).isEmpty();
    }

    @ParameterizedTest(name = "invalid: {1}")
    @MethodSource("invalidCases")
    @DisplayName("invalid — ровно один violation")
    void invalid(DateRange range, String description) {
        assertThat(validator.validate(range)).hasSize(1);
    }

    @org.junit.jupiter.api.Test
    @DisplayName("Кастомные имена полей — читаются из BeanWrapper")
    void customFieldNames() {
        CustomNames valid = new CustomNames(
                LocalDateTime.of(2026, 1, 1, 10, 0),
                LocalDateTime.of(2026, 1, 1, 11, 0));
        CustomNames invalid = new CustomNames(
                LocalDateTime.of(2026, 1, 1, 11, 0),
                LocalDateTime.of(2026, 1, 1, 10, 0));
        assertThat(validator.validate(valid)).isEmpty();
        assertThat(validator.validate(invalid)).hasSize(1);
    }
}
