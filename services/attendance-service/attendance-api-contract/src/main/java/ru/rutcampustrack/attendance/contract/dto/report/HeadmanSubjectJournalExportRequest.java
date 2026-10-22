package ru.rutcampustrack.attendance.contract.dto.report;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;

/** Own-group subject journal through the current academic week. */
public record HeadmanSubjectJournalExportRequest(
        @NotNull @Positive Long subjectId,
        @NotNull @Size(min = 1, max = 3) List<@NotBlank String> lessonTypes,
        @Size(max = 64) List<@NotNull LocalDate> weekStarts,
        @NotBlank String format) {}
