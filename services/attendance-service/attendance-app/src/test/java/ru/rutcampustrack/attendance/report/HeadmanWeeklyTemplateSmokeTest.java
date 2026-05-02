package ru.rutcampustrack.attendance.report;

import org.junit.jupiter.api.Test;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;
import java.util.zip.ZipInputStream;

import static org.assertj.core.api.Assertions.assertThat;

class HeadmanWeeklyTemplateSmokeTest {

    private static final String TEMPLATE_RESOURCE = "report-templates/headman-weekly.docx";
    private static final Pattern PLACEHOLDER = Pattern.compile("\\$\\{[^}]+}");
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd.MM.uuuu");
    private static final List<String> DAYS = List.of("mon", "tue", "wed", "thu", "fri", "sat");

    @Test
    void templateContainsEveryExpectedPlaceholderAsPlainText() throws Exception {
        String documentXml = readDocumentXml();

        assertThat(extractPlaceholders(documentXml))
                .containsExactlyInAnyOrderElementsOf(expectedPlaceholders());
    }

    @Test
    void sampleDataFillsAllPlaceholdersAndKeepsXmlValid() throws Exception {
        String renderedXml = readDocumentXml();

        for (Map.Entry<String, String> entry : sampleValues().entrySet()) {
            renderedXml = renderedXml.replace("${" + entry.getKey() + "}", escapeXml(entry.getValue()));
        }

        assertThat(extractPlaceholders(renderedXml)).isEmpty();
        assertThat(renderedXml)
                .contains("Группа: УВПВ-511")
                .contains("Студент 01")
                .contains("Мат. анализ");
        assertValidXml(renderedXml);
    }

    private static String readDocumentXml() throws Exception {
        InputStream resource = HeadmanWeeklyTemplateSmokeTest.class
                .getClassLoader()
                .getResourceAsStream(TEMPLATE_RESOURCE);
        assertThat(resource).as("template resource %s", TEMPLATE_RESOURCE).isNotNull();

        try (InputStream input = Objects.requireNonNull(resource);
             ZipInputStream zip = new ZipInputStream(input, StandardCharsets.UTF_8)) {
            var entry = zip.getNextEntry();
            while (entry != null) {
                if ("word/document.xml".equals(entry.getName())) {
                    return new String(zip.readAllBytes(), StandardCharsets.UTF_8);
                }
                entry = zip.getNextEntry();
            }
        }

        throw new IllegalStateException("word/document.xml not found in " + TEMPLATE_RESOURCE);
    }

    private static Set<String> extractPlaceholders(String documentXml) {
        Set<String> placeholders = new TreeSet<>();
        var matcher = PLACEHOLDER.matcher(documentXml);
        while (matcher.find()) {
            placeholders.add(matcher.group());
        }
        return placeholders;
    }

    private static Set<String> expectedPlaceholders() {
        Set<String> placeholders = new TreeSet<>();
        placeholders.add("${group_header}");
        DAYS.forEach(day -> placeholders.add("${date_" + day + "}"));

        for (String day : DAYS) {
            for (int lesson = 1; lesson <= 5; lesson++) {
                placeholders.add("${subject_" + day + "_" + lesson + "}");
                placeholders.add("${type_" + day + "_" + lesson + "}");
            }
        }

        for (int student = 1; student <= 35; student++) {
            String number = "%02d".formatted(student);
            placeholders.add("${student_" + number + "_name}");
            for (String day : DAYS) {
                for (int lesson = 1; lesson <= 5; lesson++) {
                    placeholders.add("${att_" + number + "_" + day + "_" + lesson + "}");
                }
            }
        }

        return placeholders;
    }

    private static Map<String, String> sampleValues() {
        Map<String, String> values = new LinkedHashMap<>();
        values.put("group_header", "Группа: УВПВ-511, 2 семестр 2025/2026 года, 5 курс");

        LocalDate weekStart = LocalDate.of(2026, 4, 27);
        for (int dayIndex = 0; dayIndex < DAYS.size(); dayIndex++) {
            values.put("date_" + DAYS.get(dayIndex), weekStart.plusDays(dayIndex).format(DATE_FORMAT));
        }

        List<String> subjects = List.of(
                "Мат. анализ",
                "История",
                "Физика",
                "Английский",
                "Программирование",
                "Алгебра",
                "Философия",
                "БЖД",
                "Экономика",
                "Проект"
        );
        List<String> lessonTypes = List.of("ЛК", "ПЗ", "ЛЗ", "ПЗ", "ЛК");

        for (int dayIndex = 0; dayIndex < DAYS.size(); dayIndex++) {
            String day = DAYS.get(dayIndex);
            for (int lesson = 1; lesson <= 5; lesson++) {
                boolean emptyLesson = "sat".equals(day) && lesson > 3;
                values.put("subject_" + day + "_" + lesson,
                        emptyLesson ? "" : subjects.get((dayIndex * 5 + lesson - 1) % subjects.size()));
                values.put("type_" + day + "_" + lesson,
                        emptyLesson ? "" : lessonTypes.get(lesson - 1));
            }
        }

        List<String> marks = List.of("+", "н", "у");
        for (int student = 1; student <= 35; student++) {
            String number = "%02d".formatted(student);
            values.put("student_" + number + "_name", "Студент " + number);

            for (int dayIndex = 0; dayIndex < DAYS.size(); dayIndex++) {
                String day = DAYS.get(dayIndex);
                for (int lesson = 1; lesson <= 5; lesson++) {
                    String mark = sampleMark(student, day, dayIndex, lesson, marks);
                    values.put("att_" + number + "_" + day + "_" + lesson, mark);
                }
            }
        }

        return values;
    }

    private static String sampleMark(int student, String day, int dayIndex, int lesson, List<String> marks) {
        if ("sat".equals(day) && lesson > 3) {
            return "";
        }
        if (student == 35) {
            return "-";
        }
        return marks.get((student + dayIndex + lesson) % marks.size());
    }

    private static String escapeXml(String value) {
        return value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;");
    }

    private static void assertValidXml(String xml) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setExpandEntityReferences(false);
        factory.newDocumentBuilder().parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
    }
}
