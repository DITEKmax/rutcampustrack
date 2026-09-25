package ru.rutcampustrack.attendance.report;

import java.text.Normalizer;
import java.util.Locale;

final class HeadmanStatsReportFiles {
    private HeadmanStatsReportFiles() { }

    static String fileName(String groupName, String semesterName, String subjectName, HeadmanStatsFormat format) {
        String group = safe(groupName, "группа");
        String semester = safe(semesterName, "семестр");
        String scope = subjectName == null || subjectName.isBlank() ? "группа" : safe(subjectName, "предмет");
        return "статистика_" + group + "_" + semester + "_" + scope + "." + format.extension();
    }

    private static String safe(String value, String fallback) {
        String normalized = Normalizer.normalize(value == null ? "" : value, Normalizer.Form.NFKC)
                .replaceAll("[\\p{Cntrl}\\\\/:*?\"<>|]+", "_")
                .replaceAll("\\s+", "_")
                .replaceAll("_+", "_")
                .replaceAll("^[._]+|[._]+$", "");
        return normalized.isBlank() ? fallback : normalized.toLowerCase(Locale.ROOT);
    }
}
