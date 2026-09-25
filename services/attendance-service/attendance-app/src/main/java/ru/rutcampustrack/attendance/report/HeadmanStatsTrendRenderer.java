package ru.rutcampustrack.attendance.report;

import org.springframework.stereotype.Component;
import ru.rutcampustrack.attendance.contract.dto.report.HeadmanStatsResponse;
import ru.rutcampustrack.attendance.contract.dto.report.HeadmanStatsTrendResponse;
import ru.rutcampustrack.attendance.contract.dto.report.HeadmanStatsTrendResponse.Point;
import ru.rutcampustrack.attendance.contract.dto.report.HeadmanStatsTrendResponse.TrendMetric;
import ru.rutcampustrack.attendance.exception.PayloadTooLargeException;

import javax.imageio.ImageIO;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Path2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Renders only server-produced trend data; no client markup or image payload is accepted. */
@Component
final class HeadmanStatsTrendRenderer {
    private static final int MAX_EXPORT_BYTES = 20 * 1024 * 1024;
    private static final Color PRESENT = new Color(12, 106, 83);
    private static final Color PRESENT_OR_EXCUSED = new Color(118, 75, 9);
    private static final Color GRID = new Color(222, 217, 232);
    private static final Color INK = new Color(41, 37, 49);
    private static final int CHART_HEIGHT = 680;

    HeadmanStatsExportResult render(HeadmanStatsTrendResponse trend, HeadmanStatsTrendFormat format) {
        byte[] content = format == HeadmanStatsTrendFormat.PNG ? renderPng(trend) : renderHtml(trend);
        if (content.length == 0 || content.length > MAX_EXPORT_BYTES) {
            throw new PayloadTooLargeException("Размер графика превышает лимит 20 МБ");
        }
        return new HeadmanStatsExportResult("headman-stats-trend." + format.extension(),
                format.contentType(), content);
    }

    private static byte[] renderPng(HeadmanStatsTrendResponse trend) {
        int pointCount = trend.points().size();
        int width = Math.max(1080, Math.min(8192, 300 + pointCount * 112));
        BufferedImage image = new BufferedImage(width, CHART_HEIGHT, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        try {
            graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            graphics.setColor(Color.WHITE);
            graphics.fillRect(0, 0, width, CHART_HEIGHT);
            graphics.setColor(INK);
            graphics.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 22));
            graphics.drawString("Динамика посещаемости", 28, 34);
            graphics.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 15));
            graphics.drawString(fit(graphics, contextLabel(trend), width - 56), 28, 60);

            int left = 132;
            int right = width - 32;
            int top = 100;
            int bottom = 405;
            for (int tick : List.of(0, 50, 100)) {
                int y = y(tick, top, bottom);
                graphics.setColor(GRID);
                graphics.drawLine(left, y, right, y);
                graphics.setColor(INK);
                graphics.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 13));
                graphics.drawString(tick + "%", 88, y + 5);
            }
            drawSeries(graphics, trend.points(), left, right, top, bottom,
                    metricSelector(true), PRESENT, false);
            drawSeries(graphics, trend.points(), left, right, top, bottom,
                    metricSelector(false), PRESENT_OR_EXCUSED, true);
            drawLegend(graphics, 28, 88);

            graphics.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
            for (int index = 0; index < pointCount; index++) {
                int x = pointX(index, pointCount, left, right);
                String label = trend.points().get(index).label();
                graphics.setColor(INK);
                var oldTransform = graphics.getTransform();
                graphics.rotate(-Math.PI / 4, x, 420);
                graphics.drawString(fit(graphics, label, 150), x, 420);
                graphics.setTransform(oldTransform);
            }
            graphics.setColor(INK);
            graphics.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 13));
            graphics.drawString("Период", 28, 492);
            graphics.drawString("Числитель / знаменатель", 28, 543);
            graphics.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
            for (int index = 0; index < pointCount; index++) {
                int x = pointX(index, pointCount, left, right);
                Point point = trend.points().get(index);
                graphics.setColor(PRESENT);
                graphics.drawString("+ " + count(point.present()), x - 34, 572);
                graphics.setColor(PRESENT_OR_EXCUSED);
                graphics.drawString("+у " + count(point.presentOrExcused()), x - 34, 595);
            }
            graphics.setColor(new Color(86, 80, 97));
            graphics.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 11));
            graphics.drawString("+ — присутствовал · +у — присутствовал или освобождён", 28, 646);
        } finally {
            graphics.dispose();
        }
        try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            if (!ImageIO.write(image, "png", output)) {
                throw new IllegalStateException("PNG encoder is unavailable");
            }
            return output.toByteArray();
        } catch (IOException impossibleForMemoryStream) {
            throw new IllegalStateException("Не удалось сформировать PNG динамики", impossibleForMemoryStream);
        }
    }

    private static void drawSeries(Graphics2D graphics, List<Point> points, int left, int right, int top, int bottom,
                                   MetricSelector selector, Color color, boolean dashed) {
        graphics.setColor(color);
        graphics.setStroke(dashed
                ? new BasicStroke(3f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND, 10f, new float[]{8f, 6f}, 0f)
                : new BasicStroke(3f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        Path2D.Double segment = new Path2D.Double();
        boolean started = false;
        for (int index = 0; index < points.size(); index++) {
            TrendMetric metric = selector.get(points.get(index));
            if (metric.percent() == null) {
                if (started) graphics.draw(segment);
                segment.reset();
                started = false;
                continue;
            }
            int x = pointX(index, points.size(), left, right);
            int y = y(metric.percent(), top, bottom);
            if (!started) {
                segment.moveTo(x, y);
                started = true;
            } else {
                segment.lineTo(x, y);
            }
        }
        if (started) graphics.draw(segment);
        for (int index = 0; index < points.size(); index++) {
            TrendMetric metric = selector.get(points.get(index));
            if (metric.percent() == null) continue;
            int x = pointX(index, points.size(), left, right);
            int y = y(metric.percent(), top, bottom);
            graphics.setColor(color);
            if (dashed) {
                int[] xs = {x, x + 5, x, x - 5};
                int[] ys = {y - 5, y, y + 5, y};
                graphics.fillPolygon(xs, ys, 4);
            } else {
                graphics.fillOval(x - 4, y - 4, 8, 8);
            }
        }
    }

    private static void drawLegend(Graphics2D graphics, int x, int y) {
        graphics.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 14));
        graphics.setColor(PRESENT);
        graphics.setStroke(new BasicStroke(3f));
        graphics.drawLine(x, y, x + 26, y);
        graphics.fillOval(x + 10, y - 4, 8, 8);
        graphics.drawString("Присутствовал", x + 34, y + 5);
        int secondX = x + 190;
        graphics.setColor(PRESENT_OR_EXCUSED);
        graphics.setStroke(new BasicStroke(3f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND, 10f,
                new float[]{8f, 6f}, 0f));
        graphics.drawLine(secondX, y, secondX + 26, y);
        graphics.drawString("Присутствовал или освобождён", secondX + 34, y + 5);
    }

    private static byte[] renderHtml(HeadmanStatsTrendResponse trend) {
        int width = Math.max(960, Math.min(8192, 220 + trend.points().size() * 96));
        StringBuilder html = new StringBuilder(8_192);
        String context = contextLabel(trend);
        html.append("<!doctype html><html lang=\"ru\"><head><meta charset=\"utf-8\">")
                .append("<meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">")
                .append("<title>Динамика посещаемости — ").append(escape(context)).append("</title>")
                .append("<style>body{font:16px/1.5 Arial,sans-serif;color:#292531;margin:2rem}")
                .append("h1{font-size:1.5rem}.chart{max-width:100%;overflow-x:auto}svg{display:block}")
                .append("table{border-collapse:collapse;margin-top:1.5rem;width:100%}")
                .append("caption{text-align:left;font-weight:700;margin-bottom:.5rem}")
                .append("th,td{border:1px solid #ded9e8;padding:.45rem;text-align:left}")
                .append("th{background:#f5f2f8} .present{color:#0c6a53}.excused{color:#764b09}")
                .append(".legend{display:flex;gap:1.5rem;flex-wrap:wrap}.empty{color:#565061}</style>")
                .append("</head><body><main><h1>Динамика посещаемости</h1><p>")
                .append(escape(context)).append("</p><p class=\"legend\"><span class=\"present\">● Присутствовал</span>")
                .append("<span class=\"excused\">◆ Присутствовал или освобождён</span></p><div class=\"chart\">")
                .append(svg(trend, width))
                .append("</div><table><caption>Значения по периодам (числитель / знаменатель)</caption>")
                .append("<thead><tr><th scope=\"col\">Период</th><th scope=\"col\">Присутствовал</th>")
                .append("<th scope=\"col\">Присутствовал или освобождён</th></tr></thead><tbody>");
        for (Point point : trend.points()) {
            html.append("<tr><th scope=\"row\">").append(escape(point.label())).append("</th><td class=\"present\">")
                    .append(metric(point.present())).append("</td><td class=\"excused\">")
                    .append(metric(point.presentOrExcused())).append("</td></tr>");
        }
        html.append("</tbody></table><p class=\"empty\">")
                .append(escape(emptyState(trend.emptyState()))).append(" · ось процентов: 0–100</p></main></body></html>");
        byte[] bytes = html.toString().getBytes(StandardCharsets.UTF_8);
        if (bytes.length > MAX_EXPORT_BYTES) {
            throw new PayloadTooLargeException("Размер графика превышает лимит 20 МБ");
        }
        return bytes;
    }

    private static String svg(HeadmanStatsTrendResponse trend, int width) {
        int left = 72;
        int right = width - 24;
        int top = 30;
        int bottom = 315;
        StringBuilder svg = new StringBuilder(4_096);
        svg.append("<svg xmlns=\"http://www.w3.org/2000/svg\" role=\"img\" aria-labelledby=\"chart-title chart-desc\" ")
                .append("viewBox=\"0 0 ").append(width).append(" 470\" width=\"").append(width)
                .append("\" height=\"470\"><title id=\"chart-title\">Две метрики посещаемости</title>")
                .append("<desc id=\"chart-desc\">").append(escape(contextLabel(trend)))
                .append(". Пустые значения показаны разрывом линии; точные числители и знаменатели приведены в таблице.</desc>");
        for (int tick : List.of(0, 50, 100)) {
            int y = y(tick, top, bottom);
            svg.append("<line x1=\"").append(left).append("\" y1=\"").append(y).append("\" x2=\"")
                    .append(right).append("\" y2=\"").append(y).append("\" stroke=\"#ded9e8\"/>")
                    .append("<text x=\"8\" y=\"").append(y + 5).append("\" fill=\"#292531\">")
                    .append(tick).append("%</text>");
        }
        appendSvgSeries(svg, trend.points(), left, right, top, bottom, metricSelector(true),
                "#0c6a53", false);
        appendSvgSeries(svg, trend.points(), left, right, top, bottom, metricSelector(false),
                "#764b09", true);
        for (int index = 0; index < trend.points().size(); index++) {
            int x = pointX(index, trend.points().size(), left, right);
            svg.append("<text x=\"").append(x).append("\" y=\"365\" transform=\"rotate(-40 ")
                    .append(x).append(" 365)\" fill=\"#292531\" font-size=\"12\">")
                    .append(escape(trend.points().get(index).label())).append("</text>");
        }
        svg.append("<line x1=\"20\" y1=\"420\" x2=\"48\" y2=\"420\" stroke=\"#0c6a53\" stroke-width=\"3\"/>")
                .append("<text x=\"56\" y=\"425\" fill=\"#292531\">Присутствовал</text>")
                .append("<line x1=\"240\" y1=\"420\" x2=\"268\" y2=\"420\" stroke=\"#764b09\" stroke-width=\"3\" stroke-dasharray=\"8 6\"/>")
                .append("<text x=\"276\" y=\"425\" fill=\"#292531\">Присутствовал или освобождён</text></svg>");
        return svg.toString();
    }

    private static void appendSvgSeries(StringBuilder svg, List<Point> points, int left, int right, int top, int bottom,
                                        MetricSelector selector, String color, boolean dashed) {
        List<String> segment = new ArrayList<>();
        for (int index = 0; index <= points.size(); index++) {
            TrendMetric metric = index == points.size() ? null : selector.get(points.get(index));
            if (metric == null || metric.percent() == null) {
                if (segment.size() > 1) {
                    svg.append("<polyline fill=\"none\" stroke=\"").append(color)
                            .append("\" stroke-width=\"3\"")
                            .append(dashed ? " stroke-dasharray=\"8 6\"" : "")
                            .append(" points=\"").append(String.join(" ", segment)).append("\"/>");
                }
                segment.clear();
                continue;
            }
            int x = pointX(index, points.size(), left, right);
            int y = y(metric.percent(), top, bottom);
            segment.add(x + "," + y);
            svg.append("<circle cx=\"").append(x).append("\" cy=\"").append(y)
                    .append("\" r=\"4\" fill=\"").append(color).append("\"/>");
        }
    }

    private static MetricSelector metricSelector(boolean present) {
        return point -> present ? point.present() : point.presentOrExcused();
    }

    private static String contextLabel(HeadmanStatsTrendResponse trend) {
        HeadmanStatsResponse.Context context = trend.context();
        String group = context.groupName().isBlank() ? "Группа " + context.groupId() : context.groupName();
        String semester = context.semesterName().isBlank()
                ? context.semesterId() == null ? "Без активного семестра" : "Семестр " + context.semesterId()
                : context.semesterName();
        String scope = switch (trend.mode()) {
            case SEMESTER -> "По семестру";
            case WEEK -> trend.points().isEmpty() ? "По неделе" : "Неделя " + trend.points().get(0).from()
                    + "–" + trend.points().get(trend.points().size() - 1).to();
            case SUBJECT -> context.subjectName().isBlank() ? "По предмету" : "Предмет: " + context.subjectName();
        };
        return group + " · " + semester + " · " + scope;
    }

    private static String metric(TrendMetric metric) {
        if (metric.percent() == null) return "Нет данных (" + metric.numerator() + "/" + metric.denominator() + ")";
        return percent(metric.percent()) + "% (" + metric.numerator() + "/" + metric.denominator() + ")";
    }

    private static String count(TrendMetric metric) {
        return metric.numerator() + "/" + metric.denominator()
                + (metric.percent() == null ? " · нет данных" : " · " + percent(metric.percent()) + "%");
    }

    private static String percent(double value) {
        NumberFormat format = NumberFormat.getNumberInstance(Locale.forLanguageTag("ru-RU"));
        format.setMinimumFractionDigits(1);
        format.setMaximumFractionDigits(1);
        return format.format(value);
    }

    private static int pointX(int index, int count, int left, int right) {
        if (count <= 1) return (left + right) / 2;
        return left + (int) Math.round((right - left) * (index / (double) (count - 1)));
    }

    private static int y(double percent, int top, int bottom) {
        return bottom - (int) Math.round((bottom - top) * percent / 100.0);
    }

    private static String fit(Graphics2D graphics, String value, int width) {
        if (graphics.getFontMetrics().stringWidth(value) <= width) return value;
        String result = value;
        while (!result.isEmpty() && graphics.getFontMetrics().stringWidth(result + "…") > width) {
            result = result.substring(0, result.length() - 1);
        }
        return result + "…";
    }

    private static String emptyState(HeadmanStatsTrendResponse.EmptyState emptyState) {
        return switch (emptyState) {
            case NONE -> "Данные по выбранному периоду";
            case NO_ACTIVE_SEMESTER -> "Нет активного семестра";
            case NO_COMPLETED_LESSONS -> "Нет завершённых занятий";
            case NO_MATCHING_LESSONS -> "Нет занятий, подходящих под выбор";
        };
    }

    private static String escape(String value) {
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&#39;");
    }

    @FunctionalInterface
    private interface MetricSelector {
        TrendMetric get(Point point);
    }
}
