package ru.rutcampustrack.academic.export;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import ru.rutcampustrack.academic.contract.dto.export.RegistryExportModels.FormatOption;
import ru.rutcampustrack.academic.exception.BadRequestException;

enum RegistryExportFormat {
    DOCX(
            "docx",
            "Word (.docx)",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "docx"),
    PDF("pdf", "PDF (.pdf)", "application/pdf", "pdf"),
    PNG("png", "PNG, архив страниц (.zip)", "application/zip", "zip"),
    HTML("html", "HTML (.html)", "text/html; charset=UTF-8", "html"),
    XLSX(
            "xlsx",
            "Excel (.xlsx)",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            "xlsx");
    final String code;
    final String label;
    final String mime;
    final String extension;

    RegistryExportFormat(String code, String label, String mime, String extension) {
        this.code = code;
        this.label = label;
        this.mime = mime;
        this.extension = extension;
    }

    static RegistryExportFormat from(String value) {
        return Arrays.stream(values())
                .filter(
                        f ->
                                f.code.equals(
                                        value == null ? "" : value.trim().toLowerCase(Locale.ROOT)))
                .findFirst()
                .orElseThrow(() -> new BadRequestException("Неизвестный формат выгрузки"));
    }

    static List<FormatOption> catalogue() {
        return Arrays.stream(values())
                .map(f -> new FormatOption(f.code, f.label, f.mime, f.extension))
                .toList();
    }
}
