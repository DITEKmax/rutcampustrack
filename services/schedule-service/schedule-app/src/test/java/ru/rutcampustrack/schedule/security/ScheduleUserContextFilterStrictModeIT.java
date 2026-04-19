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

@AutoConfigureMockMvc
@TestPropertySource(properties = "rutcampustrack.security.internal-jwt.legacy-headers-enabled=false")
class ScheduleUserContextFilterStrictModeIT extends AbstractScheduleIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private InternalJwtTestFactory factory;

    @Test
    void noHeaders_strictMode_returns401() throws Exception {
        mockMvc.perform(get("/schedule/items"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void legacyHeaders_strictMode_returns401() throws Exception {
        mockMvc.perform(get("/schedule/items")
                        .header("X-User-Id", "1")
                        .header("X-User-Role", "ADMIN"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void validInternalToken_strictMode_passes() throws Exception {
        String token = factory.validToken(1L, "ADMIN", null, false);
        MvcResult result = mockMvc.perform(get("/schedule/items")
                        .header("X-Internal-Token", token))
                .andReturn();
        assertThat(result.getResponse().getStatus()).isNotEqualTo(401);
    }
}
