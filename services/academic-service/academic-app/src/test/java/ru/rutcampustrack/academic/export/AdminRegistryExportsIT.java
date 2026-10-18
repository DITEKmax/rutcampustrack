package ru.rutcampustrack.academic.export;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import ru.rutcampustrack.academic.contract.dto.user.AdminAccessInput;
import ru.rutcampustrack.academic.contract.dto.user.CreateUserRequest;
import ru.rutcampustrack.academic.contract.enums.UserRole;
import ru.rutcampustrack.academic.identitycatalog.AuthIdentityDataClient;
import ru.rutcampustrack.academic.identityoperation.AcademicUserIdentityOperations;
import ru.rutcampustrack.academic.integration.AbstractAcademicIntegrationTest;
import ru.rutcampustrack.academic.integration.AcademicIdentityIntegrationFixture.SelectedActor;
import ru.rutcampustrack.auth.session.model.AuthRole;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.zip.*;

/**
 * Canonical HTTP + service/JPA/JDBC query; complete filtered rows, explicit safe schema, live
 * grants.
 */
@AutoConfigureMockMvc
class AdminRegistryExportsIT extends AbstractAcademicIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper mapper;
    @Autowired AcademicUserIdentityOperations identities;
    @Autowired AuthIdentityDataClient identityData;
    private SelectedActor actor;
    @MockitoBean RegistryDocumentClient documents;
    private String token;
    private long admin;
    private UUID sid;

    @BeforeEach
    void fixture() {
        var full = identityFixture.select(identityFixture.canonical().adminId(), AuthRole.ADMIN);
        admin =
                identityFixture
                        .seedUser(
                                new CreateUserRequest(
                                        "Registry",
                                        "Administrator",
                                        null,
                                        UserRole.ADMIN,
                                        null,
                                        null,
                                        null,
                                        new AdminAccessInput("FULL", List.of())),
                                full)
                        .userId();
        selectCurrentClaims();
    }

    private void selectCurrentClaims() {
        actor = identityFixture.select(admin, AuthRole.ADMIN);
        sid = actor.sessionId();
        token = actor.internalToken();
    }

    private void changeAccess(String mode, List<String> permissions) {
        var full = identityFixture.select(identityFixture.canonical().adminId(), AuthRole.ADMIN);
        try (var ignored = identityFixture.request(full)) {
            long version =
                    identityData
                            .readServiceIdentity(
                                    List.of(admin),
                                    System.nanoTime()
                                            + java.util.concurrent.TimeUnit.SECONDS.toNanos(3))
                            .get(admin)
                            .getAdminProfile()
                            .getVersion();
            identities.adminAccess(admin, new AdminAccessInput(mode, permissions), version, false);
        }
        selectCurrentClaims();
    }

    @Test
    void filteredDraftRegistryExportsAllTwentyThreeRowsInOrderRatherThanOnePage() throws Exception {
        for (int i = 0; i < 23; i++)
            jdbc.update(
                    "INSERT INTO"
                        + " groups(name,alphabetic_code,numeric_code,current_course,training_duration_years,duration_status,is_active)"
                        + " VALUES (?,?,?,1,4,'KNOWN',true)",
                    "ЭК" + (char) ('А' + i) + "-110",
                    "ЭК" + (char) ('А' + i),
                    "110");
        jdbc.update(
                "INSERT INTO"
                    + " groups(name,alphabetic_code,numeric_code,current_course,training_duration_years,duration_status,is_active)"
                    + " VALUES ('ЭКЩ-220','ЭКЩ','220',2,4,'KNOWN',true)");
        String request =
                "{\"status\":\"DRAFT\",\"search\":\"ЭК\",\"course\":1,\"studyMode\":\"FULL_TIME\",\"educationLevel\":\"BACHELOR\",\"sorts\":[{\"field\":\"name\",\"descending\":true}],\"format\":\"html\"}";
        String html =
                mvc.perform(
                                post("/academic/registry-exports/groups")
                                        .header("X-Internal-Token", token)
                                        .contentType(APPLICATION_JSON)
                                        .content(request))
                        .andExpect(status().isOk())
                        .andExpect(header().string("Cache-Control", "no-store"))
                        .andExpect(header().string("X-Export-Row-Count", "23"))
                        .andExpect(header().string("X-Export-Requires-Full", "false"))
                        .andReturn()
                        .getResponse()
                        .getContentAsString(StandardCharsets.UTF_8);
        assertThat(html)
                .contains("ЭКА-110", "ЭКЦ-110", "DRAFT", "FULL_TIME", "BACHELOR")
                .doesNotContain("ЭКЩ-220");
        assertThat(html.indexOf("ЭКЦ-110")).isLessThan(html.indexOf("ЭКА-110"));
        assertThat(
                        jdbc.queryForObject(
                                "SELECT count(*) FROM audit_log WHERE action='registry.export' AND"
                                        + " user_id=?",
                                Long.class,
                                admin))
                .isEqualTo(1);
    }

    @Test
    void
            fullAndLimitedFilesExcludePasswordsRecoveryAndLimitedTelegramIdentifierInEveryDirectFormat()
                    throws Exception {
        long user =
                identityFixture
                        .seedUser(
                                new CreateUserRequest(
                                        "Экспортный",
                                        "Студент",
                                        null,
                                        UserRole.TEACHER,
                                        null,
                                        "STAFF-FIXTURE",
                                        765432109L),
                                actor)
                        .userId();
        var credentials =
                identityFixture
                        .fixtureOwnerAuthJdbc()
                        .queryForMap(
                                "SELECT password_hash,initial_password FROM auth_identities WHERE"
                                        + " id=?",
                                user);
        String passwordHash = (String) credentials.get("password_hash");
        String initialPassword = (String) credentials.get("initial_password");
        assertThat(passwordHash).isNotBlank();
        assertThat(initialPassword).isNotBlank();
        for (String format : List.of("html", "docx", "xlsx")) {
            byte[] full = file(format);
            assertThat(text(full, format))
                    .contains("765432109", "Telegram ID", "STAFF-FIXTURE")
                    .doesNotContain(initialPassword, passwordHash, "recovery");
        }
        changeAccess("LIMITED", List.of("USERS_VIEW"));
        for (String format : List.of("html", "docx", "xlsx")) {
            byte[] limited = file(format);
            assertThat(text(limited, format))
                    .contains("Экспортный", "Привязан", "STAFF-FIXTURE")
                    .doesNotContain(
                            "765432109", "Telegram ID", initialPassword, passwordHash, "recovery");
        }
        mvc.perform(
                        get("/academic/registry-exports/users/formats")
                                .header("X-Internal-Token", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(5));
        changeAccess("LIMITED", List.of("GROUPS_VIEW"));
        mvc.perform(
                        get("/academic/registry-exports/users/formats")
                                .header("X-Internal-Token", token))
                .andExpect(status().isForbidden());
        mvc.perform(
                        post("/academic/registry-exports/users")
                                .header("X-Internal-Token", token)
                                .contentType(APPLICATION_JSON)
                                .content("{\"format\":\"html\"}"))
                .andExpect(status().isForbidden());
        String audit =
                jdbc.queryForObject(
                        "SELECT extras::text FROM audit_log WHERE action='registry.export' AND"
                                + " user_id=? LIMIT 1",
                        String.class,
                        admin);
        assertThat(audit)
                .contains("rowCount", "kind")
                .doesNotContain("Экспортный", "765432109", initialPassword, passwordHash);
    }

    @Test
    void forgedOrWrongSessionAsyncAuditLeaseCannotSuppressAuditOrGenerateFile() throws Exception {
        mvc.perform(
                        post("/academic/registry-exports/users")
                                .header("X-Internal-Token", token)
                                .header("X-Export-Job-Id", UUID.randomUUID())
                                .header("X-Export-Job-Lease", UUID.randomUUID())
                                .contentType(APPLICATION_JSON)
                                .content("{\"format\":\"html\"}"))
                .andExpect(status().isForbidden());
        assertThat(
                        jdbc.queryForObject(
                                "SELECT count(*) FROM audit_log WHERE action='registry.export' AND"
                                        + " user_id=?",
                                Long.class,
                                admin))
                .isZero();
    }

    @Test
    void unknownOptionalEnumFiltersRejectInsteadOfBroadeningTheExportAndNullRemainsOptional()
            throws Exception {
        for (String selector : List.of("status", "studyMode", "educationLevel")) {
            for (String value : List.of("\"UNKNOWN\"", "1", "\"active\"")) {
                mvc.perform(
                                post("/academic/registry-exports/groups")
                                        .header("X-Internal-Token", token)
                                        .contentType(APPLICATION_JSON)
                                        .content(
                                                "{\""
                                                        + selector
                                                        + "\":"
                                                        + value
                                                        + ",\"format\":\"html\"}"))
                        .andExpect(status().isBadRequest());
            }
        }
        mvc.perform(
                        post("/academic/registry-exports/users")
                                .header("X-Internal-Token", token)
                                .contentType(APPLICATION_JSON)
                                .content("{\"status\":\"UNKNOWN\",\"format\":\"html\"}"))
                .andExpect(status().isBadRequest());
        assertThat(
                        jdbc.queryForObject(
                                "SELECT count(*) FROM audit_log WHERE action='registry.export' AND"
                                        + " user_id=?",
                                Long.class,
                                admin))
                .isZero();
        mvc.perform(
                        post("/academic/registry-exports/groups")
                                .header("X-Internal-Token", token)
                                .contentType(APPLICATION_JSON)
                                .content(
                                        "{\"status\":null,\"studyMode\":null,\"educationLevel\":null,\"format\":\"html\"}"))
                .andExpect(status().isOk());
    }

    @Test
    void exactPrivateLeaseDefersAuditButChangedSelectorAndOtherSessionReject() throws Exception {
        UUID job = UUID.randomUUID(), lease = UUID.randomUUID();
        String selector =
                "{\"search\":null,\"role\":null,\"status\":null,\"roleStatus\":null,\"sorts\":null,\"format\":\"html\"}";
        jdbc.update(
                "INSERT INTO"
                    + " academic_export_jobs(job_id,user_id,session_id,request_id,binding_hash,identity,state,attempts,lease_id,lease_until,expires_at)"
                    + " VALUES (?,?,?,?,?,?::jsonb,'preparing',1,?,NOW()+interval '90"
                    + " seconds',NOW()+interval '5 minutes')",
                job,
                admin,
                sid,
                UUID.randomUUID(),
                "a".repeat(64),
                "{\"actor\":"
                        + mapper.writeValueAsString(actor.claims())
                        + ",\"report\":{\"kind\":\"ADMIN_USERS\",\"adminUsers\":"
                        + selector
                        + "}}",
                lease);
        mvc.perform(
                        post("/academic/registry-exports/users")
                                .header("X-Internal-Token", token)
                                .header("X-Export-Job-Id", job)
                                .header("X-Export-Job-Lease", lease)
                                .contentType(APPLICATION_JSON)
                                .content("{\"format\":\"html\"}"))
                .andExpect(status().isOk());
        assertThat(
                        jdbc.queryForObject(
                                "SELECT count(*) FROM audit_log WHERE action='registry.export' AND"
                                        + " user_id=?",
                                Long.class,
                                admin))
                .isZero();
        mvc.perform(
                        post("/academic/registry-exports/users")
                                .header("X-Internal-Token", token)
                                .header("X-Export-Job-Id", job)
                                .header("X-Export-Job-Lease", lease)
                                .contentType(APPLICATION_JSON)
                                .content("{\"search\":\"changed\",\"format\":\"html\"}"))
                .andExpect(status().isForbidden());
        jdbc.update(
                "UPDATE academic_export_jobs SET session_id=? WHERE job_id=?",
                UUID.randomUUID(),
                job);
        mvc.perform(
                        post("/academic/registry-exports/users")
                                .header("X-Internal-Token", token)
                                .header("X-Export-Job-Id", job)
                                .header("X-Export-Job-Lease", lease)
                                .contentType(APPLICATION_JSON)
                                .content("{\"format\":\"html\"}"))
                .andExpect(status().isForbidden());
    }

    private byte[] file(String format) throws Exception {
        return mvc.perform(
                        post("/academic/registry-exports/users")
                                .header("X-Internal-Token", token)
                                .contentType(APPLICATION_JSON)
                                .content(
                                        "{\"search\":\"Экспортный\",\"format\":\""
                                                + format
                                                + "\"}"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Export-Row-Count", "1"))
                .andReturn()
                .getResponse()
                .getContentAsByteArray();
    }

    private static String text(byte[] bytes, String format) throws Exception {
        if (format.equals("html")) return new String(bytes, StandardCharsets.UTF_8);
        StringBuilder text = new StringBuilder();
        try (var zip = new ZipInputStream(new ByteArrayInputStream(bytes))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null)
                if (entry.getName().endsWith(".xml"))
                    text.append(new String(zip.readAllBytes(), StandardCharsets.UTF_8));
        }
        return text.toString();
    }
}
