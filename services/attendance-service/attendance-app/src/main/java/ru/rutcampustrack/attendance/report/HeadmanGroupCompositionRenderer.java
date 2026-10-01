package ru.rutcampustrack.attendance.report;

import org.springframework.stereotype.Component;
import ru.rutcampustrack.attendance.exception.ReportExportUnavailableException;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/** Four-column roster layouts using the established report OOXML/HTML pattern. */
@Component
public final class HeadmanGroupCompositionRenderer {
    private static final List<String> HEADERS = List.of("№", "ФИО", "Логин", "Роль в группе");
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd.MM.uuuu");
    private static final String XML = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>";
    private static final String RELS = "http://schemas.openxmlformats.org/package/2006/relationships";
    private static final String OFFICE_RELS = "http://schemas.openxmlformats.org/officeDocument/2006/relationships/";

    public byte[] renderHtml(HeadmanGroupCompositionModel model) {
        StringBuilder html = new StringBuilder("<!doctype html><html lang=\"ru\"><head><meta charset=\"utf-8\">"
                + "<title>Состав группы</title><style>body{font-family:Arial,sans-serif;margin:2rem}"
                + "table{border-collapse:collapse;width:100%}th,td{border:1px solid #aaa;padding:.5rem;text-align:left}"
                + "thead{background:#eee}</style></head><body><h1>Состав группы</h1><p>Группа: ");
        html.append(escape(model.groupName())).append("</p><p>Дата выгрузки: ")
                .append(DATE.format(model.generatedOn())).append("</p><table><thead><tr>");
        HEADERS.forEach(value -> html.append("<th scope=\"col\">").append(value).append("</th>"));
        html.append("</tr></thead><tbody>");
        for (var row : model.rows()) {
            html.append("<tr>");
            values(row).forEach(value -> html.append("<td>").append(escape(value)).append("</td>"));
            html.append("</tr>");
        }
        return html.append("</tbody></table></body></html>").toString().getBytes(StandardCharsets.UTF_8);
    }

    public byte[] renderDocx(HeadmanGroupCompositionModel model) {
        StringBuilder doc = new StringBuilder(XML + "<w:document xmlns:w=\"http://schemas.openxmlformats.org/wordprocessingml/2006/main\"><w:body>");
        doc.append(paragraph("Состав группы", true)).append(paragraph("Группа: " + model.groupName(), false))
                .append(paragraph("Дата выгрузки: " + DATE.format(model.generatedOn()), false))
                .append("<w:tbl><w:tblPr><w:tblW w:w=\"9500\" w:type=\"dxa\"/><w:tblLayout w:type=\"fixed\"/>"
                        + "<w:tblBorders><w:top w:val=\"single\" w:sz=\"4\"/><w:left w:val=\"single\" w:sz=\"4\"/>"
                        + "<w:bottom w:val=\"single\" w:sz=\"4\"/><w:right w:val=\"single\" w:sz=\"4\"/>"
                        + "<w:insideH w:val=\"single\" w:sz=\"4\"/><w:insideV w:val=\"single\" w:sz=\"4\"/>"
                        + "</w:tblBorders></w:tblPr><w:tblGrid>");
        int[] widths = {600, 4500, 2400, 2000};
        for (int width : widths) doc.append("<w:gridCol w:w=\"").append(width).append("\"/>");
        doc.append("</w:tblGrid>").append(wordRow(HEADERS, widths, true));
        model.rows().forEach(row -> doc.append(wordRow(values(row), widths, false)));
        doc.append("</w:tbl><w:sectPr><w:pgSz w:w=\"11906\" w:h=\"16838\"/>"
                + "<w:pgMar w:top=\"720\" w:right=\"720\" w:bottom=\"720\" w:left=\"720\"/>"
                + "</w:sectPr></w:body></w:document>");
        Map<String, String> entries = new LinkedHashMap<>();
        entries.put("[Content_Types].xml", contentTypes(
                "<Override PartName=\"/word/document.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml\"/>"));
        entries.put("_rels/.rels", relationships(
                "<Relationship Id=\"rId1\" Type=\"" + OFFICE_RELS + "officeDocument\" Target=\"word/document.xml\"/>"));
        entries.put("word/document.xml", doc.toString());
        return zip(entries);
    }

    public byte[] renderXlsx(HeadmanGroupCompositionModel model) {
        StringBuilder sheet = new StringBuilder(XML
                + "<worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\">"
                + "<sheetViews><sheetView workbookViewId=\"0\"><pane ySplit=\"4\" topLeftCell=\"A5\" state=\"frozen\"/>"
                + "</sheetView></sheetViews><cols><col min=\"1\" max=\"1\" width=\"6\" customWidth=\"1\"/>"
                + "<col min=\"2\" max=\"2\" width=\"45\" customWidth=\"1\"/>"
                + "<col min=\"3\" max=\"4\" width=\"25\" customWidth=\"1\"/></cols><sheetData>");
        sheet.append(sheetRow(1, List.of("Состав группы")))
                .append(sheetRow(2, List.of("Группа", model.groupName())))
                .append(sheetRow(3, List.of("Дата выгрузки", DATE.format(model.generatedOn()))))
                .append(sheetRow(4, HEADERS));
        for (int i = 0; i < model.rows().size(); i++) sheet.append(sheetRow(i + 5, values(model.rows().get(i))));
        sheet.append("</sheetData><autoFilter ref=\"A4:D").append(model.rows().size() + 4).append("\"/></worksheet>");
        Map<String, String> entries = new LinkedHashMap<>();
        entries.put("[Content_Types].xml", contentTypes(
                "<Override PartName=\"/xl/workbook.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml\"/>"
                + "<Override PartName=\"/xl/worksheets/sheet1.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml\"/>"));
        entries.put("_rels/.rels", relationships(
                "<Relationship Id=\"rId1\" Type=\"" + OFFICE_RELS + "officeDocument\" Target=\"xl/workbook.xml\"/>"));
        entries.put("xl/workbook.xml", XML
                + "<workbook xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\" xmlns:r=\""
                + OFFICE_RELS.substring(0, OFFICE_RELS.length() - 1) + "\"><sheets>"
                + "<sheet name=\"Состав группы\" sheetId=\"1\" r:id=\"rId1\"/></sheets></workbook>");
        entries.put("xl/_rels/workbook.xml.rels", relationships(
                "<Relationship Id=\"rId1\" Type=\"" + OFFICE_RELS + "worksheet\" Target=\"worksheets/sheet1.xml\"/>"));
        entries.put("xl/worksheets/sheet1.xml", sheet.toString());
        return zip(entries);
    }

    private static List<String> values(HeadmanGroupCompositionModel.Row row) {
        return List.of(Integer.toString(row.number()), row.displayName(), row.login(), row.groupRole());
    }

    private static String paragraph(String text, boolean bold) {
        return "<w:p><w:r><w:rPr><w:rFonts w:ascii=\"Calibri\" w:hAnsi=\"Calibri\"/>"
                + "<w:sz w:val=\"22\"/>" + (bold ? "<w:b/>" : "")
                + "</w:rPr><w:t xml:space=\"preserve\">" + escape(text) + "</w:t></w:r></w:p>";
    }

    private static String wordRow(List<String> values, int[] widths, boolean header) {
        StringBuilder row = new StringBuilder("<w:tr><w:trPr><w:cantSplit/>"
                + (header ? "<w:tblHeader/>" : "") + "</w:trPr>");
        for (int i = 0; i < values.size(); i++) row.append("<w:tc><w:tcPr><w:tcW w:w=\"")
                .append(widths[i]).append("\" w:type=\"dxa\"/></w:tcPr>")
                .append(paragraph(values.get(i), header)).append("</w:tc>");
        return row.append("</w:tr>").toString();
    }

    private static String sheetRow(int number, List<String> values) {
        StringBuilder row = new StringBuilder("<row r=\"" + number + "\">");
        for (int i = 0; i < values.size(); i++) {
            row.append("<c r=\"").append((char) ('A' + i)).append(number)
                    .append("\" t=\"inlineStr\"><is><t xml:space=\"preserve\">")
                    .append(escape(values.get(i))).append("</t></is></c>");
        }
        return row.append("</row>").toString();
    }

    private static String contentTypes(String overrides) {
        return XML + "<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\">"
                + "<Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/>"
                + "<Default Extension=\"xml\" ContentType=\"application/xml\"/>" + overrides + "</Types>";
    }

    private static String relationships(String entries) {
        return XML + "<Relationships xmlns=\"" + RELS + "\">" + entries + "</Relationships>";
    }

    private static byte[] zip(Map<String, String> entries) {
        try (var bytes = new ByteArrayOutputStream(); var zip = new ZipOutputStream(bytes, StandardCharsets.UTF_8)) {
            for (var entry : entries.entrySet()) {
                zip.putNextEntry(new ZipEntry(entry.getKey()));
                zip.write(entry.getValue().getBytes(StandardCharsets.UTF_8));
                zip.closeEntry();
            }
            zip.finish();
            return bytes.toByteArray();
        } catch (IOException error) {
            throw new ReportExportUnavailableException("Не удалось собрать файл состава группы");
        }
    }

    private static String escape(String value) {
        StringBuilder valid = new StringBuilder();
        value.codePoints().filter(c -> c == 9 || c == 10 || c == 13
                || c >= 0x20 && c <= 0xD7FF || c >= 0xE000 && c <= 0xFFFD
                || c >= 0x10000 && c <= 0x10FFFF).forEach(valid::appendCodePoint);
        return valid.toString().replace("&", "&amp;").replace("<", "&lt;")
                .replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&apos;");
    }
}
