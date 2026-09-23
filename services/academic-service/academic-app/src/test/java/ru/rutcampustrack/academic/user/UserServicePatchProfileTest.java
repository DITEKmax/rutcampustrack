package ru.rutcampustrack.academic.user;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cache.CacheManager;
import org.springframework.context.ApplicationEventPublisher;
import ru.rutcampustrack.academic.contract.dto.user.PatchUserRequest;
import ru.rutcampustrack.academic.contract.dto.user.RoleGrantViewResponse;
import ru.rutcampustrack.academic.contract.enums.AccountStatus;
import ru.rutcampustrack.academic.contract.enums.UserRole;
import ru.rutcampustrack.academic.entity.User;
import ru.rutcampustrack.academic.exception.BadRequestException;
import ru.rutcampustrack.academic.exception.ConflictException;
import ru.rutcampustrack.academic.group.GroupHeadmanAssignmentService;
import ru.rutcampustrack.academic.repository.GroupHistoryCoverageRepository;
import ru.rutcampustrack.academic.repository.GroupRepository;
import ru.rutcampustrack.academic.repository.HeadmanAssistantRepository;
import ru.rutcampustrack.academic.repository.SemesterRepository;
import ru.rutcampustrack.academic.repository.StudentGroupHistoryRepository;
import ru.rutcampustrack.academic.repository.UserRepository;
import ru.rutcampustrack.academic.repository.UserRoleGrantReader;
import ru.rutcampustrack.academic.repository.UserRoleGrantWriter;
import ru.rutcampustrack.academic.security.RequestContext;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.mock;

@ExtendWith(MockitoExtension.class)
class UserServicePatchProfileTest {

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
    @Mock private UserRoleGrantWriter roleGrantWriter;
    @Mock private UserRoleGrantReader roleGrantReader;
    @Mock private GroupHeadmanAssignmentService headmanAssignmentService;

    @InjectMocks
    private UserService service;

    @Test
    void profilePatchTrimsNamesClearsBlankMiddleNameAndLeavesRoleGrantsUntouched() {
        User user = profileUser();
        when(user.getGroupId()).thenReturn(null);
        when(user.getStatus()).thenReturn(AccountStatus.ACTIVE);
        when(user.isHeadman()).thenReturn(false);
        when(userRepository.findByIdForUpdate(42L)).thenReturn(Optional.of(user));
        when(userRepository.save(user)).thenReturn(user);

        service.patchUser(42L, request("  Иванов ", " Иван ", "  ", null));

        verify(user).setLastName("Иванов");
        verify(user).setFirstName("Иван");
        verify(user).setMiddleName(null);
        verify(roleGrantWriter, never()).synchronize(any());
        verify(userRepository, never()).existsByEmployeeNumber(anyString());
    }

    @Test
    void teacherGrantOnMultiRoleAccountPreventsClearingEmployeeNumber() {
        User user = profileUser();
        when(user.getRole()).thenReturn(UserRole.STUDENT);
        when(userRepository.findByIdForUpdate(42L)).thenReturn(Optional.of(user));
        when(roleGrantReader.findByUserId(42L)).thenReturn(List.of(teacherGrant()));

        assertThatThrownBy(() -> service.patchUser(42L, request(null, null, null, "  ")))
                .isInstanceOfSatisfying(BadRequestException.class, error ->
                        org.assertj.core.api.Assertions.assertThat(error.getField()).isEqualTo("employeeNumber"));

        verify(userRepository, never()).save(any());
        verify(userRepository, never()).existsByEmployeeNumber(anyString());
    }

    @Test
    void unchangedEmployeeNumberDoesNotSelfConflict() {
        User user = profileUser();
        when(user.getRole()).thenReturn(UserRole.STUDENT);
        when(user.getEmployeeNumber()).thenReturn("EMP-001");
        when(user.getGroupId()).thenReturn(null);
        when(user.getStatus()).thenReturn(AccountStatus.ACTIVE);
        when(user.isHeadman()).thenReturn(false);
        when(userRepository.findByIdForUpdate(42L)).thenReturn(Optional.of(user));
        when(roleGrantReader.findByUserId(42L)).thenReturn(List.of(teacherGrant()));
        when(userRepository.save(user)).thenReturn(user);

        service.patchUser(42L, request(null, null, null, " EMP-001 "));

        verify(userRepository, never()).existsByEmployeeNumber(anyString());
        verify(user).setEmployeeNumber("EMP-001");
    }

    @Test
    void duplicateEmployeeNumberReturnsFieldSpecificConflict() {
        User user = profileUser();
        when(user.getRole()).thenReturn(UserRole.STUDENT);
        when(user.getEmployeeNumber()).thenReturn("EMP-001");
        when(userRepository.findByIdForUpdate(42L)).thenReturn(Optional.of(user));
        when(roleGrantReader.findByUserId(42L)).thenReturn(List.of(teacherGrant()));
        when(userRepository.existsByEmployeeNumber("EMP-002")).thenReturn(true);

        assertThatThrownBy(() -> service.patchUser(42L, request(null, null, null, "EMP-002")))
                .isInstanceOfSatisfying(ConflictException.class, error ->
                        org.assertj.core.api.Assertions.assertThat(error.getField()).isEqualTo("employeeNumber"));

        verify(userRepository, never()).save(any());
    }

    @Test
    void blankRequiredNameIsRejectedBeforeSave() {
        User user = profileUser();
        when(userRepository.findByIdForUpdate(42L)).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> service.patchUser(42L, request("  ", null, null, null)))
                .isInstanceOfSatisfying(BadRequestException.class, error ->
                        org.assertj.core.api.Assertions.assertThat(error.getField()).isEqualTo("lastName"));

        verify(userRepository, never()).save(any());
    }

    private User profileUser() {
        // Each scenario stubs only getters it reaches so Mockito strictness
        // catches accidental new reads in the service path.
        return mock(User.class);
    }

    private static PatchUserRequest request(String lastName, String firstName, String middleName, String employeeNumber) {
        return new PatchUserRequest(lastName, firstName, middleName, null, null, employeeNumber, null, null);
    }

    private static RoleGrantViewResponse teacherGrant() {
        return new RoleGrantViewResponse("TEACHER", "ACTIVE", null, null,
                true, false, List.of("ACTIVE", "DISMISSED", "SUSPENDED"), true, null);
    }
}
