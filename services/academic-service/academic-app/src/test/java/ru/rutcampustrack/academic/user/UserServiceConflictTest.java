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
import ru.rutcampustrack.academic.exception.ConflictException;
import ru.rutcampustrack.academic.repository.HeadmanAssistantRepository;
import ru.rutcampustrack.academic.repository.StudentGroupHistoryRepository;
import ru.rutcampustrack.academic.repository.UserRepository;
import ru.rutcampustrack.academic.security.RequestContext;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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

    @InjectMocks
    private UserService service;

    private CreateUserRequest studentRequest() {
        return new CreateUserRequest(
                "Иванов", "Иван", "Иванович",
                UserRole.STUDENT, 1L, null, null
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
