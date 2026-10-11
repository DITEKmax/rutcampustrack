package ru.rutcampustrack.academic.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import ru.rutcampustrack.shared.security.InternalJwtTestFactory;
import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Actual read DTO and durable live credential authority; no plaintext is printed in assertions. */
@AutoConfigureMockMvc
class InitialPasswordBoundaryIT extends AbstractAcademicIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper mapper;
    @Autowired InternalJwtTestFactory jwt;

    @Test
    void initialPasswordReadUsesLiveGrantTargetProtectionAndNoStore() throws Exception {
        long admin = jdbc.queryForObject("SELECT id FROM users WHERE login='admin'", Long.class);
        long student = jdbc.queryForObject("SELECT id FROM users WHERE login='student'", Long.class);
        String marker = "RCT_SYNTHETIC_INITIAL_PASSWORD_SENTINEL";
        jdbc.update("UPDATE users SET initial_password=?,password_changed=FALSE WHERE id IN (?,?)", marker, student, admin);
        String full = token(admin);
        assertPassword(student, full, marker, true);
        assertPassword(admin, full, marker, true);

        limited(admin, "USERS_VIEW");
        mvc.perform(get("/academic/users/{id}", student).header("X-Internal-Token", full))
                .andExpect(status().isForbidden());
        assertPassword(student, token(admin), marker, false);

        limited(admin, "USERS_VIEW", "USER_CREDENTIALS_MANAGE");
        String credentials = token(admin);
        assertPassword(student, credentials, marker, true);
        assertPassword(admin, credentials, marker, false);
        // Auth's real password-change transaction has a separate HTTP+DB oracle.
        // Here its documented durable post-change shape exercises the consumer DTO.
        jdbc.update("UPDATE users SET initial_password=NULL,password_changed=TRUE WHERE id=?", student);
        assertPassword(student, credentials, marker, false);
        limited(admin, "USERS_VIEW");
        mvc.perform(get("/academic/users/{id}", student).header("X-Internal-Token", credentials))
                .andExpect(status().isForbidden());
        assertPassword(student, token(admin), marker, false);
    }

    private void limited(long admin, String... grants) {
        jdbc.update("UPDATE admin_access_profiles SET mode='LIMITED',permissions=?::text[],version=version+1 WHERE user_id=?",
                "{" + String.join(",", grants) + "}", admin);
    }

    private String token(long admin) {
        UUID session = AcademicTestAdminRequest.selectedSession(jdbc, admin, 1L);
        long rolesVersion = jdbc.queryForObject("SELECT roles_version FROM users WHERE id=?", Long.class, admin);
        return jwt.validToken(admin, session, 1L, rolesVersion, "ADMIN", "ACTIVE", null, false, false);
    }

    private void assertPassword(long target, String token, String marker, boolean expected) throws Exception {
        var response = mvc.perform(get("/academic/users/{id}", target).header("X-Internal-Token", token))
                .andExpect(status().isOk()).andReturn().getResponse();
        assertThat(response.getHeader("Cache-Control")).contains("no-store");
        var field = mapper.readTree(response.getContentAsString()).path("initialPassword");
        assertThat(field.isTextual() && field.asText().equals(marker)).as("initial-password visibility").isEqualTo(expected);
        assertThat(response.getContentAsString().contains(marker)).as("initial-password response inclusion").isEqualTo(expected);
    }
}
