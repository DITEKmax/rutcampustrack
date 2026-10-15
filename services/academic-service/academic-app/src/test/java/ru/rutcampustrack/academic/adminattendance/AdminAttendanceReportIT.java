package ru.rutcampustrack.academic.adminattendance;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;

import ru.rutcampustrack.academic.admin.AcademicAdminAccess;
import ru.rutcampustrack.academic.contract.dto.adminattendance.AdminAttendanceReportModels.*;
import ru.rutcampustrack.academic.export.RegistryDocumentClient;
import ru.rutcampustrack.academic.export.RegistryTableRenderer;
import ru.rutcampustrack.academic.security.RequestContext;
import ru.rutcampustrack.attendance.grpc.AdminGroupAttendanceTotals;
import ru.rutcampustrack.shared.security.InternalJwtClaims;
import ru.rutcampustrack.shared.security.admin.AdminAccessException;
import ru.rutcampustrack.shared.security.admin.AdminAccessJdbcAuthority;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

/** Real PostgreSQL ownership/capability/query checks; the remote aggregate is an explicit seam. */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AdminAttendanceReportIT {
    private final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");
    private final UUID session = UUID.randomUUID();
    private JdbcTemplate jdbc;
    private RequestContext context;
    private AcademicAdminAccess access;
    private AttendanceAdminGroupStatsClient attendance;
    private AdminAttendanceReportService reports;
    private AdminAttendanceExportService exports;
    private ObjectMapper mapper;
    private org.springframework.test.web.servlet.MockMvc http;

    @BeforeAll
    void start() {
        postgres.start();
    }

    @AfterAll
    void stop() {
        postgres.stop();
    }

    @BeforeEach
    void schema() {
        var source =
                new DriverManagerDataSource(
                        postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
        String schema = "group_report_" + UUID.randomUUID().toString().replace("-", "");
        new JdbcTemplate(source).execute("CREATE SCHEMA " + schema);
        source.setSchema(schema);
        jdbc = new JdbcTemplate(source);
        jdbc.execute("CREATE TABLE users(id BIGINT PRIMARY KEY,status TEXT,roles_version BIGINT)");
        jdbc.execute(
                "CREATE TABLE user_role_grants(id BIGINT PRIMARY KEY,user_id BIGINT,role"
                        + " TEXT,status TEXT)");
        jdbc.execute(
                "CREATE TABLE auth_sessions(sid UUID PRIMARY KEY,user_id"
                        + " BIGINT,active_role_grant_id BIGINT,session_version BIGINT,revoked_at"
                        + " TIMESTAMPTZ,refresh_expires_at TIMESTAMPTZ)");
        jdbc.execute(
                "CREATE TABLE admin_access_profiles(user_id BIGINT PRIMARY KEY,mode"
                        + " TEXT,permissions TEXT[],version BIGINT,assigned BOOLEAN)");
        jdbc.execute(
                "CREATE TABLE semesters(id BIGINT PRIMARY KEY,name TEXT,date_from DATE,date_to"
                        + " DATE,is_active BOOLEAN,is_archived BOOLEAN)");
        jdbc.execute(
                "CREATE TABLE groups(id BIGINT PRIMARY KEY,name TEXT,archived_at TIMESTAMPTZ)");
        jdbc.execute(
                "CREATE TABLE auth_export_jobs(job_id UUID PRIMARY KEY,lease_id UUID,user_id"
                        + " BIGINT,session_id UUID,state TEXT,lease_until TIMESTAMPTZ,expires_at"
                        + " TIMESTAMPTZ,identity JSONB)");
        jdbc.update("INSERT INTO users VALUES(1,'active',1)");
        jdbc.update("INSERT INTO user_role_grants VALUES(11,1,'admin','active')");
        jdbc.update(
                "INSERT INTO auth_sessions VALUES(?,1,11,1,NULL,NOW()+INTERVAL '1 day')", session);
        jdbc.update(
                "INSERT INTO admin_access_profiles"
                        + " VALUES(1,'LIMITED',ARRAY['DASHBOARD_VIEW'],1,true)");
        jdbc.update("INSERT INTO semesters VALUES(7,'Весна','2026-02-01','2026-06-30',false,true)");
        jdbc.update(
                "INSERT INTO groups"
                        + " VALUES(10,'Альфа',NULL),(20,'Бета',NOW()),(30,'Гамма',NULL),(40,'100%_!',NULL)");
        context = new RequestContext();
        context.setClaims(
                new InternalJwtClaims(1, session, 1, 1, "ADMIN", "ACTIVE", null, false, false));
        context.setInternalToken("synthetic-internal-token");
        access = new AcademicAdminAccess(new AdminAccessJdbcAuthority(jdbc), context, jdbc);
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
                        new AdminAttendanceReadRepository(jdbc),
                        attendance,
                        context,
                        Clock.fixed(Instant.parse("2026-04-01T09:00:00Z"), ZoneOffset.UTC));
        mapper = new ObjectMapper().registerModule(new JavaTimeModule());
        exports =
                new AdminAttendanceExportService(
                        reports,
                        access,
                        jdbc,
                        context,
                        mapper,
                        new RegistryTableRenderer(),
                        mock(RegistryDocumentClient.class));
        http =
                org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup(
                                new AdminAttendanceReportController(reports, exports))
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
                                .param("semesterId", "7")
                                .param("sort", "PRESENT_PERCENT"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.totalElements").value(4))
                .andExpect(jsonPath("$.rows[0].presentPercent").value(0));
        var rejected =
                http.perform(
                                get("/academic/admin/attendance")
                                        .param("semesterId", "7")
                                        .param("sort", "private-selector-marker"))
                        .andExpect(status().isBadRequest())
                        .andExpect(header().string("Cache-Control", "no-store"))
                        .andReturn();
        assertThat(rejected.getResponse().getContentAsString().contains("private-selector-marker"))
                .isFalse();
        jdbc.update("UPDATE admin_access_profiles SET permissions=ARRAY['GROUPS_VIEW']");
        http.perform(get("/academic/admin/attendance").param("semesterId", "7"))
                .andExpect(status().isForbidden())
                .andExpect(header().string("Cache-Control", "no-store"));
    }

    @Test
    void
            dashboardOnlyReadsArchivedSemesterAndGroupsSortsCompleteRowsBeforePagingAndKeepsNoDataNull() {
        assertThat(reports.semesters().items()).hasSize(1);
        var first = reports.read("7", "", Sort.PRESENT_PERCENT, Direction.DESC, 0, 1);
        assertThat(first.totalElements()).isEqualTo(4);
        assertThat(first.totalPages()).isEqualTo(4);
        assertThat(first.rows().getFirst().groupId()).isEqualTo("20");
        assertThat(
                        reports.read("7", "", Sort.PRESENT_PERCENT, Direction.DESC, 3, 1)
                                .rows()
                                .getFirst()
                                .presentPercent())
                .isNull();
        var ascending = reports.read("7", "", Sort.PRESENT_PERCENT, Direction.ASC, 0, 100);
        assertThat(ascending.rows().stream().map(Row::groupId))
                .containsExactly("10", "20", "30", "40");
        assertThat(ascending.rows().getFirst().presentPercent()).isZero();
        assertThat(ascending.rows().get(2).dataState()).isEqualTo(DataState.NO_DATA);
        assertThat(reports.read("7", "100%_!", Sort.GROUP_NAME, Direction.ASC, 0, 25).rows())
                .extracting(Row::groupId)
                .containsExactly("40");
        // The response has no personal roster projection, even for a current FULL administrator.
        jdbc.update("UPDATE admin_access_profiles SET mode='FULL',permissions=ARRAY[]::text[]");
        var full = reports.read("7", "", Sort.GROUP_NAME, Direction.ASC, 0, 25);
        assertThat(full.rows()).hasSize(4);
        assertThat(
                        new AdminAttendanceReportController(reports, exports)
                                .read("7", "", Sort.GROUP_NAME, Direction.ASC, 0, 25)
                                .getHeaders()
                                .getCacheControl())
                .isEqualTo("no-store");
    }

    @Test
    void otherRegistryGrantsCannotReadAndRevocationDuringRemoteReadReturnsNoRows() {
        jdbc.update("UPDATE admin_access_profiles SET permissions=ARRAY['GROUPS_VIEW']");
        assertThatThrownBy(() -> reports.semesters()).isInstanceOf(AdminAccessException.class);
        assertThatThrownBy(() -> exports.formats()).isInstanceOf(AdminAccessException.class);
        verifyNoInteractions(attendance);
        jdbc.update("UPDATE admin_access_profiles SET permissions=ARRAY['DASHBOARD_VIEW']");
        doAnswer(
                        call -> {
                            jdbc.update(
                                    "UPDATE admin_access_profiles SET"
                                            + " permissions=ARRAY['GROUPS_VIEW']");
                            return List.of(totals(10), totals(20), totals(30), totals(40));
                        })
                .when(attendance)
                .read(any(), anyString());
        assertThatThrownBy(() -> reports.read("7", "", Sort.GROUP_NAME, Direction.ASC, 0, 25))
                .isInstanceOf(AdminAccessException.class);
    }

    @Test
    void exportRequiresExactOwnedPreparingLeaseAndUsesAllFilteredRowsNotDisplayedPage()
            throws Exception {
        UUID job = UUID.randomUUID(), lease = UUID.randomUUID();
        Export query = new Export("7", "", Sort.PRESENT_PERCENT, Direction.DESC, Format.html);
        var identity = mapper.createObjectNode();
        identity.putObject("report")
                .put("kind", "ADMIN_GROUP_ATTENDANCE")
                .set("adminGroupAttendance", mapper.valueToTree(query));
        jdbc.update(
                "INSERT INTO auth_export_jobs VALUES(?,?,1,?,'preparing',NOW()+INTERVAL '1"
                        + " minute',NOW()+INTERVAL '1 hour',?::jsonb)",
                job,
                lease,
                session,
                identity.toString());
        assertThatThrownBy(() -> exports.generate(query, job, UUID.randomUUID()))
                .isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
        jdbc.update("UPDATE auth_export_jobs SET session_id=?", UUID.randomUUID());
        assertThatThrownBy(() -> exports.generate(query, job, lease))
                .isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
        jdbc.update("UPDATE auth_export_jobs SET session_id=?", session);
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
        Export pdf = new Export("7", "", Sort.PRESENT_PERCENT, Direction.DESC, Format.pdf);
        ((com.fasterxml.jackson.databind.node.ObjectNode) identity.get("report"))
                .set("adminGroupAttendance", mapper.valueToTree(pdf));
        jdbc.update("UPDATE auth_export_jobs SET identity=?::jsonb", identity.toString());
        var slowRenderer = mock(RegistryDocumentClient.class);
        when(slowRenderer.convert(any(byte[].class), eq(false)))
                .thenAnswer(
                        call -> {
                            jdbc.update(
                                    "UPDATE admin_access_profiles SET permissions=ARRAY['GROUPS_VIEW']");
                            return new byte[] {1, 2, 3};
                        });
        var checkedExport =
                new AdminAttendanceExportService(
                        reports,
                        access,
                        jdbc,
                        context,
                        mapper,
                        new RegistryTableRenderer(),
                        slowRenderer);
        assertThatThrownBy(() -> checkedExport.generate(pdf, job, lease))
                .isInstanceOf(AdminAccessException.class);
        jdbc.update("UPDATE admin_access_profiles SET permissions=ARRAY['DASHBOARD_VIEW']");
        ((com.fasterxml.jackson.databind.node.ObjectNode) identity.get("report"))
                .set("adminGroupAttendance", mapper.valueToTree(query));
        jdbc.update("UPDATE auth_export_jobs SET identity=?::jsonb", identity.toString());
        jdbc.update("UPDATE auth_export_jobs SET state='ready'");
        assertThatThrownBy(() -> exports.generate(query, job, lease))
                .isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
    }

    private static AdminGroupAttendanceTotals totals(long id) {
        var value = AdminGroupAttendanceTotals.newBuilder().setGroupId(id);
        if (id == 10)
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
