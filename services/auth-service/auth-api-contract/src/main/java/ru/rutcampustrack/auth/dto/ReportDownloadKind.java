package ru.rutcampustrack.auth.dto;

/** Closed set of reports that the gateway may deliver through a download ticket. */
public enum ReportDownloadKind {
    TEACHER_JOURNAL,
    TEACHER_STATS,
    HEADMAN_WEEKLY_CURRENT,
    HEADMAN_WEEKLY_SELECTED,
    HEADMAN_STATS,
    HEADMAN_STATS_TREND,
    HEADMAN_GROUP_COMPOSITION
}
