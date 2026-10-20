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
    @Autowired private org.springframework.boot.test.web.client.TestRestTemplate http;
    @Autowired private com.fasterxml.jackson.databind.ObjectMapper mapper;

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
                            assertThat(row.has("initialPassword")).isFalse();
                        });
        assertThat(ids)
                .containsExactlyInAnyOrder(
                        identityFixture.canonical().adminId(),
                        identityFixture.canonical().teacherId(),
                        identityFixture.canonical().studentId());
    }
}
