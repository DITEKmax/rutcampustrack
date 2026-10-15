package ru.rutcampustrack.auth.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/** Closed set of reports that the gateway may deliver through a download ticket. */
public enum ReportDownloadKind {
    TEACHER_JOURNAL("TEACHER_JOURNAL"),
    TEACHER_STATS("TEACHER_STATS"),
    HEADMAN_WEEKLY_CURRENT("HEADMAN_WEEKLY_CURRENT"),
    HEADMAN_WEEKLY_SELECTED("HEADMAN_WEEKLY_SELECTED"),
    HEADMAN_STATS("HEADMAN_STATS"),
    HEADMAN_STATS_TREND("HEADMAN_STATS_TREND"),
    HEADMAN_GROUP_COMPOSITION("HEADMAN_GROUP_COMPOSITION"),
    HEADMAN_SUBJECT_JOURNAL("HEADMAN_SUBJECT_JOURNAL"),
    ADMIN_USERS("ADMIN_USERS"),
    ADMIN_GROUPS("ADMIN_GROUPS"),
    ADMIN_GROUP_ATTENDANCE("ADMIN_GROUP_ATTENDANCE");

    private final String code;

    ReportDownloadKind(String code) {
        this.code = code;
    }

    @JsonValue
    public String code() {
        return code;
    }

    @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
    public static ReportDownloadKind fromJson(Object value) {
        if (value instanceof String code) {
            for (ReportDownloadKind kind : values()) {
                if (kind.code.equals(code)) return kind;
            }
        }
        // Keep rejection strict when a consumer enables Jackson's unknown-enum-as-null option.
        throw new IllegalStateException("Report kind must be a canonical string code");
    }
}
