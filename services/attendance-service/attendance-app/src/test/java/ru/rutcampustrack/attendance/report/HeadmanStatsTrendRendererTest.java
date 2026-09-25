package ru.rutcampustrack.attendance.report;

import org.junit.jupiter.api.Test;
import ru.rutcampustrack.attendance.contract.dto.report.HeadmanStatsResponse;
import ru.rutcampustrack.attendance.contract.dto.report.HeadmanStatsTrendQueryRequest;
import ru.rutcampustrack.attendance.contract.dto.report.HeadmanStatsTrendResponse;
import ru.rutcampustrack.attendance.contract.dto.report.HeadmanStatsTrendResponse.Point;
import ru.rutcampustrack.attendance.contract.dto.report.HeadmanStatsTrendResponse.TrendMetric;

import javax.imageio.ImageIO;
import java.io.ByteArrayInputStream;
import java.time.LocalDate;
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
    void pngIsARealCyrillicCapableImageAndUsesTheRequestedFilenameAndMimeType() throws Exception {
        var result = renderer.render(trend(), HeadmanStatsTrendFormat.PNG);
        byte[] bytes = result.content();

        assertEquals("image/png", result.contentType());
        assertEquals("headman-stats-trend.png", result.fileName());
        assertArrayEquals(new byte[]{(byte) 0x89, 'P', 'N', 'G'}, java.util.Arrays.copyOf(bytes, 4));
        var image = ImageIO.read(new ByteArrayInputStream(bytes));
        assertNotNull(image);
        assertEquals(1080, image.getWidth());
        assertEquals(680, image.getHeight());
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

    private static int occurrences(String value, String token) {
        return (value.length() - value.replace(token, "").length()) / token.length();
    }
}
