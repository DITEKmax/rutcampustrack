package ru.rutcampustrack.mobilebff.student;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import ru.rutcampustrack.attendance.grpc.StudentExcuseReason;
import ru.rutcampustrack.attendance.grpc.StudentRequestBudget;
import ru.rutcampustrack.attendance.grpc.StudentRequestFileLimits;
import ru.rutcampustrack.attendance.grpc.StudentRequestOptions;
import ru.rutcampustrack.attendance.grpc.StudentRequestReasonOption;
import ru.rutcampustrack.mobilebff.contract.model.StudentRequestApiModels.ReasonOption;
import ru.rutcampustrack.mobilebff.grpc.MobileAttendanceClient;
import ru.rutcampustrack.mobilebff.security.MobileRequestContext;
import ru.rutcampustrack.shared.security.InternalJwtClaims;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StudentRequestFacadeOptionsTest {
    @Mock
    private MobileAttendanceClient attendance;
    @Mock
    private MobileRequestContext requestContext;

    @Test
    void partialReceiptReadbackKeepsOriginalPeriodAndTransferredExcludedOccurrence() throws Exception {
        when(requestContext.claims()).thenReturn(new InternalJwtClaims(
                100L, UUID.fromString("33333333-3333-4333-8333-333333333333"),
                1L, 1L, "STUDENT", "ACTIVE", 10L, false, false));
        var original = ru.rutcampustrack.attendance.grpc.StudentRequestLesson.newBuilder().setId(51L)
                .setLessonNumber(2).setStatus("closed").setDate("2026-09-07").build();
        var included = ru.rutcampustrack.attendance.grpc.StudentRequestLesson.newBuilder().setId(41L)
                .setLessonNumber(1).setStatus("closed").setOccurrenceId("10041").setOccurrenceRevision("2").build();
        var excluded = original.toBuilder().setId(151L).setOccurrenceId("10051").setOccurrenceRevision("3")
                .setDate("2026-09-09").setStartsAt("12:00").setEndsAt("13:00").build();
        var proto = ru.rutcampustrack.attendance.grpc.StudentRequestDetail.newBuilder()
                .setSummary(ru.rutcampustrack.attendance.grpc.StudentRequestSummary.newBuilder().setId("partial")
                        .setKind(ru.rutcampustrack.attendance.grpc.StudentRequestKind.STUDENT_REQUEST_KIND_EXCUSE)
                        .setStatus(ru.rutcampustrack.attendance.grpc.StudentRequestStatus.STUDENT_REQUEST_STATUS_APPROVED)
                        .setOrigin(ru.rutcampustrack.attendance.grpc.StudentRequestOrigin.STUDENT_REQUEST_ORIGIN_MANUAL)
                        .addLessons(included).addLessons(original))
                .setDecisionSelection(ru.rutcampustrack.attendance.grpc.StudentRequestDecisionSelection.newBuilder()
                        .setRequestKey("11111111-1111-4111-8111-111111111111").setExpectedRevision("a".repeat(64))
                        .addIncludedLessons(included).addExcludedLessons(excluded)).build();
        when(attendance.getRequest("partial")).thenReturn(
                ru.rutcampustrack.attendance.grpc.StudentRequestDetail.parseFrom(proto.toByteArray()));

        var result = new StudentRequestFacade(attendance, requestContext).get("partial");

        assertThat(result.summary().lessons()).extracting(ru.rutcampustrack.mobilebff.contract.model.StudentRequestApiModels.Lesson::id)
                .containsExactly("41", "51");
        assertThat(result.summary().lessons().get(1).occurrenceId()).isNull();
        assertThat(result.decisionSelection().includedLessons()).extracting(ru.rutcampustrack.mobilebff.contract.model.StudentRequestApiModels.Lesson::id)
                .containsExactly("41");
        assertThat(result.decisionSelection().excludedLessons()).singleElement().satisfies(saved -> {
            assertThat(saved.id()).isEqualTo("151");
            assertThat(saved.occurrenceId()).isEqualTo("10051");
            assertThat(saved.occurrenceRevision()).isEqualTo("3");
            assertThat(saved.date()).isEqualTo(java.time.LocalDate.of(2026, 9, 9));
            assertThat(saved.startsAt()).isEqualTo(java.time.LocalTime.of(12, 0));
        });
        assertThat(result.decisionSelection().requestKey()).isEqualTo("11111111-1111-4111-8111-111111111111");
        assertThat(result.decisionSelection().expectedRevision()).isEqualTo("a".repeat(64));
    }

    @Test
    void mapsCommentRequiredDirectlyFromAttendanceProto() {
        when(requestContext.claims()).thenReturn(new InternalJwtClaims(
                100L, UUID.fromString("33333333-3333-4333-8333-333333333333"),
                1L, 1L, "STUDENT", "ACTIVE", 10L, false, false));
        when(attendance.requestOptions()).thenReturn(StudentRequestOptions.newBuilder()
                .addReasons(StudentRequestReasonOption.newBuilder()
                        .setCode(StudentExcuseReason.STUDENT_EXCUSE_REASON_OTHER)
                        .setLabel("Другое")
                        .setCommentRequired(true)
                        .build())
                .addReasons(StudentRequestReasonOption.newBuilder()
                        .setCode(StudentExcuseReason.STUDENT_EXCUSE_REASON_ILLNESS)
                        .setLabel("Болезнь")
                        .setCommentRequired(false)
                        .build())
                .setFiles(StudentRequestFileLimits.getDefaultInstance())
                .setBudget(StudentRequestBudget.getDefaultInstance())
                .build());

        var result = new StudentRequestFacade(attendance, requestContext).options();

        assertThat(result.reasons()).extracting(ReasonOption::code)
                .containsExactly(ru.rutcampustrack.mobilebff.contract.model.StudentRequestApiModels.ExcuseReason.OTHER,
                        ru.rutcampustrack.mobilebff.contract.model.StudentRequestApiModels.ExcuseReason.ILLNESS);
        assertThat(result.reasons()).extracting(ReasonOption::commentRequired)
                .containsExactly(true, false);
    }
}
