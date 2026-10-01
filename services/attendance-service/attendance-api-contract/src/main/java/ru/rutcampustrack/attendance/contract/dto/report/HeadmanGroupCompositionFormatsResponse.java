package ru.rutcampustrack.attendance.contract.dto.report;

import java.util.List;

/** Server catalogue for the own-group composition export. */
public record HeadmanGroupCompositionFormatsResponse(List<FormatOption> formats) {
    public record FormatOption(String code, String label, String contentType, String extension) {}
}
