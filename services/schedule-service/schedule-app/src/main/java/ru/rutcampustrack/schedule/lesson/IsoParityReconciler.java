package ru.rutcampustrack.schedule.lesson;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import ru.rutcampustrack.academic.grpc.SemesterResponse;
import ru.rutcampustrack.schedule.contract.enums.WeekType;
import ru.rutcampustrack.schedule.grpc.AcademicGrpcClient;
import ru.rutcampustrack.schedule.item.entity.ScheduleItem;

import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * One-shot reconciliation job: when schedule-service starts after the week-parity
 * algorithm switch (semester-relative → ISO-based), future PLANNED lessons
 * created under the old rule end up on the wrong ISO-parity. This component
 * detects the marker row in {@code iso_parity_reconciliation} and, if absent,
 * regenerates PLANNED lessons from today onward for every active schedule
 * template in the currently active semester.
 *
 * <p>After a successful run a marker row is inserted so the job never runs
 * again — even across restarts and rolling deploys. Failures (academic-service
 * unreachable, no active semester) leave the marker empty and are simply
 * logged so the next healthy boot completes the job.</p>
 */
@Component
@ConditionalOnProperty(
        prefix = "rct.schedule.iso-parity-reconciler",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true)
public class IsoParityReconciler {

    private static final Logger log = LoggerFactory.getLogger(IsoParityReconciler.class);
    private static final short MARKER_ID = 1;

    @PersistenceContext
    private EntityManager entityManager;

    private final LessonGenerationService lessonGenerationService;
    private final AcademicGrpcClient academicGrpcClient;
    private final TransactionTemplate txTemplate;
    private final Clock clock;

    public IsoParityReconciler(LessonGenerationService lessonGenerationService,
                               AcademicGrpcClient academicGrpcClient,
                               PlatformTransactionManager txManager,
                               Clock clock) {
        this.lessonGenerationService = lessonGenerationService;
        this.academicGrpcClient = academicGrpcClient;
        this.txTemplate = new TransactionTemplate(txManager);
        this.clock = clock;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void reconcileOnce() {
        try {
            if (Boolean.TRUE.equals(txTemplate.execute(status -> alreadyExecuted()))) {
                log.debug("ISO parity reconciliation already executed — skipping");
                return;
            }
            Integer regenerated = txTemplate.execute(status -> doReconcile());
            txTemplate.executeWithoutResult(status -> markExecuted(regenerated == null ? 0 : regenerated));
            log.info("ISO parity reconciliation complete: {} schedule items regenerated", regenerated);
        } catch (Exception e) {
            // Any failure (no active semester, academic-service unreachable, etc.) is
            // logged but not rethrown — the application must still start. Next healthy
            // boot will retry until the marker row is written.
            log.warn("ISO parity reconciliation skipped due to error — will retry on next boot: {}",
                    e.getMessage());
        }
    }

    private boolean alreadyExecuted() {
        Number count = (Number) entityManager
                .createNativeQuery("SELECT COUNT(*) FROM iso_parity_reconciliation WHERE id = :id")
                .setParameter("id", MARKER_ID)
                .getSingleResult();
        return count.longValue() > 0;
    }

    private int doReconcile() {
        SemesterResponse activeSemester = academicGrpcClient.getActiveSemester();
        LocalDate semesterStart = LocalDate.parse(activeSemester.getDateFrom());
        LocalDate semesterEnd = LocalDate.parse(activeSemester.getDateTo());
        // firstWeekType is ignored by the new algorithm but kept in the regenerate signature.
        WeekType firstWeekType = academicGrpcClient.parseSemesterFirstWeekType(activeSemester);
        LocalDate today = LocalDate.now(clock);

        @SuppressWarnings("unchecked")
        List<ScheduleItem> all = entityManager.createQuery(
                        "select si from ScheduleItem si "
                                + "where si.isActive = true and si.semesterId = :sid")
                .setParameter("sid", activeSemester.getId())
                .getResultList();

        int count = 0;
        for (ScheduleItem item : all) {
            // Regenerate EVERY active slot — including WeekType.ALL. Previously
            // ALL slots were skipped on the assumption that their dates stay
            // identical under both algorithms, but that left a gap after any
            // full wipe of `lessons` (manual cleanup, fresh DB, disaster
            // recovery): ALL-typed items would never be materialised by any
            // other code path. The reconciliation-specific delete variant is
            // a no-op when there are no PLANNED/CANCELLED rows, so running it
            // for ALL slots is safe and idempotent.
            lessonGenerationService.regenerateFromDateForReconciliation(
                    item, semesterStart, semesterEnd, firstWeekType, today);
            count++;
        }
        return count;
    }

    private void markExecuted(int regenerated) {
        entityManager.createNativeQuery(
                        "INSERT INTO iso_parity_reconciliation (id, executed_at, note) "
                                + "VALUES (:id, :ts, :note) ON CONFLICT (id) DO NOTHING")
                .setParameter("id", MARKER_ID)
                .setParameter("ts", OffsetDateTime.now(clock))
                .setParameter("note", "Regenerated " + regenerated + " schedule items to ISO-parity")
                .executeUpdate();
    }
}
