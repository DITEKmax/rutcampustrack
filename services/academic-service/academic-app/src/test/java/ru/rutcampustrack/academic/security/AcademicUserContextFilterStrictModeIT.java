package ru.rutcampustrack.academic.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import ru.rutcampustrack.academic.integration.AbstractAcademicIntegrationTest;
import ru.rutcampustrack.auth.session.model.AuthRole;
import ru.rutcampustrack.shared.security.InternalJwtProperties;

/**
 * M03a: verifies strict mode (legacy-headers-enabled=false) — X-User-* headers are rejected, only a
 * valid X-Internal-Token passes.
 *
 * <p>This mirrors the final state of M03a after the strict toggle commit.
 */
@AutoConfigureMockMvc(
        print = org.springframework.boot.test.autoconfigure.web.servlet.MockMvcPrint.NONE)
@TestPropertySource(properties = "rct.fixture.legacy-headers-enabled=false")
class AcademicUserContextFilterStrictModeIT extends AbstractAcademicIntegrationTest {

    @Autowired private MockMvc mockMvc;

    @Autowired private InternalJwtProperties properties;

    @BeforeEach
    void strictModeIsEffective() {
        assertThat(properties.legacyHeadersEnabled()).isFalse();
    }

    @Test
    void noHeaders_strictMode_returns401() throws Exception {
        mockMvc.perform(get("/academic/users")).andExpect(status().isUnauthorized());
    }

    @Test
    void legacyHeaders_strictMode_returns401() throws Exception {
        mockMvc.perform(
                        get("/academic/users")
                                .header("X-User-Id", "1")
                                .header("X-User-Role", "ADMIN"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void validInternalToken_strictMode_passes() throws Exception {
        String token =
                identityFixture
                        .select(identityFixture.canonical().adminId(), AuthRole.ADMIN)
                        .internalToken();
        MvcResult result =
                mockMvc.perform(get("/academic/users").header("X-Internal-Token", token))
                        .andReturn();
        assertThat(result.getResponse().getStatus()).isNotEqualTo(401);
    }
}
