package ru.rutcampustrack.academic.group;

import ru.rutcampustrack.academic.entity.Group;

import java.util.Locale;
import java.util.Optional;

/**
 * Canonical group-code rules shared by all writers and the legacy registry
 * fallback.  A group name remains the display identity, while the split code
 * fields are kept in step with it whenever the name is changed.
 */
public final class GroupCodeRules {

    private static final GroupNameParser PARSER = new GroupNameParser();

    private GroupCodeRules() {
    }

    /** Canonical code, with a validated duration for the current programme. */
    public record CanonicalCode(
            String alphabeticCode,
            String numericCode,
            int currentCourse,
            ProgramType programType,
            int trainingDurationYears) {

        public String name() {
            return alphabeticCode + "-" + numericCode;
        }

        public CanonicalCode next() {
            if (currentCourse >= trainingDurationYears) {
                return null;
            }
            String nextNumeric = (currentCourse + 1)
                    + Integer.toString(programType.getDigit())
                    + numericCode.charAt(2);
            return fromParts(alphabeticCode, nextNumeric, trainingDurationYears);
        }
    }

    /** Field-aware validation error used by the service boundary. */
    public static final class InvalidCodeException extends IllegalArgumentException {
        private final String field;

        public InvalidCodeException(String field, String message) {
            super(message);
            this.field = field;
        }

        public String field() {
            return field;
        }
    }

    /**
     * Validate and normalize the split code supplied by the registry writer.
     * The first numeric digit is the current course; the middle digit must be
     * a registered programme type; and the course cannot exceed the supplied
     * duration.
     */
    public static CanonicalCode fromParts(String alphabeticCode,
                                          String numericCode,
                                          Integer trainingDurationYears) {
        String prefix = normalizeAlphabeticCode(alphabeticCode);
        String numeric = normalizeNumericCode(numericCode);
        if (trainingDurationYears == null || trainingDurationYears < 1) {
            throw new InvalidCodeException("trainingDurationYears",
                    "Срок обучения должен быть положительным");
        }
        int course = Character.digit(numeric.charAt(0), 10);
        if (course < 1 || course > trainingDurationYears) {
            throw new InvalidCodeException("numericCode",
                    "Первая цифра цифрового кода должна быть не больше срока обучения");
        }
        int typeDigit = Character.digit(numeric.charAt(1), 10);
        ProgramType type = ProgramType.fromDigit(typeDigit);
        return new CanonicalCode(prefix, numeric, course, type, trainingDurationYears);
    }

    /**
     * Parse an active name.  A missing duration is derived only from a known
     * programme type, which is the compatibility rule for old rows and PUT.
     */
    public static CanonicalCode fromName(String name, Integer trainingDurationYears) {
        final GroupNameParser.ParsedName parsed;
        try {
            parsed = PARSER.parse(name);
        } catch (IllegalArgumentException error) {
            throw new InvalidCodeException("name", "Некорректный формат имени группы");
        }
        ProgramType type = ProgramType.fromDigit(parsed.type());
        String numeric = Integer.toString(parsed.course())
                + parsed.type() + parsed.number();
        int duration = trainingDurationYears == null
                ? type.getMaxCourse() : trainingDurationYears;
        return fromParts(parsed.prefix(), numeric, duration);
    }

    /**
     * Safe compatibility projection for legacy rows.  Invalid or contradictory
     * names stay unknown instead of receiving an invented duration.
     */
    public static Optional<CanonicalCode> legacy(String name) {
        try {
            return Optional.of(fromName(name, null));
        } catch (RuntimeException ignored) {
            return Optional.empty();
        }
    }

    /** Apply one canonical code to the display name and all registry columns. */
    public static void apply(Group group, CanonicalCode code) {
        group.setName(code.name());
        group.setAlphabeticCode(code.alphabeticCode());
        group.setNumericCode(code.numericCode());
        group.setCurrentCourse(code.currentCourse());
        group.setTrainingDurationYears(code.trainingDurationYears());
        group.setDurationStatus("KNOWN");
    }

    private static String normalizeAlphabeticCode(String value) {
        if (value == null || value.isBlank()) {
            throw new InvalidCodeException("alphabeticCode", "Буквенный код обязателен");
        }
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        if (!normalized.matches("^[А-ЯЁ][А-ЯЁа-яё]{1,3}$")) {
            throw new InvalidCodeException("alphabeticCode",
                    "Буквенный код: 2–4 кириллических символа");
        }
        return normalized;
    }

    private static String normalizeNumericCode(String value) {
        if (value == null || !value.trim().matches("^\\d{3}$")) {
            throw new InvalidCodeException("numericCode",
                    "Цифровой код должен содержать 3 цифры");
        }
        return value.trim();
    }
}
