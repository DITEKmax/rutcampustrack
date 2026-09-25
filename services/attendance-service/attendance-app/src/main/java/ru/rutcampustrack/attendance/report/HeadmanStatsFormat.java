package ru.rutcampustrack.attendance.report;

import ru.rutcampustrack.attendance.contract.api.ReportApi;
import ru.rutcampustrack.attendance.contract.dto.report.HeadmanStatsResponse.FormatOption;
import ru.rutcampustrack.attendance.exception.BadRequestException;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

enum HeadmanStatsFormat {
    DOCX("docx", "Word (.docx)", ReportApi.DOCX_MEDIA_TYPE, "docx"),
    PDF("pdf", "PDF (.pdf)", ReportApi.PDF_MEDIA_TYPE, "pdf"),
    PNG("png", "PNG, архив страниц (.zip)", ReportApi.ZIP_MEDIA_TYPE, "zip"),
    HTML("html", "HTML (.html)", ReportApi.HTML_MEDIA_TYPE, "html"),
    XLSX("xlsx", "Excel (.xlsx)", ReportApi.XLSX_MEDIA_TYPE, "xlsx");

    private final String code;
    private final String label;
    private final String contentType;
    private final String extension;

    HeadmanStatsFormat(String code, String label, String contentType, String extension) {
        this.code = code;
        this.label = label;
        this.contentType = contentType;
        this.extension = extension;
    }

    String code() { return code; }
    String label() { return label; }
    String contentType() { return contentType; }
    String extension() { return extension; }

    FormatOption toOption() {
        return new FormatOption(code, label, contentType, extension);
    }

    static List<FormatOption> catalogue() {
        return Arrays.stream(values()).map(HeadmanStatsFormat::toOption).toList();
    }

    static HeadmanStatsFormat from(String raw) {
        if (raw == null || raw.isBlank()) throw new BadRequestException("Не выбран формат выгрузки");
        String normalized = raw.trim().toLowerCase(Locale.ROOT);
        return Arrays.stream(values()).filter(value -> value.code.equals(normalized)).findFirst()
                .orElseThrow(() -> new BadRequestException("Неизвестный формат выгрузки: " + raw));
    }
}
