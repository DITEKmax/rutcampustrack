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
        @Size(min = 1, message = "At least one week must be selected")
        List<@NotNull LocalDate> weekStarts,

        @Schema(description = "Export format: docx, pdf, or png",
                requiredMode = Schema.RequiredMode.REQUIRED,
                allowableValues = {"docx", "pdf", "png"},
                example = "pdf")
        @NotBlank
        @Pattern(regexp = "docx|pdf|png", message = "Format must be docx, pdf, or png")
        String format
) {}
