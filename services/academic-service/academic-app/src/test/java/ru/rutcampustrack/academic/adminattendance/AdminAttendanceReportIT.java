package ru.rutcampustrack.academic.adminattendance;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import ru.rutcampustrack.academic.admin.AcademicAdminAccess;
import ru.rutcampustrack.academic.contract.dto.adminattendance.AdminAttendanceReportModels.*;
import ru.rutcampustrack.academic.contract.dto.user.AdminAccessInput;
import ru.rutcampustrack.academic.contract.dto.user.CreateUserRequest;
import ru.rutcampustrack.academic.contract.enums.UserRole;
import ru.rutcampustrack.academic.export.RegistryDocumentClient;
import ru.rutcampustrack.academic.export.RegistryTableRenderer;
import ru.rutcampustrack.academic.identitycatalog.AuthIdentityDataClient;
import ru.rutcampustrack.academic.identityoperation.AcademicUserIdentityOperations;
import ru.rutcampustrack.academic.integration.AbstractAcademicIntegrationTest;
import ru.rutcampustrack.academic.integration.AcademicIdentityIntegrationFixture;
import ru.rutcampustrack.academic.integration.AcademicIdentityIntegrationFixture.SelectedActor;
import ru.rutcampustrack.academic.security.AcademicUserContextFilter;
import ru.rutcampustrack.academic.security.RequestContext;
import ru.rutcampustrack.attendance.grpc.AdminGroupAttendanceTotals;
import ru.rutcampustrack.auth.session.model.AuthRole;
import ru.rutcampustrack.shared.security.admin.AdminAccessException;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

/** Real PostgreSQL ownership/capability/query checks; the remote aggregate is an explicit seam. */
class AdminAttendanceReportIT extends AbstractAcademicIntegrationTest {
    private long admin;
    private long groupId;
    private String semesterId;
    private SelectedActor actor;
    private AcademicIdentityIntegrationFixture.RequestScope actorRequest;
    private JdbcTemplate jdbc;
    @Autowired private JdbcTemplate runtime;
    @Autowired private RequestContext context;
    @Autowired private AcademicAdminAccess access;
    @Autowired private AuthIdentityDataClient identityData;
    @Autowired private AcademicUserIdentityOperations identities;
    @Autowired private AcademicUserContextFilter admission;
    private AttendanceAdminGroupStatsClient attendance;
    private AdminAttendanceReportService reports;
    private AdminAttendanceExportService exports;
    @Autowired private ObjectMapper mapper;
    private org.springframework.test.web.servlet.MockMvc http;

    @AfterEach
    void closeActorRequest() {
        if (actorRequest != null) actorRequest.close();
    }

    @BeforeEach
    void schema() {
        jdbc = identityFixture.fixtureOwnerAcademicJdbc();
        var full = identityFixture.select(identityFixture.canonical().adminId(), AuthRole.ADMIN);
        admin =
                identityFixture
                        .seedUser(
                                new CreateUserRequest(
                                        "Synthetic",
                                        "Report",
                                        null,
                                        UserRole.ADMIN,
                                        null,
                                        null,
                                        null,
                                        new AdminAccessInput("LIMITED", List.of("DASHBOARD_VIEW"))),
                                full)
                        .userId();
        groupId = identityFixture.canonical().groupId();
        semesterId = Long.toString(identityFixture.canonical().semesterId());
        jdbc.update(
                "UPDATE semesters SET name='Весна',is_active=false,is_archived=true WHERE id=?",
                Long.parseLong(semesterId));
        jdbc.update("UPDATE groups SET name='Альфа',archived_at=NULL WHERE id=?", groupId);
        jdbc.update(
                "INSERT INTO groups(id,name,archived_at) VALUES"
                        + " (20,'Бета',NOW()),(30,'Гамма',NULL),(40,'100%_!',NULL)");
        selectCurrentClaims();
        attendance = mock(AttendanceAdminGroupStatsClient.class);
        when(attendance.read(any(), anyString()))
                .thenAnswer(
                        call -> {
                            AdminAttendanceReadRepository.Selection selected = call.getArgument(0);
                            return selected.groups().keySet().stream()
                                    .map(id -> totals(id))
                                    .toList();
                        });
        reports =
                new AdminAttendanceReportService(
                        access,
                        new AdminAttendanceReadRepository(runtime),
                        attendance,
                        context,
                        Clock.fixed(Instant.parse("2026-04-01T09:00:00Z"), ZoneOffset.UTC),
                        identityData);
        exports =
                new AdminAttendanceExportService(
                        reports,
                        access,
                        runtime,
                        context,
                        mapper,
                        new RegistryTableRenderer(),
                        mock(RegistryDocumentClient.class));
        http =
                org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup(
                                new AdminAttendanceReportController(reports, exports))
                        .addFilters(admission)
                        .setControllerAdvice(new AdminAttendanceReportAdvice())
                        .setMessageConverters(
                                new org.springframework.http.converter.json
                                        .MappingJackson2HttpMessageConverter(mapper))
                        .build();
    }

    @Test
    void actualHttpBoundaryKeepsNoStoreAndRejectsUnknownSelectorsWithoutReflectingThem()
            throws Exception {
        http.perform(
                        get("/academic/admin/attendance")
                                .with(identityFixture.signed(actor))
                                .param("semesterId", semesterId)
                                .param("sort", "PRESENT_PERCENT"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.totalElements").value(4))
                .andExpect(jsonPath("$.rows[0].presentPercent").value(0));
        var rejected =
                http.perform(
                                get("/academic/admin/attendance")
                                        .with(identityFixture.signed(actor))
                                        .param("semesterId", semesterId)
                                        .param("sort", "private-selector-marker"))
                        .andExpect(status().isBadRequest())
                        .andExpect(header().string("Cache-Control", "no-store"))
                        .andReturn();
        assertThat(rejected.getResponse().getContentAsString().contains("private-selector-marker"))
                .isFalse();
        try (var unavailable = identityFixture.unavailableAdminAuthority(identityData)) {
            http.perform(
                            get("/academic/admin/attendance")
                                    .with(identityFixture.signed(actor))
                                    .param("semesterId", semesterId))
                    .andExpect(status().isServiceUnavailable())
                    .andExpect(header().string("Cache-Control", "no-store"))
                    .andExpect(jsonPath("$.code").value("DEPENDENCY_UNAVAILABLE"));
        }
        changeAccess("LIMITED", List.of("GROUPS_VIEW"));
        selectCurrentClaims();
        http.perform(
                        get("/academic/admin/attendance")
                                .with(identityFixture.signed(actor))
                                .param("semesterId", semesterId))
                .andExpect(status().isForbidden())
                .andExpect(header().string("Cache-Control", "no-store"));
    }

    @Test
    void
            dashboardOnlyReadsArchivedSemesterAndGroupsSortsCompleteRowsBeforePagingAndKeepsNoDataNull() {
        assertThat(reports.semesters().items()).hasSize(1);
        var first = reports.read(semesterId, "", Sort.PRESENT_PERCENT, Direction.DESC, 0, 1);
        assertThat(first.totalElements()).isEqualTo(4);
        assertThat(first.totalPages()).isEqualTo(4);
        assertThat(first.rows().getFirst().groupId()).isEqualTo("20");
        assertThat(
                        reports.read(semesterId, "", Sort.PRESENT_PERCENT, Direction.DESC, 3, 1)
                                .rows()
                                .getFirst()
                                .presentPercent())
                .isNull();
        var ascending = reports.read(semesterId, "", Sort.PRESENT_PERCENT, Direction.ASC, 0, 100);
        assertThat(ascending.rows().stream().map(Row::groupId))
                .containsExactly(Long.toString(groupId), "20", "30", "40");
        assertThat(ascending.rows().getFirst().presentPercent()).isZero();
        assertThat(ascending.rows().get(2).dataState()).isEqualTo(DataState.NO_DATA);
        assertThat(reports.read(semesterId, "100%_!", Sort.GROUP_NAME, Direction.ASC, 0, 25).rows())
                .extracting(Row::groupId)
                .containsExactly("40");
        // The response has no personal roster projection, even for a current FULL administrator.
        changeAccess("FULL", List.of());
        selectCurrentClaims();
        var full = reports.read(semesterId, "", Sort.GROUP_NAME, Direction.ASC, 0, 25);
        assertThat(full.rows()).hasSize(4);
        assertThat(
                        new AdminAttendanceReportController(reports, exports)
                                .read(semesterId, "", Sort.GROUP_NAME, Direction.ASC, 0, 25)
                                .getHeaders()
                                .getCacheControl())
                .isEqualTo("no-store");
    }

    @Test
    void otherRegistryGrantsCannotReadAndRevocationDuringRemoteReadReturnsNoRows() {
        changeAccess("LIMITED", List.of("GROUPS_VIEW"));
        selectCurrentClaims();
        assertThatThrownBy(() -> reports.semesters()).isInstanceOf(AdminAccessException.class);
        assertThatThrownBy(() -> exports.formats()).isInstanceOf(AdminAccessException.class);
        verifyNoInteractions(attendance);
        changeAccess("LIMITED", List.of("DASHBOARD_VIEW"));
        selectCurrentClaims();
        doAnswer(
                        call -> {
                            changeAccess("LIMITED", List.of("GROUPS_VIEW"));
                            return List.of(totals(groupId), totals(20), totals(30), totals(40));
                        })
                .when(attendance)
                .read(any(), anyString());
        assertThatThrownBy(
                        () -> reports.read(semesterId, "", Sort.GROUP_NAME, Direction.ASC, 0, 25))
                .isInstanceOf(AdminAccessException.class);
    }

    @Test
    void exportRequiresExactOwnedPreparingLeaseAndUsesAllFilteredRowsNotDisplayedPage()
            throws Exception {
        UUID job = UUID.randomUUID(), lease = UUID.randomUUID();
        Export query =
                new Export(semesterId, "", Sort.PRESENT_PERCENT, Direction.DESC, Format.html);
        var identity = mapper.createObjectNode();
        identity.set("actor", mapper.valueToTree(actor.claims()));
        identity.putObject("report")
                .put("kind", "ADMIN_GROUP_ATTENDANCE")
                .set("adminGroupAttendance", mapper.valueToTree(query));
        jdbc.update(
                "INSERT INTO"
                    + " academic_export_jobs(job_id,lease_id,user_id,session_id,state,lease_until,expires_at,identity,request_id,binding_hash)"
                    + " VALUES(?,?,?,?,'preparing',NOW()+INTERVAL '1 minute',NOW()+INTERVAL '5"
                    + " minutes',?::jsonb,?,?)",
                job,
                lease,
                admin,
                actor.sessionId(),
                identity.toString(),
                UUID.randomUUID(),
                "0".repeat(64));
        assertThatThrownBy(() -> exports.generate(query, job, UUID.randomUUID()))
                .isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
        jdbc.update("UPDATE academic_export_jobs SET session_id=?", UUID.randomUUID());
        assertThatThrownBy(() -> exports.generate(query, job, lease))
                .isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
        jdbc.update("UPDATE academic_export_jobs SET session_id=?", actor.sessionId());
        var file = exports.generate(query, job, lease);
        assertThat(file.rowCount()).isEqualTo(4);
        String html = new String(file.content(), StandardCharsets.UTF_8);
        assertThat(
                        html.contains("Альфа")
                                && html.contains("Бета")
                                && html.contains("Гамма")
                                && html.contains("Нет данных"))
                .isTrue();
        assertThat(html.indexOf("Бета") < html.indexOf("Альфа")).isTrue();
        Export pdf = new Export(semesterId, "", Sort.PRESENT_PERCENT, Direction.DESC, Format.pdf);
        ((com.fasterxml.jackson.databind.node.ObjectNode) identity.get("report"))
                .set("adminGroupAttendance", mapper.valueToTree(pdf));
        jdbc.update("UPDATE academic_export_jobs SET identity=?::jsonb", identity.toString());
        var slowRenderer = mock(RegistryDocumentClient.class);
        when(slowRenderer.convert(any(byte[].class), eq(false)))
                .thenAnswer(
                        call -> {
                            changeAccess("LIMITED", List.of("GROUPS_VIEW"));
                            return new byte[] {1, 2, 3};
                        });
        var checkedExport =
                new AdminAttendanceExportService(
                        reports,
                        access,
                        runtime,
                        context,
                        mapper,
                        new RegistryTableRenderer(),
                        slowRenderer);
        assertThatThrownBy(() -> checkedExport.generate(pdf, job, lease))
                .isInstanceOf(AdminAccessException.class);
        changeAccess("LIMITED", List.of("DASHBOARD_VIEW"));
        selectCurrentClaims();
        identity.set("actor", mapper.valueToTree(actor.claims()));
        jdbc.update("UPDATE academic_export_jobs SET session_id=?", actor.sessionId());
        ((com.fasterxml.jackson.databind.node.ObjectNode) identity.get("report"))
                .set("adminGroupAttendance", mapper.valueToTree(query));
        jdbc.update("UPDATE academic_export_jobs SET identity=?::jsonb", identity.toString());
        jdbc.update("UPDATE academic_export_jobs SET state='ready',content=decode('01','hex')");
        assertThatThrownBy(() -> exports.generate(query, job, lease))
                .isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
    }

    private void changeAccess(String mode, List<String> permissions) {
        var full = identityFixture.select(identityFixture.canonical().adminId(), AuthRole.ADMIN);
        try (var ignored = identityFixture.request(full)) {
            long version =
                    identityData
                            .readServiceIdentity(
                                    List.of(admin),
                                    System.nanoTime()
                                            + java.util.concurrent.TimeUnit.SECONDS.toNanos(3))
                            .get(admin)
                            .getAdminProfile()
                            .getVersion();
            identities.adminAccess(admin, new AdminAccessInput(mode, permissions), version, false);
        }
    }

    private void selectCurrentClaims() {
        if (actorRequest != null) actorRequest.close();
        actor = identityFixture.select(admin, AuthRole.ADMIN);
        actorRequest = identityFixture.request(actor);
    }

    private AdminGroupAttendanceTotals totals(long id) {
        var value = AdminGroupAttendanceTotals.newBuilder().setGroupId(id);
        if (id == groupId)
            value.setCountedLessons(1)
                    .setAbsent(1)
                    .setOpportunities(1)
                    .setPresentPercent(0)
                    .setPresentOrExcusedPercent(0);
        if (id == 20)
            value.setCountedLessons(2)
                    .setPresent(2)
                    .setExcused(1)
                    .setAbsent(1)
                    .setOpportunities(4)
                    .setPresentPercent(50)
                    .setPresentOrExcusedPercent(75);
        return value.build();
    }
}
