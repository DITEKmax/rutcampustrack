package ru.rutcampustrack.attendance.integration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import ru.rutcampustrack.attendance.contract.enums.LateCheckinRequestStatus;
import ru.rutcampustrack.attendance.latecheckin.LateCheckinRepository;
import ru.rutcampustrack.attendance.latecheckin.entity.LateCheckinRequest;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;
import java.util.stream.LongStream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * M05 Группа 1 — regression guard для compound index
 * {@code lcr_group_status_created (group_id, status, created_at)}
 * на коллекции {@code late_checkin_requests}. Closes 04 P2-9.
 *
 * <p>До M05 query {@code findByGroupIdAndStatusOrderByCreatedAtAsc}
 * делал COLLSCAN + in-memory sort — на 6000 docs 2ms, на 60k (v0.1) = 20ms+.
 * С compound index: IXSCAN, docsExamined = nReturned, sort уходит
 * (index order совпадает с запросом).
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class LateCheckinPerformanceIT extends AbstractAttendanceIntegrationTest {

    private static final Logger log = LoggerFactory.getLogger(LateCheckinPerformanceIT.class);

    private static final int GROUPS = 20;
    private static final int REQUESTS_PER_GROUP = 300;
    private static final long QUERY_TIME_LIMIT_MS = 50L;

    @Autowired private LateCheckinRepository lateCheckinRepository;

    @BeforeEach
    void seed() {
        // Idempotent cleanup for seed docs (student_id >= 900500).
        mongoTemplate.remove(
                Query.query(Criteria.where("student_id").gte(900500L)),
                LateCheckinRequest.class);

        List<LateCheckinRequest> batch = new ArrayList<>(GROUPS * REQUESTS_PER_GROUP);
        LateCheckinRequestStatus[] statuses = new LateCheckinRequestStatus[] {
                LateCheckinRequestStatus.PENDING, LateCheckinRequestStatus.PENDING,
                LateCheckinRequestStatus.PENDING, LateCheckinRequestStatus.PENDING,
                LateCheckinRequestStatus.APPROVED, LateCheckinRequestStatus.APPROVED,
                LateCheckinRequestStatus.APPROVED, LateCheckinRequestStatus.APPROVED,
                LateCheckinRequestStatus.REJECTED, LateCheckinRequestStatus.REJECTED,
        };

        Instant now = Instant.now();
        for (int g = 1; g <= GROUPS; g++) {
            long groupId = 900000L + g;
            for (int r = 1; r <= REQUESTS_PER_GROUP; r++) {
                int studentOffset = (r - 1) % 25;
                long studentId = 900500L + (g - 1) * 25L + studentOffset;
                long lessonId = 800000L + ((g * 300L + r) % 10000);
                LateCheckinRequestStatus status = statuses[r % statuses.length];
                int createdOffsetDays = (r * 137) % 30;
                Instant createdAt = now.minus(createdOffsetDays, ChronoUnit.DAYS);

                batch.add(LateCheckinRequest.builder()
                        .studentId(studentId)
                        .groupId(groupId)
                        .lessonId(lessonId)
                        .studentName("M05 Student g" + g + " r" + r)
                        .status(status)
                        .decisionBy(status != LateCheckinRequestStatus.PENDING
                                ? 900100L + (r % 20) : null)
                        .decisionAt(status != LateCheckinRequestStatus.PENDING
                                ? createdAt.plus(1, ChronoUnit.HOURS) : null)
                        .createdAt(createdAt)
                        .updatedAt(createdAt)
                        .build());
            }
        }
        mongoTemplate.insertAll(batch);

        // Sanity: compound index exists (applied by MongoConfig.initIndexes()).
        boolean hasCompound = mongoTemplate
                .indexOps("late_checkin_requests")
                .getIndexInfo()
                .stream()
                .anyMatch(i -> "lcr_group_status_created".equals(i.getName()));
        assertThat(hasCompound)
                .as("lcr_group_status_created index should be auto-registered on startup")
                .isTrue();
    }

    @Test
    void lateCheckinQueryStaysUnder50ms() {
        Long groupId = 900010L;

        // Warm-up.
        lateCheckinRepository.findByGroupIdAndStatusOrderByCreatedAtAsc(
                groupId, LateCheckinRequestStatus.PENDING);

        long[] timings = IntStream.range(0, 5).mapToLong(i -> {
            long start = System.nanoTime();
            List<LateCheckinRequest> result = lateCheckinRepository
                    .findByGroupIdAndStatusOrderByCreatedAtAsc(
                            groupId, LateCheckinRequestStatus.PENDING);
            long elapsed = (System.nanoTime() - start) / 1_000_000L;
            assertThat(result).isNotEmpty();
            return elapsed;
        }).toArray();

        long best = LongStream.of(timings).min().orElseThrow();
        log.info("Q4 late_checkin findBy(group,status)order timings(ms): {} — best={}ms",
                timings, best);
        assertThat(best)
                .as("findByGroupIdAndStatusOrderByCreatedAtAsc — "
                        + "regression guard for lcr_group_status_created")
                .isLessThan(QUERY_TIME_LIMIT_MS);
    }
}
