package ru.rutcampustrack.auth.session;

import java.nio.charset.StandardCharsets;

/** Frozen new-password policy: Unicode scalar count, Nd, P/S, and UTF-8 cap. */
public final class PasswordPolicy {

    public static final int MIN_CODE_POINTS = 12;
    public static final int MAX_UTF8_BYTES = 72;

    public Validation validate(CharSequence password) {
        if (password == null) {
            return Validation.failure(Code.EMPTY, 0, 0);
        }

        int scalarCount = 0;
        boolean hasDecimalDigit = false;
        boolean hasSpecial = false;
        int index = 0;
        while (index < password.length()) {
            char first = password.charAt(index);
            final int codePoint;
            if (Character.isHighSurrogate(first)) {
                if (index + 1 >= password.length()
                        || !Character.isLowSurrogate(password.charAt(index + 1))) {
                    return Validation.failure(Code.UNPAIRED_SURROGATE, scalarCount, 0);
                }
                codePoint = Character.toCodePoint(first, password.charAt(index + 1));
                index += 2;
            } else if (Character.isLowSurrogate(first)) {
                return Validation.failure(Code.UNPAIRED_SURROGATE, scalarCount, 0);
            } else {
                codePoint = first;
                index++;
            }

            scalarCount++;
            hasDecimalDigit |= Character.getType(codePoint) == Character.DECIMAL_DIGIT_NUMBER;
            hasSpecial |= isPunctuationOrSymbol(codePoint);
        }

        String exactText = password.toString();
        int utf8Bytes = exactText.getBytes(StandardCharsets.UTF_8).length;
        if (scalarCount < MIN_CODE_POINTS) {
            return Validation.failure(Code.TOO_SHORT, scalarCount, utf8Bytes);
        }
        if (!hasDecimalDigit) {
            return Validation.failure(Code.MISSING_DECIMAL_DIGIT, scalarCount, utf8Bytes);
        }
        if (!hasSpecial) {
            return Validation.failure(Code.MISSING_SPECIAL, scalarCount, utf8Bytes);
        }
        if (utf8Bytes > MAX_UTF8_BYTES) {
            return Validation.failure(Code.TOO_MANY_UTF8_BYTES, scalarCount, utf8Bytes);
        }
        return Validation.success(scalarCount, utf8Bytes);
    }

    public boolean isValid(CharSequence password) {
        return validate(password).valid();
    }

    private static boolean isPunctuationOrSymbol(int codePoint) {
        return switch (Character.getType(codePoint)) {
            case Character.CONNECTOR_PUNCTUATION,
                    Character.DASH_PUNCTUATION,
                    Character.START_PUNCTUATION,
                    Character.END_PUNCTUATION,
                    Character.OTHER_PUNCTUATION,
                    Character.INITIAL_QUOTE_PUNCTUATION,
                    Character.FINAL_QUOTE_PUNCTUATION,
                    Character.MATH_SYMBOL,
                    Character.CURRENCY_SYMBOL,
                    Character.MODIFIER_SYMBOL,
                    Character.OTHER_SYMBOL -> true;
            default -> false;
        };
    }

    public enum Code {
        OK,
        EMPTY,
        UNPAIRED_SURROGATE,
        TOO_SHORT,
        MISSING_DECIMAL_DIGIT,
        MISSING_SPECIAL,
        TOO_MANY_UTF8_BYTES
    }

    public record Validation(Code code, int scalarCount, int utf8Bytes) {
        public Validation {
            if (code == null) {
                throw new IllegalArgumentException("code is required");
            }
            if (scalarCount < 0 || utf8Bytes < 0) {
                throw new IllegalArgumentException("counts must not be negative");
            }
        }

        public static Validation success(int scalarCount, int utf8Bytes) {
            return new Validation(Code.OK, scalarCount, utf8Bytes);
        }

        public static Validation failure(Code code, int scalarCount, int utf8Bytes) {
            return new Validation(code, scalarCount, utf8Bytes);
        }

        public boolean valid() {
            return code == Code.OK;
        }
    }
}
