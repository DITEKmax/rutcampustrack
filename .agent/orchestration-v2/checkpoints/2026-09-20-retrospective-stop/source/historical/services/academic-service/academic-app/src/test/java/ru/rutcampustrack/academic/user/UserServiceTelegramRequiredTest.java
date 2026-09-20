package ru.rutcampustrack.academic.user;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cache.CacheManager;
import org.springframework.context.ApplicationEventPublisher;
import ru.rutcampustrack.academic.contract.dto.user.CreateUserRequest;
import ru.rutcampustrack.academic.contract.enums.UserRole;
import ru.rutcampustrack.academic.entity.Group;
import ru.rutcampustrack.academic.entity.GroupHistoryCoverage;
import ru.rutcampustrack.academic.entity.Semester;
import ru.rutcampustrack.academic.exception.BadRequestException;
import ru.rutcampustrack.academic.repository.GroupHistoryCoverageRepository;
import ru.rutcampustrack.academic.repository.GroupRepository;
import ru.rutcampustrack.academic.repository.HeadmanAssistantRepository;
import ru.rutcampustrack.academic.repository.SemesterRepository;
import ru.rutcampustrack.academic.repository.StudentGroupHistoryRepository;
import ru.rutcampustrack.academic.repository.UserRepository;
import ru.rutcampustrack.academic.security.RequestContext;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * BUG-006-3 / D-08..D-11: telegramId is required for STUDENT, optional for
 * TEACHER and ADMIN. Validation lives inside {@link UserService#createUser}
 * (guard runs before any repo access) and surfaces a
 * {@link BadRequestException} with {@code field="telegramId"} — so the frontend
 * can highlight the offending control via the Problem-Detail payload.
 */
@ExtendWith(MockitoExtension.class)
class UserServiceTelegramRequiredTest {

    @Mock private UserRepository userRepository;
    @Mock private HeadmanAssistantRepository headmanAssistantRepository;
    @Mock private StudentGroupHistoryRepository studentGroupHistoryRepository;
    @Mock private RequestContext requestContext;
    @Mock private UserAssembler userAssembler;
    @Mock private CacheManager cacheManager;
    @Mock private ApplicationEventPublisher eventPublisher;
    @Mock private GroupRepository groupRepository;
    @Mock private SemesterRepository semesterRepository;
    @Mock private GroupHistoryCoverageRepository coverageRepository;

    @InjectMocks
    private UserService service;

    @org.junit.jupiter.api.BeforeEach
    void managedEnrollmentFixture() {
        Group group = org.mockito.Mockito.mock(Group.class);
        lenient().when(group.getId()).thenReturn(1L);
        lenient().when(group.isActive()).thenReturn(true);
        Semester semester = org.mockito.Mockito.mock(Semester.class);
        lenient().when(semester.getDateFrom()).thenReturn(LocalDate.of(2026, 9, 1));
        lenient().when(semester.getDateTo()).thenReturn(LocalDate.of(2027, 1, 31));
        GroupHistoryCoverage coverage = org.mockito.Mockito.mock(GroupHistoryCoverage.class);
        lenient().when(coverage.getCoverageFrom()).thenReturn(LocalDate.of(2026, 9, 1));
        lenient().when(coverage.getWriterVersion()).thenReturn("managed_v1");
        lenient().when(groupRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(group));
        lenient().when(semesterRepository.findAllByIsActiveTrueOrderByIdAsc())
                .thenReturn(List.of(semester));
        lenient().when(coverageRepository.findById(1L)).thenReturn(Optional.of(coverage));
    }

    private CreateUserRequest req(UserRole role, Long telegramId, String employeeNumber) {
        return new CreateUserRequest(
                "Иванов", "Иван", "Иванович",
                role,
                role == UserRole.STUDENT ? 1L : null,
                employeeNumber,
                telegramId
        );
    }

    @Test
    void studentWithoutTelegramIdIsRejected() {
        CreateUserRequest req = req(UserRole.STUDENT, null, null);

        assertThatThrownBy(() -> service.createUser(req))
                .isInstanceOfSatisfying(BadRequestException.class, ex -> {
                    assertThat(ex.getField()).isEqualTo("telegramId");
                    assertThat(ex.getMessage()).isEqualTo("Telegram ID обязателен для студента");
                });

        verify(userRepository, never()).save(any());
        verify(userRepository, never()).nextStudentLoginSeq();
    }

    @Test
    void studentWithZeroTelegramIdIsRejected() {
        // Some clients submit 0 instead of null — treat as missing.
        CreateUserRequest req = req(UserRole.STUDENT, 0L, null);

        assertThatThrownBy(() -> service.createUser(req))
                .isInstanceOfSatisfying(BadRequestException.class, ex -> {
                    assertThat(ex.getField()).isEqualTo("telegramId");
                });

        verify(userRepository, never()).save(any());
    }

    @Test
    void teacherWithoutTelegramIdIsAccepted() {
        CreateUserRequest req = req(UserRole.TEACHER, null, "EMP-777");
        when(userRepository.nextTeacherLoginSeq()).thenReturn(7L);
        lenient().when(userRepository.existsByEmployeeNumber("EMP-777")).thenReturn(false);
        when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        assertThatCode(() -> service.createUser(req)).doesNotThrowAnyException();

        verify(userRepository).save(any());
    }

    @Test
    void adminWithoutTelegramIdIsAccepted() {
        CreateUserRequest req = req(UserRole.ADMIN, null, null);
        when(userRepository.nextTeacherLoginSeq()).thenReturn(3L);
        when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        assertThatCode(() -> service.createUser(req)).doesNotThrowAnyException();

        verify(userRepository).save(any());
    }

    @Test
    void studentWithTelegramIdIsAccepted() {
        CreateUserRequest req = req(UserRole.STUDENT, 123456789L, null);
        when(userRepository.nextStudentLoginSeq()).thenReturn(10L);
        lenient().when(userRepository.existsByTelegramId(123456789L)).thenReturn(false);
        when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        assertThatCode(() -> service.createUser(req)).doesNotThrowAnyException();

        verify(userRepository).save(any());
    }
}
