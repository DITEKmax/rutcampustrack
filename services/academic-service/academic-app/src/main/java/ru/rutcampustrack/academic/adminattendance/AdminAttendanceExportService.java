package ru.rutcampustrack.academic.adminattendance;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import ru.rutcampustrack.academic.admin.AcademicAdminAccess;
import ru.rutcampustrack.academic.contract.dto.adminattendance.AdminAttendanceReportModels.*;
import ru.rutcampustrack.academic.export.RegistryDocumentClient;
import ru.rutcampustrack.academic.export.RegistryTableRenderer;
import ru.rutcampustrack.academic.security.RequestContext;

import java.util.List;
import java.util.UUID;

/** Generation is bound to one owned durable job; Auth commits the immutable export audit fact. */
@Service
public class AdminAttendanceExportService {
    private final AdminAttendanceReportService reports;
    private final AcademicAdminAccess access;
    private final JdbcTemplate jdbc;
    private final RequestContext context;
    private final ObjectMapper mapper;
    private final RegistryTableRenderer renderer;
    private final RegistryDocumentClient documents;

    public AdminAttendanceExportService(
            AdminAttendanceReportService reports,
            AcademicAdminAccess access,
            JdbcTemplate jdbc,
            RequestContext context,
            ObjectMapper mapper,
            RegistryTableRenderer renderer,
            RegistryDocumentClient documents) {
        this.reports = reports;
        this.access = access;
        this.jdbc = jdbc;
        this.context = context;
        this.mapper = mapper;
        this.renderer = renderer;
        this.documents = documents;
    }

    public List<FormatOption> formats() {
        access.require("DASHBOARD_VIEW");
        return List.of(
                new FormatOption("html", "HTML", "text/html", "html"),
                new FormatOption("pdf", "PDF", "application/pdf", "pdf"),
                new FormatOption(
                        "docx",
                        "DOCX",
                        "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                        "docx"),
                new FormatOption(
                        "xlsx",
                        "XLSX",
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                        "xlsx"),
                new FormatOption("png", "PNG, архив страниц", "application/zip", "zip"));
    }

    public Download generate(Export query, UUID jobId, UUID leaseId) {
        access.require("DASHBOARD_VIEW");
        requireJob(query, jobId, leaseId);
        var report =
                reports.full(query.semesterId(), query.search(), query.sort(), query.direction());
        List<List<Object>> rows =
                report.rows().stream()
                        .map(
                                row ->
                                        List.<Object>of(
                                                row.groupName(),
                                                percentage(row.presentPercent()),
                                                percentage(row.presentOrExcusedPercent()),
                                                row.countedLessons(),
                                                row.presentCount(),
                                                row.absentCount(),
                                                row.excusedCount(),
                                                row.opportunities()))
                        .toList();
        var table =
                new RegistryTableRenderer.Table(
                        "Посещаемость групп",
                        List.of(
                                "Семестр: " + report.semester().name(),
                                "Период: "
                                        + report.semester().dateFrom()
                                        + " — "
                                        + report.semester().dateTo(),
                                "Поиск: " + report.search(),
                                "Порядок: " + report.sort() + " " + report.direction(),
                                "Учтены завершённые неотменённые пары; нет данных отличается от"
                                        + " 0%"),
                        List.of(
                                "Группа",
                                "%+",
                                "%(+ и у)",
                                "Учтено пар",
                                "+",
                                "н",
                                "у",
                                "Отметок в знаменателе"),
                        rows);
        byte[] content =
                switch (query.format()) {
                    case html -> renderer.html(table);
                    case docx -> renderer.docx(table);
                    case xlsx -> renderer.xlsx(table);
                    case pdf, png ->
                            documents.convert(renderer.docx(table), query.format() == Format.png);
                };
        if (content == null || content.length == 0)
            throw AdminAttendanceReportService.unavailable();
        if (content.length > 20 * 1024 * 1024)
            throw new ResponseStatusException(
                    HttpStatus.PAYLOAD_TOO_LARGE, "Файл превышает допустимый размер");
        // Recheck both the live capability and exact lease after potentially slow rendering.
        access.require("DASHBOARD_VIEW");
        requireJob(query, jobId, leaseId);
        var format =
                formats().stream()
                        .filter(value -> value.code().equals(query.format().name()))
                        .findFirst()
                        .orElseThrow();
        return new Download(
                "admin-group-attendance." + format.extension(),
                format.mime(),
                content,
                rows.size());
    }

    private void requireJob(Export query, UUID jobId, UUID leaseId) {
        if (query == null || jobId == null || leaseId == null || context.getClaims() == null)
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN, "Требуется текущее задание выгрузки");
        var claims = context.getClaims();
        var rows =
                jdbc.query(
                        "SELECT selector_json AS selector FROM"
                                + " auth_api.read_export_selector(?,?,?,?,?,?,?,?)",
                        (rs, n) -> rs.getString("selector"),
                        claims.userId(),
                        claims.rolesVersion(),
                        claims.sessionId(),
                        claims.sessionVersion(),
                        jobId,
                        leaseId,
                        "ADMIN_GROUP_ATTENDANCE",
                        "adminGroupAttendance");
        try {
            if (rows.size() != 1
                    || !mapper.readTree(rows.getFirst()).equals(mapper.valueToTree(query)))
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Разрез задания изменился");
        } catch (JsonProcessingException invalid) {
            throw AdminAttendanceReportService.unavailable();
        }
    }

    private static Object percentage(Double value) {
        return value == null ? "Нет данных" : value;
    }

    public record Download(String filename, String contentType, byte[] content, int rowCount) {
        @Override
        public String toString() {
            return "AdminAttendanceDownload[rows=" + rowCount + "]";
        }
    }
}
