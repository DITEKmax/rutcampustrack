package ru.rutcampustrack.academic.grpc;

import io.grpc.stub.StreamObserver;
import org.junit.jupiter.api.Test;
import ru.rutcampustrack.academic.entity.User;
import ru.rutcampustrack.academic.history.HistoricalMembershipException;
import ru.rutcampustrack.academic.history.HistoricalMembershipService;
import ru.rutcampustrack.academic.homework.HomeworkStudentService;
import ru.rutcampustrack.academic.repository.GroupRepository;
import ru.rutcampustrack.academic.repository.HomeworkCompletionRepository;
import ru.rutcampustrack.academic.repository.HomeworkRepository;
import ru.rutcampustrack.academic.repository.SubjectRepository;
import ru.rutcampustrack.academic.repository.TeacherSubjectGroupRepository;
import ru.rutcampustrack.academic.repository.UserRepository;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Paired dated GroupMembers request/echo contract. */
class HistoricalMembershipGrpcTest {

    @Test
    void datedRequestReturnsExactDateAndSemesterEcho() {
        AcademicReadService readService = mock(AcademicReadService.class);
        User student = mock(User.class);
        when(student.getId()).thenReturn(41L);
        when(student.getDisplayName()).thenReturn("Исторический Студент");
        when(readService.fetchHistoricalGroupMembers(
                7L, LocalDate.of(2026, 4, 20), 3L))
                .thenReturn(new HistoricalMembershipService.RosterSnapshot(
                        7L, LocalDate.of(2026, 4, 20), 3L, List.of(student)));

        AcademicGrpcServiceImpl service = service(readService);
        RecordingObserver<GroupMembersResponse> observer = new RecordingObserver<>();

        service.getGroupMembers(GroupMembersRequest.newBuilder()
                .setGroupId(7L)
                .setAsOfDate("2026-04-20")
                .setSemesterId(3L)
                .build(), observer);

        assertThat(observer.error).isNull();
        assertThat(observer.value.getAsOfDate()).isEqualTo("2026-04-20");
        assertThat(observer.value.getSemesterId()).isEqualTo(3L);
        assertThat(observer.value.getStudentsList()).extracting(StudentInfo::getUserId)
                .containsExactly(41L);
    }

    @Test
    void oneMissingPairedFieldIsInvalidArgument() {
        AcademicGrpcServiceImpl service = service(mock(AcademicReadService.class));

        assertThatThrownBy(() -> service.getGroupMembers(GroupMembersRequest.newBuilder()
                .setGroupId(7L)
                .setAsOfDate("2026-04-20")
                .build(), new RecordingObserver<>()))
                .isInstanceOfSatisfying(HistoricalMembershipException.class, error ->
                        assertThat(error.code())
                                .isEqualTo(HistoricalMembershipException.Code.INVALID_ARGUMENT));
    }

    private static AcademicGrpcServiceImpl service(AcademicReadService readService) {
        return new AcademicGrpcServiceImpl(
                readService,
                mock(GroupRepository.class),
                mock(UserRepository.class),
                mock(SubjectRepository.class),
                mock(TeacherSubjectGroupRepository.class),
                mock(HomeworkRepository.class),
                mock(HomeworkCompletionRepository.class),
                mock(HeadmanRateLimiter.class),
                mock(HomeworkStudentService.class));
    }

    private static final class RecordingObserver<T> implements StreamObserver<T> {
        private T value;
        private Throwable error;

        @Override public void onNext(T value) { this.value = value; }
        @Override public void onError(Throwable error) { this.error = error; }
        @Override public void onCompleted() { }
    }
}
