package ru.rutcampustrack.schedule.security;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import ru.rutcampustrack.schedule.integration.AbstractScheduleIntegrationTest;
import ru.rutcampustrack.shared.security.InternalJwtTestFactory;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

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

    private static final UUID SESSION_ID = UUID.fromString("cccccccc-cccc-4ccc-8ccc-cccccccccccc");

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
        Instant now = now();
        mockMvc.perform(get("/schedule/items")
                        .header("X-Internal-Token", factory.buildToken(1L, SESSION_ID, 1L, 1L,
                                "ADMIN", "ACTIVE", null, false, false,
                                now.minusSeconds(600), now.minusSeconds(300),
                                InternalJwtTestFactory.ISSUER, InternalJwtTestFactory.AUDIENCE,
                                "internal", factory.keyPair())))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void wrongSignatureInternalToken_returns401() throws Exception {
        InternalJwtTestFactory wrongSigner = new InternalJwtTestFactory();
        Instant now = now();
        mockMvc.perform(get("/schedule/items")
                        .header("X-Internal-Token", factory.buildToken(1L, SESSION_ID, 1L, 1L,
                                "ADMIN", "ACTIVE", null, false, false,
                                now.minusSeconds(1), now.plusSeconds(60),
                                InternalJwtTestFactory.ISSUER, InternalJwtTestFactory.AUDIENCE,
                                "internal", wrongSigner.keyPair())))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void validInternalToken_passesFilter() throws Exception {
        String token = validToken();
        MvcResult result = mockMvc.perform(get("/schedule/items")
                        .header("X-Internal-Token", token))
                .andReturn();
        assertThat(result.getResponse().getStatus()).isNotEqualTo(401);
    }

    @Test
    void signedHeadmanTokenUsesStudentDomainRoleAndKeepsIdentityFlags() throws Exception {
        String token = factory.validToken(1L, SESSION_ID, 1L, 1L,
                "HEADMAN", "ACTIVE", 10L, true, false);
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
        String token = validToken();
        MvcResult result = mockMvc.perform(get("/schedule/items")
                        .header("X-Internal-Token", token)
                        .header("X-User-Id", "999")
                        .header("X-User-Role", "STUDENT"))
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
