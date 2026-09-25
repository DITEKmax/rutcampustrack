package ru.rutcampustrack.attendance.contract.dto.report;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

/** Selector for rendering the authoritative current-group trend as a downloadable chart. */
public record HeadmanStatsTrendExportRequest(
        @NotNull @Valid HeadmanStatsTrendQueryRequest query,
        @NotBlank @Pattern(regexp = "(?i:png|html)") String format
) {
}
