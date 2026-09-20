package ru.rutcampustrack.attendance.report.studentprojection;

import org.junit.jupiter.api.Test;
import ru.rutcampustrack.attendance.contract.enums.AttendanceStatus;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AttendanceMetricCalculatorTest {

    @Test
    void calculatesCountsAndScaleTwoPercentagesFromHeldClosedOccurrences() {
        AttendanceMetricCalculator.Metrics metrics = AttendanceMetricCalculator.calculate(List.of(
                AttendanceMetricCalculator.Occurrence.closed(1L, AttendanceStatus.PRESENT),
                AttendanceMetricCalculator.Occurrence.closed(2L, AttendanceStatus.EXCUSED),
                AttendanceMetricCalculator.Occurrence.closed(3L, AttendanceStatus.ABSENT),
                AttendanceMetricCalculator.Occurrence.planned(4L),
                new AttendanceMetricCalculator.Occurrence(
                        5L,
                        AttendanceMetricCalculator.OccurrenceState.ACTIVE,
                        AttendanceStatus.PRESENT),
                new AttendanceMetricCalculator.Occurrence(
                        6L,
                        AttendanceMetricCalculator.OccurrenceState.CANCELLED,
                        AttendanceStatus.ABSENT),
                new AttendanceMetricCalculator.Occurrence(
                        7L,
                        AttendanceMetricCalculator.OccurrenceState.TRANSFERRED_OUT,
                        AttendanceStatus.EXCUSED)));

        assertThat(metrics.plannedCount()).isEqualTo(5);
        assertThat(metrics.futureCount()).isEqualTo(2);
        assertThat(metrics.heldCount()).isEqualTo(3);
        assertThat(metrics.present().count()).isEqualTo(1);
        assertThat(metrics.presentOrExcused().count()).isEqualTo(2);
        assertThat(metrics.excused().count()).isEqualTo(1);
        assertThat(metrics.absent().count()).isEqualTo(1);
        assertThat(metrics.present().percent()).isEqualByComparingTo(new BigDecimal("33.33"));
        assertThat(metrics.presentOrExcused().percent())
                .isEqualByComparingTo(new BigDecimal("66.67"));
        assertThat(metrics.excused().percent()).isEqualByComparingTo(new BigDecimal("33.33"));
        assertThat(metrics.absent().percent()).isEqualByComparingTo(new BigDecimal("33.33"));
        assertThat(metrics.present().percent().scale()).isEqualTo(2);
        assertThat(metrics.missingClosedCount()).isZero();
    }

    @Test
    void zeroHeldKeepsFutureCountButReturnsNullPercentages() {
        AttendanceMetricCalculator.Metrics metrics = AttendanceMetricCalculator.calculate(List.of(
                AttendanceMetricCalculator.Occurrence.planned(1L),
                AttendanceMetricCalculator.Occurrence.active(2L),
                AttendanceMetricCalculator.Occurrence.cancelled(3L),
                AttendanceMetricCalculator.Occurrence.transferredOut(4L)));

        assertThat(metrics.plannedCount()).isEqualTo(2);
        assertThat(metrics.futureCount()).isEqualTo(2);
        assertThat(metrics.heldCount()).isZero();
        assertThat(metrics.present().count()).isZero();
        assertThat(metrics.presentOrExcused().count()).isZero();
        assertThat(metrics.excused().count()).isZero();
        assertThat(metrics.absent().count()).isZero();
        assertThat(metrics.present().percent()).isNull();
        assertThat(metrics.presentOrExcused().percent()).isNull();
        assertThat(metrics.excused().percent()).isNull();
        assertThat(metrics.absent().percent()).isNull();
    }

    @Test
    void plannedAndActivePersistedMarksAreRetainedAsInputButStayOutsideHeld() {
        AttendanceMetricCalculator.Metrics metrics = AttendanceMetricCalculator.calculate(List.of(
                new AttendanceMetricCalculator.Occurrence(
                        51L,
                        AttendanceMetricCalculator.OccurrenceState.PLANNED,
                        AttendanceStatus.PRESENT),
                new AttendanceMetricCalculator.Occurrence(
                        52L,
                        AttendanceMetricCalculator.OccurrenceState.PLANNED,
                        AttendanceStatus.ABSENT),
                new AttendanceMetricCalculator.Occurrence(
                        53L,
                        AttendanceMetricCalculator.OccurrenceState.PLANNED,
                        AttendanceStatus.EXCUSED),
                new AttendanceMetricCalculator.Occurrence(
                        54L,
                        AttendanceMetricCalculator.OccurrenceState.ACTIVE,
                        AttendanceStatus.PRESENT),
                new AttendanceMetricCalculator.Occurrence(
                        55L,
                        AttendanceMetricCalculator.OccurrenceState.ACTIVE,
                        AttendanceStatus.ABSENT),
                new AttendanceMetricCalculator.Occurrence(
                        56L,
                        AttendanceMetricCalculator.OccurrenceState.ACTIVE,
                        AttendanceStatus.EXCUSED),
                AttendanceMetricCalculator.Occurrence.closed(57L, AttendanceStatus.PRESENT)));

        assertThat(metrics.plannedCount()).isEqualTo(7);
        assertThat(metrics.futureCount()).isEqualTo(6);
        assertThat(metrics.heldCount()).isEqualTo(1);
        assertThat(metrics.present().count()).isEqualTo(1);
        assertThat(metrics.presentOrExcused().count()).isEqualTo(1);
        assertThat(metrics.excused().count()).isZero();
        assertThat(metrics.absent().count()).isZero();
    }

    @Test
    void missingClosedMarkProjectsAbsentAndReturnsDiagnosticWithoutMutationSurface() {
        AttendanceMetricCalculator.Metrics metrics = AttendanceMetricCalculator.calculate(List.of(
                AttendanceMetricCalculator.Occurrence.closedWithoutMark(11L),
                AttendanceMetricCalculator.Occurrence.closed(12L, AttendanceStatus.PRESENT)));

        assertThat(metrics.heldCount()).isEqualTo(2);
        assertThat(metrics.absent().count()).isEqualTo(1);
        assertThat(metrics.missingClosedCount()).isEqualTo(1);
        assertThat(metrics.diagnostics().missingClosedCount()).isEqualTo(1);
        assertThat(metrics.hasMissingClosedMarks()).isTrue();
    }

    @Test
    void transferSourceAndCancelledRowsAreExcludedWhileTargetIsCountedOnce() {
        AttendanceMetricCalculator.Metrics metrics = AttendanceMetricCalculator.calculate(List.of(
                new AttendanceMetricCalculator.Occurrence(
                        100L,
                        AttendanceMetricCalculator.OccurrenceState.TRANSFERRED_OUT,
                        AttendanceStatus.PRESENT),
                AttendanceMetricCalculator.Occurrence.closed(101L, AttendanceStatus.PRESENT),
                AttendanceMetricCalculator.Occurrence.closed(102L, AttendanceStatus.EXCUSED),
                new AttendanceMetricCalculator.Occurrence(
                        103L,
                        AttendanceMetricCalculator.OccurrenceState.CANCELLED,
                        AttendanceStatus.ABSENT)));

        assertThat(metrics.plannedCount()).isEqualTo(2);
        assertThat(metrics.heldCount()).isEqualTo(2);
        assertThat(metrics.present().count()).isEqualTo(1);
        assertThat(metrics.excused().count()).isEqualTo(1);
    }

    @Test
    void freeAttendanceFailsAsTypedUnsupportedDataEvenOnExcludedRow() {
        assertThatThrownBy(() -> AttendanceMetricCalculator.calculate(List.of(
                new AttendanceMetricCalculator.Occurrence(
                        21L,
                        AttendanceMetricCalculator.OccurrenceState.TRANSFERRED_OUT,
                        AttendanceStatus.FREE_ATTENDANCE))))
                .isInstanceOf(StudentProjectionException.class)
                .satisfies(error -> assertThat(((StudentProjectionException) error).kind())
                        .isEqualTo(StudentProjectionException.Kind.UNSUPPORTED_ATTENDANCE_STATUS));
    }

    @Test
    void cancelledMarkFailsForBothIncludedLifecycleStates() {
        for (AttendanceMetricCalculator.OccurrenceState state :
                List.of(AttendanceMetricCalculator.OccurrenceState.PLANNED,
                        AttendanceMetricCalculator.OccurrenceState.ACTIVE)) {
            assertThatThrownBy(() -> AttendanceMetricCalculator.calculate(List.of(
                    new AttendanceMetricCalculator.Occurrence(22L, state, AttendanceStatus.CANCELLED))))
                    .isInstanceOf(StudentProjectionException.class)
                    .satisfies(error -> assertThat(((StudentProjectionException) error).kind())
                            .isEqualTo(StudentProjectionException.Kind.INVALID_OCCURRENCE));
        }
    }

    @Test
    void duplicateOccurrenceIdFailsInsteadOfBeingSilentlyDeduplicated() {
        assertThatThrownBy(() -> AttendanceMetricCalculator.calculate(List.of(
                AttendanceMetricCalculator.Occurrence.closed(31L, AttendanceStatus.PRESENT),
                AttendanceMetricCalculator.Occurrence.closed(31L, AttendanceStatus.ABSENT))))
                .isInstanceOf(StudentProjectionException.class)
                .satisfies(error -> assertThat(((StudentProjectionException) error).kind())
                        .isEqualTo(StudentProjectionException.Kind.DUPLICATE_OCCURRENCE));
    }

    @Test
    void malformedOccurrenceInputsFailWithTypedErrors() {
        assertThatThrownBy(() -> AttendanceMetricCalculator.calculate(Collections.singletonList(null)))
                .isInstanceOf(StudentProjectionException.class)
                .satisfies(error -> assertThat(((StudentProjectionException) error).kind())
                        .isEqualTo(StudentProjectionException.Kind.INVALID_OCCURRENCE));

        assertThatThrownBy(() -> AttendanceMetricCalculator.calculate(List.of(
                new AttendanceMetricCalculator.Occurrence(
                        0L,
                        AttendanceMetricCalculator.OccurrenceState.CLOSED,
                        AttendanceStatus.PRESENT))))
                .isInstanceOf(StudentProjectionException.class)
                .satisfies(error -> assertThat(((StudentProjectionException) error).kind())
                        .isEqualTo(StudentProjectionException.Kind.INVALID_OCCURRENCE));

        assertThatThrownBy(() -> AttendanceMetricCalculator.calculate(List.of(
                new AttendanceMetricCalculator.Occurrence(
                        41L,
                        null,
                        AttendanceStatus.PRESENT))))
                .isInstanceOf(StudentProjectionException.class)
                .satisfies(error -> assertThat(((StudentProjectionException) error).kind())
                        .isEqualTo(StudentProjectionException.Kind.INVALID_OCCURRENCE));

        assertThatThrownBy(() -> AttendanceMetricCalculator.calculate(List.of(
                AttendanceMetricCalculator.Occurrence.closed(42L, AttendanceStatus.CANCELLED))))
                .isInstanceOf(StudentProjectionException.class)
                .satisfies(error -> assertThat(((StudentProjectionException) error).kind())
                        .isEqualTo(StudentProjectionException.Kind.INVALID_OCCURRENCE));

        assertThatThrownBy(() -> AttendanceMetricCalculator.calculate(null))
                .isInstanceOf(StudentProjectionException.class)
                .satisfies(error -> assertThat(((StudentProjectionException) error).kind())
                        .isEqualTo(StudentProjectionException.Kind.INVALID_OCCURRENCE));
    }

    @Test
    void malformedMetricCountsAreRejectedAndDerivedPercentagesCannotDisagree() {
        assertThatThrownBy(() -> new AttendanceMetricCalculator.Metrics(
                2, 1, 1, 1, 1, 0, AttendanceMetricCalculator.Diagnostics.none()))
                .isInstanceOf(StudentProjectionException.class)
                .satisfies(error -> assertThat(((StudentProjectionException) error).kind())
                        .isEqualTo(StudentProjectionException.Kind.INVALID_METRICS));

        AttendanceMetricCalculator.Metrics metrics = new AttendanceMetricCalculator.Metrics(
                3, 3, 1, 2, 1, 1, AttendanceMetricCalculator.Diagnostics.none());
        assertThat(metrics.present().percent()).isEqualByComparingTo(new BigDecimal("33.33"));
    }
}
