package ru.rutcampustrack.attendance.integration;

import org.junit.jupiter.api.Test;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Verifies security AOP enforcement:
 * - STUDENT role allowed on STUDENT-only endpoint
 * - TEACHER role rejected with 403
 * - No headers rejected with 403
 */
class SecuritySmokeTest extends AbstractAttendanceIntegrationTest {

    @Test
    void studentEndpoint_withStudentRole_returns200() throws Exception {
        mockMvc.perform(get("/attendance/health-check")
                        .header("X-User-Id", "1")
                        .header("X-User-Role", "STUDENT"))
                .andExpect(status().isOk());
    }

    @Test
    void studentEndpoint_withTeacherRole_returns403() throws Exception {
        mockMvc.perform(get("/attendance/health-check")
                        .header("X-User-Id", "1")
                        .header("X-User-Role", "TEACHER"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));
    }

    @Test
    void studentEndpoint_withNoHeaders_returns403() throws Exception {
        mockMvc.perform(get("/attendance/health-check"))
                .andExpect(status().isForbidden());
    }
}
