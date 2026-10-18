package ru.rutcampustrack.mobilebff.botread;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.grpc.StatusRuntimeException;

import org.junit.jupiter.api.Test;

import ru.rutcampustrack.schedule.grpc.LessonResponse;

import java.time.Instant;
import java.util.List;

class CurrentHeadmanLessonPolicyTest {
    private static final Instant NOW = Instant.parse("2026-10-17T21:15:00Z");

    @Test
    void usesMoscowDayAndCompleteCurrentSetForSafeChoice() {
        var first = lesson(101, "00:00", "00:30", "active");
        var second = lesson(102, "00:10", "00:40", "ACTIVE");
        var result =
                select(
                        List.of(
                                second,
                                first,
                                lesson(103, "00:00", "00:30", "cancelled"),
                                lesson(104, "00:20", "00:40", "active")),
                        0);
        assertThat(result.state()).isEqualTo(CurrentHeadmanLessonPolicy.State.CHOOSE);
        assertThat(result.lesson()).isNull();
        assertThat(result.choices()).extracting(LessonResponse::getId).containsExactly(101L, 102L);
        var selected = select(List.of(second, first), 102);
        assertThat(selected.state()).isEqualTo(CurrentHeadmanLessonPolicy.State.READY);
        assertThat(selected.lesson().getId()).isEqualTo(102);
    }

    @Test
    void zeroAndOneCurrentLessonAndExclusiveEndAreExplicit() {
        assertThat(select(List.of(), 0).state())
                .isEqualTo(CurrentHeadmanLessonPolicy.State.NO_CURRENT);
        assertThat(select(List.of(lesson(101, "00:00", "00:15", "active")), 0).state())
                .isEqualTo(CurrentHeadmanLessonPolicy.State.NO_CURRENT);
        assertThat(select(List.of(lesson(101, "00:15", "00:30", "active")), 0).state())
                .isEqualTo(CurrentHeadmanLessonPolicy.State.READY);
    }

    @Test
    void staleOrForeignSelectionNeverReturnsAnotherCurrentLesson() {
        assertThatThrownBy(() -> select(List.of(lesson(101, "00:00", "00:30", "active")), 999))
                .isInstanceOf(StatusRuntimeException.class);
        assertThatThrownBy(() -> select(List.of(), 101)).isInstanceOf(StatusRuntimeException.class);
        assertThatThrownBy(() -> select(List.of(lesson(101, "00:00", "00:15", "active")), 101))
                .isInstanceOf(StatusRuntimeException.class);
    }

    @Test
    void contaminatedIncompleteOrTransferringSnapshotFailsClosed() {
        var own = lesson(101, "00:00", "00:30", "active");
        for (var invalid :
                List.of(
                        own.toBuilder().setGroupId(99).build(),
                        own.toBuilder().setSemesterId(99).build(),
                        own.toBuilder().setDate("2026-10-17").build(),
                        own.toBuilder().setTransferState("PENDING").build(),
                        own.toBuilder().setStatus("unknown").build(),
                        own.toBuilder().setEndTime("bad-time").build(),
                        own.toBuilder().setEndTime("00:00").build())) {
            assertThatThrownBy(() -> select(List.of(invalid), 0))
                    .isInstanceOf(StatusRuntimeException.class);
        }
        assertThatThrownBy(() -> select(List.of(own, own), 0))
                .isInstanceOf(StatusRuntimeException.class);
    }

    private static CurrentHeadmanLessonPolicy.Selection select(
            List<LessonResponse> lessons, long id) {
        return CurrentHeadmanLessonPolicy.select(33, 9, NOW, lessons, id);
    }

    private static LessonResponse lesson(long id, String start, String end, String status) {
        return LessonResponse.newBuilder()
                .setId(id)
                .setGroupId(33)
                .setSemesterId(9)
                .setDate("2026-10-18")
                .setStartTime(start)
                .setEndTime(end)
                .setStatus(status)
                .setLessonNumber(1)
                .build();
    }
}
