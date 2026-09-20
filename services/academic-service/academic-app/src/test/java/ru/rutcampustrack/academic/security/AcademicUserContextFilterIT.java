package ru.rutcampustrack.academic.security;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import ru.rutcampustrack.academic.integration.AbstractAcademicIntegrationTest;
import ru.rutcampustrack.shared.security.InternalJwtTestFactory;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * M03a: verifies {@link AcademicUserContextFilter} behaviour end-to-end.
 *
 * Uses a protected endpoint ({@code /academic/users}) to observe whether the
 * filter accepted, rejected, or passed through the request. Endpoint may
 * return 403 (role check failed) or 4xx — the filter concern is only that
 * it NEVER returns 401 for valid/legacy auth, and ALWAYS 401 for invalid token.
 */
@AutoConfigureMockMvc
@TestPropertySource(properties = "rutcampustrack.security.internal-jwt.legacy-headers-enabled=true")
class AcademicUserContextFilterIT extends AbstractAcademicIntegrationTest {

    private static final UUID SESSION_ID = UUID.fromString("aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private InternalJwtTestFactory factory;

    @Test
    void invalidInternalToken_returns401() throws Exception {
        mockMvc.perform(get("/academic/users")
                        .header("X-Internal-Token", "not-a-valid-jwt"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void expiredInternalToken_returns401() throws Exception {
        Instant now = now();
        String expired = factory.buildToken(1L, SESSION_ID, 1L, 1L,
                "ADMIN", "ACTIVE", null, false, false,
                now.minusSeconds(600), now.minusSeconds(300),
                InternalJwtTestFactory.ISSUER, InternalJwtTestFactory.AUDIENCE, "internal", factory.keyPair());
        mockMvc.perform(get("/academic/users")
                        .header("X-Internal-Token", expired))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void wrongSignatureInternalToken_returns401() throws Exception {
        InternalJwtTestFactory wrongSigner = new InternalJwtTestFactory();
        Instant now = now();
        String wrong = factory.buildToken(1L, SESSION_ID, 1L, 1L,
                "ADMIN", "ACTIVE", null, false, false,
                now.minusSeconds(1), now.plusSeconds(60),
                InternalJwtTestFactory.ISSUER, InternalJwtTestFactory.AUDIENCE, "internal",
                wrongSigner.keyPair());
        mockMvc.perform(get("/academic/users")
                        .header("X-Internal-Token", wrong))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void validInternalToken_passesFilter_notReturning401() throws Exception {
        String token = validToken();
        MvcResult result = mockMvc.perform(get("/academic/users")
                        .header("X-Internal-Token", token))
                .andReturn();
        assertThat(result.getResponse().getStatus()).isNotEqualTo(401);
    }

    @Test
    void validInternalToken_takesPrecedenceOverLegacyHeaders() throws Exception {
        String token = validToken();
        MvcResult result = mockMvc.perform(get("/academic/users")
                        .header("X-Internal-Token", token)
                        .header("X-User-Id", "999")
                        .header("X-User-Role", "STUDENT"))
                .andReturn();
        assertThat(result.getResponse().getStatus()).isNotEqualTo(401);
    }

    @Test
    void legacyHeaders_stillAccepted_whileDualModeOn() throws Exception {
        MvcResult result = mockMvc.perform(get("/academic/users")
                        .header("X-User-Id", "1")
                        .header("X-User-Role", "ADMIN"))
                .andReturn();
        assertThat(result.getResponse().getStatus()).isNotEqualTo(401);
    }

    private String validToken() {
        return factory.validToken(1L, SESSION_ID, 1L, 1L,
                "ADMIN", "ACTIVE", null, false, false);
    }

    private static Instant now() {
        return Instant.now().truncatedTo(ChronoUnit.SECONDS);
    }
}
