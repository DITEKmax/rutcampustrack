package ru.rutcampustrack.attendance.report;

import ru.rutcampustrack.attendance.contract.api.ReportApi;
import ru.rutcampustrack.attendance.exception.BadRequestException;

import java.util.Locale;

enum HeadmanWeeklyReportFormat {
    DOCX("docx", ReportApi.DOCX_MEDIA_TYPE),
    PDF("pdf", ReportApi.PDF_MEDIA_TYPE),
    PNG("png", ReportApi.PNG_MEDIA_TYPE);

    private final String extension;
    private final String contentType;

    HeadmanWeeklyReportFormat(String extension, String contentType) {
        this.extension = extension;
        this.contentType = contentType;
    }

    String extension() {
        return extension;
    }

    String contentType() {
        return contentType;
    }

    static HeadmanWeeklyReportFormat from(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new BadRequestException("Export format is required");
        }
        String normalized = raw.trim().toUpperCase(Locale.ROOT);
        try {
            return HeadmanWeeklyReportFormat.valueOf(normalized);
        } catch (IllegalArgumentException ex) {
            throw new BadRequestException("Unknown export format: " + raw);
        }
    }
}
