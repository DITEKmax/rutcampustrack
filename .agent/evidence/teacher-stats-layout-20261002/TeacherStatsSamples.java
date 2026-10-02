import ru.rutcampustrack.attendance.report.teacher.TeacherStatsDocxRenderer;
import ru.rutcampustrack.attendance.report.teacher.TeacherStatsExportModel;
import ru.rutcampustrack.attendance.report.teacher.TeacherStatsExportModel.*;
import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipInputStream;

/** Synthetic evidence generator calling the frozen product renderer directly. */
public final class TeacherStatsSamples {
    public static void main(String[] args) throws Exception {
        Path output = Path.of(args[0]);
        Files.createDirectories(output);
        Metric present = new Metric(7, 28, new BigDecimal("26.3"));
        Metric presentOrExcused = new Metric(9, 28, new BigDecimal("32.1"));
        Metric excused = new Metric(2, 28, new BigDecimal("7.1"));
        Metric absent = new Metric(19, 28, new BigDecimal("67.9"));
        Metric noData = new Metric(0, 0, BigDecimal.ZERO);
        for (Scope scope : Scope.values()) {
            boolean groups = scope == Scope.GROUPS;
            int count = groups ? 60 : 80;
            List<Row> rows = new ArrayList<>();
            for (int number = 1; number <= count; number++) {
                String label = String.format(groups ? "Группа-%03d" : "Студент-%03d", number)
                        + (groups ? " <Синтетическая> & Длинное название выбранной учебной группы"
                        : " <Синтетический> & Длинная Фамилия Имя Отчество");
                boolean last = number == count;
                rows.add(new Row(label, last ? noData : present, last ? noData : presentOrExcused,
                        last ? noData : excused, last ? noData : absent, groups ? (last ? 0 : 28) : null));
            }
            List<String> labels = groups ? rows.stream().map(Row::label).toList()
                    : List.of("Выбранная группа <80> & полный состав");
            Context context = new Context(scope, 83, LocalDate.of(2026, 2, 2), LocalDate.of(2026, 8, 16),
                    LocalDate.of(2026, 2, 9), LocalDate.of(2026, 7, 31), labels,
                    groups ? "" : "Выбранный предмет <длинный> & отдельные типы",
                    List.of("Лекция", "Лабораторная работа"), 28, Instant.parse("2026-08-16T12:34:00Z"));
            byte[] docx = new TeacherStatsDocxRenderer().render(new TeacherStatsExportModel(context, rows));
            String word = document(docx);
            require(word.split("<w:cantSplit/>", -1).length == count + 1, "whole body rows " + scope);
            require(word.contains("<w:tblHeader/>") && word.contains("26.3%") && word.contains("— (0/0)"), "header/metrics " + scope);
            require(word.contains("Период семестра: 02.02.2026 — 16.08.2026")
                    && word.contains("Период данных: 09.02.2026 — 31.07.2026")
                    && word.contains("Типы занятий: Лекция, Лабораторная работа")
                    && word.contains("Учтено пар: 28") && word.contains("16.08.2026 12:34 UTC"), "context " + scope);
            for (Row row : rows) {
                String escaped = row.label().replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
                int expected = groups ? 2 : 1; // GROUPS context also lists each selected group.
                require(word.split(java.util.regex.Pattern.quote(escaped), -1).length == expected + 1, "complete label " + row.label());
            }
            String name = "teacher-stats-" + scope.name().toLowerCase(java.util.Locale.ROOT);
            Files.write(output.resolve(name + ".docx"), docx);
            Files.write(output.resolve(name + "-labels.txt"), rows.stream().map(Row::label).toList(), StandardCharsets.UTF_8);
            System.out.println(scope + " sample PASS rows=" + count + " bytes=" + docx.length
                    + " supplied=7/28@26.3%,9/28@32.1%,2/28@7.1%,19/28@67.9%; last=0/0");
        }
    }
    private static String document(byte[] content) throws Exception {
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(content), StandardCharsets.UTF_8)) {
            for (var entry = zip.getNextEntry(); entry != null; entry = zip.getNextEntry()) {
                if (entry.getName().equals("word/document.xml")) return new String(zip.readAllBytes(), StandardCharsets.UTF_8);
            }
        }
        throw new IllegalStateException("Missing DOCX document");
    }
    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
