package ru.rutcampustrack.attendance.report;

import ru.rutcampustrack.attendance.contract.api.ReportApi;
import ru.rutcampustrack.attendance.contract.dto.report.HeadmanWeeklyExportFormatOption;
import ru.rutcampustrack.attendance.exception.BadRequestException;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

enum HeadmanWeeklyReportFormat {
    DOCX("docx", "Word (.docx)", ReportApi.DOCX_MEDIA_TYPE, "docx"),
    PDF("pdf", "PDF (.pdf)", ReportApi.PDF_MEDIA_TYPE, "pdf"),
    PNG("png", "PNG, архив страниц (.zip)", ReportApi.ZIP_MEDIA_TYPE, "zip"),
    HTML("html", "HTML (.html)", ReportApi.HTML_MEDIA_TYPE, "html"),
    XLSX("xlsx", "Excel (.xlsx)", ReportApi.XLSX_MEDIA_TYPE, "xlsx");

    private final String label;
    private final String contentType;
    private final String extension;

    HeadmanWeeklyReportFormat(String code, String label, String contentType, String extension) {
        this.code = code;
        this.label = label;
        this.contentType = contentType;
        this.extension = extension;
    }

    private final String code;

    String code() {
        return code;
    }

    String label() {
        return label;
    }

    String extension() {
        return extension;
    }

    String contentType() {
        return contentType;
    }

    HeadmanWeeklyExportFormatOption toOption() {
        return new HeadmanWeeklyExportFormatOption(code, label, contentType, extension);
    }

    static List<HeadmanWeeklyExportFormatOption> catalogue() {
        return Arrays.stream(values()).map(HeadmanWeeklyReportFormat::toOption).toList();
    }

    static HeadmanWeeklyReportFormat from(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new BadRequestException("Не выбран формат выгрузки");
        }
        String normalized = raw.trim().toLowerCase(Locale.ROOT);
        return Arrays.stream(values())
                .filter(format -> format.code.equals(normalized))
                .findFirst()
                .orElseThrow(() -> new BadRequestException("Неизвестный формат выгрузки: " + raw));
    }
}
