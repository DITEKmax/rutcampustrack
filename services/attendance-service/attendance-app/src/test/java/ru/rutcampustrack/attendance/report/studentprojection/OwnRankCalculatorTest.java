package ru.rutcampustrack.attendance.report.studentprojection;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OwnRankCalculatorTest {

    @Test
    void competitionRankProducesOneTwoTwoFour() {
        List<OwnRankCalculator.Participant> roster = List.of(
                OwnRankCalculator.Participant.withCounts(1L, 4, 4),
                OwnRankCalculator.Participant.withCounts(2L, 4, 3),
                OwnRankCalculator.Participant.withCounts(3L, 4, 3),
                OwnRankCalculator.Participant.withCounts(4L, 4, 2));

        assertThat(OwnRankCalculator.calculate(1L, roster))
                .isEqualTo(new OwnRankCalculator.Rank(1, 4, true));
        assertThat(OwnRankCalculator.calculate(2L, roster))
                .isEqualTo(new OwnRankCalculator.Rank(2, 4, true));
        assertThat(OwnRankCalculator.calculate(3L, roster))
                .isEqualTo(new OwnRankCalculator.Rank(2, 4, true));
        assertThat(OwnRankCalculator.calculate(4L, roster))
                .isEqualTo(new OwnRankCalculator.Rank(4, 4, true));
    }

    @Test
    void exactFractionsAreComparedBeforePublicRounding() {
        List<OwnRankCalculator.Participant> roster = List.of(
                OwnRankCalculator.Participant.withCounts(1L, 3, 2),
                OwnRankCalculator.Participant.withCounts(2L, 10_000, 6_667));

        assertThat(AttendanceMetricCalculator.Metrics.forRanking(3, 2).present().percent())
                .isEqualByComparingTo("66.67");
        assertThat(AttendanceMetricCalculator.Metrics.forRanking(10_000, 6_667)
                .present().percent()).isEqualByComparingTo("66.67");
        assertThat(OwnRankCalculator.calculate(1L, roster))
                .isEqualTo(new OwnRankCalculator.Rank(2, 2, true));
    }

    @Test
    void fullRankingUsesExactFractionsAndStableIdsForTies() {
        List<OwnRankCalculator.Participant> roster = List.of(
                OwnRankCalculator.Participant.withCounts(1L, 1_000_000, 333_333),
                OwnRankCalculator.Participant.withCounts(5L, 6, 3),
                OwnRankCalculator.Participant.withCounts(4L, 2, 1),
                OwnRankCalculator.Participant.withCounts(2L, 3, 1));

        assertThat(OwnRankCalculator.rankAll(roster))
                .extracting(OwnRankCalculator.RankedParticipant::participantId,
                        OwnRankCalculator.RankedParticipant::position,
                        OwnRankCalculator.RankedParticipant::percentage)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(4L, 1, new java.math.BigDecimal("50.00")),
                        org.assertj.core.groups.Tuple.tuple(5L, 1, new java.math.BigDecimal("50.00")),
                        org.assertj.core.groups.Tuple.tuple(2L, 3, new java.math.BigDecimal("33.33")),
                        org.assertj.core.groups.Tuple.tuple(1L, 4, new java.math.BigDecimal("33.33")));
    }

    @Test
    void crossMultiplicationUsesBigIntegerBeyondLongRange() {
        List<OwnRankCalculator.Participant> roster = List.of(
                OwnRankCalculator.Participant.withCounts(1L, 2_000_000_000, 1_000_000_000),
                OwnRankCalculator.Participant.withCounts(2L, 2_000_000_000, 1_000_000_001));

        assertThat(OwnRankCalculator.calculate(1L, roster))
                .isEqualTo(new OwnRankCalculator.Rank(2, 2, true));
        assertThat(OwnRankCalculator.calculate(2L, roster))
                .isEqualTo(new OwnRankCalculator.Rank(1, 2, true));
    }

    @Test
    void noRecordParticipantRemainsInParticipantCount() {
        List<OwnRankCalculator.Participant> roster = List.of(
                OwnRankCalculator.Participant.withCounts(1L, 2, 2),
                new OwnRankCalculator.Participant(2L, null),
                OwnRankCalculator.Participant.withCounts(3L, 2, 1));

        assertThat(OwnRankCalculator.calculate(1L, roster))
                .isEqualTo(new OwnRankCalculator.Rank(1, 3, true));
    }

    @Test
    void projectedAbsentOwnWithHeldLessonsIsRankableButH0OwnIsUnavailable() {
        AttendanceMetricCalculator.Metrics projectedAbsent = AttendanceMetricCalculator.calculate(List.of(
                AttendanceMetricCalculator.Occurrence.closedWithoutMark(10L)));
        assertThat(OwnRankCalculator.calculate(1L, List.of(
                new OwnRankCalculator.Participant(1L, projectedAbsent),
                OwnRankCalculator.Participant.withCounts(2L, 1, 1))))
                .isEqualTo(new OwnRankCalculator.Rank(2, 2, true));

        assertThat(OwnRankCalculator.calculate(1L, List.of(
                new OwnRankCalculator.Participant(1L, AttendanceMetricCalculator.Metrics.empty()),
                OwnRankCalculator.Participant.withCounts(2L, 1, 1))))
                .isEqualTo(new OwnRankCalculator.Rank(null, 2, false));
    }

    @Test
    void absentOwnFromAuthoritativeRosterIsUnavailableWithoutImplicitInjection() {
        assertThat(OwnRankCalculator.calculate(1L, List.of(
                OwnRankCalculator.Participant.withCounts(2L, 1, 1))))
                .isEqualTo(new OwnRankCalculator.Rank(null, 1, false));
    }

    @Test
    void duplicateOrMalformedRosterInputFailsWithTypedErrors() {
        assertThatThrownBy(() -> OwnRankCalculator.calculate(1L, List.of(
                OwnRankCalculator.Participant.withCounts(1L, 1, 1),
                OwnRankCalculator.Participant.withCounts(1L, 1, 0))))
                .isInstanceOf(StudentProjectionException.class)
                .satisfies(error -> assertThat(((StudentProjectionException) error).kind())
                        .isEqualTo(StudentProjectionException.Kind.INVALID_ROSTER));

        assertThatThrownBy(() -> OwnRankCalculator.calculate(1L, List.of(
                new OwnRankCalculator.Participant(0L, null))))
                .isInstanceOf(StudentProjectionException.class)
                .satisfies(error -> assertThat(((StudentProjectionException) error).kind())
                        .isEqualTo(StudentProjectionException.Kind.INVALID_ROSTER));

        assertThatThrownBy(() -> OwnRankCalculator.calculate(1L, List.of(
                new OwnRankCalculator.Participant(
                        2L,
                        new AttendanceMetricCalculator.Metrics(
                                0, 1, 1, 1, 0, 0, AttendanceMetricCalculator.Diagnostics.none())))))
                .isInstanceOf(StudentProjectionException.class)
                .satisfies(error -> assertThat(((StudentProjectionException) error).kind())
                        .isEqualTo(StudentProjectionException.Kind.INVALID_METRICS));

        assertThatThrownBy(() -> OwnRankCalculator.calculate(null, List.of()))
                .isInstanceOf(StudentProjectionException.class)
                .satisfies(error -> assertThat(((StudentProjectionException) error).kind())
                        .isEqualTo(StudentProjectionException.Kind.INVALID_ROSTER));
    }

    @Test
    void rankSummaryHasNoPeerPayloadFields() {
        assertThat(OwnRankCalculator.Rank.class.getRecordComponents())
                .extracting(java.lang.reflect.RecordComponent::getName)
                .containsExactly("position", "participantCount", "available");
    }
}
