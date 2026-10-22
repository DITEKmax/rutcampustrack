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

import ru.rutcampustrack.academic.integration.AbstractAcademicIntegrationTest;
import ru.rutcampustrack.auth.session.model.AuthRole;
import ru.rutcampustrack.shared.security.InternalJwtProperties;
import ru.rutcampustrack.shared.security.InternalJwtTestFactory;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

/**
 * M03a: verifies {@link AcademicUserContextFilter} behaviour end-to-end.
 *
 * <p>Uses a protected endpoint ({@code /academic/users}) to observe whether the filter accepted,
 * rejected, or passed through the request. Endpoint may return 403 (role check failed) or 4xx — the
 * filter concern is only that it NEVER returns 401 for valid/legacy auth, and ALWAYS 401 for
 * invalid token.
 */
@AutoConfigureMockMvc(
        print = org.springframework.boot.test.autoconfigure.web.servlet.MockMvcPrint.NONE)
@TestPropertySource(properties = "rct.fixture.legacy-headers-enabled=true")
class AcademicUserContextFilterIT extends AbstractAcademicIntegrationTest {

    private static final UUID SESSION_ID = UUID.fromString("aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa");

    @Autowired private MockMvc mockMvc;
    @Autowired private org.springframework.boot.test.web.client.TestRestTemplate http;
    @Autowired private com.fasterxml.jackson.databind.ObjectMapper mapper;

    @Autowired private InternalJwtTestFactory factory;
    @Autowired private AcademicUserContextFilter filter;
    @Autowired private RequestContext context;

    @Autowired private InternalJwtProperties properties;

    @BeforeEach
    void dualModeIsEffective() {
        assertThat(properties.legacyHeadersEnabled()).isTrue();
    }

    @Test
    void invalidInternalToken_returns401() throws Exception {
        mockMvc.perform(get("/academic/users").header("X-Internal-Token", "not-a-valid-jwt"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void expiredInternalToken_returns401() throws Exception {
        Instant now = now();
        String expired =
                factory.buildToken(
                        1L,
                        SESSION_ID,
                        1L,
                        1L,
                        "ADMIN",
                        "ACTIVE",
                        null,
                        false,
                        false,
                        now.minusSeconds(600),
                        now.minusSeconds(300),
                        InternalJwtTestFactory.ISSUER,
                        InternalJwtTestFactory.AUDIENCE,
                        "internal",
                        factory.keyPair());
        mockMvc.perform(get("/academic/users").header("X-Internal-Token", expired))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void wrongSignatureInternalToken_returns401() throws Exception {
        InternalJwtTestFactory wrongSigner = new InternalJwtTestFactory();
        Instant now = now();
        String wrong =
                factory.buildToken(
                        1L,
                        SESSION_ID,
                        1L,
                        1L,
                        "ADMIN",
                        "ACTIVE",
                        null,
                        false,
                        false,
                        now.minusSeconds(1),
                        now.plusSeconds(60),
                        InternalJwtTestFactory.ISSUER,
                        InternalJwtTestFactory.AUDIENCE,
                        "internal",
                        wrongSigner.keyPair());
        mockMvc.perform(get("/academic/users").header("X-Internal-Token", wrong))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void validInternalToken_passesFilter_notReturning401() throws Exception {
        var selected =
                identityFixture.select(identityFixture.canonical().adminId(), AuthRole.ADMIN);
        var headers = new org.springframework.http.HttpHeaders();
        headers.set("X-Internal-Token", selected.internalToken());
        var response =
                http.exchange(
                        "/academic/users",
                        org.springframework.http.HttpMethod.GET,
                        new org.springframework.http.HttpEntity<>(headers),
                        String.class);
        assertThat(response.getStatusCode()).isEqualTo(org.springframework.http.HttpStatus.OK);
        var page = mapper.readTree(response.getBody());
        assertThat(page.path("page").path("totalElements").asLong()).isEqualTo(3);
        var ids = new java.util.HashSet<Long>();
        page.path("_embedded")
                .path("userResponseList")
                .forEach(
                        row -> {
                            ids.add(row.path("id").asLong());
                            assertThat(row.has("passwordHash")).isFalse();
                            assertThat(
                                            row.path("initialPassword").isMissingNode()
                                                    || row.path("initialPassword").isNull())
                                    .isTrue();
                        });
        assertThat(ids)
                .containsExactlyInAnyOrder(
                        identityFixture.canonical().adminId(),
                        identityFixture.canonical().teacherId(),
                        identityFixture.canonical().studentId());
    }

    @Test
    void validInternalToken_takesPrecedenceOverLegacyHeaders() throws Exception {
        var selected =
                identityFixture.select(identityFixture.canonical().adminId(), AuthRole.ADMIN);
        var headers = new org.springframework.http.HttpHeaders();
        headers.set("X-Internal-Token", selected.internalToken());
        headers.set("X-User-Id", "999");
        headers.set("X-User-Role", "STUDENT");
        headers.set("X-Group-Id", "999");
        headers.set("X-Is-Headman", "true");
        var response =
                http.exchange(
                        "/academic/users",
                        org.springframework.http.HttpMethod.GET,
                        new org.springframework.http.HttpEntity<>(headers),
                        String.class);
        assertThat(response.getStatusCode()).isEqualTo(org.springframework.http.HttpStatus.OK);
        var page = mapper.readTree(response.getBody());
        assertThat(page.path("page").path("totalElements").asLong()).isEqualTo(3);
        var ids = new java.util.HashSet<Long>();
        page.path("_embedded")
                .path("userResponseList")
                .forEach(
                        row -> {
                            ids.add(row.path("id").asLong());
                            assertThat(row.has("passwordHash")).isFalse();
                            assertThat(
                                            row.path("initialPassword").isMissingNode()
                                                    || row.path("initialPassword").isNull())
                                    .isTrue();
                        });
        assertThat(ids)
                .containsExactlyInAnyOrder(
                        identityFixture.canonical().adminId(),
                        identityFixture.canonical().teacherId(),
                        identityFixture.canonical().studentId());
    }

    @Test
    void legacyHeaders_stillAccepted_whileDualModeOn() throws Exception {
        var request =
                new org.springframework.mock.web.MockHttpServletRequest("GET", "/academic/users");
        request.setServletPath("/academic/users");
        request.addHeader("X-User-Id", "17");
        request.addHeader("X-User-Role", "STUDENT");
        request.addHeader("X-Group-Id", "23");
        request.addHeader("X-Is-Headman", "true");
        var response = new org.springframework.mock.web.MockHttpServletResponse();
        var accepted = new java.util.concurrent.atomic.AtomicBoolean();
        org.springframework.web.context.request.RequestContextHolder.setRequestAttributes(
                new org.springframework.web.context.request.ServletRequestAttributes(
                        request, response));
        try {
            filter.doFilter(
                    request,
                    response,
                    (req, res) -> {
                        accepted.set(true);
                        assertThat(context.getUserId()).isEqualTo(17L);
                        assertThat(context.getRole())
                                .isEqualTo(
                                        ru.rutcampustrack.academic.contract.enums.UserRole.STUDENT);
                        assertThat(context.getGroupId()).isEqualTo(23L);
                        assertThat(context.isHeadman()).isTrue();
                        assertThat(context.getClaims()).isNull();
                        assertThat(context.getInternalToken()).isNull();
                    });
            assertThat(accepted).isTrue();
        } finally {
            org.springframework.web.context.request.RequestContextHolder.resetRequestAttributes();
        }
    }

    private String validToken() {
        return identityFixture
                .select(identityFixture.canonical().adminId(), AuthRole.ADMIN)
                .internalToken();
    }

    private static Instant now() {
        return Instant.now().truncatedTo(ChronoUnit.SECONDS);
    }
}
