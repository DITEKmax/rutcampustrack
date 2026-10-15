package ru.rutcampustrack.auth.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.annotation.Nulls;
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
        @NotNull @JsonSetter(nulls = Nulls.FAIL) ReportDownloadKind kind,
        @Valid TeacherJournalParameters teacherJournal,
        @Valid TeacherStatsParameters teacherStats,
        @Valid HeadmanWeeklyCurrentParameters headmanWeeklyCurrent,
        @Valid HeadmanWeeklySelectedParameters headmanWeeklySelected,
        @Valid HeadmanStatsParameters headmanStats,
        @Valid HeadmanStatsTrendParameters headmanStatsTrend,
        @Valid HeadmanGroupCompositionParameters headmanGroupComposition,
        @Valid HeadmanSubjectJournalParameters headmanSubjectJournal,
        @Valid AdminUsersParameters adminUsers,
        @Valid AdminGroupsParameters adminGroups,
        @com.fasterxml.jackson.annotation.JsonInclude(
                        com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
                @Valid
                AdminGroupAttendanceParameters adminGroupAttendance) {
    private static final int MAX_PARAMETER_VALUES = 20;
    private static final int MAX_PARAMETER_LENGTH = 128;

    @AssertTrue(message = "Exactly the parameter object for the selected report kind is required")
    @JsonIgnore
    public boolean isParametersConsistent() {
        if (kind == null) return false;
        Object selected =
                switch (kind) {
                    case TEACHER_JOURNAL -> teacherJournal;
                    case TEACHER_STATS -> teacherStats;
                    case HEADMAN_WEEKLY_CURRENT -> headmanWeeklyCurrent;
                    case HEADMAN_WEEKLY_SELECTED -> headmanWeeklySelected;
                    case HEADMAN_STATS -> headmanStats;
                    case HEADMAN_STATS_TREND -> headmanStatsTrend;
                    case HEADMAN_GROUP_COMPOSITION -> headmanGroupComposition;
                    case HEADMAN_SUBJECT_JOURNAL -> headmanSubjectJournal;
                    case ADMIN_USERS -> adminUsers;
                    case ADMIN_GROUPS -> adminGroups;
                    case ADMIN_GROUP_ATTENDANCE -> adminGroupAttendance;
                };
        long count =
                java.util.stream.Stream.of(
                                teacherJournal,
                                teacherStats,
                                headmanWeeklyCurrent,
                                headmanWeeklySelected,
                                headmanStats,
                                headmanStatsTrend,
                                headmanGroupComposition,
                                headmanSubjectJournal,
                                adminUsers,
                                adminGroups,
                                adminGroupAttendance)
                        .filter(Objects::nonNull)
                        .count();
        if (selected == null || count != 1) return false;
        return switch (kind) {
            case TEACHER_JOURNAL -> teacherJournal.isLessonIdValid();
            case HEADMAN_STATS_TREND -> headmanStatsTrend.isConsistent();
            case HEADMAN_GROUP_COMPOSITION -> headmanGroupComposition.format() != null;
            case ADMIN_GROUP_ATTENDANCE -> adminGroupAttendance.isConsistent();
            default -> true;
        };
    }

    /** Existing selectors retain their constructors and wire representation. */
    public IssueReportDownloadTicketRequest(
            ReportDownloadKind kind,
            TeacherJournalParameters teacherJournal,
            TeacherStatsParameters teacherStats,
            HeadmanWeeklyCurrentParameters headmanWeeklyCurrent,
            HeadmanWeeklySelectedParameters headmanWeeklySelected,
            HeadmanStatsParameters headmanStats,
            HeadmanStatsTrendParameters headmanStatsTrend,
            HeadmanGroupCompositionParameters headmanGroupComposition,
            HeadmanSubjectJournalParameters headmanSubjectJournal,
            AdminUsersParameters adminUsers,
            AdminGroupsParameters adminGroups) {
        this(
                kind,
                teacherJournal,
                teacherStats,
                headmanWeeklyCurrent,
                headmanWeeklySelected,
                headmanStats,
                headmanStatsTrend,
                headmanGroupComposition,
                headmanSubjectJournal,
                adminUsers,
                adminGroups,
                null);
    }

    public IssueReportDownloadTicketRequest(
            ReportDownloadKind kind,
            TeacherJournalParameters teacherJournal,
            TeacherStatsParameters teacherStats,
            HeadmanWeeklyCurrentParameters headmanWeeklyCurrent,
            HeadmanWeeklySelectedParameters headmanWeeklySelected,
            HeadmanStatsParameters headmanStats,
            HeadmanStatsTrendParameters headmanStatsTrend,
            HeadmanGroupCompositionParameters headmanGroupComposition) {
        this(
                kind,
                teacherJournal,
                teacherStats,
                headmanWeeklyCurrent,
                headmanWeeklySelected,
                headmanStats,
                headmanStatsTrend,
                headmanGroupComposition,
                null,
                null,
                null);
    }

    /** Retains source compatibility for callers constructing existing ticket kinds. */
    public IssueReportDownloadTicketRequest(
            ReportDownloadKind kind,
            TeacherJournalParameters teacherJournal,
            TeacherStatsParameters teacherStats,
            HeadmanWeeklyCurrentParameters headmanWeeklyCurrent,
            HeadmanWeeklySelectedParameters headmanWeeklySelected,
            HeadmanStatsParameters headmanStats,
            HeadmanStatsTrendParameters headmanStatsTrend) {
        this(
                kind,
                teacherJournal,
                teacherStats,
                headmanWeeklyCurrent,
                headmanWeeklySelected,
                headmanStats,
                headmanStatsTrend,
                null);
    }

    /** Retains source compatibility for callers constructing existing ticket kinds. */
    public IssueReportDownloadTicketRequest(
            ReportDownloadKind kind,
            TeacherJournalParameters teacherJournal,
            TeacherStatsParameters teacherStats,
            HeadmanWeeklyCurrentParameters headmanWeeklyCurrent,
            HeadmanWeeklySelectedParameters headmanWeeklySelected,
            HeadmanStatsParameters headmanStats) {
        this(
                kind,
                teacherJournal,
                teacherStats,
                headmanWeeklyCurrent,
                headmanWeeklySelected,
                headmanStats,
                null);
    }

    /**
     * Stable digest signed into the short-lived internal JWT to bind dispatch to this exact report.
     */
    @JsonIgnore
    public String bindingHash() {
        if (!isParametersConsistent()) {
            throw new IllegalArgumentException("Report parameters do not match the selected kind");
        }
        StringBuilder canonical = new StringBuilder(512);
        append(canonical, kind.code());
        switch (kind) {
            case TEACHER_JOURNAL -> {
                append(canonical, teacherJournal.semesterId());
                append(canonical, teacherJournal.groupId());
                append(canonical, teacherJournal.subjectId());
                appendList(canonical, teacherJournal.lessonTypes());
                append(canonical, teacherJournal.dateFrom());
                append(canonical, teacherJournal.dateTo());
                append(canonical, teacherJournal.format().code());
                if (teacherJournal.pastOnly() || teacherJournal.lessonId() != null) {
                    append(canonical, "teacher-journal-selection-v1");
                    append(canonical, teacherJournal.pastOnly());
                    append(canonical, teacherJournal.lessonId());
                }
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
                appendList(
                        canonical,
                        headmanWeeklySelected.weekStarts().stream()
                                .map(LocalDate::toString)
                                .toList());
                append(canonical, headmanWeeklySelected.format().code());
            }
            case HEADMAN_STATS -> {
                append(canonical, headmanStats.subjectId());
                appendList(canonical, headmanStats.lessonTypes());
                append(
                        canonical,
                        headmanStats.sorts() == null
                                ? null
                                : Integer.toString(headmanStats.sorts().size()));
                if (headmanStats.sorts() != null) {
                    headmanStats
                            .sorts()
                            .forEach(
                                    sort -> {
                                        append(canonical, sort.field());
                                        append(canonical, sort.descending());
                                    });
                }
                append(
                        canonical,
                        headmanStats.filters() == null
                                ? null
                                : Integer.toString(headmanStats.filters().size()));
                if (headmanStats.filters() != null) {
                    headmanStats
                            .filters()
                            .forEach(
                                    filter -> {
                                        append(canonical, filter.field());
                                        append(canonical, filter.contains());
                                        append(canonical, canonicalDecimal(filter.minimum()));
                                        append(canonical, canonicalDecimal(filter.maximum()));
                                    });
                }
                append(canonical, headmanStats.format().code());
                if (headmanStats.semesterId() != null) {
                    append(canonical, "semesterId");
                    append(canonical, headmanStats.semesterId());
                }
            }
            case HEADMAN_STATS_TREND -> {
                append(canonical, headmanStatsTrend.mode().name());
                append(canonical, headmanStatsTrend.weekStart());
                append(canonical, headmanStatsTrend.subjectId());
                appendList(canonical, headmanStatsTrend.lessonTypes());
                append(canonical, headmanStatsTrend.format().code());
                if (headmanStatsTrend.semesterId() != null) {
                    append(canonical, "semesterId");
                    append(canonical, headmanStatsTrend.semesterId());
                }
                if (headmanStatsTrend.bucketUnit() != null) {
                    append(canonical, "bucketUnit");
                    append(canonical, headmanStatsTrend.bucketUnit().name());
                }
            }
            case HEADMAN_GROUP_COMPOSITION ->
                    append(canonical, headmanGroupComposition.format().code());
            case HEADMAN_SUBJECT_JOURNAL -> {
                append(canonical, headmanSubjectJournal.subjectId());
                appendList(canonical, headmanSubjectJournal.lessonTypes());
                appendList(
                        canonical,
                        headmanSubjectJournal.weekStarts() == null
                                ? null
                                : headmanSubjectJournal.weekStarts().stream()
                                        .map(LocalDate::toString)
                                        .toList());
                append(canonical, headmanSubjectJournal.format().code());
            }
            case ADMIN_USERS -> {
                append(canonical, adminUsers.search());
                append(canonical, adminUsers.role());
                append(canonical, adminUsers.status());
                append(canonical, adminUsers.roleStatus());
                appendSorts(canonical, adminUsers.sorts());
                append(canonical, adminUsers.format().code());
            }
            case ADMIN_GROUP_ATTENDANCE -> {
                append(canonical, adminGroupAttendance.semesterId());
                append(canonical, adminGroupAttendance.search());
                append(canonical, adminGroupAttendance.sort());
                append(canonical, adminGroupAttendance.direction());
                append(canonical, adminGroupAttendance.format().code());
            }
            case ADMIN_GROUPS -> {
                append(canonical, adminGroups.status());
                append(canonical, adminGroups.search());
                append(canonical, adminGroups.course());
                append(canonical, adminGroups.studyMode());
                append(canonical, adminGroups.educationLevel());
                appendSorts(canonical, adminGroups.sorts());
                append(canonical, adminGroups.format().code());
            }
        }
        try {
            return HexFormat.of()
                    .formatHex(
                            MessageDigest.getInstance("SHA-256")
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
        String base =
                switch (kind) {
                    case TEACHER_JOURNAL -> "teacher-journal";
                    case TEACHER_STATS -> "teacher-stats";
                    case HEADMAN_WEEKLY_CURRENT -> "headman-weekly-current";
                    case HEADMAN_WEEKLY_SELECTED -> "headman-weekly-selected";
                    case HEADMAN_STATS -> "headman-stats";
                    case HEADMAN_STATS_TREND -> "headman-stats-trend";
                    case HEADMAN_GROUP_COMPOSITION -> "headman-group-composition";
                    case HEADMAN_SUBJECT_JOURNAL -> "headman-subject-journal";
                    case ADMIN_USERS -> "admin-users";
                    case ADMIN_GROUPS -> "admin-groups";
                    case ADMIN_GROUP_ATTENDANCE -> "admin-group-attendance";
                };
        String extension =
                kind == ReportDownloadKind.HEADMAN_STATS_TREND
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
            case HEADMAN_GROUP_COMPOSITION -> headmanGroupComposition.format();
            case HEADMAN_SUBJECT_JOURNAL -> headmanSubjectJournal.format();
            case ADMIN_USERS -> adminUsers.format();
            case ADMIN_GROUPS -> adminGroups.format();
            case ADMIN_GROUP_ATTENDANCE -> adminGroupAttendance.format();
        };
    }

    private static void appendSorts(StringBuilder canonical, List<RegistrySort> sorts) {
        append(canonical, sorts == null ? null : sorts.size());
        if (sorts != null)
            for (RegistrySort sort : sorts) {
                append(canonical, sort.field());
                append(canonical, sort.descending());
            }
    }

    public record HeadmanSubjectJournalParameters(
            @NotNull @Positive Long subjectId,
            @NotNull @Size(min = 1, max = 3) List<@NotBlank String> lessonTypes,
            @Size(max = 64) List<@NotNull LocalDate> weekStarts,
            @NotNull ReportDownloadFormat format) {
        public HeadmanSubjectJournalParameters {
            lessonTypes = immutable(lessonTypes);
            weekStarts = immutable(weekStarts);
        }
    }

    public record RegistrySort(@NotBlank @Size(max = 64) String field, boolean descending) {}

    public record AdminUsersParameters(
            @Size(max = 255) String search,
            @Pattern(regexp = "STUDENT|TEACHER|ADMIN|HEADMAN") String role,
            @Pattern(regexp = "ACTIVE|EXPELLED|SUSPENDED|ARCHIVED") String status,
            @Pattern(regexp = "ACTIVE|EXPELLED|GRADUATED|SUSPENDED|DISMISSED|ARCHIVED")
                    String roleStatus,
            @Size(max = 8) List<@NotNull @Valid RegistrySort> sorts,
            @NotNull ReportDownloadFormat format) {
        public AdminUsersParameters {
            sorts = immutable(sorts);
        }
    }

    public record AdminGroupsParameters(
            @Pattern(regexp = "ACTIVE|DRAFT|ARCHIVED") String status,
            @Size(max = 255) String search,
            @jakarta.validation.constraints.Min(1) @jakarta.validation.constraints.Max(5)
                    Integer course,
            @Pattern(regexp = "FULL_TIME|PART_TIME|EXTRAMURAL") String studyMode,
            @Pattern(regexp = "BACHELOR|SPECIALIST|MASTER") String educationLevel,
            @Size(max = 8) List<@NotNull @Valid RegistrySort> sorts,
            @NotNull ReportDownloadFormat format) {
        public AdminGroupsParameters {
            sorts = immutable(sorts);
        }
    }

    public record AdminGroupAttendanceParameters(
            @NotBlank @Pattern(regexp = "[1-9][0-9]{0,18}") String semesterId,
            @NotNull @Size(max = 200) String search,
            @NotBlank
                    @Pattern(
                            regexp =
                                    "GROUP_NAME|PRESENT_PERCENT|PRESENT_OR_EXCUSED_PERCENT|COUNTED_LESSONS|PRESENT|ABSENT|EXCUSED")
                    String sort,
            @NotBlank @Pattern(regexp = "ASC|DESC") String direction,
            @NotNull ReportDownloadFormat format) {
        public AdminGroupAttendanceParameters {
            if (search != null) search = search.trim();
        }

        @JsonIgnore
        public boolean isConsistent() {
            return isSemesterIdValid()
                    && search != null
                    && search.length() <= 200
                    && sort != null
                    && sort.matches(
                            "GROUP_NAME|PRESENT_PERCENT|PRESENT_OR_EXCUSED_PERCENT|COUNTED_LESSONS|PRESENT|ABSENT|EXCUSED")
                    && ("ASC".equals(direction) || "DESC".equals(direction))
                    && format != null;
        }

        @AssertTrue(message = "Semester identity must be a positive long")
        @JsonIgnore
        public boolean isSemesterIdValid() {
            try {
                return semesterId != null
                        && semesterId.matches("[1-9][0-9]{0,18}")
                        && Long.parseLong(semesterId) > 0;
            } catch (NumberFormatException invalid) {
                return false;
            }
        }
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
            @NotNull @Size(min = 1, max = 3) List<@NotBlank @Size(max = 64) String> lessonTypes,
            LocalDate dateFrom,
            LocalDate dateTo,
            @NotNull ReportDownloadFormat format,
            boolean pastOnly,
            @Pattern(regexp = "[1-9][0-9]{0,18}") String lessonId) {
        public TeacherJournalParameters {
            lessonTypes = immutable(lessonTypes);
        }

        public TeacherJournalParameters(
                Long semesterId,
                Long groupId,
                Long subjectId,
                List<String> lessonTypes,
                LocalDate dateFrom,
                LocalDate dateTo,
                ReportDownloadFormat format) {
            this(
                    semesterId,
                    groupId,
                    subjectId,
                    lessonTypes,
                    dateFrom,
                    dateTo,
                    format,
                    false,
                    null);
        }

        @AssertTrue(message = "lessonId must be a positive long identity")
        @JsonIgnore
        public boolean isLessonIdValid() {
            if (lessonId == null) return true;
            try {
                return lessonId.matches("[1-9][0-9]*") && Long.parseLong(lessonId) > 0;
            } catch (NumberFormatException invalid) {
                return false;
            }
        }

        public TeacherJournalParameters(
                Long semesterId,
                Long groupId,
                Long subjectId,
                List<String> lessonTypes,
                ReportDownloadFormat format) {
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
            @Size(max = MAX_PARAMETER_VALUES) List<@NotBlank @Size(max = 64) String> lessonTypes,
            @Size(max = MAX_PARAMETER_VALUES)
                    List<@NotBlank @Size(max = MAX_PARAMETER_LENGTH) String> sorts,
            @Size(max = MAX_PARAMETER_VALUES)
                    List<@NotBlank @Size(max = MAX_PARAMETER_LENGTH) String> filters,
            @NotNull ReportDownloadFormat format) {
        public TeacherStatsParameters {
            lessonTypes = immutable(lessonTypes);
            sorts = immutable(sorts);
            filters = immutable(filters);
        }
    }

    @Schema(description = "Current headman weekly export parameters")
    public record HeadmanWeeklyCurrentParameters(
            @NotNull LocalDate weekStart, @NotNull ReportDownloadFormat format) {}

    @Schema(description = "Selected headman weekly export parameters")
    public record HeadmanWeeklySelectedParameters(
            @NotNull @Size(min = 1, max = 64) List<@NotNull LocalDate> weekStarts,
            @NotNull ReportDownloadFormat format) {
        public HeadmanWeeklySelectedParameters {
            weekStarts = immutable(weekStarts);
        }
    }

    @Schema(description = "Headman current-group statistics export parameters")
    public record HeadmanStatsParameters(
            @Positive Long subjectId,
            @Size(max = MAX_PARAMETER_VALUES) List<@NotBlank @Size(max = 64) String> lessonTypes,
            @Size(max = MAX_PARAMETER_VALUES) List<@NotNull @Valid HeadmanStatsSort> sorts,
            @Size(max = MAX_PARAMETER_VALUES) List<@NotNull @Valid HeadmanStatsFilter> filters,
            @NotNull ReportDownloadFormat format,
            @Positive Long semesterId) {
        public HeadmanStatsParameters {
            lessonTypes = immutable(lessonTypes);
            sorts = immutable(sorts);
            filters = immutable(filters);
        }

        public HeadmanStatsParameters(
                Long subjectId,
                List<String> lessonTypes,
                List<HeadmanStatsSort> sorts,
                List<HeadmanStatsFilter> filters,
                ReportDownloadFormat format) {
            this(subjectId, lessonTypes, sorts, filters, format, null);
        }
    }

    @Schema(
            description =
                    "Current headman's own group roster export; group is selected by the server")
    public record HeadmanGroupCompositionParameters(@NotNull ReportDownloadFormat format) {}

    @Schema(description = "Current-group headman trend chart export parameters")
    public record HeadmanStatsTrendParameters(
            @NotNull HeadmanStatsTrendMode mode,
            LocalDate weekStart,
            @Positive Long subjectId,
            @Size(max = MAX_PARAMETER_VALUES) List<@NotBlank @Size(max = 64) String> lessonTypes,
            @NotNull ReportDownloadFormat format,
            @Positive Long semesterId,
            HeadmanStatsBucketUnit bucketUnit) {
        public HeadmanStatsTrendParameters {
            lessonTypes = immutable(lessonTypes);
        }

        public HeadmanStatsTrendParameters(
                HeadmanStatsTrendMode mode,
                LocalDate weekStart,
                Long subjectId,
                List<String> lessonTypes,
                ReportDownloadFormat format) {
            this(mode, weekStart, subjectId, lessonTypes, format, null, null);
        }

        @JsonIgnore
        public boolean isConsistent() {
            if (mode == null
                    || (format != ReportDownloadFormat.PNG
                            && format != ReportDownloadFormat.HTML)) {
                return false;
            }
            List<String> types = lessonTypes == null ? List.of() : lessonTypes;
            if (types.stream().anyMatch(value -> value == null || value.isBlank())
                    || types.size() > MAX_PARAMETER_VALUES
                    || types.stream().distinct().count() != types.size()) {
                return false;
            }
            return switch (mode) {
                case SEMESTER -> weekStart == null && subjectId == null && types.isEmpty();
                case WEEK ->
                        weekStart != null
                                && weekStart.getDayOfWeek().getValue() == 1
                                && subjectId == null
                                && types.isEmpty();
                case SUBJECT -> weekStart == null && subjectId != null && subjectId > 0;
            };
        }
    }

    public enum HeadmanStatsTrendMode {
        SEMESTER,
        WEEK,
        SUBJECT
    }

    public enum HeadmanStatsBucketUnit {
        DAY,
        WEEK
    }

    @Schema(description = "One priority-ordered headman statistics sort field")
    public record HeadmanStatsSort(@NotBlank @Size(max = 64) String field, boolean descending) {}

    @Schema(description = "A bounded text or numeric headman statistics filter")
    public record HeadmanStatsFilter(
            @NotBlank @Size(max = 64) String field,
            @Size(max = MAX_PARAMETER_LENGTH) String contains,
            @Digits(integer = 12, fraction = 4) java.math.BigDecimal minimum,
            @Digits(integer = 12, fraction = 4) java.math.BigDecimal maximum) {}

    private static <T> List<T> immutable(List<T> values) {
        return values == null ? null : List.copyOf(values);
    }
}
