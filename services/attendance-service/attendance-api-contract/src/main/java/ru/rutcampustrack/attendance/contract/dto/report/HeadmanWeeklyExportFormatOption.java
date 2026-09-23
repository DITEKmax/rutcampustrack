package ru.rutcampustrack.attendance.contract.dto.report;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Server-supported format for a headman weekly attendance export")
public record HeadmanWeeklyExportFormatOption(
        @Schema(example = "docx") String code,
        @Schema(example = "Word (.docx)") String label,
        @Schema(example = "application/vnd.openxmlformats-officedocument.wordprocessingml.document") String contentType,
        @Schema(example = "docx") String extension
) {}
