package ru.rutcampustrack.academic.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import ru.rutcampustrack.academic.admin.AdminAccessService;
import ru.rutcampustrack.academic.contract.dto.user.AdminAccessInput;
import ru.rutcampustrack.academic.contract.dto.user.CreateUserRequest;
import ru.rutcampustrack.academic.contract.dto.user.UserCreatedResponse;
import ru.rutcampustrack.academic.contract.enums.UserRole;
import ru.rutcampustrack.academic.integration.AcademicIdentityIntegrationFixture.SelectedActor;
import ru.rutcampustrack.academic.user.UserService;
import ru.rutcampustrack.auth.session.model.AuthRole;

import java.util.List;

/** Actual read DTO and durable live credential authority; no plaintext is printed in assertions. */
@AutoConfigureMockMvc(
        print = org.springframework.boot.test.autoconfigure.web.servlet.MockMvcPrint.NONE)
class InitialPasswordBoundaryIT extends AbstractAcademicIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired UserService users;
    @Autowired AdminAccessService adminAccess;

    @Test
    void initialPasswordReadUsesLiveGrantTargetProtectionAndNoStore() throws Exception {
        var control = identityFixture.select(identityFixture.canonical().adminId(), AuthRole.ADMIN);
        var admin = create(control, UserRole.ADMIN, "CredentialReader");
        var protectedAdmin = create(control, UserRole.ADMIN, "ProtectedControl");
        var student = create(control, UserRole.STUDENT, "CredentialTarget");
        long adminId = admin.getId(), studentId = student.getId();
        String studentPassword = initial(student);
        String protectedPassword = initial(protectedAdmin);
        String full = token(adminId);
        assertPassword(studentId, full, studentPassword, true);
        assertPassword(protectedAdmin.getId(), full, protectedPassword, true);

        // The canonical FULL control and separately provisioned FULL target both survive.
        limited(control, adminId, 1L, "USERS_VIEW");
        assertStaleDenied(studentId, full);
        assertPassword(studentId, token(adminId), studentPassword, false);

        limited(control, adminId, 2L, "USERS_VIEW", "USER_CREDENTIALS_MANAGE");
        String credentials = token(adminId);
        assertPassword(studentId, credentials, studentPassword, true);
        assertPassword(protectedAdmin.getId(), credentials, protectedPassword, false);

        // Validate the actual generated password and perform Auth's own credential transaction.
        identityFixture.changePassword(
                identityFixture.select(studentId, AuthRole.STUDENT),
                studentPassword,
                "RCT-synthetic-changed-password-2026!");
        identityFixture.catchUpCatalog();
        assertPassword(studentId, credentials, studentPassword, false);

        limited(control, adminId, 3L, "USERS_VIEW");
        assertStaleDenied(studentId, credentials);
        assertPassword(studentId, token(adminId), studentPassword, false);
    }

    private UserCreatedResponse create(SelectedActor control, UserRole role, String name) {
        try (var ignored = identityFixture.request(control)) {
            var created =
                    users.createUser(
                                    new CreateUserRequest(
                                            "Synthetic",
                                            name,
                                            null,
                                            role,
                                            role == UserRole.STUDENT
                                                    ? identityFixture.canonical().groupId()
                                                    : null,
                                            null,
                                            role == UserRole.STUDENT ? 620000000101L : null,
                                            role == UserRole.ADMIN
                                                    ? new AdminAccessInput("FULL", List.of())
                                                    : null))
                            .getContent();
            assertThat(created != null && created.getId() != null).as("created identity").isTrue();
            return created;
        }
    }

    private static String initial(UserCreatedResponse created) {
        String initial = created.getInitialPassword();
        assertThat(initial != null && !initial.isBlank())
                .as("generated initial credential")
                .isTrue();
        return initial;
    }

    private void limited(SelectedActor control, long admin, long version, String... grants) {
        try (var ignored = identityFixture.request(control)) {
            var updated =
                    adminAccess.put(
                            admin, new AdminAccessInput("LIMITED", List.of(grants)), version);
            assertThat(updated.version()).isEqualTo(version + 1);
            assertThat(updated.permissions()).containsExactlyInAnyOrder(grants);
        }
    }

    private String token(long admin) {
        return identityFixture.select(admin, AuthRole.ADMIN).internalToken();
    }

    private void assertStaleDenied(long target, String token) throws Exception {
        // Real permission operations advance rolesVersion, invalidating the old signed identity.
        mvc.perform(get("/academic/users/{id}", target).header("X-Internal-Token", token))
                .andExpect(status().isUnauthorized());
    }

    private void assertPassword(long target, String token, String marker, boolean expected)
            throws Exception {
        var response =
                mvc.perform(get("/academic/users/{id}", target).header("X-Internal-Token", token))
                        .andExpect(status().isOk())
                        .andReturn()
                        .getResponse();
        assertThat(response.getHeader("Cache-Control")).contains("no-store");
        var field = mapper.readTree(response.getContentAsString()).path("initialPassword");
        assertThat(field.isTextual() && field.asText().equals(marker))
                .as("initial-password visibility")
                .isEqualTo(expected);
        assertThat(response.getContentAsString().contains(marker))
                .as("initial-password response inclusion")
                .isEqualTo(expected);
    }
}
