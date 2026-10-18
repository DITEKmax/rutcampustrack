package ru.rutcampustrack.academic.adminattendance;

import io.grpc.StatusRuntimeException;

import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import ru.rutcampustrack.academic.admin.AcademicAdminAccess;
import ru.rutcampustrack.academic.contract.dto.adminattendance.AdminAttendanceReportModels.*;
import ru.rutcampustrack.academic.identitycatalog.AuthIdentityDataClient;
import ru.rutcampustrack.academic.security.RequestContext;
import ru.rutcampustrack.shared.security.admin.AdminAccessException;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Service
public class AdminAttendanceReportService {
    private final AcademicAdminAccess access;
    private final AdminAttendanceReadRepository repository;
    private final AttendanceAdminGroupStatsClient attendance;
    private final RequestContext context;
    private final Clock clock;
    private final AuthIdentityDataClient identities;

    public AdminAttendanceReportService(
            AcademicAdminAccess access,
            AdminAttendanceReadRepository repository,
            AttendanceAdminGroupStatsClient attendance,
            RequestContext context,
            Clock clock,
            AuthIdentityDataClient identities) {
        this.access = access;
        this.repository = repository;
        this.attendance = attendance;
        this.context = context;
        this.clock = clock;
        this.identities = identities;
    }

    public Semesters semesters() {
        requireCurrentAccess();
        try {
            Semesters result = new Semesters(repository.semesters());
            requireCurrentAccess();
            return result;
        } catch (DataAccessException unavailable) {
            throw unavailable();
        }
    }

    public Page read(
            String semesterId, String search, Sort sort, Direction direction, int page, int size) {
        if (page < 0 || size < 1 || size > 100) throw invalid();
        FullReport result = full(semesterId, search, sort, direction);
        long from = Math.min((long) page * size, result.rows().size());
        int to = (int) Math.min(from + size, result.rows().size());
        return new Page(
                result.semester(),
                result.search(),
                sort,
                direction,
                page,
                size,
                result.rows().size(),
                (result.rows().size() + size - 1L) / size,
                result.observedAt(),
                result.rows().subList((int) from, to));
    }

    public FullReport full(String semesterId, String search, Sort sort, Direction direction) {
        requireCurrentAccess();
        long id = positiveId(semesterId);
        if (search == null || search.length() > 200 || sort == null || direction == null)
            throw invalid();
        search = search.trim();
        try {
            var selected = repository.select(id, search);
            List<Row> rows = new ArrayList<>();
            for (var value : attendance.read(selected, context.getInternalToken())) {
                long total = value.getOpportunities();
                rows.add(
                        new Row(
                                Long.toString(value.getGroupId()),
                                selected.groups().get(value.getGroupId()),
                                value.getCountedLessons(),
                                value.getPresent(),
                                value.getAbsent(),
                                value.getExcused(),
                                total,
                                total == 0 ? null : value.getPresentPercent(),
                                total == 0 ? null : value.getPresentOrExcusedPercent(),
                                total == 0 ? DataState.NO_DATA : DataState.AVAILABLE));
            }
            rows.sort(comparator(sort, direction));
            requireCurrentAccess();
            return new FullReport(
                    selected.semester(),
                    search,
                    sort,
                    direction,
                    clock.instant(),
                    List.copyOf(rows));
        } catch (DataAccessException unavailable) {
            throw unavailable();
        }
    }

    /**
     * Live Auth checks run before/after completed local reads, never under their DB transaction.
     */
    void requireCurrentAccess() {
        access.require("DASHBOARD_VIEW");
        try {
            identities.checkAdminAccess(
                    List.of("DASHBOARD_VIEW"),
                    false,
                    System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(3));
        } catch (StatusRuntimeException failure) {
            switch (failure.getStatus().getCode()) {
                case PERMISSION_DENIED, UNAUTHENTICATED -> throw AdminAccessException.denied();
                default -> throw unavailable();
            }
        }
    }

    static Comparator<Row> comparator(Sort sort, Direction direction) {
        Comparator<Double> numbers =
                direction == Direction.DESC ? Comparator.reverseOrder() : Comparator.naturalOrder();
        Comparator<Row> order =
                switch (sort) {
                    case PRESENT_PERCENT ->
                            Comparator.comparing(
                                    Row::presentPercent, Comparator.nullsLast(numbers));
                    case PRESENT_OR_EXCUSED_PERCENT ->
                            Comparator.comparing(
                                    Row::presentOrExcusedPercent, Comparator.nullsLast(numbers));
                    default -> {
                        Comparator<Row> other =
                                switch (sort) {
                                    case GROUP_NAME ->
                                            Comparator.comparing(
                                                    Row::groupName, String.CASE_INSENSITIVE_ORDER);
                                    case COUNTED_LESSONS ->
                                            Comparator.comparingLong(Row::countedLessons);
                                    case PRESENT -> Comparator.comparingLong(Row::presentCount);
                                    case ABSENT -> Comparator.comparingLong(Row::absentCount);
                                    case EXCUSED -> Comparator.comparingLong(Row::excusedCount);
                                    default -> throw invalid();
                                };
                        yield direction == Direction.DESC ? other.reversed() : other;
                    }
                };
        return order.thenComparingLong(row -> Long.parseLong(row.groupId()));
    }

    static long positiveId(String value) {
        try {
            if (value == null || !value.matches("[1-9][0-9]{0,18}")) throw invalid();
            return Long.parseLong(value);
        } catch (NumberFormatException invalid) {
            throw invalid();
        }
    }

    static ResponseStatusException invalid() {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, "Некорректный разрез отчёта");
    }

    static ResponseStatusException unavailable() {
        return new ResponseStatusException(
                HttpStatus.SERVICE_UNAVAILABLE, "Данные отчёта недоступны");
    }

    public record FullReport(
            Semester semester,
            String search,
            Sort sort,
            Direction direction,
            Instant observedAt,
            List<Row> rows) {}
}
