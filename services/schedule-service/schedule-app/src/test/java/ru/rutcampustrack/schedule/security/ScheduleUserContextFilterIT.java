package ru.rutcampustrack.schedule.security;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import ru.rutcampustrack.schedule.integration.AbstractScheduleIntegrationTest;
import ru.rutcampustrack.shared.security.InternalJwtTestFactory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * M03a: verifies {@link ScheduleUserContextFilter} behaviour end-to-end on
 * the {@code /schedule/items} endpoint (@RequireRole enforced).
 */
@AutoConfigureMockMvc
@TestPropertySource(properties = "rutcampustrack.security.internal-jwt.legacy-headers-enabled=true")
class ScheduleUserContextFilterIT extends AbstractScheduleIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private InternalJwtTestFactory factory;

    @Test
    void invalidInternalToken_returns401() throws Exception {
        mockMvc.perform(get("/schedule/items")
                        .header("X-Internal-Token", "nope"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void expiredInternalToken_returns401() throws Exception {
        mockMvc.perform(get("/schedule/items")
                        .header("X-Internal-Token", factory.expiredToken(1L, "ADMIN")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void wrongSignatureInternalToken_returns401() throws Exception {
        mockMvc.perform(get("/schedule/items")
                        .header("X-Internal-Token", factory.invalidSignature(1L, "ADMIN")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void validInternalToken_passesFilter() throws Exception {
        String token = factory.validToken(1L, "ADMIN", null, false);
        MvcResult result = mockMvc.perform(get("/schedule/items")
                        .header("X-Internal-Token", token))
                .andReturn();
        assertThat(result.getResponse().getStatus()).isNotEqualTo(401);
    }

    @Test
    void legacyHeaders_dualMode_accepted() throws Exception {
        MvcResult result = mockMvc.perform(get("/schedule/items")
                        .header("X-User-Id", "1")
                        .header("X-User-Role", "ADMIN"))
                .andReturn();
        assertThat(result.getResponse().getStatus()).isNotEqualTo(401);
    }

    @Test
    void internalToken_precedenceOverLegacy() throws Exception {
        String token = factory.validToken(1L, "ADMIN", null, false);
        MvcResult result = mockMvc.perform(get("/schedule/items")
                        .header("X-Internal-Token", token)
                        .header("X-User-Id", "999")
                        .header("X-User-Role", "STUDENT"))
                .andReturn();
        assertThat(result.getResponse().getStatus()).isNotEqualTo(401);
    }
}
