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
    void mapsLateCheckinDecisionApproved() {
        assertThat(NotificationHistoryConsumer.mapType("late_checkin.decision",
                Map.of("status", "approved")))
                .contains(NotificationType.LATE_CHECKIN_APPROVED);
        assertThat(NotificationHistoryConsumer.mapType("late_checkin.decided",
                Map.of("status", "approved")))
                .contains(NotificationType.LATE_CHECKIN_APPROVED);
    }

    @Test
    void mapsLateCheckinDecisionRejected() {
        assertThat(NotificationHistoryConsumer.mapType("late_checkin.decision",
                Map.of("status", "rejected")))
                .contains(NotificationType.LATE_CHECKIN_REJECTED);
        assertThat(NotificationHistoryConsumer.mapType("late_checkin.decided",
                Map.of("status", "rejected")))
                .contains(NotificationType.LATE_CHECKIN_REJECTED);
    }

    @Test
    void mapsAttendanceMarked() {
        assertThat(NotificationHistoryConsumer.mapType("attendance.marked", EMPTY))
                .contains(NotificationType.ATTENDANCE_RED_ZONE);
    }

    @Test
    void skipsBroadcastLessonEvents() {
        assertThat(NotificationHistoryConsumer.mapType("lesson.started", EMPTY)).isEmpty();
        assertThat(NotificationHistoryConsumer.mapType("lesson.closed", EMPTY)).isEmpty();
        assertThat(NotificationHistoryConsumer.mapType("lesson.cancelled", EMPTY)).isEmpty();
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
