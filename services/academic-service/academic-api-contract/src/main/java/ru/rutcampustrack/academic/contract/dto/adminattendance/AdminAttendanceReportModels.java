package ru.rutcampustrack.academic.contract.dto.adminattendance;

import com.fasterxml.jackson.annotation.JsonCreator;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/** Aggregate-only D026 report; no student-level projection is part of this contract. */
public final class AdminAttendanceReportModels {
    private AdminAttendanceReportModels() {}

    public enum Sort {
        GROUP_NAME,
        PRESENT_PERCENT,
        PRESENT_OR_EXCUSED_PERCENT,
        COUNTED_LESSONS,
        PRESENT,
        ABSENT,
        EXCUSED;

        @JsonCreator
        public static Sort from(String value) {
            return valueOf(value);
        }
    }

    public enum Direction {
        ASC,
        DESC;

        @JsonCreator
        public static Direction from(String value) {
            return valueOf(value);
        }
    }

    public enum DataState {
        AVAILABLE,
        NO_DATA
    }

    public enum Format {
        html,
        pdf,
        docx,
        xlsx,
        png;

        @JsonCreator
        public static Format from(String value) {
            return valueOf(value);
        }
    }

    public record Semester(
            String id,
            String name,
            LocalDate dateFrom,
            LocalDate dateTo,
            boolean active,
            boolean archived) {}

    public record Semesters(List<Semester> items) {
        public Semesters {
            items = List.copyOf(items);
        }
    }

    public record Row(
            String groupId,
            String groupName,
            long countedLessons,
            long presentCount,
            long absentCount,
            long excusedCount,
            long opportunities,
            @io.swagger.v3.oas.annotations.media.Schema(
                            types = {"number", "null"},
                            format = "double")
                    Double presentPercent,
            @io.swagger.v3.oas.annotations.media.Schema(
                            types = {"number", "null"},
                            format = "double")
                    Double presentOrExcusedPercent,
            DataState dataState) {}

    public record Page(
            Semester semester,
            String search,
            Sort sort,
            Direction direction,
            int page,
            int size,
            long totalElements,
            long totalPages,
            Instant observedAt,
            List<Row> rows) {
        public Page {
            rows = List.copyOf(rows);
        }
    }

    public record Export(
            @NotBlank @Pattern(regexp = "[1-9][0-9]{0,18}") String semesterId,
            @NotNull @Size(max = 200) String search,
            @NotNull Sort sort,
            @NotNull Direction direction,
            @NotNull Format format) {}

    public record FormatOption(String code, String label, String mime, String extension) {}
}
