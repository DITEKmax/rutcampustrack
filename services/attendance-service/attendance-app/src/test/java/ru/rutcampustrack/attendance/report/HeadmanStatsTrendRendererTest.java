package ru.rutcampustrack.attendance.report;

import org.junit.jupiter.api.Test;
import ru.rutcampustrack.attendance.contract.dto.report.HeadmanStatsResponse;
import ru.rutcampustrack.attendance.contract.dto.report.HeadmanStatsTrendQueryRequest;
import ru.rutcampustrack.attendance.contract.dto.report.HeadmanStatsTrendResponse;
import ru.rutcampustrack.attendance.contract.dto.report.HeadmanStatsTrendResponse.Point;
import ru.rutcampustrack.attendance.contract.dto.report.HeadmanStatsTrendResponse.TrendMetric;

import javax.imageio.ImageIO;
import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HeadmanStatsTrendRendererTest {

    private final HeadmanStatsTrendRenderer renderer = new HeadmanStatsTrendRenderer();

    @Test
    void htmlEscapesContextAndIncludesBothSeriesPeriodsAndDenominatorsWithoutBridgingNullGaps() {
        var result = renderer.render(trend(), HeadmanStatsTrendFormat.HTML);
        String html = new String(result.content(), java.nio.charset.StandardCharsets.UTF_8);

        assertEquals("text/html; charset=UTF-8", result.contentType());
        assertEquals("headman-stats-trend.html", result.fileName());
        assertTrue(html.contains("&lt;script&gt;alert(&#39;x&#39;)&lt;/script&gt;"));
        assertTrue(html.contains("Весна &amp; осень"));
        assertTrue(html.contains("<th scope=\"col\">Присутствовал</th>"));
        assertTrue(html.contains("52,0% (13/25)"));
        assertTrue(html.contains("Нет данных (0/0)"));
        assertTrue(html.contains("Неделя 1"));
        assertEquals(2, occurrences(html, "<polyline"));
        assertTrue(html.contains("axis is 0–100") || html.contains("ось процентов: 0–100"));
        assertTrue(!html.contains("<script>"));
    }

    @Test
    void pngKeepsAllSemesterWeeksInBoundedTableAtFixedChartWidth() throws Exception {
        var result = renderer.render(semesterTrend(), HeadmanStatsTrendFormat.PNG);
        byte[] bytes = result.content();

        assertEquals("image/png", result.contentType());
        assertEquals("headman-stats-trend.png", result.fileName());
        assertArrayEquals(new byte[]{(byte) 0x89, 'P', 'N', 'G'}, java.util.Arrays.copyOf(bytes, 4));
        var image = ImageIO.read(new ByteArrayInputStream(bytes));
        assertNotNull(image);
        assertEquals(1080, image.getWidth());
        assertEquals(1148, image.getHeight());
        assertTrue(bytes.length <= 20 * 1024 * 1024);

        Path preview = Path.of("build", "headman-stats-trend-22-weeks.png");
        Files.createDirectories(preview.getParent());
        Files.write(preview, bytes);
    }

    private static HeadmanStatsTrendResponse trend() {
        var context = new HeadmanStatsResponse.Context(7, "<script>alert('x')</script>",
                4L, "Весна & осень", LocalDate.parse("2026-02-01"), LocalDate.parse("2026-06-30"),
                12L, "Математика", List.of("LECTURE"), 3, java.time.Instant.parse("2026-09-26T08:00:00Z"));
        List<Point> points = List.of(
                new Point("2026-04-06", "Неделя 1", LocalDate.parse("2026-04-06"), LocalDate.parse("2026-04-12"),
                        new TrendMetric(13, 25, 52.0), new TrendMetric(15, 25, 60.0)),
                new Point("2026-04-13", "Неделя 2", LocalDate.parse("2026-04-13"), LocalDate.parse("2026-04-19"),
                        new TrendMetric(0, 0, null), new TrendMetric(0, 0, null)),
                new Point("2026-04-20", "Неделя 3", LocalDate.parse("2026-04-20"), LocalDate.parse("2026-04-26"),
                        new TrendMetric(18, 20, 90.0), new TrendMetric(19, 20, 95.0)),
                new Point("2026-04-27", "Неделя 4", LocalDate.parse("2026-04-27"), LocalDate.parse("2026-05-03"),
                        new TrendMetric(16, 20, 80.0), new TrendMetric(17, 20, 85.0)));
        return new HeadmanStatsTrendResponse(context, HeadmanStatsTrendQueryRequest.Mode.SUBJECT, points,
                HeadmanStatsTrendResponse.EmptyState.NONE);
    }

    private static HeadmanStatsTrendResponse semesterTrend() {
        var context = new HeadmanStatsResponse.Context(2, "РТ-319", 2L, "Осенний 2026/2027",
                LocalDate.parse("2026-09-01"), LocalDate.parse("2027-01-25"), null, "", List.of(), 4,
                java.time.Instant.parse("2026-09-26T10:47:31.986203831Z"));
        List<Point> points = new ArrayList<>(22);
        for (int index = 0; index < 22; index++) {
            LocalDate from = index == 0 ? LocalDate.parse("2026-09-01")
                    : LocalDate.parse("2026-09-07").plusWeeks(index - 1);
            LocalDate to = index == 0 ? LocalDate.parse("2026-09-06")
                    : index == 21 ? from : from.plusDays(6);
            TrendMetric present = index == 0 ? new TrendMetric(1, 2, 50.0)
                    : index < 4 ? new TrendMetric(0, 2, 0.0) : new TrendMetric(0, 0, null);
            TrendMetric presentOrExcused = index == 0 ? new TrendMetric(1, 2, 50.0)
                    : index < 4 ? new TrendMetric(0, 2, 0.0) : new TrendMetric(0, 0, null);
            String key = index == 0 ? "2026-08-31" : from.toString();
            points.add(new Point(key, from + " – " + to, from, to, present, presentOrExcused));
        }
        return new HeadmanStatsTrendResponse(context, HeadmanStatsTrendQueryRequest.Mode.SEMESTER, points,
                HeadmanStatsTrendResponse.EmptyState.NONE);
    }

    private static int occurrences(String value, String token) {
        return (value.length() - value.replace(token, "").length()) / token.length();
    }
}
