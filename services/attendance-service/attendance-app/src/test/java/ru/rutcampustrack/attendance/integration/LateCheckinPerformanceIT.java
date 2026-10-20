package ru.rutcampustrack.attendance.integration;

import static org.assertj.core.api.Assertions.assertThat;

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

/**
 * M05 Группа 1 — regression guard для compound index {@code lcr_group_status_created (group_id,
 * status, created_at)} на коллекции {@code late_checkin_requests}. Closes 04 P2-9.
 *
 * <p>До M05 query {@code findByGroupIdAndStatusOrderByCreatedAtAsc} делал COLLSCAN + in-memory sort
 * — на 6000 docs 2ms, на 60k (v0.1) = 20ms+. С compound index: IXSCAN, docsExamined = nReturned,
 * sort уходит (index order совпадает с запросом).
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class LateCheckinPerformanceIT extends AbstractAttendanceIntegrationTest {
    @org.springframework.test.context.DynamicPropertySource
    static void auditIsolation(org.springframework.test.context.DynamicPropertyRegistry registry) {
        ru.rutcampustrack.attendance.integration.AuditTestIsolation.register(
                registry, "latecheckinperformanceit", 10);
    }

    private static final Logger log = LoggerFactory.getLogger(LateCheckinPerformanceIT.class);

    private static final int GROUPS = 8;
    private static final int REQUESTS_PER_GROUP = 40;
    private List<LateCheckinRequest> seeded;

    @Autowired private LateCheckinRepository lateCheckinRepository;

    @BeforeEach
    void seed() {
        // Idempotent cleanup for seed docs (student_id >= 900500).
        mongoTemplate.remove(
                Query.query(Criteria.where("student_id").gte(900500L)), LateCheckinRequest.class);

        List<LateCheckinRequest> batch = new ArrayList<>(GROUPS * REQUESTS_PER_GROUP);
        LateCheckinRequestStatus[] statuses =
                new LateCheckinRequestStatus[] {
                    LateCheckinRequestStatus.PENDING, LateCheckinRequestStatus.PENDING,
                    LateCheckinRequestStatus.PENDING, LateCheckinRequestStatus.PENDING,
                    LateCheckinRequestStatus.APPROVED, LateCheckinRequestStatus.APPROVED,
                    LateCheckinRequestStatus.APPROVED, LateCheckinRequestStatus.APPROVED,
                    LateCheckinRequestStatus.REJECTED, LateCheckinRequestStatus.REJECTED,
                };

        Instant now = Instant.parse("2026-09-01T12:00:00Z");
        for (int g = 1; g <= GROUPS; g++) {
            long groupId = 900000L + g;
            for (int r = 1; r <= REQUESTS_PER_GROUP; r++) {
                int studentOffset = (r - 1) % 25;
                long studentId = 900500L + (g - 1) * 25L + studentOffset;
                long lessonId = 800000L + ((g * 300L + r) % 10000);
                LateCheckinRequestStatus status = statuses[r % statuses.length];
                int createdOffsetDays = (r * 137) % 30;
                Instant createdAt = now.minus(r * 137L, ChronoUnit.SECONDS);

                batch.add(
                        LateCheckinRequest.builder()
                                .studentId(studentId)
                                .groupId(groupId)
                                .lessonId(lessonId)
                                .studentName("M05 Student g" + g + " r" + r)
                                .status(status)
                                .decisionBy(
                                        status != LateCheckinRequestStatus.PENDING
                                                ? 900100L + (r % 20)
                                                : null)
                                .decisionAt(
                                        status != LateCheckinRequestStatus.PENDING
                                                ? createdAt.plus(1, ChronoUnit.HOURS)
                                                : null)
                                .createdAt(createdAt)
                                .updatedAt(createdAt)
                                .build());
            }
        }
        seeded = new ArrayList<>(mongoTemplate.insertAll(batch));

        // Sanity: compound index exists (applied by MongoConfig.initIndexes()).
        boolean hasCompound =
                mongoTemplate.indexOps("late_checkin_requests").getIndexInfo().stream()
                        .anyMatch(i -> "lcr_group_status_created".equals(i.getName()));
        assertThat(hasCompound)
                .as("lcr_group_status_created index should be auto-registered on startup")
                .isTrue();
    }

    @Test
    void lateCheckinQueryReturnsExactScopeInIndexOrderWithoutBlockingSort() {
        Long groupId = 900004L;
        var expected =
                seeded.stream()
                        .filter(
                                row ->
                                        row.getGroupId().equals(groupId)
                                                && row.getStatus()
                                                        == LateCheckinRequestStatus.PENDING)
                        .sorted(java.util.Comparator.comparing(LateCheckinRequest::getCreatedAt))
                        .toList();
        var actual =
                lateCheckinRepository.findByGroupIdAndStatusOrderByCreatedAtAsc(
                        groupId, LateCheckinRequestStatus.PENDING);
        assertThat(actual)
                .extracting(LateCheckinRequest::getId)
                .containsExactlyElementsOf(
                        expected.stream().map(LateCheckinRequest::getId).toList());
        assertThat(actual)
                .allSatisfy(
                        row -> {
                            assertThat(row.getGroupId()).isEqualTo(groupId);
                            assertThat(row.getStatus()).isEqualTo(LateCheckinRequestStatus.PENDING);
                        });
        assertThat(actual).extracting(LateCheckinRequest::getCreatedAt).isSorted();
        org.bson.Document query =
                new org.bson.Document("find", "late_checkin_requests")
                        .append(
                                "filter",
                                new org.bson.Document("group_id", groupId)
                                        .append("status", "PENDING"))
                        .append("sort", new org.bson.Document("created_at", 1));
        var explain =
                mongoTemplate
                        .getDb()
                        .runCommand(
                                new org.bson.Document("explain", query)
                                        .append("verbosity", "executionStats"));
        String plan =
                explain.get("queryPlanner", org.bson.Document.class)
                        .get("winningPlan", org.bson.Document.class)
                        .toJson();
        assertThat(plan).contains("lcr_group_status_created").doesNotContain("COLLSCAN");
        assertThat(plan.replace(" ", "")).doesNotContain("\"stage\":\"SORT\"");
        var stats = explain.get("executionStats", org.bson.Document.class);
        assertThat(((Number) stats.get("nReturned")).intValue()).isEqualTo(expected.size());
        assertThat(((Number) stats.get("totalDocsExamined")).intValue())
                .isLessThanOrEqualTo(expected.size() + 2);
    }
}
