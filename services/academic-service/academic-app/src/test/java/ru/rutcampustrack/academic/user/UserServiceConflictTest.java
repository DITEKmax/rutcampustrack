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
import ru.rutcampustrack.academic.exception.ConflictException;
import ru.rutcampustrack.academic.repository.GroupHistoryCoverageRepository;
import ru.rutcampustrack.academic.repository.GroupRepository;
import ru.rutcampustrack.academic.repository.HeadmanAssistantRepository;
import ru.rutcampustrack.academic.repository.SemesterRepository;
import ru.rutcampustrack.academic.repository.StudentGroupHistoryRepository;
import ru.rutcampustrack.academic.repository.UserRepository;
import ru.rutcampustrack.academic.security.RequestContext;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Unit test for UserService.createUser pre-check behavior (BUG-006 / D-07).
 *
 * Verifies that explicit existsByXxx checks throw ConflictException with the
 * correct field name BEFORE a save() call is attempted.
 */
@ExtendWith(MockitoExtension.class)
class UserServiceConflictTest {

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

    private CreateUserRequest studentRequest() {
        // telegramId is mandatory for STUDENT since BUG-006-3; use a placeholder
        // value so this fixture exercises the happy path.
        return new CreateUserRequest(
                "Иванов", "Иван", "Иванович",
                UserRole.STUDENT, 1L, null, 123456789L
        );
    }

    @Test
    void existingTelegramIdThrowsConflictWithFieldTelegramId() {
        // Request with telegramId that already exists
        CreateUserRequest req = new CreateUserRequest(
                "Петров", "Пётр", null,
                UserRole.STUDENT, 1L, null, 55555L
        );
        when(userRepository.nextStudentLoginSeq()).thenReturn(42L);
        when(userRepository.existsByTelegramId(55555L)).thenReturn(true);

        assertThatThrownBy(() -> service.createUser(req))
                .isInstanceOfSatisfying(ConflictException.class, ex -> {
                    assertThat(ex.getField()).isEqualTo("telegramId");
                });

        verify(userRepository, never()).save(any());
    }

    @Test
    void existingEmployeeNumberThrowsConflictWithFieldEmployeeNumber() {
        CreateUserRequest req = new CreateUserRequest(
                "Учителев", "Учитель", null,
                UserRole.TEACHER, null, "EMP-001", null
        );
        when(userRepository.nextTeacherLoginSeq()).thenReturn(7L);
        when(userRepository.existsByEmployeeNumber("EMP-001")).thenReturn(true);

        assertThatThrownBy(() -> service.createUser(req))
                .isInstanceOfSatisfying(ConflictException.class, ex -> {
                    assertThat(ex.getField()).isEqualTo("employeeNumber");
                });

        verify(userRepository, never()).save(any());
    }

    @Test
    void noConflictWhenNoExistingValues() {
        CreateUserRequest req = studentRequest();
        when(userRepository.nextStudentLoginSeq()).thenReturn(10L);
        when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.createUser(req);

        verify(userRepository).save(any());
    }
}
