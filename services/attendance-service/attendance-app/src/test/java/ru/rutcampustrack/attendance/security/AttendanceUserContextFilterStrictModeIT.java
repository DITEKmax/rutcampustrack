package ru.rutcampustrack.attendance.security;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MvcResult;
import ru.rutcampustrack.attendance.integration.AbstractAttendanceIntegrationTest;
import ru.rutcampustrack.shared.security.InternalJwtTestFactory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@TestPropertySource(properties = "rutcampustrack.security.internal-jwt.legacy-headers-enabled=false")
class AttendanceUserContextFilterStrictModeIT extends AbstractAttendanceIntegrationTest {

    @Autowired
    private InternalJwtTestFactory factory;

    @Test
    void noHeaders_strictMode_returns401() throws Exception {
        mockMvc.perform(get("/attendance/reports/student/stats"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void legacyHeaders_strictMode_returns401() throws Exception {
        mockMvc.perform(get("/attendance/reports/student/stats")
                        .header("X-User-Id", "1")
                        .header("X-User-Role", "STUDENT"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void validInternalToken_strictMode_passes() throws Exception {
        String token = factory.validToken(1L, "STUDENT", 5L, false);
        MvcResult result = mockMvc.perform(get("/attendance/reports/student/stats")
                        .header("X-Internal-Token", token))
                .andReturn();
        assertThat(result.getResponse().getStatus()).isNotEqualTo(401);
    }
}
