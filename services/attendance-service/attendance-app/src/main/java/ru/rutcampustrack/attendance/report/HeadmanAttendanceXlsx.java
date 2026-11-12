package ru.rutcampustrack.attendance.report;

import static ru.rutcampustrack.attendance.report.HeadmanTabularAttendance.DASH;
import static ru.rutcampustrack.attendance.report.HeadmanTabularAttendance.NO_LESSONS;
import static ru.rutcampustrack.attendance.report.HeadmanTabularAttendance.NO_MARKS;
import static ru.rutcampustrack.attendance.report.HeadmanTabularAttendance.NO_STUDENTS;
import static ru.rutcampustrack.attendance.report.HeadmanTabularAttendance.SUBJECT_STATS;
import static ru.rutcampustrack.attendance.report.HeadmanTabularAttendance.SUMMARY;
import static ru.rutcampustrack.attendance.report.HeadmanTabularAttendance.WEEKS;
import static ru.rutcampustrack.attendance.report.HeadmanTabularAttendance.cancelled;
import static ru.rutcampustrack.attendance.report.HeadmanTabularAttendance.numberAndType;
import static ru.rutcampustrack.attendance.report.HeadmanTabularAttendance.range;
import static ru.rutcampustrack.attendance.report.HeadmanTabularAttendance.shortType;
import static ru.rutcampustrack.attendance.report.HeadmanWeeklyTabularRenderer.ABSENT_FILL;
import static ru.rutcampustrack.attendance.report.HeadmanWeeklyTabularRenderer.CANCELLED_HEADER;
import static ru.rutcampustrack.attendance.report.HeadmanWeeklyTabularRenderer.EXCUSED_FILL;
import static ru.rutcampustrack.attendance.report.HeadmanWeeklyTabularRenderer.NAVY;
import static ru.rutcampustrack.attendance.report.HeadmanWeeklyTabularRenderer.PRESENT_FILL;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import ru.rutcampustrack.attendance.exception.ReportExportTooLargeException;
import ru.rutcampustrack.attendance.report.HeadmanTabularAttendance.Counts;
import ru.rutcampustrack.attendance.report.HeadmanTabularAttendance.Journal;
import ru.rutcampustrack.attendance.report.HeadmanTabularAttendance.JournalRow;
import ru.rutcampustrack.attendance.report.HeadmanTabularAttendance.Mark;
import ru.rutcampustrack.attendance.report.HeadmanTabularAttendance.Student;
import ru.rutcampustrack.attendance.report.teacher.TeacherAttendanceExportModel;
import ru.rutcampustrack.attendance.report.teacher.TeacherAttendanceExportModel.Column;

/** Writes the journal and its weekly analysis as a minimal OOXML workbook. */
final class HeadmanAttendanceXlsx {
    private static final int EXCEL_MAX_COLUMNS = 16_384;
    private static final int EXCEL_MAX_ROWS = 1_048_576;
    private static final String XML = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>";
    private static final String SHEET_NS =
            "http://schemas.openxmlformats.org/spreadsheetml/2006/main";
    private static final String REL_NS =
            "http://schemas.openxmlformats.org/package/2006/relationships";
    private static final String DOCUMENT_REL =
            "http://schemas.openxmlformats.org/officeDocument/2006/relationships";
    private static final String CONTENT_PREFIX =
            "application/vnd.openxmlformats-officedocument.spreadsheetml.";

    private HeadmanAttendanceXlsx() {}

    static byte[] render(List<TeacherAttendanceExportModel> models, boolean selectedWeeks) {
        long journalRows = 3;
        for (var model : models) {
            boolean subject = model.context().subjectId() != null;
            int fixedColumns = 1 + (subject ? SUBJECT_STATS.size() : 0);
            if (model.columns().size() > EXCEL_MAX_COLUMNS - fixedColumns) {
                throw new ReportExportTooLargeException(
                        "Слишком много занятий для одного листа Excel.");
            }
            journalRows += model.rows().size() + (subject ? 8L : 6L);
        }
        if (journalRows > EXCEL_MAX_ROWS || selectedWeeks && models.size() > EXCEL_MAX_COLUMNS - 2) {
            throw new ReportExportTooLargeException("Выбранный период превышает лимит Excel.");
        }

        var report = HeadmanTabularAttendance.from(models);
        var sheets = new ArrayList<Sheet>();
        sheets.add(journalSheet(report));
        if (report.weekly()) {
            sheets.add(summarySheet(report));
            if (selectedWeeks) {
                sheets.add(trendSheet(report));
            }
        }
        return workbook(sheets);
    }

    private static Sheet journalSheet(HeadmanTabularAttendance report) {
        var sheet = new Worksheet();
        int lessonColumns =
                report.journals().stream().mapToInt(journal -> journal.columns().size()).max().orElse(0);
        boolean subject = !report.journals().isEmpty() && !report.weekly();
        sheet.width(1, 1, 34);
        sheet.width(2, lessonColumns + 1, 12);
        if (subject) {
            sheet.width(lessonColumns + 2, lessonColumns + 1 + SUBJECT_STATS.size(), 17);
        }
        sheet.row(24).text(1, "Журнал посещаемости", Style.TITLE).endRow();
        if (report.journals().isEmpty()) {
            sheet.row(0).text(1, NO_LESSONS, Style.DEFAULT).endRow();
        } else {
            var context = report.journals().getFirst().context();
            sheet.row(0)
                    .text(
                            1,
                            "Группа: " + context.groupLabel() + " · Семестр: " + context.semesterLabel(),
                            Style.DEFAULT)
                    .endRow();
            sheet.row(0)
                    .text(1, "Сформировано: " + context.generatedAt(), Style.DEFAULT)
                    .endRow();
        }
        for (Journal journal : report.journals()) {
            appendJournal(sheet, journal);
        }
        int frozenRows = report.journals().isEmpty() ? 0 : 3;
        return sheet.finish("Журнал", 1, frozenRows);
    }

    private static void appendJournal(Worksheet sheet, Journal journal) {
        sheet.row(22)
                .text(
                        1,
                        journal.subject()
                                ? journal.title() + " · " + range(journal.context())
                                : journal.title(),
                        Style.TITLE)
                .endRow();
        if (journal.subject()) {
            for (String filter : journal.subjectFilters()) {
                sheet.row(0).text(1, filter, Style.DEFAULT).endRow();
            }
            appendSubjectHeaders(sheet, journal);
        } else {
            appendWeekHeaders(sheet, journal);
        }
        for (JournalRow row : journal.rows()) {
            sheet.row(0).text(1, row.row().displayName(), Style.STUDENT);
            int index = 2;
            for (Column column : journal.columns()) {
                Mark mark = row.mark(column);
                sheet.text(index++, mark.symbol, Style.forMark(mark));
            }
            if (journal.subject()) {
                Counts counts = row.counts();
                sheet.number(index++, counts.present(), Style.VALUE)
                        .percent(index++, counts, Style.PERCENT)
                        .number(index++, counts.excused(), Style.VALUE)
                        .number(index++, counts.absent(), Style.VALUE)
                        .number(index, counts.denominator(), Style.VALUE);
            }
            sheet.endRow();
        }
        String emptyMessage = journal.emptyMessage();
        if (!emptyMessage.isEmpty()) {
            sheet.row(0).text(1, emptyMessage, Style.DEFAULT).endRow();
        }
        sheet.row(8).endRow();
    }

    private static void appendSubjectHeaders(Worksheet sheet, Journal journal) {
        sheet.row(68).text(1, "Студент", Style.HEADER_LEFT);
        int index = 2;
        for (Column column : journal.columns()) {
            sheet.text(
                    index++,
                    column.date().toString(),
                    cancelled(column) ? Style.CANCELLED_ROTATED_HEADER : Style.ROTATED_HEADER);
        }
        for (String label : SUBJECT_STATS) {
            sheet.text(index, label, Style.HEADER).merge(index, index, 1);
            index++;
        }
        sheet.merge(1, 1, 1).endRow();
        sheet.row(22).text(1, "", Style.HEADER);
        index = 2;
        for (Column column : journal.columns()) {
            sheet.text(
                    index++,
                    numberAndType(column),
                    cancelled(column) ? Style.CANCELLED_HEADER : Style.HEADER);
        }
        for (int remaining = 0; remaining < SUBJECT_STATS.size(); remaining++) {
            sheet.text(index++, "", Style.HEADER);
        }
        sheet.endRow();
    }

    private static void appendWeekHeaders(Worksheet sheet, Journal journal) {
        sheet.row(22).text(1, "Студент", Style.HEADER_LEFT).merge(1, 1, 2);
        for (var date : journal.dates()) {
            int first = date.start() + 2;
            int last = date.end() + 1;
            sheet.text(first, date.date().toString(), Style.HEADER);
            if (last > first) {
                sheet.merge(first, last, 0);
                for (int column = first + 1; column <= last; column++) {
                    sheet.text(column, "", Style.HEADER);
                }
            }
        }
        sheet.endRow();
        int subjectLength =
                journal.columns().stream().mapToInt(column -> column.subjectLabel().length()).max().orElse(0);
        sheet.row(Math.clamp(16L + 6L * subjectLength, 64, 405))
                .text(1, "", Style.HEADER);
        int index = 2;
        for (Column column : journal.columns()) {
            sheet.text(index++, column.subjectLabel(), Style.ROTATED_HEADER);
        }
        sheet.endRow();
        sheet.row(22).text(1, "", Style.HEADER);
        index = 2;
        for (Column column : journal.columns()) {
            sheet.text(index++, shortType(column), Style.HEADER);
        }
        sheet.endRow();
    }

    private static void appendWeeklyHeading(
            Worksheet sheet, String title, HeadmanTabularAttendance report) {
        var first = report.journals().getFirst().context();
        var last = report.journals().getLast().context();
        sheet.row(24).text(1, title, Style.TITLE).endRow();
        sheet.row(0)
                .text(1, "Группа: " + first.groupLabel() + " · Семестр: " + first.semesterLabel(), Style.DEFAULT)
                .endRow();
        sheet.row(0)
                .text(1, "Период отчёта: " + first.periodFrom() + " — " + last.periodTo(), Style.DEFAULT)
                .endRow();
    }

    private static Sheet summarySheet(HeadmanTabularAttendance report) {
        var sheet = new Worksheet();
        List<Student> students = report.students();
        sheet.width(1, 1, 6);
        sheet.width(2, 2, 34);
        sheet.width(3, 4, 18);
        sheet.width(5, 5, 23);
        appendWeeklyHeading(sheet, SUMMARY, report);
        sheet.row(24)
                .text(1, "№", Style.HEADER)
                .text(2, "Студент", Style.HEADER_LEFT)
                .text(3, "Присутствовал", Style.HEADER)
                .text(4, "Всего отметок", Style.HEADER)
                .text(5, "Посещаемость", Style.HEADER)
                .endRow();
        if (students.isEmpty()) {
            sheet.row(0).text(2, NO_STUDENTS, Style.DEFAULT).endRow();
        } else {
            int rank = 0;
            for (Student student : students) {
                Counts counts = student.total();
                sheet.row(0)
                        .number(1, ++rank, Style.VALUE)
                        .text(2, student.displayName(), Style.STUDENT)
                        .number(3, counts.present(), Style.VALUE)
                        .number(4, counts.denominator(), Style.VALUE)
                        .percent(5, counts, Style.PERCENT)
                        .endRow();
            }
            sheet.conditionalFormatting(
                    "E5:E" + (students.size() + 4),
                    """
                    <cfRule type="dataBar" priority="1"><dataBar minLength="0" maxLength="100">
                    <cfvo type="num" val="0"/><cfvo type="num" val="1"/>
                    <color rgb="FF8DD2BC"/></dataBar></cfRule>
                    """);
            if (students.stream().allMatch(student -> student.total().denominator() == 0)) {
                sheet.row(0).text(2, NO_MARKS, Style.DEFAULT).endRow();
            }
        }
        return sheet.finish(SUMMARY, 2, 4);
    }

    private static Sheet trendSheet(HeadmanTabularAttendance report) {
        var sheet = new Worksheet();
        int lastColumn = report.journals().size() + 2;
        sheet.width(1, 1, 34);
        sheet.width(2, lastColumn, 27);
        appendWeeklyHeading(sheet, WEEKS, report);
        sheet.row(28).text(1, "Студент", Style.HEADER_LEFT);
        int column = 2;
        for (Journal journal : report.journals()) {
            sheet.text(column++, range(journal.context()), Style.HEADER);
        }
        sheet.text(lastColumn, "За весь период", Style.HEADER).endRow();

        if (report.students().isEmpty()) {
            sheet.row(0).text(1, NO_STUDENTS, Style.DEFAULT).endRow();
        } else {
            for (Student student : report.students()) {
                sheet.row(0).text(1, student.displayName(), Style.STUDENT);
                column = 2;
                for (Counts counts : student.weeks()) {
                    sheet.percent(column++, counts, Style.PERCENT);
                }
                sheet.percent(lastColumn, student.total(), Style.TOTAL_PERCENT).endRow();
            }
            sheet.conditionalFormatting(
                    "B5:" + columnName(lastColumn) + (report.students().size() + 4),
                    """
                    <cfRule type="colorScale" priority="1"><colorScale>
                    <cfvo type="num" val="0"/><cfvo type="num" val="0.5"/><cfvo type="num" val="1"/>
                    <color rgb="FF%s"/><color rgb="FF%s"/><color rgb="FF%s"/>
                    </colorScale></cfRule>
                    """
                            .formatted(ABSENT_FILL, EXCUSED_FILL, PRESENT_FILL));
        }
        return sheet.finish(WEEKS, 1, 4);
    }

    private static byte[] workbook(List<Sheet> sheets) {
        StringBuilder contentTypes =
                new StringBuilder(XML)
                        .append(
                                """
                                <Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
                                <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
                                <Default Extension="xml" ContentType="application/xml"/>
                                """);
        override(contentTypes, "/xl/workbook.xml", "sheet.main");
        override(contentTypes, "/xl/styles.xml", "styles");

        StringBuilder workbook =
                new StringBuilder(XML)
                        .append("<workbook xmlns=\"")
                        .append(SHEET_NS)
                        .append("\" xmlns:r=\"")
                        .append(DOCUMENT_REL)
                        .append("\"><bookViews><workbookView activeTab=\"0\"/></bookViews><sheets>");
        StringBuilder relationships = new StringBuilder(XML).append("<Relationships xmlns=\"")
                .append(REL_NS).append("\">");
        try (ByteArrayOutputStream output = new ByteArrayOutputStream();
                ZipOutputStream zip = new ZipOutputStream(output, StandardCharsets.UTF_8)) {
            for (int index = 0; index < sheets.size(); index++) {
                int sheetId = index + 1;
                Sheet sheet = sheets.get(index);
                String path = "worksheets/sheet" + sheetId + ".xml";
                override(contentTypes, "/xl/" + path, "worksheet");
                relationship(relationships, sheetId, "worksheet", path);
                workbook.append("<sheet name=\"")
                        .append(escapeXml(sheet.name()))
                        .append("\" sheetId=\"")
                        .append(sheetId)
                        .append("\" r:id=\"rId")
                        .append(sheetId)
                        .append("\"/>");
                put(zip, "xl/" + path, sheet.xml());
            }
            relationship(relationships, sheets.size() + 1, "styles", "styles.xml");
            put(zip, "xl/styles.xml", stylesXml());
            put(zip, "xl/workbook.xml", workbook.append("</sheets></workbook>").toString());
            put(zip, "xl/_rels/workbook.xml.rels", relationships.append("</Relationships>").toString());
            put(zip, "[Content_Types].xml", contentTypes.append("</Types>").toString());

            StringBuilder rootRelationships =
                    new StringBuilder(XML).append("<Relationships xmlns=\"").append(REL_NS).append("\">");
            relationship(rootRelationships, 1, "officeDocument", "xl/workbook.xml");
            put(zip, "_rels/.rels", rootRelationships.append("</Relationships>").toString());
            zip.finish();
            return output.toByteArray();
        } catch (IOException ex) {
            throw new IllegalStateException("Не удалось собрать книгу Excel", ex);
        }
    }

    private static void override(StringBuilder xml, String path, String type) {
        xml.append("<Override PartName=\"")
                .append(path)
                .append("\" ContentType=\"")
                .append(CONTENT_PREFIX)
                .append(type)
                .append("+xml\"/>");
    }

    private static void relationship(StringBuilder xml, int id, String type, String path) {
        xml.append("<Relationship Id=\"rId")
                .append(id)
                .append("\" Type=\"")
                .append(DOCUMENT_REL)
                .append('/')
                .append(type)
                .append("\" Target=\"")
                .append(path)
                .append("\"/>");
    }

    private static void put(ZipOutputStream zip, String name, String content) throws IOException {
        zip.putNextEntry(new ZipEntry(name));
        zip.write(content.getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
    }

    private static String stylesXml() {
        StringBuilder styles =
                new StringBuilder(XML)
                        .append(
                                """
                                <styleSheet xmlns="%s">
                                <fonts count="4">
                                  <font><sz val="11"/><name val="Arial"/></font>
                                  <font><b/><sz val="11"/><name val="Arial"/></font>
                                  <font><b/><sz val="11"/><color rgb="FFFFFFFF"/><name val="Arial"/></font>
                                  <font><b/><strike/><sz val="11"/><color rgb="FFFFFFFF"/><name val="Arial"/></font>
                                </fonts>
                                <fills count="8">
                                  <fill><patternFill patternType="none"/></fill>
                                  <fill><patternFill patternType="gray125"/></fill>
                                """
                                        .formatted(SHEET_NS));
        for (String color :
                List.of(NAVY, "F2F5F9", PRESENT_FILL, EXCUSED_FILL, ABSENT_FILL, CANCELLED_HEADER)) {
            styles.append("<fill><patternFill patternType=\"solid\"><fgColor rgb=\"FF")
                    .append(color)
                    .append("\"/><bgColor indexed=\"64\"/></patternFill></fill>");
        }
        styles.append(
                """
                </fills><borders count="2">
                  <border><left/><right/><top/><bottom/><diagonal/></border>
                  <border>
                    <left style="thin"><color rgb="FFD1D9E3"/></left>
                    <right style="thin"><color rgb="FFD1D9E3"/></right>
                    <top style="thin"><color rgb="FFD1D9E3"/></top>
                    <bottom style="thin"><color rgb="FFD1D9E3"/></bottom>
                    <diagonal/>
                  </border>
                </borders>
                <cellStyleXfs count="1"><xf numFmtId="0" fontId="0" fillId="0" borderId="0"/></cellStyleXfs>
                <cellXfs count="
                """.stripTrailing());
        styles.append(Style.values().length).append("\">");
        for (Style style : Style.values()) {
            styles.append("<xf xfId=\"0\" numFmtId=\"")
                    .append(style.format)
                    .append("\" fontId=\"")
                    .append(style.font)
                    .append("\" fillId=\"")
                    .append(style.fill)
                    .append("\" borderId=\"")
                    .append(style.border)
                    .append("\" applyFont=\"1\" applyFill=\"1\" applyBorder=\"1\"")
                    .append(" applyNumberFormat=\"1\" applyAlignment=\"1\"><alignment vertical=\"center\" horizontal=\"")
                    .append(style.horizontal)
                    .append("\" wrapText=\"")
                    .append(style.wrap ? 1 : 0)
                    .append("\" textRotation=\"")
                    .append(style.rotation)
                    .append("\"/></xf>");
        }
        return styles.append(
                        """
                        </cellXfs><cellStyles count="1">
                        <cellStyle name="Normal" xfId="0" builtinId="0"/></cellStyles></styleSheet>
                        """)
                .toString();
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

    private static String escapeXml(String value) {
        StringBuilder result = new StringBuilder(value.length());
        value.codePoints()
                .filter(HeadmanAttendanceXlsx::isXml10CodePoint)
                .forEach(
                        codePoint -> {
                            switch (codePoint) {
                                case '&' -> result.append("&amp;");
                                case '<' -> result.append("&lt;");
                                case '>' -> result.append("&gt;");
                                case '"' -> result.append("&quot;");
                                case '\'' -> result.append("&apos;");
                                default -> result.appendCodePoint(codePoint);
                            }
                        });
        return result.toString();
    }

    private static boolean isXml10CodePoint(int codePoint) {
        return codePoint == 0x9
                || codePoint == 0xA
                || codePoint == 0xD
                || codePoint >= 0x20 && codePoint <= 0xD7FF
                || codePoint >= 0xE000 && codePoint <= 0xFFFD
                || codePoint >= 0x10000 && codePoint <= 0x10FFFF;
    }

    private record Sheet(String name, String xml) {}

    private enum Style {
        DEFAULT(0, 0, 0, 0, "left", false, 0),
        TITLE(1, 0, 0, 0, "left", false, 0),
        HEADER(2, 2, 1, 0, "center", true, 0),
        HEADER_LEFT(2, 2, 1, 0, "left", true, 0),
        ROTATED_HEADER(2, 2, 1, 0, "center", true, 90),
        STUDENT(1, 3, 1, 0, "left", true, 0),
        VALUE(0, 0, 1, 0, "center", false, 0),
        PRESENT(0, 4, 1, 0, "center", false, 0),
        EXCUSED(0, 5, 1, 0, "center", false, 0),
        ABSENT(0, 6, 1, 0, "center", false, 0),
        PERCENT(0, 0, 1, 10, "center", false, 0),
        TOTAL_PERCENT(1, 0, 1, 10, "center", false, 0),
        CANCELLED_HEADER(3, 7, 1, 0, "center", true, 0),
        CANCELLED_ROTATED_HEADER(3, 7, 1, 0, "center", true, 90);

        final int font;
        final int fill;
        final int border;
        final int format;
        final String horizontal;
        final boolean wrap;
        final int rotation;

        Style(int font, int fill, int border, int format, String horizontal, boolean wrap, int rotation) {
            this.font = font;
            this.fill = fill;
            this.border = border;
            this.format = format;
            this.horizontal = horizontal;
            this.wrap = wrap;
            this.rotation = rotation;
        }

        static Style forMark(Mark mark) {
            return switch (mark) {
                case EMPTY -> VALUE;
                case PRESENT -> PRESENT;
                case EXCUSED -> EXCUSED;
                case ABSENT -> ABSENT;
            };
        }
    }

    private static final class Worksheet {
        private final StringBuilder columns = new StringBuilder();
        private final StringBuilder rows = new StringBuilder(32_768);
        private final List<String> merges = new ArrayList<>();
        private final StringBuilder conditionalFormats = new StringBuilder();
        private int row;

        void width(int first, int last, int width) {
            if (last >= first) {
                columns.append("<col min=\"")
                        .append(first)
                        .append("\" max=\"")
                        .append(last)
                        .append("\" width=\"")
                        .append(width)
                        .append("\" customWidth=\"1\"/>");
            }
        }

        Worksheet row(int height) {
            rows.append("<row r=\"").append(++row).append('"');
            if (height > 0) {
                rows.append(" ht=\"").append(height).append("\" customHeight=\"1\"");
            }
            rows.append('>');
            return this;
        }

        void endRow() {
            rows.append("</row>");
        }

        Worksheet text(int column, String value, Style style) {
            cell(column, style)
                    .append(" t=\"inlineStr\"><is><t xml:space=\"preserve\">")
                    .append(escapeXml(value))
                    .append("</t></is></c>");
            return this;
        }

        Worksheet number(int column, int value, Style style) {
            return number(column, BigDecimal.valueOf(value), style);
        }

        Worksheet percent(int column, Counts counts, Style style) {
            return counts.denominator() == 0
                    ? text(column, DASH, style)
                    : number(column, counts.fraction(), style);
        }

        private Worksheet number(int column, BigDecimal value, Style style) {
            cell(column, style).append("><v>").append(value.toPlainString()).append("</v></c>");
            return this;
        }

        private StringBuilder cell(int column, Style style) {
            return rows.append("<c r=\"")
                    .append(columnName(column))
                    .append(row)
                    .append("\" s=\"")
                    .append(style.ordinal())
                    .append('"');
        }

        Worksheet merge(int first, int last, int extraRows) {
            merges.add(columnName(first) + row + ":" + columnName(last) + (row + extraRows));
            return this;
        }

        void conditionalFormatting(String range, String rules) {
            conditionalFormats.append("<conditionalFormatting sqref=\"")
                    .append(range)
                    .append("\">")
                    .append(rules)
                    .append("</conditionalFormatting>");
        }

        Sheet finish(String name, int frozenColumns, int frozenRows) {
            StringBuilder xml =
                    new StringBuilder(XML)
                            .append("<worksheet xmlns=\"")
                            .append(SHEET_NS)
                            .append("\"><sheetViews><sheetView workbookViewId=\"0\">");
            if (frozenRows > 0) {
                String firstScrollable = columnName(frozenColumns + 1) + (frozenRows + 1);
                xml.append("<pane xSplit=\"")
                        .append(frozenColumns)
                        .append("\" ySplit=\"")
                        .append(frozenRows)
                        .append("\" topLeftCell=\"")
                        .append(firstScrollable)
                        .append("\" activePane=\"bottomRight\" state=\"frozen\"/>")
                        .append("<selection pane=\"bottomRight\" activeCell=\"")
                        .append(firstScrollable)
                        .append("\" sqref=\"")
                        .append(firstScrollable)
                        .append("\"/>");
            }
            xml.append("</sheetView></sheetViews><cols>")
                    .append(columns)
                    .append("</cols><sheetData>")
                    .append(rows)
                    .append("</sheetData>");
            if (!merges.isEmpty()) {
                xml.append("<mergeCells count=\"").append(merges.size()).append("\">");
                for (String merge : merges) {
                    xml.append("<mergeCell ref=\"").append(merge).append("\"/>");
                }
                xml.append("</mergeCells>");
            }
            return new Sheet(name, xml.append(conditionalFormats).append("</worksheet>").toString());
        }
    }
}
