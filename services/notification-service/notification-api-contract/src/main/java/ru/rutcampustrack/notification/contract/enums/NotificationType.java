package ru.rutcampustrack.notification.contract.enums;

/**
 * User-facing типы уведомлений, сохраняемые в notification_history (M10).
 *
 * <p>Маппинг event-type → NotificationType выполняется в
 * NotificationHistoryConsumer (notification-app). System events
 * (user.logged-out, alert.fired и т.п.) в history НЕ попадают.
 */
public enum NotificationType {
    EXCUSE_REQUESTED,
    EXCUSE_APPROVED,
    EXCUSE_REJECTED,
    LATE_CHECKIN_REQUESTED,
    LATE_CHECKIN_APPROVED,
    LATE_CHECKIN_REJECTED,
    LESSON_STARTED,
    LESSON_CLOSED,
    LESSON_CANCELLED,
    LESSON_REMINDER,
    ATTENDANCE_RED_ZONE,
    ATTENDANCE_MARKED_BY_HEADMAN
}
