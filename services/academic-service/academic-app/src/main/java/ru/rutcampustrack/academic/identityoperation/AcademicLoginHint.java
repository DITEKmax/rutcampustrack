package ru.rutcampustrack.academic.identityoperation;

import ru.rutcampustrack.academic.contract.dto.user.CreateUserRequest;

/**
 * Nonsecret naming hint only; Auth alone allocates the fallback sequence and unique final login.
 */
final class AcademicLoginHint {
    record Hint(String stem, String digits) {}

    static Hint from(CreateUserRequest request) {
        String stem =
                (transliterate(request.lastName()) + "-" + transliterate(request.firstName()))
                        .replaceAll("-+", "-");
        if (stem.equals("-")) stem = "user";
        stem =
                stem.toLowerCase(java.util.Locale.ROOT)
                        .replaceAll("[^a-z0-9-]", "")
                        .replaceAll("^-+|-+$", "");
        if (stem.length() > 32) stem = stem.substring(0, 32);
        stem = stem.replaceAll("-+$", "");
        String source =
                request.telegramId() == null
                        ? request.employeeNumber()
                        : request.telegramId().toString();
        String digits = source == null ? "" : source.replaceAll("\\D", "");
        if (!digits.isEmpty())
            digits =
                    digits.length() <= 4
                            ? String.format("%04d", Long.parseLong(digits))
                            : digits.substring(digits.length() - 4);
        return new Hint(stem, digits);
    }

    private static String transliterate(String value) {
        if (value == null) return "";
        StringBuilder result = new StringBuilder(value.length());
        String lower = value.toLowerCase(java.util.Locale.ROOT);
        String[] source = {"щ", "ш", "ч", "ц", "ю", "я", "ж", "ё", "й", "х", "ъ", "ь"};
        String[] target = {"shch", "sh", "ch", "ts", "yu", "ya", "zh", "yo", "y", "kh", "", ""};
        for (int i = 0; i < lower.length(); i++) {
            String symbol = lower.substring(i, i + 1);
            boolean replaced = false;
            for (int j = 0; j < source.length; j++) {
                if (symbol.equals(source[j])) {
                    result.append(target[j]);
                    replaced = true;
                    break;
                }
            }
            if (replaced) continue;
            char c = lower.charAt(i);
            if (c >= 'а' && c <= 'я') {
                String mapped =
                        switch (c) {
                            case 'а' -> "a";
                            case 'б' -> "b";
                            case 'в' -> "v";
                            case 'г' -> "g";
                            case 'д' -> "d";
                            case 'е' -> "e";
                            case 'з' -> "z";
                            case 'и' -> "i";
                            case 'к' -> "k";
                            case 'л' -> "l";
                            case 'м' -> "m";
                            case 'н' -> "n";
                            case 'о' -> "o";
                            case 'п' -> "p";
                            case 'р' -> "r";
                            case 'с' -> "s";
                            case 'т' -> "t";
                            case 'у' -> "u";
                            case 'ф' -> "f";
                            case 'ы' -> "y";
                            case 'э' -> "e";
                            default -> "";
                        };
                result.append(mapped);
            } else if (Character.isLetterOrDigit(c)) {
                result.append(c);
            }
        }
        return result.toString();
    }
}
