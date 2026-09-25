package ru.rutcampustrack.auth.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/** Formats supported by the allowlisted teacher and headman report exporters. */
public enum ReportDownloadFormat {
    DOCX("docx", "docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document"),
    PDF("pdf", "pdf", "application/pdf"),
    PNG("png", "zip", "application/zip"),
    HTML("html", "html", "text/html"),
    XLSX("xlsx", "xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");

    private final String code;
    private final String filenameExtension;
    private final String mediaType;

    ReportDownloadFormat(String code, String filenameExtension, String mediaType) {
        this.code = code;
        this.filenameExtension = filenameExtension;
        this.mediaType = mediaType;
    }

    @JsonValue
    public String code() {
        return code;
    }

    @JsonCreator
    public static ReportDownloadFormat fromCode(String code) {
        for (ReportDownloadFormat format : values()) {
            if (format.code.equals(code)) {
                return format;
            }
        }
        throw new IllegalArgumentException("Unsupported report format");
    }

    public String filenameExtension() {
        return filenameExtension;
    }

    public String mediaType() {
        return mediaType;
    }
}
