package ru.rutcampustrack.academic.user;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import io.grpc.Status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import ru.rutcampustrack.academic.admin.AcademicAdminAccess;
import ru.rutcampustrack.academic.admin.AcademicAuthDataCommands;
import ru.rutcampustrack.academic.contract.dto.user.RoleGrantViewResponse;
import ru.rutcampustrack.academic.contract.enums.AccountStatus;
import ru.rutcampustrack.academic.contract.enums.UserRole;
import ru.rutcampustrack.academic.entity.User;
import ru.rutcampustrack.academic.repository.UserRoleGrantReader;
import ru.rutcampustrack.academic.security.RequestContext;
import ru.rutcampustrack.shared.security.admin.AdminAccessProfile;
import ru.rutcampustrack.shared.security.admin.AdminPermission;

import java.time.OffsetDateTime;
import java.util.List;

@ExtendWith(MockitoExtension.class)
class UserAssemblerTest {
    @Mock UserRoleGrantReader grants;
    @Mock RequestContext context;
    @Mock AcademicAdminAccess adminAccess;
    @Mock AcademicAuthDataCommands authCommands;
    UserAssembler assembler;

    @BeforeEach
    void currentFullAdmin() {
        assembler = new UserAssembler(grants);
        ReflectionTestUtils.setField(assembler, "context", context);
        ReflectionTestUtils.setField(assembler, "adminAccess", adminAccess);
        ReflectionTestUtils.setField(assembler, "authCommands", authCommands);
        when(context.getRole()).thenReturn(UserRole.ADMIN);
        when(adminAccess.current())
                .thenReturn(
                        new AdminAccessProfile(99, true, "FULL", AdminPermission.allCodes(), 1));
    }

    @Test
    void archivedDetailsRetainHistoryAndNeverRequestInitialCredentials() {
        var user = user(AccountStatus.ARCHIVED);
        var history =
                List.of(
                        new RoleGrantViewResponse(
                                "STUDENT",
                                "ARCHIVED",
                                7L,
                                "Own group",
                                false,
                                true,
                                List.of("ARCHIVED"),
                                false,
                                "Archived role"));
        var response = assembler.toResponse(user, true, history);
        assertThat(response.getId()).isEqualTo(user.getId());
        assertThat(response.getLogin()).isEqualTo(user.getLogin());
        assertThat(response.getStatus()).isEqualTo(AccountStatus.ARCHIVED);
        assertThat(response.getRoles()).containsExactlyElementsOf(history);
        assertThat(response.getAvatarId()).isEqualTo(user.getAvatarId());
        assertThat(response.getCreatedAt()).isEqualTo(user.getCreatedAt());
        assertThat(response.getInitialPassword()).isNull();
        verifyNoInteractions(authCommands);
    }

    @Test
    void nonArchivedStatusesRetainCurrentAuthCredentialEnrichment() {
        when(authCommands.initialPassword(8)).thenReturn("synthetic-initial-material");
        for (var status :
                List.of(AccountStatus.ACTIVE, AccountStatus.EXPELLED, AccountStatus.SUSPENDED)) {
            var response = assembler.toResponse(user(status), true, List.of());
            assertThat(response.getStatus()).isEqualTo(status);
            assertThat(response.getInitialPassword()).isEqualTo("synthetic-initial-material");
        }
        verify(authCommands, times(3)).initialPassword(8);
    }

    @Test
    void changedPasswordOrNonCredentialProjectionDoesNotRequestInitialMaterial() {
        var user = user(AccountStatus.ACTIVE);
        user.setPasswordChanged(true);
        assertThat(assembler.toResponse(user, true, List.of()).getInitialPassword()).isNull();
        user.setPasswordChanged(false);
        assertThat(assembler.toResponse(user, false, List.of()).getInitialPassword()).isNull();
        verifyNoInteractions(authCommands);
    }

    @Test
    void activeCredentialFailureIsNotConvertedToSuccessfulDetail() {
        var failure = Status.FAILED_PRECONDITION.asRuntimeException();
        when(authCommands.initialPassword(8)).thenThrow(failure);
        assertThatThrownBy(() -> assembler.toResponse(user(AccountStatus.ACTIVE), true, List.of()))
                .isSameAs(failure);
    }

    private User user(AccountStatus status) {
        var user = new User();
        user.setId(8L);
        user.setLogin("owned-account");
        user.setFirstName("Own");
        user.setLastName("Account");
        user.setRole(UserRole.STUDENT);
        user.setStatus(status);
        user.setAvatarId("avatar_03");
        user.setCreatedAt(OffsetDateTime.parse("2026-01-01T00:00:00Z"));
        return user;
    }
}
