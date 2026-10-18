package ru.rutcampustrack.academic.export;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

import ru.rutcampustrack.academic.admin.AcademicAdminAccess;
import ru.rutcampustrack.academic.contract.dto.export.RegistryExportModels;
import ru.rutcampustrack.academic.entity.User;
import ru.rutcampustrack.academic.exception.BadRequestException;
import ru.rutcampustrack.academic.group.GroupAssembler;
import ru.rutcampustrack.academic.group.GroupService;
import ru.rutcampustrack.academic.repository.UserRoleGrantReader;
import ru.rutcampustrack.academic.security.RequestContext;
import ru.rutcampustrack.academic.user.UserService;
import ru.rutcampustrack.shared.web.audit.AuditLogEntry;
import ru.rutcampustrack.shared.web.audit.AuditLogStorage;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class AdminRegistryExportService {
    private static final int MAX_ROWS = 10_000;
    private static final int MAX_BYTES = 20 * 1024 * 1024;
    private final AcademicAdminAccess access;
    private final GroupService groups;
    private final GroupAssembler groupAssembler;
    private final UserService users;
    private final UserRoleGrantReader grants;
    private final RegistryTableRenderer renderer;
    private final RegistryDocumentClient documents;
    private final AuditLogStorage audit;
    private final JdbcTemplate jdbc;
    private final RequestContext context;
    private final ObjectMapper mapper;
    private final TransactionTemplate snapshot;

    public AdminRegistryExportService(
            AcademicAdminAccess access,
            GroupService groups,
            GroupAssembler groupAssembler,
            UserService users,
            UserRoleGrantReader grants,
            RegistryTableRenderer renderer,
            RegistryDocumentClient documents,
            AuditLogStorage audit,
            JdbcTemplate jdbc,
            RequestContext context,
            ObjectMapper mapper,
            PlatformTransactionManager transactionManager) {
        this.access = access;
        this.groups = groups;
        this.groupAssembler = groupAssembler;
        this.users = users;
        this.grants = grants;
        this.renderer = renderer;
        this.documents = documents;
        this.audit = audit;
        this.jdbc = jdbc;
        this.context = context;
        this.mapper = mapper;
        this.snapshot = new TransactionTemplate(transactionManager);
        this.snapshot.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
        this.snapshot.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.snapshot.setReadOnly(true);
    }

    public List<RegistryExportModels.FormatOption> formats(String permission) {
        access.require(permission);
        return RegistryExportFormat.catalogue();
    }

    public Download groups(RegistryExportModels.Groups query, UUID jobId, UUID leaseId) {
        access.require("GROUPS_VIEW");
        boolean deferredAudit =
                asyncGeneration(jobId, leaseId, "ADMIN_GROUPS", "adminGroups", query);
        RegistryExportFormat format = RegistryExportFormat.from(query.format());
        Sort sort = sorts(query.sorts(), Set.of("name", "currentCourse", "programType"), false);
        var table =
                snapshot.execute(
                        transaction -> {
                            var page =
                                    groups.listAdminGroups(
                                                    query.status(),
                                                    query.search(),
                                                    query.course(),
                                                    query.studyMode(),
                                                    query.educationLevel(),
                                                    PageRequest.of(0, MAX_ROWS + 1, sort))
                                            .page();
                            requireSize(page.getTotalElements());
                            List<List<Object>> rows =
                                    page.getContent().stream()
                                            .map(groupAssembler::toRegistryResponse)
                                            .map(
                                                    row ->
                                                            List.<Object>of(
                                                                    text(row.name()),
                                                                    text(row.alphabeticCode()),
                                                                    text(row.numericCode()),
                                                                    number(row.currentCourse()),
                                                                    number(
                                                                            row
                                                                                    .trainingDurationYears()),
                                                                    row.studentCount(),
                                                                    text(row.headmanFio()),
                                                                    text(row.draftReason())))
                                            .toList();
                            return new RegistryTableRenderer.Table(
                                    "Реестр групп",
                                    List.of(
                                            "Разрез: "
                                                    + (query.status() == null
                                                            ? "ACTIVE"
                                                            : query.status().name()),
                                            "Поиск: " + text(query.search()),
                                            "Курс: " + text(query.course()),
                                            "Форма обучения: " + text(query.studyMode()),
                                            "Уровень образования: " + text(query.educationLevel()),
                                            "Порядок: " + sort,
                                            "Количество строк: " + rows.size(),
                                            "Сформировано: " + Instant.now()),
                                    List.of(
                                            "Группа",
                                            "Буквенный код",
                                            "Цифровой код",
                                            "Курс",
                                            "Срок обучения, лет",
                                            "Студентов",
                                            "Староста",
                                            "Почему черновик"),
                                    rows);
                        });
        Download file = render("groups", table, format);
        access.require("GROUPS_VIEW");
        if (!deferredAudit) record("groups", table.rows().size());
        return file;
    }

    public Download users(RegistryExportModels.Users query, UUID jobId, UUID leaseId) {
        access.require("USERS_VIEW");
        boolean deferredAudit = asyncGeneration(jobId, leaseId, "ADMIN_USERS", "adminUsers", query);
        boolean full = access.current().full();
        RegistryExportFormat format = RegistryExportFormat.from(query.format());
        Sort sort =
                sorts(
                        query.sorts(),
                        Set.of(
                                "id",
                                "login",
                                "lastName",
                                "firstName",
                                "middleName",
                                "role",
                                "status",
                                "employeeNumber",
                                "createdAt"),
                        true);
        var table =
                snapshot.execute(
                        transaction -> {
                            var page =
                                    users.listUsers(
                                            query.search(),
                                            query.role(),
                                            query.status(),
                                            query.roleStatus(),
                                            PageRequest.of(0, MAX_ROWS + 1, sort));
                            requireSize(page.getTotalElements());
                            var roles =
                                    grants.findByUserIds(
                                            page.getContent().stream().map(User::getId).toList());
                            List<List<Object>> rows = new ArrayList<>();
                            for (User user : page.getContent()) {
                                var userRoles = roles.getOrDefault(user.getId(), List.of());
                                String rolesLabel =
                                        userRoles.isEmpty()
                                                ? user.getRole() + ": " + user.getStatus()
                                                : String.join(
                                                        "; ",
                                                        userRoles.stream()
                                                                .map(
                                                                        grant ->
                                                                                grant.role()
                                                                                        + ": "
                                                                                        + grant
                                                                                                .status())
                                                                .toList());
                                String groupLabel =
                                        String.join(
                                                "; ",
                                                userRoles.stream()
                                                        .filter(grant -> grant.groupId() != null)
                                                        .map(
                                                                grant ->
                                                                        grant.groupName() == null
                                                                                ? grant.groupId()
                                                                                        .toString()
                                                                                : grant.groupName())
                                                        .distinct()
                                                        .toList());
                                List<Object> row =
                                        new ArrayList<>(
                                                List.of(
                                                        String.join(
                                                                        " ",
                                                                        List.of(
                                                                                text(
                                                                                        user
                                                                                                .getLastName()),
                                                                                text(
                                                                                        user
                                                                                                .getFirstName()),
                                                                                text(
                                                                                        user
                                                                                                .getMiddleName())))
                                                                .trim(),
                                                        text(user.getLogin()),
                                                        rolesLabel,
                                                        groupLabel,
                                                        text(user.getEmployeeNumber()),
                                                        user.getTelegramId() == null
                                                                ? "Не привязан"
                                                                : "Привязан"));
                                if (full) row.add(text(user.getTelegramId()));
                                rows.add(row);
                            }
                            List<String> columns =
                                    new ArrayList<>(
                                            List.of(
                                                    "ФИО",
                                                    "Логин",
                                                    "Роли и статусы",
                                                    "Группа",
                                                    "Табельный номер",
                                                    "Привязка Telegram"));
                            if (full) columns.add("Telegram ID");
                            return new RegistryTableRenderer.Table(
                                    "Реестр пользователей",
                                    List.of(
                                            "Поиск: " + text(query.search()),
                                            "Роль: " + text(query.role()),
                                            "Статус: " + text(query.status()),
                                            "Статус роли: " + text(query.roleStatus()),
                                            "Порядок: " + sort,
                                            "Количество строк: " + rows.size(),
                                            "Сформировано: " + Instant.now()),
                                    columns,
                                    rows);
                        });
        Download file = render("users", table, format);
        access.require("USERS_VIEW");
        if (full && !access.current().full())
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Полный доступ отозван");
        if (!deferredAudit) record("users", table.rows().size());
        return new Download(
                file.filename(), file.contentType(), file.content(), file.rowCount(), full);
    }

    private Download render(
            String kind, RegistryTableRenderer.Table table, RegistryExportFormat format) {
        byte[] bytes =
                switch (format) {
                    case DOCX -> renderer.docx(table);
                    case HTML -> renderer.html(table);
                    case XLSX -> renderer.xlsx(table);
                    case PDF, PNG ->
                            documents.convert(
                                    renderer.docx(table), format == RegistryExportFormat.PNG);
                };
        if (bytes == null || bytes.length == 0)
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE, "Файл не сформирован");
        if (bytes.length > MAX_BYTES)
            throw new ResponseStatusException(
                    HttpStatus.PAYLOAD_TOO_LARGE, "Выгрузка превышает лимит ответа");
        return new Download(
                kind + "." + format.extension, format.mime, bytes, table.rows().size(), false);
    }

    private boolean asyncGeneration(
            UUID jobId, UUID leaseId, String kind, String selector, Object query) {
        if (jobId == null && leaseId == null) return false;
        if (jobId == null || leaseId == null || context.getClaims() == null)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Некорректное задание");
        var claims = context.getClaims();
        var rows =
                jdbc.query(
                        "SELECT identity->'report'->? AS selector FROM academic_export_jobs"
                                + " WHERE user_id=? AND identity->'actor'->>'rolesVersion'=?"
                                + " AND session_id=? AND identity->'actor'->>'sessionVersion'=?"
                                + " AND job_id=? AND lease_id=? AND identity->'report'->>'kind'=?"
                                + " AND state='preparing' AND lease_until>NOW() AND expires_at>NOW()",
                        (rs, n) -> rs.getString("selector"),
                        selector,
                        claims.userId(),
                        Long.toString(claims.rolesVersion()),
                        claims.sessionId(),
                        Long.toString(claims.sessionVersion()),
                        jobId,
                        leaseId,
                        kind);
        try {
            if (rows.size() != 1
                    || !mapper.readTree(rows.getFirst()).equals(mapper.valueToTree(query))) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Разрез задания изменился");
            }
        } catch (com.fasterxml.jackson.core.JsonProcessingException invalid) {
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE, "Состояние задания недоступно");
        }
        return true;
    }

    private void record(String kind, int count) {
        audit.store(
                AuditLogEntry.builder("registry.export")
                        .userId(access.actorId())
                        .targetType(kind)
                        .extras(Map.of("kind", kind, "rowCount", count))
                        .build());
    }

    private static void requireSize(long total) {
        if (total > MAX_ROWS)
            throw new ResponseStatusException(
                    HttpStatus.PAYLOAD_TOO_LARGE,
                    "Сузь фильтры: слишком много строк для одной выгрузки");
    }

    private static Sort sorts(
            List<RegistryExportModels.SortOrder> values, Set<String> allowed, boolean userDefault) {
        if (values == null || values.isEmpty())
            return userDefault
                    ? Sort.by("lastName", "firstName", "middleName", "id")
                    : Sort.unsorted();
        if (values.size() > 8
                || values.stream()
                        .anyMatch(value -> value == null || !allowed.contains(value.field())))
            throw new BadRequestException("sort", "Недопустимая сортировка");
        List<Sort.Order> orders =
                new ArrayList<>(
                        values.stream()
                                .map(
                                        value ->
                                                new Sort.Order(
                                                        value.descending()
                                                                ? Sort.Direction.DESC
                                                                : Sort.Direction.ASC,
                                                        value.field()))
                                .toList());
        if (userDefault && orders.stream().noneMatch(order -> order.getProperty().equals("id")))
            orders.add(Sort.Order.asc("id"));
        return Sort.by(orders);
    }

    private static Object number(Integer value) {
        return value == null ? "" : value;
    }

    private static String text(Object value) {
        return value == null ? "" : value.toString();
    }

    public record Download(
            String filename,
            String contentType,
            byte[] content,
            int rowCount,
            boolean requiresFull) {
        @Override
        public String toString() {
            return "RegistryDownload[rows=" + rowCount + "]";
        }
    }
}
