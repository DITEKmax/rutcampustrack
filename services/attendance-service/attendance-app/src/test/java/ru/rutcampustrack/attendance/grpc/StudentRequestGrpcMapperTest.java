package ru.rutcampustrack.attendance.grpc;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import ru.rutcampustrack.attendance.contract.enums.ExcuseType;
import ru.rutcampustrack.attendance.studentrequest.StudentRequestModels;

class StudentRequestGrpcMapperTest {

    @Test
    void partialReceiptSurvivesProtoSerializationWithoutReplacingOriginalPeriod() throws Exception {
        var first =
                new StudentRequestModels.LessonSnapshot(
                        41L,
                        10L,
                        1041L,
                        "Предмет",
                        "LECTURE",
                        30L,
                        1,
                        java.time.LocalDate.of(2026, 9, 7),
                        java.time.LocalTime.of(10, 0),
                        java.time.LocalTime.of(11, 0),
                        "closed",
                        false,
                        "10041",
                        "2");
        var excluded =
                new StudentRequestModels.LessonSnapshot(
                        151L,
                        10L,
                        1051L,
                        "Предмет",
                        "LECTURE",
                        30L,
                        2,
                        java.time.LocalDate.of(2026, 9, 9),
                        java.time.LocalTime.of(12, 0),
                        java.time.LocalTime.of(13, 0),
                        "closed",
                        false,
                        "10051",
                        "3");
        var original =
                new StudentRequestModels.LessonSnapshot(
                        51L,
                        10L,
                        1051L,
                        30L,
                        2,
                        java.time.LocalDate.of(2026, 9, 7),
                        java.time.LocalTime.of(10, 0),
                        java.time.LocalTime.of(11, 0),
                        "closed",
                        false);
        var summary =
                new StudentRequestModels.RequestSummary(
                        "partial",
                        ru.rutcampustrack.attendance.contract.enums.StudentRequestKind.EXCUSE,
                        ru.rutcampustrack.attendance.contract.enums.StudentRequestStatus.APPROVED,
                        ru.rutcampustrack.attendance.contract.enums.StudentRequestOrigin.MANUAL,
                        List.of(first, original),
                        null,
                        null);
        var domain =
                new StudentRequestModels.RequestDetail(
                        summary,
                        ExcuseType.ILLNESS,
                        null,
                        List.of(),
                        null,
                        new StudentRequestModels.DecisionSelection(
                                "11111111-1111-4111-8111-111111111111",
                                "a".repeat(64),
                                List.of(first),
                                List.of(excluded)));

        var wire =
                StudentRequestDetail.parseFrom(
                        StudentRequestGrpcMapper.detail(domain).toByteArray());

        assertThat(wire.getSummary().getLessonsList())
                .extracting(StudentRequestLesson::getId)
                .containsExactly(41L, 51L);
        assertThat(wire.getDecisionSelection().getIncludedLessonsList())
                .extracting(StudentRequestLesson::getId)
                .containsExactly(41L);
        var saved = wire.getDecisionSelection().getExcludedLessons(0);
        assertThat(saved.getId()).isEqualTo(151L);
        assertThat(saved.getOccurrenceId()).isEqualTo("10051");
        assertThat(saved.getOccurrenceRevision()).isEqualTo("3");
        assertThat(saved.getDate()).isEqualTo("2026-09-09");
        assertThat(saved.getStartsAt()).isEqualTo("12:00");
        assertThat(wire.getDecisionSelection().getExpectedRevision()).isEqualTo("a".repeat(64));
        assertThat(wire.getSummary().getLessons(1).hasOccurrenceId()).isFalse();
    }

    @Test
    void mapsCommentRequiredExplicitlyForOtherAndNonOtherReasons() {
        StudentRequestModels.RequestOptions domain =
                new StudentRequestModels.RequestOptions(
                        List.of(
                                new StudentRequestModels.ReasonOption(
                                        ExcuseType.OTHER, "Другое", true),
                                new StudentRequestModels.ReasonOption(
                                        ExcuseType.ILLNESS, "Болезнь", false)),
                        new StudentRequestModels.FileLimits(2, 10L, 20L, List.of(), List.of()),
                        new StudentRequestModels.Budget(30L, 5, 0, 5),
                        List.of());

        StudentRequestOptions result = StudentRequestGrpcMapper.options(domain);

        assertThat(result.getReasonsList()).hasSize(2);
        assertThat(result.getReasons(0).getCommentRequired()).isTrue();
        assertThat(result.getReasons(1).getCommentRequired()).isFalse();
    }

    @Test
    void studentReasonCodesRoundtripWithoutChangingPublishedNumbers() throws Exception {
        var reasons =
                List.of(
                        ExcuseType.ILLNESS,
                        ExcuseType.MEDICAL_EXAMINATION,
                        ExcuseType.COMPETITION_PARTICIPATION,
                        ExcuseType.FAMILY_CIRCUMSTANCES,
                        ExcuseType.OTHER,
                        ExcuseType.UNIVERSITY_ORDER,
                        ExcuseType.SUMMONS);
        var domain =
                new StudentRequestModels.RequestOptions(
                        reasons.stream()
                                .map(
                                        reason ->
                                                new StudentRequestModels.ReasonOption(
                                                        reason,
                                                        "reason",
                                                        reason == ExcuseType.OTHER))
                                .toList(),
                        new StudentRequestModels.FileLimits(2, 10L, 20L, List.of(), List.of()),
                        new StudentRequestModels.Budget(30L, 5, 0, 5),
                        List.of());

        var wire =
                StudentRequestOptions.parseFrom(
                        StudentRequestGrpcMapper.options(domain).toByteArray());

        assertThat(StudentExcuseReason.STUDENT_EXCUSE_REASON_UNSPECIFIED.getNumber()).isZero();
        assertThat(wire.getReasonsList())
                .extracting(StudentRequestReasonOption::getCodeValue)
                .containsExactly(1, 2, 3, 4, 5, 6, 7);
        assertThat(wire.getReasonsList())
                .extracting(option -> option.getCode().name())
                .containsExactly(
                        "STUDENT_EXCUSE_REASON_ILLNESS",
                        "STUDENT_EXCUSE_REASON_MEDICAL_EXAMINATION",
                        "STUDENT_EXCUSE_REASON_COMPETITION_PARTICIPATION",
                        "STUDENT_EXCUSE_REASON_FAMILY_CIRCUMSTANCES",
                        "STUDENT_EXCUSE_REASON_OTHER",
                        "STUDENT_EXCUSE_REASON_UNIVERSITY_ORDER",
                        "STUDENT_EXCUSE_REASON_SUMMONS");
    }
}
