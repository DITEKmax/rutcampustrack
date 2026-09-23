package ru.rutcampustrack.attendance.report;

import org.springframework.stereotype.Component;
import ru.rutcampustrack.attendance.exception.ReportExportTooLargeException;
import ru.rutcampustrack.attendance.report.teacher.TeacherAttendanceExportModel;
import ru.rutcampustrack.attendance.report.teacher.TeacherAttendanceExportModel.Column;
import ru.rutcampustrack.attendance.report.teacher.TeacherAttendanceExportModel.Row;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/** HTML and XLSX views over the exact server-assembled weekly attendance matrices. */
@Component
final class HeadmanWeeklyTabularRenderer {

    private static final int EXCEL_MAX_COLUMNS = 16_384;
    private static final int EXCEL_MAX_ROWS = 1_048_576;

    byte[] renderHtml(List<TeacherAttendanceExportModel> models) {
        StringBuilder html = new StringBuilder(32_768);
        html.append("<!doctype html><html lang=\"ru\"><head><meta charset=\"utf-8\">")
                .append("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">")
                .append("<title>Журнал посещаемости</title><style>")
                .append("body{font:15px/1.45 Arial,sans-serif;color:#222;margin:1.5rem}")
                .append("main{max-width:100%;margin:auto}table{border-collapse:collapse;min-width:100%;white-space:normal}")
                .append(".table-wrap{max-width:100%;overflow-x:auto;margin-block:1rem 2rem}")
                .append("th,td{border:1px solid #aab4be;padding:.45rem .55rem;text-align:left;vertical-align:top}")
                .append("thead th{background:#eaf1f7}tbody th{font-weight:600}td{text-align:center}")
                .append("h1,h2{line-height:1.2}h2{margin-block:1.5rem .5rem}small{color:#52606d}")
                .append("@media print{body{margin:.5rem}.table-wrap{overflow:visible}table{min-width:0;font-size:10pt}}")
                .append("</style></head><body><main><h1>Журнал посещаемости</h1>");
        if (!models.isEmpty()) {
            TeacherAttendanceExportModel.Context first = models.get(0).context();
            html.append("<p>Группа: <strong>").append(escapeHtml(first.groupLabel())).append("</strong>")
                    .append(" · Семестр: ").append(escapeHtml(first.semesterLabel()))
                    .append(" · Сформировано: ").append(escapeHtml(first.generatedAt().toString())).append("</p>");
        }
        for (TeacherAttendanceExportModel model : models) {
            TeacherAttendanceExportModel.Context context = model.context();
            html.append("<section><h2>Неделя ")
                    .append(escapeHtml(context.periodFrom().toString()))
                    .append(" — ").append(escapeHtml(context.periodTo().toString())).append("</h2>")
                    .append("<small>Типы занятий: ").append(escapeHtml(String.join(", ", context.typeLabels())))
                    .append("</small><div class=\"table-wrap\"><table><thead><tr><th scope=\"col\">Студент</th>");
            for (Column column : model.columns()) {
                html.append("<th scope=\"col\">").append(escapeHtml(columnHeader(column))).append("</th>");
            }
            html.append("</tr></thead><tbody>");
            for (Row row : model.rows()) {
                html.append("<tr><th scope=\"row\">").append(escapeHtml(row.displayName())).append("</th>");
                for (Column column : model.columns()) {
                    var cell = row.cellsByLessonId().get(column.lessonId());
                    html.append("<td>").append(escapeHtml(cell == null ? "" : cell.symbol().isBlank() ? "·" : cell.symbol()))
                            .append("</td>");
                }
                html.append("</tr>");
            }
            if (model.rows().isEmpty()) {
                html.append("<tr><td colspan=\"").append(model.columns().size() + 1)
                        .append("\">Состав группы по урокам недели не найден.</td></tr>");
            }
            html.append("</tbody></table></div></section>");
        }
        html.append("</main></body></html>");
        return html.toString().getBytes(StandardCharsets.UTF_8);
    }

    byte[] renderXlsx(List<TeacherAttendanceExportModel> models) {
        int maxLessonColumns = models.stream().mapToInt(model -> model.columns().size()).max().orElse(0);
        if (maxLessonColumns + 1 > EXCEL_MAX_COLUMNS) {
            throw new ReportExportTooLargeException("В одной неделе слишком много пар для листа Excel.");
        }
        long rowCount = 5;
        for (TeacherAttendanceExportModel model : models) {
            rowCount += 3L + model.rows().size();
        }
        if (rowCount > EXCEL_MAX_ROWS) {
            throw new ReportExportTooLargeException("Выбранные недели превышают лимит строк Excel.");
        }

        StringBuilder sheet = new StringBuilder(32_768);
        sheet.append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>")
                .append("<worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\">")
                .append("<sheetViews><sheetView workbookViewId=\"0\"><pane xSplit=\"1\" ySplit=\"7\" topLeftCell=\"B8\" activePane=\"bottomRight\" state=\"frozen\"/>")
                .append("<selection pane=\"topRight\" activeCell=\"B1\" sqref=\"B1\"/><selection pane=\"bottomLeft\" activeCell=\"A8\" sqref=\"A8\"/><selection pane=\"bottomRight\" activeCell=\"B8\" sqref=\"B8\"/></sheetView></sheetViews><cols>")
                .append("<col min=\"1\" max=\"1\" width=\"34\" customWidth=\"1\"/>");
        for (int column = 2; column <= maxLessonColumns + 1; column++) {
            sheet.append("<col min=\"").append(column).append("\" max=\"").append(column)
                    .append("\" width=\"24\" customWidth=\"1\"/>");
        }
        sheet.append("</cols><sheetData>");

        int rowIndex = 1;
        if (!models.isEmpty()) {
            TeacherAttendanceExportModel.Context context = models.get(0).context();
            appendTextRow(sheet, rowIndex++, List.of("Журнал посещаемости", context.groupLabel()));
            appendTextRow(sheet, rowIndex++, List.of("Семестр", context.semesterLabel()));
            appendTextRow(sheet, rowIndex++, List.of("Сформировано", context.generatedAt().toString()));
            appendTextRow(sheet, rowIndex++, List.of("Типы занятий", String.join(", ", context.typeLabels())));
        }
        rowIndex++;
        for (TeacherAttendanceExportModel model : models) {
            var context = model.context();
            appendTextRow(sheet, rowIndex++, List.of("Неделя", context.periodFrom().toString() + " — " + context.periodTo()));
            List<String> headers = new ArrayList<>(model.columns().size() + 1);
            headers.add("Студент");
            model.columns().stream().map(HeadmanWeeklyTabularRenderer::columnHeader).forEach(headers::add);
            appendTextRow(sheet, rowIndex++, headers, true);
            for (Row row : model.rows()) {
                List<String> values = new ArrayList<>(model.columns().size() + 1);
                values.add(row.displayName());
                for (Column column : model.columns()) {
                    var cell = row.cellsByLessonId().get(column.lessonId());
                    values.add(cell == null ? "" : cell.symbol().isBlank() ? "·" : cell.symbol());
                }
                appendTextRow(sheet, rowIndex++, values, false);
            }
            rowIndex++;
        }
        sheet.append("</sheetData></worksheet>");

        try (ByteArrayOutputStream output = new ByteArrayOutputStream();
             ZipOutputStream zip = new ZipOutputStream(output, StandardCharsets.UTF_8)) {
            put(zip, "[Content_Types].xml", "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                    + "<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\">"
                    + "<Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/>"
                    + "<Default Extension=\"xml\" ContentType=\"application/xml\"/>"
                    + "<Override PartName=\"/xl/workbook.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml\"/>"
                    + "<Override PartName=\"/xl/styles.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml\"/>"
                    + "<Override PartName=\"/xl/worksheets/sheet1.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml\"/>"
                    + "</Types>");
            put(zip, "_rels/.rels", "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                    + "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">"
                    + "<Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\" Target=\"xl/workbook.xml\"/>"
                    + "</Relationships>");
            put(zip, "xl/workbook.xml", "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
                    + "<workbook xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\" xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\"><sheets>"
                    + "<sheet name=\"Журнал\" sheetId=\"1\" r:id=\"rId1\"/></sheets></workbook>");
            put(zip, "xl/_rels/workbook.xml.rels", "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                    + "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">"
                    + "<Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet\" Target=\"worksheets/sheet1.xml\"/>"
                    + "<Relationship Id=\"rId2\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles\" Target=\"styles.xml\"/>"
                    + "</Relationships>");
            put(zip, "xl/styles.xml", stylesXml());
            put(zip, "xl/worksheets/sheet1.xml", sheet.toString());
            zip.finish();
            return output.toByteArray();
        } catch (IOException ex) {
            throw new IllegalStateException("Не удалось собрать книгу Excel", ex);
        }
    }

    private static String columnHeader(Column column) {
        StringBuilder value = new StringBuilder(column.date().toString());
        if (column.startTime() != null) value.append('\n').append(column.startTime());
        if (column.lessonNumber() > 0) value.append("\n№ ").append(column.lessonNumber());
        if (!column.subjectLabel().isBlank()) value.append('\n').append(column.subjectLabel());
        String type = column.typeLabel().isBlank() ? column.lessonType() : column.typeLabel();
        if (!type.isBlank()) value.append('\n').append(type);
        String state = stateLabel(column.state());
        if (!state.isBlank()) value.append('\n').append(state);
        return value.toString();
    }

    private static String stateLabel(String state) {
        if (state == null) return "";
        return switch (state.toUpperCase(Locale.ROOT)) {
            case "PLANNED" -> "Запланировано";
            case "ACTIVE" -> "Идёт";
            case "CLOSED" -> "Проведено";
            case "CANCELLED" -> "Отменено";
            default -> "";
        };
    }

    private static void appendTextRow(StringBuilder sheet, int row, List<String> values) {
        appendTextRow(sheet, row, values, false);
    }

    private static void appendTextRow(StringBuilder sheet, int row, List<String> values, boolean header) {
        sheet.append("<row r=\"").append(row).append("\">");
        for (int index = 0; index < values.size(); index++) {
            String reference = columnName(index + 1) + row;
            sheet.append("<c r=\"").append(reference).append("\" t=\"inlineStr\"")
                    .append(header ? " s=\"1\"" : "")
                    .append(">");
            sheet
                    .append("<is><t xml:space=\"preserve\">").append(escapeXml(values.get(index)))
                    .append("</t></is></c>");
        }
        sheet.append("</row>");
    }

    private static String stylesXml() {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
                + "<styleSheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\">"
                + "<fonts count=\"2\"><font><sz val=\"11\"/><name val=\"Arial\"/></font>"
                + "<font><b/><sz val=\"11\"/><name val=\"Arial\"/></font></fonts>"
                + "<fills count=\"3\"><fill><patternFill patternType=\"none\"/></fill>"
                + "<fill><patternFill patternType=\"gray125\"/></fill>"
                + "<fill><patternFill patternType=\"solid\"><fgColor rgb=\"FFEAF1F7\"/>"
                + "<bgColor indexed=\"64\"/></patternFill></fill></fills>"
                + "<borders count=\"1\"><border><left/><right/><top/><bottom/><diagonal/></border></borders>"
                + "<cellStyleXfs count=\"1\"><xf numFmtId=\"0\" fontId=\"0\" fillId=\"0\" borderId=\"0\"/></cellStyleXfs>"
                + "<cellXfs count=\"2\"><xf numFmtId=\"0\" fontId=\"0\" fillId=\"0\" borderId=\"0\" xfId=\"0\"/>"
                + "<xf numFmtId=\"0\" fontId=\"1\" fillId=\"2\" borderId=\"0\" xfId=\"0\" applyFont=\"1\" applyFill=\"1\" applyAlignment=\"1\"><alignment vertical=\"top\" wrapText=\"1\"/></xf>"
                + "</cellXfs><cellStyles count=\"1\"><cellStyle name=\"Normal\" xfId=\"0\" builtinId=\"0\"/></cellStyles></styleSheet>";
    }

    private static String columnName(int column) {
        StringBuilder name = new StringBuilder();
        int remaining = column;
        while (remaining > 0) {
            int digit = (remaining - 1) % 26;
            name.append((char) ('A' + digit));
            remaining = (remaining - 1) / 26;
        }
        return name.reverse().toString();
    }

    private static String escapeHtml(String value) {
        return value == null ? "" : value.replace("&", "&amp;").replace("<", "&lt;")
                .replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&#39;");
    }

    private static String escapeXml(String value) {
        return value == null ? "" : value.replace("&", "&amp;").replace("<", "&lt;")
                .replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&apos;");
    }

    private static void put(ZipOutputStream zip, String name, String content) throws IOException {
        zip.putNextEntry(new ZipEntry(name));
        zip.write(content.getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
    }
}
