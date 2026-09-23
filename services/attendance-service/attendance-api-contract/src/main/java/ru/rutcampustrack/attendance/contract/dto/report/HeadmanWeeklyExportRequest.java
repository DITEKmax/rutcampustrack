package ru.rutcampustrack.attendance.contract.dto.report;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.List;

@Schema(description = "Request for exporting one or more headman weekly reports")
public record HeadmanWeeklyExportRequest(
        @Schema(description = "Monday dates of selected academic weeks",
                requiredMode = Schema.RequiredMode.REQUIRED,
                example = "[\"2026-04-27\", \"2026-05-11\"]")
        @NotNull
        @Size(min = 1, max = 64, message = "Можно выбрать не более 64 недель")
        List<@NotNull LocalDate> weekStarts,

        @Schema(description = "Server-catalogue format code: docx, pdf, png, html, or xlsx",
                requiredMode = Schema.RequiredMode.REQUIRED,
                allowableValues = {"docx", "pdf", "png", "html", "xlsx"},
                example = "pdf")
        @NotBlank
        @Pattern(regexp = "docx|pdf|png|html|xlsx", message = "Format must be one of the server-supported export formats")
        String format
) {}
