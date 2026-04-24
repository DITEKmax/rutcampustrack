package ru.rutcampustrack.notification.history;

import org.junit.jupiter.api.Test;
import ru.rutcampustrack.notification.contract.enums.NotificationType;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Юнит-тест маппера event_type → NotificationType (M10 G3 D6).
 * Гарантирует, что broadcast events skip'аются, а user-facing — маппятся.
 */
class NotificationHistoryConsumerMapTypeTest {

    @Test
    void mapsExcuseRequested() {
        assertThat(NotificationHistoryConsumer.mapType("excuse.requested"))
                .contains(NotificationType.EXCUSE_REQUESTED);
    }

    @Test
    void mapsExcuseDecidedToApproved() {
        assertThat(NotificationHistoryConsumer.mapType("excuse.decided"))
                .contains(NotificationType.EXCUSE_APPROVED);
    }

    @Test
    void mapsLateCheckinDecision() {
        assertThat(NotificationHistoryConsumer.mapType("late_checkin.decision"))
                .contains(NotificationType.LATE_CHECKIN_APPROVED);
        assertThat(NotificationHistoryConsumer.mapType("late_checkin.decided"))
                .contains(NotificationType.LATE_CHECKIN_APPROVED);
    }

    @Test
    void mapsAttendanceMarked() {
        assertThat(NotificationHistoryConsumer.mapType("attendance.marked"))
                .contains(NotificationType.ATTENDANCE_RED_ZONE);
    }

    @Test
    void skipsBroadcastLessonEvents() {
        assertThat(NotificationHistoryConsumer.mapType("lesson.started")).isEmpty();
        assertThat(NotificationHistoryConsumer.mapType("lesson.closed")).isEmpty();
        assertThat(NotificationHistoryConsumer.mapType("lesson.cancelled")).isEmpty();
    }

    @Test
    void skipsSystemEvents() {
        assertThat(NotificationHistoryConsumer.mapType("otp.requested")).isEmpty();
        assertThat(NotificationHistoryConsumer.mapType("group.renamed")).isEmpty();
        assertThat(NotificationHistoryConsumer.mapType("alert.fired")).isEmpty();
    }

    @Test
    void skipsUnknownType() {
        assertThat(NotificationHistoryConsumer.mapType("random.garbage")).isEqualTo(Optional.empty());
    }
}
