package ru.rutcampustrack.attendance.security;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.TestPropertySource;

import ru.rutcampustrack.attendance.integration.AbstractAttendanceIntegrationTest;
import ru.rutcampustrack.shared.security.InternalJwtTestFactory;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

@TestPropertySource(properties = "rutcampustrack.security.internal-jwt.legacy-headers-enabled=true")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class AttendanceUserContextFilterIT extends AbstractAttendanceIntegrationTest {
    @org.springframework.test.context.DynamicPropertySource
    static void auditIsolation(org.springframework.test.context.DynamicPropertyRegistry registry) {
        ru.rutcampustrack.attendance.integration.AuditTestIsolation.register(
                registry, "attendanceusercontextfilterit", 1);
    }

    private static final UUID SESSION_ID = UUID.fromString("cccccccc-cccc-4ccc-8ccc-cccccccccccc");

    @Autowired private InternalJwtTestFactory factory;

    @Test
    void invalidInternalToken_returns401() throws Exception {
        mockMvc.perform(get("/attendance/reports/student/stats").header("X-Internal-Token", "nope"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void expiredInternalToken_returns401() throws Exception {
        Instant now = now();
        mockMvc.perform(
                        get("/attendance/reports/student/stats")
                                .header(
                                        "X-Internal-Token",
                                        factory.buildToken(
                                                1L,
                                                SESSION_ID,
                                                1L,
                                                1L,
                                                "STUDENT",
                                                "ACTIVE",
                                                5L,
                                                false,
                                                false,
                                                now.minusSeconds(600),
                                                now.minusSeconds(300),
                                                InternalJwtTestFactory.ISSUER,
                                                InternalJwtTestFactory.AUDIENCE,
                                                "internal",
                                                factory.keyPair())))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void wrongSignatureInternalToken_returns401() throws Exception {
        InternalJwtTestFactory wrongSigner = new InternalJwtTestFactory();
        Instant now = now();
        mockMvc.perform(
                        get("/attendance/reports/student/stats")
                                .header(
                                        "X-Internal-Token",
                                        factory.buildToken(
                                                1L,
                                                SESSION_ID,
                                                1L,
                                                1L,
                                                "STUDENT",
                                                "ACTIVE",
                                                5L,
                                                false,
                                                false,
                                                now.minusSeconds(1),
                                                now.plusSeconds(60),
                                                InternalJwtTestFactory.ISSUER,
                                                InternalJwtTestFactory.AUDIENCE,
                                                "internal",
                                                wrongSigner.keyPair())))
                .andExpect(status().isUnauthorized());
    }

    @Autowired private org.springframework.boot.test.web.client.TestRestTemplate http;

    @Test
    void validInternalToken_passesFilter() throws Exception {
        var headers = new org.springframework.http.HttpHeaders();
        headers.set("X-Internal-Token", validToken());
        AttendanceIdentityRecordFixture.assertOwnRecords(
                http, mongoTemplate, scheduleGrpcClient, semesterCacheService, headers);
    }

    @Test
    void legacyHeaders_dualMode_accepted() throws Exception {
        var headers = new org.springframework.http.HttpHeaders();
        headers.set("X-User-Id", "1");
        headers.set("X-User-Role", "STUDENT");
        headers.set("X-Group-Id", "5");
        AttendanceIdentityRecordFixture.assertOwnRecords(
                http, mongoTemplate, scheduleGrpcClient, semesterCacheService, headers);
    }

    @Test
    void internalToken_precedenceOverLegacy() throws Exception {
        var headers = new org.springframework.http.HttpHeaders();
        headers.set("X-Internal-Token", validToken());
        headers.set("X-User-Id", "999");
        headers.set("X-User-Role", "TEACHER");
        headers.set("X-Group-Id", "88");
        AttendanceIdentityRecordFixture.assertOwnRecords(
                http, mongoTemplate, scheduleGrpcClient, semesterCacheService, headers);
    }

    private String validToken() {
        return factory.validToken(1L, SESSION_ID, 1L, 1L, "STUDENT", "ACTIVE", 5L, false, false);
    }

    private static Instant now() {
        return Instant.now().truncatedTo(ChronoUnit.SECONDS);
    }
}
