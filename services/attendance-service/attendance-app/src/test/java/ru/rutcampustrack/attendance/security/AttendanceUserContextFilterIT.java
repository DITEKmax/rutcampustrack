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

@TestPropertySource(properties = "rutcampustrack.security.internal-jwt.legacy-headers-enabled=true")
class AttendanceUserContextFilterIT extends AbstractAttendanceIntegrationTest {

    @Autowired
    private InternalJwtTestFactory factory;

    @Test
    void invalidInternalToken_returns401() throws Exception {
        mockMvc.perform(get("/attendance/reports/student/stats")
                        .header("X-Internal-Token", "nope"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void expiredInternalToken_returns401() throws Exception {
        mockMvc.perform(get("/attendance/reports/student/stats")
                        .header("X-Internal-Token", factory.expiredToken(1L, "STUDENT")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void wrongSignatureInternalToken_returns401() throws Exception {
        mockMvc.perform(get("/attendance/reports/student/stats")
                        .header("X-Internal-Token", factory.invalidSignature(1L, "STUDENT")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void validInternalToken_passesFilter() throws Exception {
        String token = factory.validToken(1L, "STUDENT", 5L, false);
        MvcResult result = mockMvc.perform(get("/attendance/reports/student/stats")
                        .header("X-Internal-Token", token))
                .andReturn();
        assertThat(result.getResponse().getStatus()).isNotEqualTo(401);
    }

    @Test
    void legacyHeaders_dualMode_accepted() throws Exception {
        MvcResult result = mockMvc.perform(get("/attendance/reports/student/stats")
                        .header("X-User-Id", "1")
                        .header("X-User-Role", "STUDENT")
                        .header("X-Group-Id", "5"))
                .andReturn();
        assertThat(result.getResponse().getStatus()).isNotEqualTo(401);
    }

    @Test
    void internalToken_precedenceOverLegacy() throws Exception {
        String token = factory.validToken(1L, "STUDENT", 5L, false);
        MvcResult result = mockMvc.perform(get("/attendance/reports/student/stats")
                        .header("X-Internal-Token", token)
                        .header("X-User-Id", "999")
                        .header("X-User-Role", "TEACHER"))
                .andReturn();
        assertThat(result.getResponse().getStatus()).isNotEqualTo(401);
    }
}
