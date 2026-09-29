package ru.rutcampustrack.notification.history;

import org.junit.jupiter.api.Test;
import ru.rutcampustrack.notification.contract.enums.NotificationType;

import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Юнит-тест маппера event_type → NotificationType (M10 G3 D6 + G9 H1).
 * Гарантирует, что разрешённые групповые и личные события маппятся.
 */
class NotificationHistoryConsumerMapTypeTest {

    private static final Map<String, Object> EMPTY = Map.of();

    @Test
    void mapsExcuseRequested() {
        assertThat(NotificationHistoryEventProcessor.mapType("excuse.requested", EMPTY))
                .contains(NotificationType.EXCUSE_REQUESTED);
    }

    @Test
    void mapsExcuseDecidedApproved() {
        assertThat(NotificationHistoryEventProcessor.mapType("excuse.decided",
                Map.of("status", "approved")))
                .contains(NotificationType.EXCUSE_APPROVED);
    }

    @Test
    void mapsExcuseDecidedRejected() {
        // G9 H1: до hot-patch'а REJECTED тихо терялся как APPROVED.
        assertThat(NotificationHistoryEventProcessor.mapType("excuse.decided",
                Map.of("status", "rejected")))
                .contains(NotificationType.EXCUSE_REJECTED);
    }

    @Test
    void mapsExcuseDecidedDefaultsToApprovedWhenStatusMissing() {
        // Defensive: если payload.status отсутствует — fallback APPROVED
        // (исторически decided без status трактовался как approve).
        assertThat(NotificationHistoryEventProcessor.mapType("excuse.decided", EMPTY))
                .contains(NotificationType.EXCUSE_APPROVED);
    }

    @Test
    void skipsLateCheckinDecisionCommand() {
        assertThat(NotificationHistoryEventProcessor.mapType("late_checkin.decision",
                Map.of("approved", true, "request_id", "req-1")))
                .isEmpty();
    }

    @Test
    void mapsLateCheckinDecidedApproved() {
        assertThat(NotificationHistoryEventProcessor.mapType("late_checkin.decided",
                Map.of("status", "approved")))
                .contains(NotificationType.LATE_CHECKIN_APPROVED);
    }

    @Test
    void mapsLateCheckinDecidedRejected() {
        assertThat(NotificationHistoryEventProcessor.mapType("late_checkin.decided",
                Map.of("status", "rejected")))
                .contains(NotificationType.LATE_CHECKIN_REJECTED);
    }

    @Test
    void skipsLateCheckinCancelledByGeoInsteadOfRecordingFalseApproval() {
        assertThat(NotificationHistoryEventProcessor.mapType("late_checkin.decided",
                Map.of("status", "cancelled", "resolution_reason", "geo_confirmed")))
                .isEmpty();
    }

    @Test
    void mapsAttendanceMarkedByHeadman() {
        assertThat(NotificationHistoryEventProcessor.mapType("attendance.marked",
                Map.of("marked_by", "headman")))
                .contains(NotificationType.ATTENDANCE_MARKED_BY_HEADMAN);
    }

    @Test
    void mapsHomeworkWeeklyDigest() {
        assertThat(NotificationHistoryEventProcessor.mapType("homework.weekly_digest", EMPTY))
                .contains(NotificationType.HOMEWORK_WEEKLY_DIGEST);
    }

    @Test
    void mapsHomeworkDueReminder() {
        assertThat(NotificationHistoryEventProcessor.mapType("homework.due_reminder", EMPTY))
                .contains(NotificationType.HOMEWORK_DUE_REMINDER);
    }

    @Test
    void skipsAttendanceMarkedBySelf() {
        // Self check-in — собственное действие студента, не уведомление.
        assertThat(NotificationHistoryEventProcessor.mapType("attendance.marked",
                Map.of("marked_by", "self")))
                .isEmpty();
    }

    @Test
    void skipsAttendanceMarkedByAuto() {
        // Авто-absent при закрытии пары — служебная запись, не уведомление.
        assertThat(NotificationHistoryEventProcessor.mapType("attendance.marked",
                Map.of("marked_by", "auto")))
                .isEmpty();
    }

    @Test
    void skipsAttendanceMarkedWithoutMarkedBy() {
        assertThat(NotificationHistoryEventProcessor.mapType("attendance.marked", EMPTY)).isEmpty();
    }

    @Test
    void mapsSupportedGroupBroadcasts() {
        assertThat(NotificationHistoryEventProcessor.mapType("lesson.started", EMPTY))
                .contains(NotificationType.LESSON_STARTED);
        assertThat(NotificationHistoryEventProcessor.mapType("lesson.cancelled", EMPTY))
                .contains(NotificationType.LESSON_CANCELLED);
        assertThat(NotificationHistoryEventProcessor.mapType("lesson.closed", EMPTY))
                .contains(NotificationType.LESSON_CLOSED);
        assertThat(NotificationHistoryEventProcessor.mapType("homework.published", EMPTY))
                .contains(NotificationType.HOMEWORK_PUBLISHED);
        // lesson.reminder тоже broadcast по группе — в per-user history не пишется.
        assertThat(NotificationHistoryEventProcessor.mapType("lesson.reminder", EMPTY)).isEmpty();
    }

    @Test
    void skipsSystemEvents() {
        assertThat(NotificationHistoryEventProcessor.mapType("otp.requested", EMPTY)).isEmpty();
        assertThat(NotificationHistoryEventProcessor.mapType("group.renamed", EMPTY)).isEmpty();
        assertThat(NotificationHistoryEventProcessor.mapType("alert.fired", EMPTY)).isEmpty();
    }

    @Test
    void skipsUnknownType() {
        assertThat(NotificationHistoryEventProcessor.mapType("random.garbage", EMPTY))
                .isEqualTo(Optional.empty());
    }
}
