package ru.rutcampustrack.attendance.security;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.TestPropertySource;

import ru.rutcampustrack.attendance.integration.AbstractAttendanceIntegrationTest;
import ru.rutcampustrack.shared.security.InternalJwtTestFactory;

import java.util.UUID;

@TestPropertySource(
        properties = "rutcampustrack.security.internal-jwt.legacy-headers-enabled=false")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class AttendanceUserContextFilterStrictModeIT extends AbstractAttendanceIntegrationTest {
    @org.springframework.test.context.DynamicPropertySource
    static void auditIsolation(org.springframework.test.context.DynamicPropertyRegistry registry) {
        ru.rutcampustrack.attendance.integration.AuditTestIsolation.register(
                registry, "attendanceusercontextfilterstrictmodeit", 2);
    }

    private static final UUID SESSION_ID = UUID.fromString("dddddddd-dddd-4ddd-8ddd-dddddddddddd");

    @Autowired private InternalJwtTestFactory factory;

    @Test
    void noHeaders_strictMode_returns401() throws Exception {
        mockMvc.perform(get("/attendance/reports/student/stats"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void legacyHeaders_strictMode_returns401() throws Exception {
        mockMvc.perform(
                        get("/attendance/reports/student/stats")
                                .header("X-User-Id", "1")
                                .header("X-User-Role", "STUDENT"))
                .andExpect(status().isUnauthorized());
    }

    @Autowired private org.springframework.boot.test.web.client.TestRestTemplate http;

    @Test
    void validInternalToken_strictMode_passes() throws Exception {
        var headers = new org.springframework.http.HttpHeaders();
        headers.set(
                "X-Internal-Token",
                factory.validToken(1L, SESSION_ID, 1L, 1L, "STUDENT", "ACTIVE", 5L, false, false));
        AttendanceIdentityRecordFixture.assertOwnRecords(
                http, mongoTemplate, scheduleGrpcClient, semesterCacheService, headers);
    }
}
