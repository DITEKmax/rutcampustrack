package ru.rutcampustrack.academic.dashboard;

import jakarta.persistence.EntityManager;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import ru.rutcampustrack.academic.contract.dto.dashboard.DashboardDailyGrowthResponse;
import ru.rutcampustrack.academic.contract.dto.dashboard.DashboardDailyGrowthResponse.Status;
import ru.rutcampustrack.academic.event.DashboardInvalidatedEvent;
import ru.rutcampustrack.academic.event.GroupUpdatedEvent;
import ru.rutcampustrack.academic.repository.GroupRegistryReadRepository;
import ru.rutcampustrack.academic.repository.UserRoleGrantRepository;

/** Prospective scale history; an uncertain commit interval never becomes a yesterday snapshot. */
@Service
public class DashboardScaleObservationService {
    private static final Logger log =
            LoggerFactory.getLogger(DashboardScaleObservationService.class);
    private static final ZoneId MOSCOW = ZoneId.of("Europe/Moscow");
    // Only this final commit fence and snapshot reads take this lock. Snapshot reads never
    // lock domain rows, so they cannot invert the existing group/user lock order.
    private static final String LOCK = "SELECT pg_advisory_xact_lock(742110, 11)";

    private final JdbcTemplate jdbc;
    private final EntityManager entityManager;
    private final UserRoleGrantRepository grants;
    private final GroupRegistryReadRepository groups;
    private final Clock clock;
    private final TransactionTemplate captureTransaction;

    public DashboardScaleObservationService(
            JdbcTemplate jdbc,
            EntityManager entityManager,
            UserRoleGrantRepository grants,
            GroupRegistryReadRepository groups,
            Clock clock,
            PlatformTransactionManager transactionManager) {
        this.jdbc = jdbc;
        this.entityManager = entityManager;
        this.grants = grants;
        this.groups = groups;
        this.clock = clock;
        captureTransaction = new TransactionTemplate(transactionManager);
        captureTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        captureTransaction.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
    }

    // Assigning the first HEADMAN turns a registry DRAFT into ACTIVE and emits GroupUpdatedEvent.
    // Other count-changing role/archive/group paths already emit DashboardInvalidatedEvent.
    @TransactionalEventListener(
            classes = {DashboardInvalidatedEvent.class, GroupUpdatedEvent.class},
            phase = TransactionPhase.BEFORE_COMMIT)
    public void beforeCountChangeCommit() {
        if (TransactionSynchronizationManager.getSynchronizations().stream()
                .anyMatch(CountChangeCompletion.class::isInstance)) return;
        // Finish pending JPA changes before the last, shared commit fence. No count query
        // uses the domain transaction's possibly older REPEATABLE_READ snapshot.
        entityManager.flush();
        jdbc.execute(LOCK);
        jdbc.update(
                "INSERT INTO dashboard_scale_observations(kind,observed_at) VALUES ('CHANGE',?)",
                clock.instant().atZone(MOSCOW).toOffsetDateTime());
        TransactionSynchronizationManager.registerSynchronization(new CountChangeCompletion());
    }

    private final class CountChangeCompletion implements TransactionSynchronization {
        @Override
        public void afterCommit() {
            try {
                capture();
            } catch (RuntimeException failure) {
                // Domain success is already durable. The committed unsettled marker forces
                // unknown history until a later read can settle it conservatively.
                log.warn("Dashboard scale observation remains pending after committed change");
            }
        }
    }

    /** A new writable transaction also repairs a marker left by a crash/failed afterCommit. */
    public Snapshot capture() {
        return captureTransaction.execute(
                ignored -> {
                    jdbc.execute(LOCK);
                    DashboardScaleCounts current = DashboardScaleCounts.read(grants, groups);
                    Instant capturedAt = clock.instant();
                    LocalDate today = capturedAt.atZone(MOSCOW).toLocalDate();
                    LocalDate baselineDate = today.minusDays(1);
                    LocalDate latestDay =
                            jdbc.queryForObject(
                                    "SELECT max(snapshot_day) FROM dashboard_scale_observations WHERE kind='SNAPSHOT'",
                                    LocalDate.class);
                    if (latestDay != null && latestDay.isAfter(today)) {
                        // A calendar regression cannot rewrite a previously closed baseline.
                        return new Snapshot(
                                current, DashboardDailyGrowthResponse.unknown(baselineDate));
                    }
                    var captured = capturedAt.atZone(MOSCOW).toOffsetDateTime();
                    jdbc.update(
                            "UPDATE dashboard_scale_observations SET settled_at=GREATEST(observed_at,?) "
                                    + "WHERE kind='CHANGE' AND settled_at IS NULL",
                            captured);
                    jdbc.update(
                            """
                            INSERT INTO dashboard_scale_observations
                                (kind,observed_at,snapshot_day,total_students,total_teachers,total_groups)
                            VALUES ('SNAPSHOT',?,?,?,?,?)
                            ON CONFLICT (snapshot_day) WHERE kind='SNAPSHOT' DO UPDATE SET
                                observed_at=EXCLUDED.observed_at,
                                total_students=EXCLUDED.total_students,
                                total_teachers=EXCLUDED.total_teachers,
                                total_groups=EXCLUDED.total_groups
                            """,
                            captured,
                            today,
                            current.students(),
                            current.teachers(),
                            current.groups());
                    var cutoff = today.atStartOfDay(MOSCOW).toOffsetDateTime();
                    boolean uncertain =
                            Boolean.TRUE.equals(
                                    jdbc.queryForObject(
                                            """
                                            SELECT EXISTS(SELECT 1 FROM dashboard_scale_observations
                                              WHERE kind='CHANGE' AND observed_at < ?
                                                AND (settled_at IS NULL OR settled_at >= ?))
                                            """,
                                            Boolean.class,
                                            cutoff,
                                            cutoff));
                    var baseline =
                            jdbc.query(
                                    """
                                    SELECT total_students,total_teachers,total_groups
                                      FROM dashboard_scale_observations
                                     WHERE kind='SNAPSHOT' AND snapshot_day < ?
                                     ORDER BY snapshot_day DESC LIMIT 1
                                    """,
                                    (row, index) ->
                                            new DashboardScaleCounts(
                                                    row.getLong("total_students"),
                                                    row.getLong("total_teachers"),
                                                    row.getLong("total_groups")),
                                    today);
                    if (uncertain || baseline.isEmpty()) {
                        return new Snapshot(
                                current, DashboardDailyGrowthResponse.unknown(baselineDate));
                    }
                    // With no intervening changes, an earlier observed state carries forward
                    // through quiet days. A straddling/pending marker above prevents guessing.
                    DashboardScaleCounts previous = baseline.getFirst();
                    return new Snapshot(
                            current,
                            new DashboardDailyGrowthResponse(
                                    Status.AVAILABLE,
                                    baselineDate,
                                    current.students() - previous.students(),
                                    current.teachers() - previous.teachers(),
                                    current.groups() - previous.groups()));
                });
    }

    public record Snapshot(DashboardScaleCounts counts, DashboardDailyGrowthResponse dailyGrowth) {}
}
