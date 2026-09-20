package ru.rutcampustrack.academic.homework;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import ru.rutcampustrack.academic.contract.enums.AccountStatus;
import ru.rutcampustrack.academic.contract.enums.UserRole;
import ru.rutcampustrack.academic.entity.Homework;
import ru.rutcampustrack.academic.entity.HomeworkCompletion;
import ru.rutcampustrack.academic.entity.Semester;
import ru.rutcampustrack.academic.entity.User;
import ru.rutcampustrack.academic.exception.AccessDeniedException;
import ru.rutcampustrack.academic.repository.HomeworkCompletionRepository;
import ru.rutcampustrack.academic.repository.HomeworkRepository;
import ru.rutcampustrack.academic.repository.SemesterRepository;
import ru.rutcampustrack.academic.repository.UserRepository;
import ru.rutcampustrack.shared.security.InternalJwtClaims;

import java.time.OffsetDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class HomeworkStudentServiceTest {
    private static final long STUDENT_ID = 100L;
    private static final long GROUP_ID = 10L;
    private static final long SEMESTER_ID = 5L;
    private static final long HOMEWORK_ID = 77L;

    @Mock private HomeworkRepository homeworkRepository;
    @Mock private HomeworkCompletionRepository completionRepository;
    @Mock private SemesterRepository semesterRepository;
    @Mock private UserRepository userRepository;
    @Mock private Homework homework;
    @Mock private HomeworkCompletion completion;
    @Mock private Semester semester;
    @Mock private User user;

    private HomeworkStudentService service;
    private InternalJwtClaims claims;

    @BeforeEach
    void setUp() {
        service = new HomeworkStudentService(
                homeworkRepository, completionRepository, semesterRepository, userRepository);
        claims = new InternalJwtClaims(STUDENT_ID, "STUDENT", GROUP_ID, false);
        lenient().when(userRepository.findById(STUDENT_ID)).thenReturn(Optional.of(user));
        lenient().when(user.getRole()).thenReturn(UserRole.STUDENT);
        lenient().when(user.getStatus()).thenReturn(AccountStatus.ACTIVE);
        lenient().when(user.getGroupId()).thenReturn(GROUP_ID);
        lenient().when(semesterRepository.findByIsActiveTrue()).thenReturn(Optional.of(semester));
        lenient().when(semester.getId()).thenReturn(SEMESTER_ID);
        lenient().when(homeworkRepository.findById(HOMEWORK_ID)).thenReturn(Optional.of(homework));
        lenient().when(homework.getId()).thenReturn(HOMEWORK_ID);
        lenient().when(homework.getGroupId()).thenReturn(GROUP_ID);
        lenient().when(homework.getSemesterId()).thenReturn(SEMESTER_ID);
    }

    @Test
    void completeIsIdempotentAndReturnsTheObservedState() {
        OffsetDateTime completedAt = OffsetDateTime.parse("2026-09-07T10:00:00+03:00");
        when(completionRepository.findByHomeworkIdAndStudentId(HOMEWORK_ID, STUDENT_ID))
                .thenReturn(Optional.of(completion));
        when(completion.getCompletedAt()).thenReturn(completedAt);

        assertThat(service.setCompletion(HOMEWORK_ID, SEMESTER_ID, claims, true))
                .isEqualTo(new HomeworkStudentService.CompletionState(true, completedAt));
        assertThat(service.setCompletion(HOMEWORK_ID, SEMESTER_ID, claims, true))
                .isEqualTo(new HomeworkStudentService.CompletionState(true, completedAt));

        verify(completionRepository, times(2)).insertIfAbsent(HOMEWORK_ID, STUDENT_ID);
        verify(completionRepository, times(2)).findByHomeworkIdAndStudentId(HOMEWORK_ID, STUDENT_ID);
        verify(completionRepository, never()).deleteByHomeworkIdAndStudentId(anyLong(), anyLong());
    }

    @Test
    void uncompleteIsIdempotentAndDeletesOnlyTheCurrentStudentRow() {
        assertThat(service.setCompletion(HOMEWORK_ID, SEMESTER_ID, claims, false))
                .isEqualTo(new HomeworkStudentService.CompletionState(false, null));

        verify(completionRepository).deleteByHomeworkIdAndStudentId(HOMEWORK_ID, STUDENT_ID);
        verify(completionRepository, never()).insertIfAbsent(anyLong(), anyLong());
    }

    @Test
    void foreignGroupIsRejectedBeforeWritingCompletion() {
        when(homework.getGroupId()).thenReturn(999L);

        assertThatThrownBy(() -> service.setCompletion(HOMEWORK_ID, SEMESTER_ID, claims, true))
                .isInstanceOf(AccessDeniedException.class);

        verifyNoInteractions(completionRepository);
    }

    @Test
    void wrongRoleIsRejectedBeforeReadingHomework() {
        when(user.getRole()).thenReturn(UserRole.ADMIN);

        assertThatThrownBy(() -> service.setCompletion(HOMEWORK_ID, SEMESTER_ID, claims, true))
                .isInstanceOf(AccessDeniedException.class);

        verifyNoInteractions(homeworkRepository, completionRepository);
    }

    @Test
    void inactiveStudentIsRejectedBeforeReadingHomework() {
        when(user.getStatus()).thenReturn(AccountStatus.SUSPENDED);

        assertThatThrownBy(() -> service.setCompletion(HOMEWORK_ID, SEMESTER_ID, claims, true))
                .isInstanceOf(AccessDeniedException.class);

        verifyNoInteractions(homeworkRepository, completionRepository);
    }

    @Test
    void inactiveSemesterIsRejected() {
        when(homework.getSemesterId()).thenReturn(6L);

        assertThatThrownBy(() -> service.setCompletion(HOMEWORK_ID, SEMESTER_ID, claims, true))
                .isInstanceOf(AccessDeniedException.class);

        verifyNoInteractions(completionRepository);
    }
}
