package ru.rutcampustrack.notification.history;

import org.junit.jupiter.api.Test;
import ru.rutcampustrack.notification.contract.enums.NotificationType;

import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Юнит-тест маппера event_type → NotificationType (M10 G3 D6 + G9 H1).
 * Гарантирует, что broadcast events skip'аются, а user-facing — маппятся.
 */
class NotificationHistoryConsumerMapTypeTest {

    private static final Map<String, Object> EMPTY = Map.of();

    @Test
    void mapsExcuseRequested() {
        assertThat(NotificationHistoryConsumer.mapType("excuse.requested", EMPTY))
                .contains(NotificationType.EXCUSE_REQUESTED);
    }

    @Test
    void mapsExcuseDecidedApproved() {
        assertThat(NotificationHistoryConsumer.mapType("excuse.decided",
                Map.of("status", "approved")))
                .contains(NotificationType.EXCUSE_APPROVED);
    }

    @Test
    void mapsExcuseDecidedRejected() {
        // G9 H1: до hot-patch'а REJECTED тихо терялся как APPROVED.
        assertThat(NotificationHistoryConsumer.mapType("excuse.decided",
                Map.of("status", "rejected")))
                .contains(NotificationType.EXCUSE_REJECTED);
    }

    @Test
    void mapsExcuseDecidedDefaultsToApprovedWhenStatusMissing() {
        // Defensive: если payload.status отсутствует — fallback APPROVED
        // (исторически decided без status трактовался как approve).
        assertThat(NotificationHistoryConsumer.mapType("excuse.decided", EMPTY))
                .contains(NotificationType.EXCUSE_APPROVED);
    }

    @Test
    void skipsLateCheckinDecisionCommand() {
        assertThat(NotificationHistoryConsumer.mapType("late_checkin.decision",
                Map.of("approved", true, "request_id", "req-1")))
                .isEmpty();
    }

    @Test
    void mapsLateCheckinDecidedApproved() {
        assertThat(NotificationHistoryConsumer.mapType("late_checkin.decided",
                Map.of("status", "approved")))
                .contains(NotificationType.LATE_CHECKIN_APPROVED);
    }

    @Test
    void mapsLateCheckinDecidedRejected() {
        assertThat(NotificationHistoryConsumer.mapType("late_checkin.decided",
                Map.of("status", "rejected")))
                .contains(NotificationType.LATE_CHECKIN_REJECTED);
    }

    @Test
    void mapsAttendanceMarkedByHeadman() {
        assertThat(NotificationHistoryConsumer.mapType("attendance.marked",
                Map.of("marked_by", "headman")))
                .contains(NotificationType.ATTENDANCE_MARKED_BY_HEADMAN);
    }

    @Test
    void skipsAttendanceMarkedBySelf() {
        // Self check-in — собственное действие студента, не уведомление.
        assertThat(NotificationHistoryConsumer.mapType("attendance.marked",
                Map.of("marked_by", "self")))
                .isEmpty();
    }

    @Test
    void skipsAttendanceMarkedByAuto() {
        // Авто-absent при закрытии пары — служебная запись, не уведомление.
        assertThat(NotificationHistoryConsumer.mapType("attendance.marked",
                Map.of("marked_by", "auto")))
                .isEmpty();
    }

    @Test
    void skipsAttendanceMarkedWithoutMarkedBy() {
        assertThat(NotificationHistoryConsumer.mapType("attendance.marked", EMPTY)).isEmpty();
    }

    @Test
    void skipsBroadcastLessonEvents() {
        assertThat(NotificationHistoryConsumer.mapType("lesson.started", EMPTY)).isEmpty();
        assertThat(NotificationHistoryConsumer.mapType("lesson.closed", EMPTY)).isEmpty();
        assertThat(NotificationHistoryConsumer.mapType("lesson.cancelled", EMPTY)).isEmpty();
        // lesson.reminder тоже broadcast по группе — в per-user history не пишется.
        assertThat(NotificationHistoryConsumer.mapType("lesson.reminder", EMPTY)).isEmpty();
    }

    @Test
    void skipsSystemEvents() {
        assertThat(NotificationHistoryConsumer.mapType("otp.requested", EMPTY)).isEmpty();
        assertThat(NotificationHistoryConsumer.mapType("group.renamed", EMPTY)).isEmpty();
        assertThat(NotificationHistoryConsumer.mapType("alert.fired", EMPTY)).isEmpty();
    }

    @Test
    void skipsUnknownType() {
        assertThat(NotificationHistoryConsumer.mapType("random.garbage", EMPTY))
                .isEqualTo(Optional.empty());
    }
}
