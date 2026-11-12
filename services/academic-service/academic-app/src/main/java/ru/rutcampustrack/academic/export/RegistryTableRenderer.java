package ru.rutcampustrack.academic.export;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.springframework.stereotype.Component;

/** All formats consume this explicit safe projection, never a User entity/DTO. */
@Component
public class RegistryTableRenderer {
    private static final String NO_DATA = "Нет данных";
    private static final String LABEL_FILL = "F2F2F2";

    public record Table(
            String title, List<String> context, List<String> columns, List<List<Object>> rows) {
        public Table {
            context = List.copyOf(context);
            columns = List.copyOf(columns);
            rows = rows.stream().map(List::copyOf).toList();
        }

        @Override
        public String toString() {
            return "RegistryTable[rows=" + rows.size() + "]";
        }
    }

    public byte[] html(Table table) {
        StringBuilder out =
                new StringBuilder(
                        "<!doctype html><html lang=\"ru\"><meta charset=\"utf-8\"><title>");
        out.append(xml(table.title()))
                .append(
                        "</title><style>body{font:11pt Arial;color:#222}table{border-collapse:collapse}"
                            + "th,td{border:1pt solid #888;padding:5pt}"
                            + "th{text-align:left;font-weight:bold;background-color:#")
                .append(LABEL_FILL)
                .append("}</style><h1>")
                .append(xml(table.title()))
                .append("</h1>");
        table.context().forEach(c -> out.append("<p>").append(xml(c)).append("</p>"));
        out.append("<table><thead><tr>");
        table.columns().forEach(c -> out.append("<th>").append(xml(c)).append("</th>"));
        out.append("</tr></thead><tbody>");
        if (table.rows().isEmpty()) {
            out.append("<tr><td colspan=\"")
                    .append(table.columns().size())
                    .append("\">")
                    .append(NO_DATA)
                    .append("</td></tr>");
        }
        for (List<Object> row : table.rows()) {
            out.append("<tr>");
            for (int column = 0; column < row.size(); column++) {
                if (column == 0) {
                    out.append("<th scope=\"row\">").append(xml(row.get(column))).append("</th>");
                } else {
                    out.append("<td>").append(xml(row.get(column))).append("</td>");
                }
            }
            out.append("</tr>");
        }
        return out.append("</tbody></table></html>").toString().getBytes(StandardCharsets.UTF_8);
    }

    public byte[] docx(Table table) {
        StringBuilder document =
                new StringBuilder(
                        "<?xml version=\"1.0\" encoding=\"UTF-8\"?><w:document xmlns:w=\"http://schemas.openxmlformats.org/wordprocessingml/2006/main\"><w:body>");
        paragraph(document, table.title());
        table.context().forEach(c -> paragraph(document, c));
        document.append("<w:tbl><w:tblPr><w:tblW w:w=\"0\" w:type=\"auto\"/><w:tblBorders>");
        for (String side : List.of("top", "left", "bottom", "right", "insideH", "insideV"))
            document.append("<w:").append(side).append(" w:val=\"single\" w:sz=\"4\"/>");
        document.append("</w:tblBorders></w:tblPr><w:tblGrid>")
                .append("<w:gridCol w:w=\"2000\"/>".repeat(table.columns().size()))
                .append("</w:tblGrid>");
        wordRow(document, table.columns(), true);
        if (table.rows().isEmpty()) {
            document.append("<w:tr><w:tc><w:tcPr><w:gridSpan w:val=\"")
                    .append(table.columns().size())
                    .append("\"/></w:tcPr>");
            paragraph(document, NO_DATA);
            document.append("</w:tc></w:tr>");
        } else {
            table.rows().forEach(row -> wordRow(document, row, false));
        }
        document.append(
                "</w:tbl><w:sectPr><w:pgSz w:w=\"16838\" w:h=\"11906\" w:orient=\"landscape\"/><w:pgMar w:top=\"600\" w:right=\"600\" w:bottom=\"600\" w:left=\"600\"/></w:sectPr></w:body></w:document>");
        return zip(
                Map.of(
                        "[Content_Types].xml",
                                "<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\"><Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/><Default Extension=\"xml\" ContentType=\"application/xml\"/><Override PartName=\"/word/document.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml\"/></Types>",
                        "_rels/.rels",
                                "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\"><Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\" Target=\"word/document.xml\"/></Relationships>",
                        "word/document.xml", document.toString()));
    }

    public byte[] xlsx(Table table) {
        StringBuilder sheet =
                new StringBuilder(
                        "<worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\"><sheetData>");
        int index = 1;
        sheetRow(sheet, index++, List.of(table.title()), 0);
        for (String context : table.context()) sheetRow(sheet, index++, List.of(context), 0);
        sheetRow(sheet, index++, table.columns(), table.columns().size());
        if (table.rows().isEmpty()) {
            sheetRow(sheet, index, List.of(NO_DATA), 0);
        } else {
            for (List<Object> row : table.rows()) sheetRow(sheet, index++, row, 1);
        }
        sheet.append("</sheetData>");
        if (table.rows().isEmpty() && table.columns().size() > 1) {
            sheet.append("<mergeCells count=\"1\"><mergeCell ref=\"A")
                    .append(index)
                    .append(':')
                    .append(spreadsheetColumn(table.columns().size()))
                    .append(index)
                    .append("\"/></mergeCells>");
        }
        sheet.append("</worksheet>");
        Map<String, String> entries = new LinkedHashMap<>();
        entries.put(
                "[Content_Types].xml",
                "<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\"><Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/><Default Extension=\"xml\" ContentType=\"application/xml\"/><Override PartName=\"/xl/workbook.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml\"/><Override PartName=\"/xl/worksheets/sheet1.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml\"/><Override PartName=\"/xl/styles.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml\"/></Types>");
        entries.put(
                "_rels/.rels",
                "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\"><Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\" Target=\"xl/workbook.xml\"/></Relationships>");
        entries.put(
                "xl/workbook.xml",
                "<workbook xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\" xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\"><sheets><sheet name=\"Реестр\" sheetId=\"1\" r:id=\"rId1\"/></sheets></workbook>");
        entries.put(
                "xl/_rels/workbook.xml.rels",
                "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\"><Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet\" Target=\"worksheets/sheet1.xml\"/><Relationship Id=\"rId2\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles\" Target=\"styles.xml\"/></Relationships>");
        entries.put(
                "xl/styles.xml",
                """
                <styleSheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
                  <fonts count="2"><font/><font><b/></font></fonts>
                  <fills count="3">
                    <fill><patternFill patternType="none"/></fill>
                    <fill><patternFill patternType="gray125"/></fill>
                    <fill><patternFill patternType="solid"><fgColor rgb="FF%s"/></patternFill></fill>
                  </fills>
                  <borders count="1"><border/></borders>
                  <cellStyleXfs count="1"><xf numFmtId="0" fontId="0" fillId="0" borderId="0"/></cellStyleXfs>
                  <cellXfs count="2">
                    <xf numFmtId="0" fontId="0" fillId="0" borderId="0" xfId="0"/>
                    <xf numFmtId="0" fontId="1" fillId="2" borderId="0" xfId="0" applyFont="1" applyFill="1"/>
                  </cellXfs>
                  <cellStyles count="1"><cellStyle name="Normal" xfId="0" builtinId="0"/></cellStyles>
                </styleSheet>
                """.formatted(LABEL_FILL));
        entries.put("xl/worksheets/sheet1.xml", sheet.toString());
        return zip(entries);
    }

    private static String spreadsheetColumn(int number) {
        StringBuilder column = new StringBuilder();
        while (number > 0) {
            number--;
            column.append((char) ('A' + number % 26));
            number /= 26;
        }
        return column.reverse().toString();
    }

    private static void sheetRow(StringBuilder out, int row, List<?> values, int labelColumns) {
        out.append("<row r=\"").append(row).append("\">");
        for (int column = 0; column < values.size(); column++) {
            Object value = values.get(column);
            out.append("<c");
            if (column < labelColumns) out.append(" s=\"1\"");
            if (value instanceof Integer || value instanceof Long)
                out.append(" t=\"n\"><v>").append(value).append("</v></c>");
            else
                out.append(" t=\"inlineStr\"><is><t xml:space=\"preserve\">")
                        .append(xml(value))
                        .append("</t></is></c>");
        }
        out.append("</row>");
    }

    private static void wordRow(StringBuilder out, List<?> values, boolean header) {
        out.append("<w:tr>");
        if (header) out.append("<w:trPr><w:tblHeader/></w:trPr>");
        for (int column = 0; column < values.size(); column++) {
            boolean label = header || column == 0;
            out.append("<w:tc><w:tcPr><w:tcW w:w=\"2000\" w:type=\"dxa\"/>");
            if (label) {
                out.append("<w:shd w:val=\"clear\" w:color=\"auto\" w:fill=\"")
                        .append(LABEL_FILL)
                        .append("\"/>");
            }
            out.append("</w:tcPr>");
            paragraph(out, values.get(column), label);
            out.append("</w:tc>");
        }
        out.append("</w:tr>");
    }

    private static void paragraph(StringBuilder out, Object value) {
        paragraph(out, value, false);
    }

    private static void paragraph(StringBuilder out, Object value, boolean bold) {
        out.append("<w:p><w:r>");
        if (bold) out.append("<w:rPr><w:b/></w:rPr>");
        out.append("<w:t xml:space=\"preserve\">")
                .append(xml(value))
                .append("</w:t></w:r></w:p>");
    }

    private static byte[] zip(Map<String, String> entries) {
        try (ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                ZipOutputStream zip = new ZipOutputStream(bytes, StandardCharsets.UTF_8)) {
            for (var entry : entries.entrySet()) {
                zip.putNextEntry(new ZipEntry(entry.getKey()));
                zip.write(entry.getValue().getBytes(StandardCharsets.UTF_8));
                zip.closeEntry();
            }
            zip.finish();
            return bytes.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("Формирование файла недоступно", e);
        }
    }

    private static String xml(Object value) {
        if (value == null) return "";
        StringBuilder out = new StringBuilder();
        value.toString()
                .codePoints()
                .filter(
                        c ->
                                c == 9
                                        || c == 10
                                        || c == 13
                                        || c >= 32 && c <= 0xD7FF
                                        || c >= 0xE000 && c <= 0xFFFD
                                        || c >= 0x10000 && c <= 0x10FFFF)
                .forEach(
                        c -> {
                            switch (c) {
                                case '&' -> out.append("&amp;");
                                case '<' -> out.append("&lt;");
                                case '>' -> out.append("&gt;");
                                case '"' -> out.append("&quot;");
                                case '\'' -> out.append("&apos;");
                                default -> out.appendCodePoint(c);
                            }
                        });
        return out.toString();
    }
}
