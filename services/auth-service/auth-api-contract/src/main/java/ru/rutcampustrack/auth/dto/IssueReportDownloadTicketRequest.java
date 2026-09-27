package ru.rutcampustrack.auth.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;

/** Typed, bounded report selector stored with a short-lived report capability. */
@Schema(description = "One allowlisted report and its bounded export parameters")
public record IssueReportDownloadTicketRequest(
        @NotNull ReportDownloadKind kind,
        @Valid TeacherJournalParameters teacherJournal,
        @Valid TeacherStatsParameters teacherStats,
        @Valid HeadmanWeeklyCurrentParameters headmanWeeklyCurrent,
        @Valid HeadmanWeeklySelectedParameters headmanWeeklySelected,
        @Valid HeadmanStatsParameters headmanStats,
        @Valid HeadmanStatsTrendParameters headmanStatsTrend
) {
    private static final int MAX_PARAMETER_VALUES = 20;
    private static final int MAX_PARAMETER_LENGTH = 128;

    @AssertTrue(message = "Exactly the parameter object for the selected report kind is required")
    @JsonIgnore
    public boolean isParametersConsistent() {
        if (kind == null) {
            return false;
        }
        return switch (kind) {
            case TEACHER_JOURNAL -> teacherJournal != null && teacherStats == null
                    && headmanWeeklyCurrent == null && headmanWeeklySelected == null && headmanStats == null
                    && headmanStatsTrend == null;
            case TEACHER_STATS -> teacherStats != null && teacherJournal == null
                    && headmanWeeklyCurrent == null && headmanWeeklySelected == null && headmanStats == null
                    && headmanStatsTrend == null;
            case HEADMAN_WEEKLY_CURRENT -> headmanWeeklyCurrent != null && teacherJournal == null
                    && teacherStats == null && headmanWeeklySelected == null && headmanStats == null
                    && headmanStatsTrend == null;
            case HEADMAN_WEEKLY_SELECTED -> headmanWeeklySelected != null && teacherJournal == null
                    && teacherStats == null && headmanWeeklyCurrent == null && headmanStats == null
                    && headmanStatsTrend == null;
            case HEADMAN_STATS -> headmanStats != null && teacherJournal == null && teacherStats == null
                    && headmanWeeklyCurrent == null && headmanWeeklySelected == null
                    && headmanStatsTrend == null;
            case HEADMAN_STATS_TREND -> headmanStatsTrend != null && teacherJournal == null
                    && teacherStats == null && headmanWeeklyCurrent == null && headmanWeeklySelected == null
                    && headmanStats == null && headmanStatsTrend.isConsistent();
        };
    }

    /** Retains source compatibility for callers constructing existing ticket kinds. */
    public IssueReportDownloadTicketRequest(
            ReportDownloadKind kind,
            TeacherJournalParameters teacherJournal,
            TeacherStatsParameters teacherStats,
            HeadmanWeeklyCurrentParameters headmanWeeklyCurrent,
            HeadmanWeeklySelectedParameters headmanWeeklySelected,
            HeadmanStatsParameters headmanStats
    ) {
        this(kind, teacherJournal, teacherStats, headmanWeeklyCurrent, headmanWeeklySelected, headmanStats, null);
    }

    /** Stable digest signed into the short-lived internal JWT to bind dispatch to this exact report. */
    @JsonIgnore
    public String bindingHash() {
        if (!isParametersConsistent()) {
            throw new IllegalArgumentException("Report parameters do not match the selected kind");
        }
        StringBuilder canonical = new StringBuilder(512);
        append(canonical, kind.name());
        switch (kind) {
            case TEACHER_JOURNAL -> {
                append(canonical, teacherJournal.semesterId());
                append(canonical, teacherJournal.groupId());
                append(canonical, teacherJournal.subjectId());
                appendList(canonical, teacherJournal.lessonTypes());
                append(canonical, teacherJournal.dateFrom());
                append(canonical, teacherJournal.dateTo());
                append(canonical, teacherJournal.format().code());
            }
            case TEACHER_STATS -> {
                append(canonical, teacherStats.semesterId());
                append(canonical, teacherStats.scope());
                append(canonical, teacherStats.groupId());
                append(canonical, teacherStats.subjectId());
                appendList(canonical, teacherStats.lessonTypes());
                appendList(canonical, teacherStats.sorts());
                appendList(canonical, teacherStats.filters());
                append(canonical, teacherStats.format().code());
            }
            case HEADMAN_WEEKLY_CURRENT -> {
                append(canonical, teacherlessDate(headmanWeeklyCurrent.weekStart()));
                append(canonical, headmanWeeklyCurrent.format().code());
            }
            case HEADMAN_WEEKLY_SELECTED -> {
                appendList(canonical, headmanWeeklySelected.weekStarts().stream()
                        .map(LocalDate::toString).toList());
                append(canonical, headmanWeeklySelected.format().code());
            }
            case HEADMAN_STATS -> {
                append(canonical, headmanStats.subjectId());
                appendList(canonical, headmanStats.lessonTypes());
                append(canonical, headmanStats.sorts() == null
                        ? null : Integer.toString(headmanStats.sorts().size()));
                if (headmanStats.sorts() != null) {
                    headmanStats.sorts().forEach(sort -> {
                        append(canonical, sort.field());
                        append(canonical, sort.descending());
                    });
                }
                append(canonical, headmanStats.filters() == null
                        ? null : Integer.toString(headmanStats.filters().size()));
                if (headmanStats.filters() != null) {
                    headmanStats.filters().forEach(filter -> {
                        append(canonical, filter.field());
                        append(canonical, filter.contains());
                        append(canonical, canonicalDecimal(filter.minimum()));
                        append(canonical, canonicalDecimal(filter.maximum()));
                    });
                }
                append(canonical, headmanStats.format().code());
            }
            case HEADMAN_STATS_TREND -> {
                append(canonical, headmanStatsTrend.mode().name());
                append(canonical, headmanStatsTrend.weekStart());
                append(canonical, headmanStatsTrend.subjectId());
                appendList(canonical, headmanStatsTrend.lessonTypes());
                append(canonical, headmanStatsTrend.format().code());
            }
        }
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(canonical.toString().getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }

    /** Server-derived filename; user supplied values never enter a response header. */
    @JsonIgnore
    public String suggestedFilename() {
        if (!isParametersConsistent()) {
            throw new IllegalArgumentException("Report parameters do not match the selected kind");
        }
        String base = switch (kind) {
            case TEACHER_JOURNAL -> "teacher-journal";
            case TEACHER_STATS -> "teacher-stats";
            case HEADMAN_WEEKLY_CURRENT -> "headman-weekly-current";
            case HEADMAN_WEEKLY_SELECTED -> "headman-weekly-selected";
            case HEADMAN_STATS -> "headman-stats";
            case HEADMAN_STATS_TREND -> "headman-stats-trend";
        };
        String extension = kind == ReportDownloadKind.HEADMAN_STATS_TREND
                ? headmanStatsTrend.format().code()
                : selectedFormat().filenameExtension();
        return base + "." + extension;
    }

    @JsonIgnore
    public String expectedMediaType() {
        return kind == ReportDownloadKind.HEADMAN_STATS_TREND
                ? headmanStatsTrend.format() == ReportDownloadFormat.PNG ? "image/png" : "text/html"
                : selectedFormat().mediaType();
    }

    private ReportDownloadFormat selectedFormat() {
        return switch (kind) {
            case TEACHER_JOURNAL -> teacherJournal.format();
            case TEACHER_STATS -> teacherStats.format();
            case HEADMAN_WEEKLY_CURRENT -> headmanWeeklyCurrent.format();
            case HEADMAN_WEEKLY_SELECTED -> headmanWeeklySelected.format();
            case HEADMAN_STATS -> headmanStats.format();
            case HEADMAN_STATS_TREND -> headmanStatsTrend.format();
        };
    }

    private static String canonicalDecimal(java.math.BigDecimal value) {
        return value == null ? null : value.stripTrailingZeros().toPlainString();
    }

    private static String teacherlessDate(LocalDate date) {
        return Objects.requireNonNull(date, "weekStart").toString();
    }

    private static void appendList(StringBuilder target, List<String> values) {
        append(target, values == null ? null : Integer.toString(values.size()));
        if (values != null) {
            values.forEach(value -> append(target, value));
        }
    }

    private static void append(StringBuilder target, Object value) {
        if (value == null) {
            target.append("-1:");
            return;
        }
        String text = value.toString();
        target.append(text.length()).append(':').append(text);
    }

    @Schema(description = "Complete teacher subject journal parameters")
    public record TeacherJournalParameters(
            @NotNull @Positive Long semesterId,
            @NotNull @Positive Long groupId,
            @NotNull @Positive Long subjectId,
            @NotNull @Size(min = 1, max = 3)
            List<@NotBlank @Size(max = 64) String> lessonTypes,
            LocalDate dateFrom,
            LocalDate dateTo,
            @NotNull ReportDownloadFormat format
    ) {
        public TeacherJournalParameters {
            lessonTypes = immutable(lessonTypes);
        }

        public TeacherJournalParameters(Long semesterId, Long groupId, Long subjectId,
                                        List<String> lessonTypes, ReportDownloadFormat format) {
            this(semesterId, groupId, subjectId, lessonTypes, null, null, format);
        }

        @AssertTrue(message = "Teacher journal period must provide both ordered dates or neither")
        @JsonIgnore
        public boolean isDateRangeConsistent() {
            return dateFrom == null && dateTo == null
                    || dateFrom != null && dateTo != null && !dateTo.isBefore(dateFrom);
        }
    }

    @Schema(description = "Teacher statistics export parameters")
    public record TeacherStatsParameters(
            @NotNull @Positive Long semesterId,
            @NotBlank @Pattern(regexp = "(?i:students|student|groups|group)") String scope,
            @Positive Long groupId,
            @Positive Long subjectId,
            @Size(max = MAX_PARAMETER_VALUES)
            List<@NotBlank @Size(max = 64) String> lessonTypes,
            @Size(max = MAX_PARAMETER_VALUES)
            List<@NotBlank @Size(max = MAX_PARAMETER_LENGTH) String> sorts,
            @Size(max = MAX_PARAMETER_VALUES)
            List<@NotBlank @Size(max = MAX_PARAMETER_LENGTH) String> filters,
            @NotNull ReportDownloadFormat format
    ) {
        public TeacherStatsParameters {
            lessonTypes = immutable(lessonTypes);
            sorts = immutable(sorts);
            filters = immutable(filters);
        }
    }

    @Schema(description = "Current headman weekly export parameters")
    public record HeadmanWeeklyCurrentParameters(
            @NotNull LocalDate weekStart,
            @NotNull ReportDownloadFormat format
    ) {
    }

    @Schema(description = "Selected headman weekly export parameters")
    public record HeadmanWeeklySelectedParameters(
            @NotNull @Size(min = 1, max = 64) List<@NotNull LocalDate> weekStarts,
            @NotNull ReportDownloadFormat format
    ) {
        public HeadmanWeeklySelectedParameters {
            weekStarts = immutable(weekStarts);
        }
    }

    @Schema(description = "Headman current-group statistics export parameters")
    public record HeadmanStatsParameters(
            @Positive Long subjectId,
            @Size(max = MAX_PARAMETER_VALUES)
            List<@NotBlank @Size(max = 64) String> lessonTypes,
            @Size(max = MAX_PARAMETER_VALUES) List<@NotNull @Valid HeadmanStatsSort> sorts,
            @Size(max = MAX_PARAMETER_VALUES) List<@NotNull @Valid HeadmanStatsFilter> filters,
            @NotNull ReportDownloadFormat format
    ) {
        public HeadmanStatsParameters {
            lessonTypes = immutable(lessonTypes);
            sorts = immutable(sorts);
            filters = immutable(filters);
        }
    }

    @Schema(description = "Current-group headman trend chart export parameters")
    public record HeadmanStatsTrendParameters(
            @NotNull HeadmanStatsTrendMode mode,
            LocalDate weekStart,
            @Positive Long subjectId,
            @Size(max = MAX_PARAMETER_VALUES)
            List<@NotBlank @Size(max = 64) String> lessonTypes,
            @NotNull ReportDownloadFormat format
    ) {
        public HeadmanStatsTrendParameters {
            lessonTypes = immutable(lessonTypes);
        }

        @JsonIgnore
        public boolean isConsistent() {
            if (mode == null || (format != ReportDownloadFormat.PNG && format != ReportDownloadFormat.HTML)) {
                return false;
            }
            List<String> types = lessonTypes == null ? List.of() : lessonTypes;
            if (types.stream().anyMatch(value -> value == null || value.isBlank())
                    || types.size() > MAX_PARAMETER_VALUES || types.stream().distinct().count() != types.size()) {
                return false;
            }
            return switch (mode) {
                case SEMESTER -> weekStart == null && subjectId == null && types.isEmpty();
                case WEEK -> weekStart != null && weekStart.getDayOfWeek().getValue() == 1
                        && subjectId == null && types.isEmpty();
                case SUBJECT -> weekStart == null && subjectId != null && subjectId > 0;
            };
        }
    }

    public enum HeadmanStatsTrendMode {
        SEMESTER,
        WEEK,
        SUBJECT
    }

    @Schema(description = "One priority-ordered headman statistics sort field")
    public record HeadmanStatsSort(
            @NotBlank @Size(max = 64) String field,
            boolean descending
    ) {
    }

    @Schema(description = "A bounded text or numeric headman statistics filter")
    public record HeadmanStatsFilter(
            @NotBlank @Size(max = 64) String field,
            @Size(max = MAX_PARAMETER_LENGTH) String contains,
            @Digits(integer = 12, fraction = 4) java.math.BigDecimal minimum,
            @Digits(integer = 12, fraction = 4) java.math.BigDecimal maximum
    ) {
    }

    private static <T> List<T> immutable(List<T> values) {
        return values == null ? null : List.copyOf(values);
    }
}
