package ru.rutcampustrack.academic.security;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import ru.rutcampustrack.academic.integration.AbstractAcademicIntegrationTest;
import ru.rutcampustrack.shared.security.InternalJwtTestFactory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * M03a: verifies strict mode (legacy-headers-enabled=false) — X-User-* headers
 * are rejected, only a valid X-Internal-Token passes.
 *
 * This mirrors the final state of M03a after the strict toggle commit.
 */
@AutoConfigureMockMvc
@TestPropertySource(properties = "rutcampustrack.security.internal-jwt.legacy-headers-enabled=false")
class AcademicUserContextFilterStrictModeIT extends AbstractAcademicIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private InternalJwtTestFactory factory;

    @Test
    void noHeaders_strictMode_returns401() throws Exception {
        mockMvc.perform(get("/academic/users"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void legacyHeaders_strictMode_returns401() throws Exception {
        mockMvc.perform(get("/academic/users")
                        .header("X-User-Id", "1")
                        .header("X-User-Role", "ADMIN"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void validInternalToken_strictMode_passes() throws Exception {
        String token = factory.validToken(1L, "ADMIN", null, false);
        MvcResult result = mockMvc.perform(get("/academic/users")
                        .header("X-Internal-Token", token))
                .andReturn();
        assertThat(result.getResponse().getStatus()).isNotEqualTo(401);
    }
}
