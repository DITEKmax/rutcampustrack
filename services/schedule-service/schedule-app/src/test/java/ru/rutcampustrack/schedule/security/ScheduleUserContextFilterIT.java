package ru.rutcampustrack.schedule.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import ru.rutcampustrack.schedule.integration.AbstractScheduleIntegrationTest;
import ru.rutcampustrack.shared.security.InternalJwtTestFactory;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

/**
 * M03a: verifies {@link ScheduleUserContextFilter} behaviour end-to-end on the {@code
 * /schedule/items} endpoint (@RequireRole enforced).
 */
@AutoConfigureMockMvc
@TestPropertySource(properties = "rutcampustrack.security.internal-jwt.legacy-headers-enabled=true")
class ScheduleUserContextFilterIT extends AbstractScheduleIntegrationTest {

    private static final UUID SESSION_ID = UUID.fromString("cccccccc-cccc-4ccc-8ccc-cccccccccccc");

    @Autowired private MockMvc mockMvc;
    @Autowired private org.springframework.boot.test.web.client.TestRestTemplate http;
    @org.springframework.boot.test.web.server.LocalServerPort private int httpPort;

    @org.springframework.test.context.bean.override.mockito.MockitoBean
    private ru.rutcampustrack.schedule.grpc.AcademicGrpcClient academic;

    @org.junit.jupiter.api.BeforeEach
    void seedIdentityScope() {
        resetScheduleData();
        fixture(10L, 501L);
        fixture(20L, 502L);
    }

    private void fixture(long group, long assignment) {
        var item = new ru.rutcampustrack.schedule.item.entity.ScheduleItem();
        item.setAssignmentId(assignment);
        item.setGroupId(group);
        item.setSubjectId(100L);
        item.setSemesterId(1L);
        item.setDayOfWeek((short) 1);
        item.setLessonNumber((short) 1);
        item.setWeekType(ru.rutcampustrack.schedule.contract.enums.WeekType.ALL);
        item.setActive(true);
        item = saveFixtureScheduleItem(item);
        var lesson = new ru.rutcampustrack.schedule.lesson.entity.Lesson();
        lesson.setScheduleItemId(item.getId());
        lesson.setDate(java.time.LocalDate.of(2026, 4, 6));
        lesson.setStatus(ru.rutcampustrack.schedule.contract.enums.LessonStatus.PLANNED);
        saveFixtureLesson(lesson);
    }

    private org.springframework.http.HttpHeaders tokenHeaders(String token) {
        var headers = new org.springframework.http.HttpHeaders();
        headers.set("X-Internal-Token", token);
        return headers;
    }

    private void readOwnScope(org.springframework.http.HttpHeaders headers, long group) {
        var response =
                http.exchange(
                        "/schedule/groups/"
                                + group
                                + "/lessons?dateFrom=2026-04-01&dateTo=2026-04-30",
                        org.springframework.http.HttpMethod.GET,
                        new org.springframework.http.HttpEntity<>(headers),
                        com.fasterxml.jackson.databind.JsonNode.class);
        assertThat(response.getStatusCode()).isEqualTo(org.springframework.http.HttpStatus.OK);
        var rows = response.getBody().path("_embedded").path("lessonResponseList");
        assertThat(rows.size()).isEqualTo(1);
        assertThat(rows.get(0).path("groupId").asLong()).isEqualTo(group);
    }

    @Autowired private InternalJwtTestFactory factory;

    @Test
    void invalidInternalToken_returns401() throws Exception {
        mockMvc.perform(get("/schedule/items").header("X-Internal-Token", "nope"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void expiredInternalToken_returns401() throws Exception {
        Instant now = now();
        mockMvc.perform(
                        get("/schedule/items")
                                .header(
                                        "X-Internal-Token",
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
                                                factory.keyPair())))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void wrongSignatureInternalToken_returns401() throws Exception {
        InternalJwtTestFactory wrongSigner = new InternalJwtTestFactory();
        Instant now = now();
        mockMvc.perform(
                        get("/schedule/items")
                                .header(
                                        "X-Internal-Token",
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
                                                wrongSigner.keyPair())))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void validInternalToken_passesFilter() {
        readOwnScope(tokenHeaders(validToken()), 10L);
    }

    @Test
    void signedHeadmanTokenUsesStudentDomainRoleAndKeepsIdentityFlags() throws Exception {
        var headers =
                tokenHeaders(
                        factory.validToken(
                                1L, SESSION_ID, 1L, 1L, "HEADMAN", "ACTIVE", 10L, true, false));
        headers.setContentType(org.springframework.http.MediaType.APPLICATION_JSON);
        org.mockito.Mockito.when(academic.isHeadman(1L, 10L)).thenReturn(true);
        org.mockito.Mockito.when(academic.getSemesterArchiveAuthorityState(1L))
                .thenReturn(
                        ru.rutcampustrack.academic.grpc.SemesterStateResponse.newBuilder()
                                .setId(1L)
                                .build());
        long lesson =
                jdbcTemplate.queryForObject(
                        "SELECT id FROM lessons WHERE group_id = 10", Long.class);
        var accepted =
                patch(
                        "/schedule/lessons/" + lesson + "/cancel",
                        headers,
                        "{\"reason\":\"synthetic cancellation\"}");
        assertThat(accepted.statusCode()).isEqualTo(200);
        assertThat(
                        jdbcTemplate.queryForObject(
                                "SELECT cancelled_by FROM lessons WHERE id = ?",
                                Long.class,
                                lesson))
                .isEqualTo(1L);
        assertThat(
                        jdbcTemplate.queryForObject(
                                "SELECT group_id FROM lessons WHERE id = ?", Long.class, lesson))
                .isEqualTo(10L);
        org.mockito.Mockito.when(academic.isHeadman(1L, 10L)).thenReturn(false);
        var denied = patch("/schedule/lessons/" + lesson + "/restore", headers, "");
        assertThat(denied.statusCode()).isEqualTo(403);
        assertThat(
                        jdbcTemplate.queryForObject(
                                "SELECT status::text FROM lessons WHERE id = ?",
                                String.class,
                                lesson))
                .isEqualTo("cancelled");
    }

    @Test
    void legacyHeaders_dualMode_accepted() {
        var headers = new org.springframework.http.HttpHeaders();
        headers.set("X-User-Id", "1");
        headers.set("X-User-Role", "ADMIN");
        readOwnScope(headers, 10L);
    }

    @Test
    void internalToken_precedenceOverLegacy() {
        var headers =
                tokenHeaders(
                        factory.validToken(
                                1L, SESSION_ID, 1L, 1L, "STUDENT", "ACTIVE", 10L, false, false));
        headers.set("X-User-Id", "999");
        headers.set("X-User-Role", "ADMIN");
        headers.set("X-Group-Id", "20");
        readOwnScope(headers, 10L);
        var response =
                http.exchange(
                        "/schedule/groups/20/lessons?dateFrom=2026-04-01&dateTo=2026-04-30",
                        org.springframework.http.HttpMethod.GET,
                        new org.springframework.http.HttpEntity<>(headers),
                        String.class);
        assertThat(response.getStatusCode())
                .isEqualTo(org.springframework.http.HttpStatus.FORBIDDEN);
    }

    private java.net.http.HttpResponse<String> patch(
            String path, org.springframework.http.HttpHeaders headers, String body)
            throws Exception {
        var request =
                java.net.http.HttpRequest.newBuilder(
                                java.net.URI.create("http://127.0.0.1:" + httpPort + path))
                        .timeout(java.time.Duration.ofSeconds(5))
                        .method("PATCH", java.net.http.HttpRequest.BodyPublishers.ofString(body));
        headers.forEach((name, values) -> values.forEach(value -> request.header(name, value)));
        return java.net.http.HttpClient.newHttpClient()
                .send(request.build(), java.net.http.HttpResponse.BodyHandlers.ofString());
    }

    private String validToken() {
        return factory.validToken(1L, SESSION_ID, 1L, 1L, "ADMIN", "ACTIVE", null, false, false);
    }

    private static Instant now() {
        return Instant.now().truncatedTo(ChronoUnit.SECONDS);
    }
}
