package ru.rutcampustrack.attendance.studentrequest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.MongoTemplate;
import ru.rutcampustrack.attendance.checkin.AttendanceRepository;
import ru.rutcampustrack.attendance.contract.dto.headman.HeadmanRequestLessonResponse;
import ru.rutcampustrack.attendance.contract.enums.ExcuseTicketStatus;
import ru.rutcampustrack.attendance.contract.enums.UserRole;
import ru.rutcampustrack.attendance.exception.AccessDeniedException;
import ru.rutcampustrack.attendance.exception.ConflictException;
import ru.rutcampustrack.attendance.excuse.ExcuseRepository;
import ru.rutcampustrack.attendance.excuse.entity.ExcuseTicket;
import ru.rutcampustrack.attendance.grpc.ScheduleGrpcClient;
import ru.rutcampustrack.attendance.latecheckin.LateCheckinRepository;
import ru.rutcampustrack.attendance.studentrequest.StudentRequestModels.Identity;
import ru.rutcampustrack.attendance.studentrequest.entity.ExcuseDecisionSelectionDocument;
import ru.rutcampustrack.attendance.studentrequest.entity.StudentLessonSnapshotDocument;
import ru.rutcampustrack.attendance.subjectdeletion.ExcuseSubjectCoverage;

class HeadmanRequestServiceTest {
    private static final long GROUP_ID = 5L;
    private static final long SEMESTER_ID = 3L;
    private static final String TICKET_ID = "partial-excuse";
    private static final Identity HEADMAN = new Identity(8L, UserRole.STUDENT, GROUP_ID, true);

    private final ExcuseRepository excuses = mock(ExcuseRepository.class);
    private final StudentRequestService studentRequests = mock(StudentRequestService.class);
    private final ScheduleGrpcClient schedule = mock(ScheduleGrpcClient.class);
    private final HeadmanRequestService service =
            new HeadmanRequestService(
                    excuses,
                    mock(LateCheckinRepository.class),
                    mock(AttendanceRepository.class),
                    mock(RequestAttachmentRepository.class),
                    studentRequests,
                    schedule,
                    mock(MongoTemplate.class),
                    Clock.systemUTC());

    @Test
    void readsSurvivingExcludedPairAfterSubjectDeletionRemovedLastApprovedPair() {
        ExcuseTicket ticket = partiallyApproved();
        removeApprovedSubject(ticket);
        assertThat(ticket.getLessonIds()).isEmpty();
        when(excuses.findById(TICKET_ID)).thenReturn(Optional.of(ticket));

        var detail = service.get(HEADMAN, TICKET_ID);

        assertThat(detail.summary().status()).isEqualTo("APPROVED");
        assertThat(detail.summary().coverageStart()).isEqualTo(LocalDate.of(2026, 10, 15));
        assertThat(detail.lessons())
                .extracting(HeadmanRequestLessonResponse::lessonId)
                .containsExactly(12L);
        assertThat(detail.currentAffectedLessons()).isNull();
        assertThat(detail.decisionRevision()).isNull();
        var receipt = detail.decisionSelection();
        assertThat(receipt.requestKey()).isEqualTo("original-decision");
        assertThat(receipt.includedLessons()).isEmpty();
        assertThat(receipt.excludedLessons())
                .singleElement()
                .satisfies(
                        lesson -> {
                            assertThat(lesson.occurrenceId()).isEqualTo("102");
                            assertThat(lesson.attendanceStatus()).isEqualTo("PRESENT");
                        });
        assertThat(receipt.beforeLessons()).isEqualTo(receipt.excludedLessons());
        verify(studentRequests).requireHeadmanAuthority(HEADMAN, GROUP_ID);
        verifyNoInteractions(schedule);

        Identity foreign = new Identity(9L, UserRole.STUDENT, 20L, true);
        when(studentRequests.requireHeadmanAuthority(foreign, GROUP_ID))
                .thenThrow(new AccessDeniedException("Нет доступа к группе"));
        assertThatThrownBy(() -> service.get(foreign, TICKET_ID))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void stillRejectsEmptyOrInvalidTargetsOutsideTheEffectiveArchiveCase() {
        ExcuseTicket unadjusted = partiallyApproved();
        unadjusted.setLessonIds(List.of());
        when(excuses.findById(TICKET_ID)).thenReturn(Optional.of(unadjusted));
        assertConflict();

        ExcuseTicket adjusted = partiallyApproved();
        removeApprovedSubject(adjusted);
        when(excuses.findById(TICKET_ID)).thenReturn(Optional.of(adjusted));
        adjusted.setStatus(ExcuseTicketStatus.SUBMITTED);
        assertConflict();

        adjusted.setStatus(ExcuseTicketStatus.APPROVED);
        adjusted.setLessonIds(List.of(0L));
        assertConflict();

        adjusted.setLessonIds(List.of());
        adjusted.getEffectiveDecisionSelection().getExcludedLessons().getFirst()
                .setOccurrenceId("0");
        assertConflict();
    }

    private void assertConflict() {
        assertThatThrownBy(() -> service.get(HEADMAN, TICKET_ID))
                .isInstanceOf(ConflictException.class)
                .hasMessage("Текущие пары заявки недоступны");
    }

    private static void removeApprovedSubject(ExcuseTicket ticket) {
        assertThat(
                        ExcuseSubjectCoverage.subtract(
                                ticket,
                                Set.of(11L),
                                101L,
                                "operation",
                                8L,
                                Instant.parse("2026-10-16T10:00:00Z")))
                .isTrue();
    }

    private static ExcuseTicket partiallyApproved() {
        var approved = snapshot(11L, 101L, "101", LocalDate.of(2026, 10, 14), "EXCUSED");
        var before = snapshot(11L, 101L, "101", LocalDate.of(2026, 10, 14), "ABSENT");
        var excluded = snapshot(12L, 102L, "102", LocalDate.of(2026, 10, 15), "PRESENT");
        return ExcuseTicket.builder()
                .id(TICKET_ID)
                .groupId(GROUP_ID)
                .semesterId(SEMESTER_ID)
                .studentId(7L)
                .status(ExcuseTicketStatus.APPROVED)
                .lessonIds(List.of(11L))
                .originalLessonIds(List.of(11L, 12L))
                .lessonSnapshots(List.of(before, excluded))
                .decisionSelection(
                        ExcuseDecisionSelectionDocument.builder()
                                .requestKey("original-decision")
                                .expectedRevision("a".repeat(64))
                                .includedLessons(List.of(approved))
                                .excludedLessons(List.of(excluded))
                                .beforeLessons(List.of(before, excluded))
                                .build())
                .build();
    }

    private static StudentLessonSnapshotDocument snapshot(
            long lessonId, long subjectId, String occurrenceId, LocalDate date, String mark) {
        return StudentLessonSnapshotDocument.builder()
                .lessonId(lessonId)
                .subjectId(subjectId)
                .groupId(GROUP_ID)
                .semesterId(SEMESTER_ID)
                .occurrenceId(occurrenceId)
                .occurrenceRevision("1")
                .date(date)
                .attendanceStatus(mark)
                .build();
    }
}
