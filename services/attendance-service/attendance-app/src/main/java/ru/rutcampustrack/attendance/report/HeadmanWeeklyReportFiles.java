package ru.rutcampustrack.attendance.report;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

final class HeadmanWeeklyReportFiles {

    private static final DateTimeFormatter FILE_DATE = DateTimeFormatter.ofPattern("dd.MM.uuuu");
    private static final String GAP_SUFFIX = "_\u0421_\u041f\u0420\u041e\u041f\u0423\u0421\u041a\u0410\u041c\u0418";

    private HeadmanWeeklyReportFiles() {
    }

    static String buildFileName(String groupCode,
                                List<LocalDate> weekStarts,
                                HeadmanWeeklyReportFormat format) {
        if (weekStarts == null || weekStarts.isEmpty()) {
            throw new IllegalArgumentException("weekStarts must not be empty");
        }

        List<LocalDate> sorted = sortedDistinct(weekStarts);
        LocalDate first = sorted.get(0);
        LocalDate last = sorted.get(sorted.size() - 1).plusDays(6);

        String suffix = hasGaps(sorted) ? GAP_SUFFIX : "";
        if (format == HeadmanWeeklyReportFormat.PNG && sorted.size() > 1) {
            return "%s_%s_%s%s_png.zip".formatted(
                    sanitizeGroupCode(groupCode),
                    first.format(FILE_DATE),
                    last.format(FILE_DATE),
                    suffix);
        }

        return "%s_%s_%s%s.%s".formatted(
                sanitizeGroupCode(groupCode),
                first.format(FILE_DATE),
                last.format(FILE_DATE),
                suffix,
                format.extension());
    }

    static boolean hasGaps(List<LocalDate> weekStarts) {
        List<LocalDate> sorted = sortedDistinct(weekStarts);
        for (int i = 1; i < sorted.size(); i++) {
            if (!sorted.get(i).equals(sorted.get(i - 1).plusWeeks(1))) {
                return true;
            }
        }
        return false;
    }

    static String pngEntryName(HeadmanWeeklyReportModel model) {
        return "week-%02d_%s_%s.png".formatted(
                model.weekOfSemester(),
                model.weekStart().format(FILE_DATE),
                model.weekEnd().format(FILE_DATE));
    }

    private static List<LocalDate> sortedDistinct(List<LocalDate> weekStarts) {
        return weekStarts.stream()
                .distinct()
                .sorted(Comparator.naturalOrder())
                .toList();
    }

    private static String sanitizeGroupCode(String groupCode) {
        String value = groupCode == null ? "" : groupCode.trim();
        if (value.isEmpty()) {
            value = "group";
        }
        return value
                .replaceAll("\\s+", "_")
                .replaceAll("[\\\\/:*?\"<>|]", "_")
                .toUpperCase(Locale.ROOT);
    }
}
