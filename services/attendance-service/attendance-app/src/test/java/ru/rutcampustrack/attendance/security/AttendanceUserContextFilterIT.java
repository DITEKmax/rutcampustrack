package ru.rutcampustrack.attendance.security;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MvcResult;
import ru.rutcampustrack.attendance.integration.AbstractAttendanceIntegrationTest;
import ru.rutcampustrack.shared.security.InternalJwtTestFactory;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@TestPropertySource(properties = "rutcampustrack.security.internal-jwt.legacy-headers-enabled=true")
class AttendanceUserContextFilterIT extends AbstractAttendanceIntegrationTest {

    private static final UUID SESSION_ID = UUID.fromString("cccccccc-cccc-4ccc-8ccc-cccccccccccc");

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
        Instant now = now();
        mockMvc.perform(get("/attendance/reports/student/stats")
                        .header("X-Internal-Token", factory.buildToken(1L, SESSION_ID, 1L, 1L,
                                "STUDENT", "ACTIVE", 5L, false, false,
                                now.minusSeconds(600), now.minusSeconds(300),
                                InternalJwtTestFactory.ISSUER, InternalJwtTestFactory.AUDIENCE,
                                "internal", factory.keyPair())))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void wrongSignatureInternalToken_returns401() throws Exception {
        InternalJwtTestFactory wrongSigner = new InternalJwtTestFactory();
        Instant now = now();
        mockMvc.perform(get("/attendance/reports/student/stats")
                        .header("X-Internal-Token", factory.buildToken(1L, SESSION_ID, 1L, 1L,
                                "STUDENT", "ACTIVE", 5L, false, false,
                                now.minusSeconds(1), now.plusSeconds(60),
                                InternalJwtTestFactory.ISSUER, InternalJwtTestFactory.AUDIENCE,
                                "internal", wrongSigner.keyPair())))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void validInternalToken_passesFilter() throws Exception {
        String token = validToken();
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
        String token = validToken();
        MvcResult result = mockMvc.perform(get("/attendance/reports/student/stats")
                        .header("X-Internal-Token", token)
                        .header("X-User-Id", "999")
                        .header("X-User-Role", "TEACHER"))
                .andReturn();
        assertThat(result.getResponse().getStatus()).isNotEqualTo(401);
    }

    private String validToken() {
        return factory.validToken(1L, SESSION_ID, 1L, 1L,
                "STUDENT", "ACTIVE", 5L, false, false);
    }

    private static Instant now() {
        return Instant.now().truncatedTo(ChronoUnit.SECONDS);
    }
}
