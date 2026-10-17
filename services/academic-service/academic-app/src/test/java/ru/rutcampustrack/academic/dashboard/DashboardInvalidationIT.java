package ru.rutcampustrack.academic.dashboard;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import ru.rutcampustrack.academic.contract.dto.dashboard.DashboardDailyGrowthResponse.Status;
import ru.rutcampustrack.academic.contract.dto.group.AssignHeadmanRequest;
import ru.rutcampustrack.academic.contract.dto.user.UserArchiveModels.RestoreRequest;
import ru.rutcampustrack.academic.entity.Group;
import ru.rutcampustrack.academic.group.GroupArchivalService;
import ru.rutcampustrack.academic.group.GroupHeadmanAssignmentService;
import ru.rutcampustrack.academic.integration.AbstractAcademicIntegrationTest;
import ru.rutcampustrack.academic.repository.GroupRepository;
import ru.rutcampustrack.academic.repository.UserRoleGrantWriter;
import ru.rutcampustrack.academic.user.UserArchiveService;

/** Real SQL proof for readable counters and the BEFORE_COMMIT durable signal. */
@Import(DashboardInvalidationIT.ObservationClockFixture.class)
class DashboardInvalidationIT extends AbstractAcademicIntegrationTest {
    @Autowired JdbcTemplate jdbc;
    @Autowired UserRoleGrantWriter grants;
    @Autowired DashboardService dashboard;
    @Autowired PlatformTransactionManager transactionManager;
    @Autowired ObjectMapper mapper;
    @Autowired GroupRepository groups;
    @Autowired GroupArchivalService archival;
    @Autowired GroupHeadmanAssignmentService headmen;
    @Autowired UserArchiveService archives;
    @Autowired ObservationClock observationClock;

    @BeforeEach
    void startObservedMoscowDay() {
        observationClock.at("2026-09-30T20:55:00Z");
    }

    @Test
    void committedGrantAndEmptySignalAreAtomicAndRollbackLeavesNeitherChange() throws Exception {
        long before = dashboard.getStats().getContent().getTotalTeachers();
        long user =
                jdbc.queryForObject(
                        """
                INSERT INTO users(login,password_hash,last_name,first_name,role,status,is_headman,
                                  password_changed,created_at,updated_at)
                VALUES (?,'synthetic-hash','Dashboard','Teacher','teacher','active',FALSE,FALSE,NOW(),NOW()) RETURNING id
                """,
                        Long.class,
                        UUID.randomUUID().toString().replace("-", ""));
        var tx = new TransactionTemplate(transactionManager);
        long signalsBefore = signals();
        try {
            tx.executeWithoutResult(
                    status -> {
                        grants.upsertRole(user, "teacher", "active", null);
                        assertThat(signals())
                                .isEqualTo(signalsBefore); // not publishable inside domain mutation
                    });
            assertThat(dashboard.getStats().getContent().getTotalTeachers()).isEqualTo(before + 1);
            assertThat(signals()).isEqualTo(signalsBefore + 1);
            String json =
                    jdbc.queryForObject(
                            "SELECT payload::text FROM academic_outbox "
                                    + "WHERE event_type='dashboard.invalidated' ORDER BY id DESC LIMIT 1",
                            String.class);
            var envelope = mapper.readTree(json);
            assertThat(envelope.path("payload").isObject()).isTrue();
            assertThat(envelope.path("payload").size()).isZero();
            assertThat(envelope.path("source").asText()).isEqualTo("academic-service");
            tx.executeWithoutResult(
                    status -> {
                        grants.upsertRole(user, "teacher", "suspended", null);
                        status.setRollbackOnly();
                    });
            assertThat(dashboard.getStats().getContent().getTotalTeachers()).isEqualTo(before + 1);
            assertThat(signals()).isEqualTo(signalsBefore + 1);
        } finally {
            jdbc.update("DELETE FROM user_role_grants WHERE user_id=?", user);
            jdbc.update("DELETE FROM users WHERE id=?", user);
        }
    }

    private long signals() {
        return jdbc.queryForObject(
                "SELECT COUNT(*) FROM academic_outbox WHERE event_type='dashboard.invalidated'",
                Long.class);
    }

    @Test
    void canonicalGraduationArchiveCommitsReadableGroupCounterAndSignalButRollbackAndRepeatDoNot() {
        String random = UUID.randomUUID().toString();
        String alphabet = "АБВГДЕЖЗИКЛМНОПРС";
        String prefix =
                "ДР"
                        + alphabet.charAt(Character.digit(random.charAt(0), 16))
                        + alphabet.charAt(Character.digit(random.charAt(1), 16));
        Group group = new Group();
        group.setName(prefix + "-411");
        group.setAlphabeticCode(prefix);
        group.setNumericCode("411");
        group.setCurrentCourse(4);
        group.setTrainingDurationYears(4);
        group.setDurationStatus("KNOWN");
        group.setActive(true);
        group.setCreatedAt(OffsetDateTime.now());
        long groupId = groups.saveAndFlush(group).getId();
        long headman =
                jdbc.queryForObject(
                        """
                INSERT INTO users(login,password_hash,last_name,first_name,role,status,is_headman,
                                  password_changed,created_at,updated_at)
                VALUES (?,'synthetic-hash','Dashboard','Headman','student','active',FALSE,FALSE,NOW(),NOW()) RETURNING id
                """,
                        Long.class,
                        UUID.randomUUID().toString().replace("-", ""));
        jdbc.update(
                "INSERT INTO user_role_grants(user_id,role,status,group_id,created_at,updated_at) "
                        + "VALUES (?,'headman','active',?,NOW(),NOW())",
                headman,
                groupId);
        var tx = new TransactionTemplate(transactionManager);
        long activeBefore = dashboard.getStats().getContent().getActiveGroups();
        long signalsBefore = signals();
        long archivedBefore = archivedSignals();
        try {
            tx.executeWithoutResult(
                    status -> {
                        archival.archive(groups.findByIdForUpdate(groupId).orElseThrow(), 2026);
                        groups.flush();
                        status.setRollbackOnly();
                    });
            assertThat(groups.findById(groupId).orElseThrow().isActive()).isTrue();
            assertThat(dashboard.getStats().getContent().getActiveGroups()).isEqualTo(activeBefore);
            assertThat(signals()).isEqualTo(signalsBefore);
            assertThat(archivedSignals()).isEqualTo(archivedBefore);
            tx.executeWithoutResult(
                    status ->
                            archival.archive(
                                    groups.findByIdForUpdate(groupId).orElseThrow(), 2026));
            assertThat(groups.findById(groupId).orElseThrow().isActive()).isFalse();
            assertThat(dashboard.getStats().getContent().getActiveGroups())
                    .isEqualTo(activeBefore - 1);
            assertThat(signals()).isEqualTo(signalsBefore + 1);
            assertThat(archivedSignals()).isEqualTo(archivedBefore + 1);
            assertThatThrownBy(
                            () ->
                                    tx.executeWithoutResult(
                                            status ->
                                                    archival.archive(
                                                            groups.findByIdForUpdate(groupId)
                                                                    .orElseThrow(),
                                                            2026)))
                    .isInstanceOf(IllegalStateException.class);
            assertThat(signals()).isEqualTo(signalsBefore + 1);
            assertThat(archivedSignals()).isEqualTo(archivedBefore + 1);
        } finally {
            jdbc.update("DELETE FROM user_role_grants WHERE user_id=?", headman);
            jdbc.update("DELETE FROM users WHERE id=?", headman);
            jdbc.update("DELETE FROM groups WHERE id=?", groupId);
        }
    }

    @Test
    void dailyGrowthKeepsClosedMoscowBaselineAcrossRolesHeadmanRestoreAndQuietDays() {
        Group group = countGroup();
        long student = countUser("student", "active", group.getId());
        jdbc.update(
                "INSERT INTO user_role_grants(user_id,role,status,group_id,created_at,updated_at) "
                        + "VALUES (?,'student','active',?,NOW(),NOW())",
                student,
                group.getId());
        long teacher = countUser("teacher", "active", null);
        long restored = countUser("teacher", "archived", null);
        jdbc.update(
                "INSERT INTO user_role_grants(user_id,role,status,created_at,updated_at) "
                        + "VALUES (?,'teacher','archived',NOW(),NOW())",
                restored);
        var baseline = dashboard.getStats().getContent();
        assertThat(baseline.getDailyGrowth().status()).isEqualTo(Status.UNKNOWN_HISTORY);
        assertThat(baseline.getDailyGrowth().baselineDate()).isEqualTo(LocalDate.of(2026, 9, 29));
        assertThat(baseline.getDailyGrowth().studentsDelta()).isNull();
        assertThat(baseline.getDailyGrowth().teachersDelta()).isNull();
        assertThat(baseline.getDailyGrowth().groupsDelta()).isNull();
        var closed = closedScaleDay(LocalDate.of(2026, 9, 30));

        // 21:05 UTC is already the next Moscow day. The HEADMAN path emits only GroupUpdatedEvent.
        observationClock.at("2026-09-30T21:05:00Z");
        headmen.assign(group.getId(), new AssignHeadmanRequest(student, null));
        var tx = new TransactionTemplate(transactionManager);
        tx.executeWithoutResult(
                ignored -> {
                    grants.upsertRole(teacher, "teacher", "active", null);
                    grants.upsertRole(teacher, "student", "active", group.getId());
                });
        var changed = dashboard.getStats().getContent();
        assertThat(changed.getTotalStudents()).isEqualTo(baseline.getTotalStudents() + 1);
        assertThat(changed.getTotalTeachers()).isEqualTo(baseline.getTotalTeachers() + 1);
        assertThat(changed.getActiveGroups()).isEqualTo(baseline.getActiveGroups() + 1);
        assertThat(changed.getDailyGrowth().status()).isEqualTo(Status.AVAILABLE);
        assertThat(changed.getDailyGrowth().baselineDate()).isEqualTo(LocalDate.of(2026, 9, 30));
        assertThat(changed.getDailyGrowth().studentsDelta()).isEqualTo(1L);
        assertThat(changed.getDailyGrowth().teachersDelta()).isEqualTo(1L);
        assertThat(changed.getDailyGrowth().groupsDelta()).isEqualTo(1L);

        long changes = scaleChanges();
        tx.executeWithoutResult(
                status -> {
                    grants.upsertRole(teacher, "teacher", "suspended", null);
                    status.setRollbackOnly();
                });
        assertThat(scaleChanges()).isEqualTo(changes);
        assertThat(dashboard.getStats().getContent().getDailyGrowth().teachersDelta())
                .isEqualTo(1L);
        archives.restore(restored, new RestoreRequest(UUID.randomUUID()));
        assertThat(
                        jdbc.queryForObject(
                                "SELECT status::text FROM users WHERE id=?",
                                String.class,
                                restored))
                .isEqualTo("active");
        assertThat(closedScaleDay(LocalDate.of(2026, 9, 30))).isEqualTo(closed);
        assertThat(dashboard.getStats().getContent().getDailyGrowth().teachersDelta())
                .isEqualTo(1L);

        // No observations or mutations on October 2: the proven unchanged state carries forward.
        observationClock.at("2026-10-02T21:05:00Z");
        var quiet = dashboard.getStats().getContent().getDailyGrowth();
        assertThat(quiet.status()).isEqualTo(Status.AVAILABLE);
        assertThat(quiet.baselineDate()).isEqualTo(LocalDate.of(2026, 10, 2));
        assertThat(quiet.studentsDelta()).isZero();
        assertThat(quiet.teachersDelta()).isZero();
        assertThat(quiet.groupsDelta()).isZero();
        tx.executeWithoutResult(
                ignored -> grants.upsertRole(teacher, "teacher", "suspended", null));
        assertThat(dashboard.getStats().getContent().getDailyGrowth().teachersDelta())
                .isEqualTo(-1L);
        assertThat(closedScaleDay(LocalDate.of(2026, 9, 30))).isEqualTo(closed);
    }

    @Test
    void crossingCommitAndFailedAfterCommitStayUnknownUntilACompleteDayIsObserved() {
        long before = dashboard.getStats().getContent().getTotalTeachers();
        long teacher = countUser("teacher", "active", null);
        observationClock.at("2026-09-30T20:59:59Z");
        var tx = new TransactionTemplate(transactionManager);
        tx.executeWithoutResult(
                ignored -> {
                    grants.upsertRole(teacher, "teacher", "active", null);
                    TransactionSynchronizationManager.registerSynchronization(
                            new TransactionSynchronization() {
                                @Override
                                public void beforeCompletion() {
                                    // AFTER all BEFORE_COMMIT observers, BEFORE the physical
                                    // commit.
                                    observationClock.at("2026-09-30T21:00:01Z");
                                }
                            });
                });
        var crossed = dashboard.getStats().getContent();
        assertThat(crossed.getTotalTeachers()).isEqualTo(before + 1);
        assertThat(crossed.getDailyGrowth().status()).isEqualTo(Status.UNKNOWN_HISTORY);
        assertThat(crossed.getDailyGrowth().baselineDate()).isEqualTo(LocalDate.of(2026, 9, 30));

        // A real SQL failure after the domain commit must preserve domain success and a pending
        // marker. NOT VALID leaves existing snapshots intact but refuses the next snapshot write.
        jdbc.execute(
                "ALTER TABLE dashboard_scale_observations ADD CONSTRAINT dashboard_test_capture_failure "
                        + "CHECK (kind <> 'SNAPSHOT') NOT VALID");
        Throwable primary = null;
        try {
            tx.executeWithoutResult(
                    ignored -> grants.upsertRole(teacher, "teacher", "suspended", null));
            assertThat(
                            jdbc.queryForObject(
                                    "SELECT status FROM user_role_grants WHERE user_id=? AND role='teacher'",
                                    String.class,
                                    teacher))
                    .isEqualTo("suspended");
            assertThat(
                            jdbc.queryForObject(
                                    "SELECT COUNT(*) FROM dashboard_scale_observations WHERE kind='CHANGE' AND settled_at IS NULL",
                                    Long.class))
                    .isGreaterThan(0);
        } catch (RuntimeException | Error failure) {
            primary = failure;
            throw failure;
        } finally {
            try {
                jdbc.execute(
                        "ALTER TABLE dashboard_scale_observations DROP CONSTRAINT dashboard_test_capture_failure");
            } catch (RuntimeException cleanup) {
                var failure =
                        new IllegalStateException(
                                "Own dashboard capture-failure constraint cleanup failed");
                if (primary != null) primary.addSuppressed(failure);
                else throw failure;
            }
        }
        observationClock.at("2026-10-01T21:05:00Z");
        var repaired = dashboard.getStats().getContent();
        assertThat(repaired.getTotalTeachers()).isEqualTo(before);
        assertThat(repaired.getDailyGrowth().status()).isEqualTo(Status.UNKNOWN_HISTORY);
        assertThat(repaired.getDailyGrowth().baselineDate()).isEqualTo(LocalDate.of(2026, 10, 1));
        observationClock.at("2026-10-02T21:05:00Z");
        var covered = dashboard.getStats().getContent().getDailyGrowth();
        assertThat(covered.status()).isEqualTo(Status.AVAILABLE);
        assertThat(covered.teachersDelta()).isZero();
    }

    @Test
    void concurrentCommittedRoleChangesDoNotLoseObservedTotals() throws Exception {
        var baseline = dashboard.getStats().getContent();
        long firstUser = countUser("teacher", "active", null);
        long secondUser = countUser("teacher", "active", null);
        observationClock.at("2026-09-30T21:05:00Z");
        var entered = new CountDownLatch(2);
        var release = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(2);
        try {
            var first = executor.submit(() -> concurrentTeacher(firstUser, entered, release));
            var second = executor.submit(() -> concurrentTeacher(secondUser, entered, release));
            try {
                assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();
            } finally {
                release.countDown();
            }
            first.get(10, TimeUnit.SECONDS);
            second.get(10, TimeUnit.SECONDS);
        } finally {
            release.countDown();
            executor.shutdownNow();
            assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
        }
        // Inspect the automatic captures BEFORE a dashboard GET can repair or replace them.
        assertThat(closedScaleDay(LocalDate.of(2026, 10, 1)).get("total_teachers"))
                .isEqualTo(baseline.getTotalTeachers() + 2);
        var current = dashboard.getStats().getContent();
        assertThat(current.getTotalTeachers()).isEqualTo(baseline.getTotalTeachers() + 2);
        assertThat(current.getDailyGrowth().status()).isEqualTo(Status.AVAILABLE);
        assertThat(current.getDailyGrowth().teachersDelta()).isEqualTo(2L);
    }

    private void concurrentTeacher(long user, CountDownLatch entered, CountDownLatch release) {
        new TransactionTemplate(transactionManager)
                .executeWithoutResult(
                        ignored -> {
                            grants.upsertRole(user, "teacher", "active", null);
                            entered.countDown();
                            try {
                                if (!release.await(5, TimeUnit.SECONDS))
                                    throw new IllegalStateException(
                                            "Own scale writer barrier timed out");
                            } catch (InterruptedException failure) {
                                Thread.currentThread().interrupt();
                                throw new IllegalStateException("Own scale writer interrupted");
                            }
                        });
    }

    private long scaleChanges() {
        return jdbc.queryForObject(
                "SELECT COUNT(*) FROM dashboard_scale_observations WHERE kind='CHANGE'",
                Long.class);
    }

    private java.util.Map<String, Object> closedScaleDay(LocalDate date) {
        return jdbc.queryForMap(
                "SELECT total_students,total_teachers,total_groups FROM dashboard_scale_observations "
                        + "WHERE kind='SNAPSHOT' AND snapshot_day=?",
                date);
    }

    private long countUser(String role, String status, Long groupId) {
        return jdbc.queryForObject(
                """
                INSERT INTO users(login,password_hash,last_name,first_name,role,status,group_id,
                                  employee_number,is_headman,password_changed,created_at,updated_at)
                VALUES (?,'synthetic-hash','Dashboard','Counts',?::user_role,?::account_status,?,?,FALSE,FALSE,NOW(),NOW()) RETURNING id
                """,
                Long.class,
                UUID.randomUUID().toString().replace("-", ""),
                role,
                status,
                groupId,
                "DG-" + UUID.randomUUID().toString().substring(0, 20));
    }

    private Group countGroup() {
        Group group = new Group();
        group.setName("ДП-111");
        group.setAlphabeticCode("ДП");
        group.setNumericCode("111");
        group.setCurrentCourse(1);
        group.setTrainingDurationYears(4);
        group.setDurationStatus("KNOWN");
        group.setActive(true);
        group.setCreatedAt(OffsetDateTime.now());
        return groups.saveAndFlush(group);
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class ObservationClockFixture {
        @Bean
        @Primary
        ObservationClock dashboardObservationClock() {
            return new ObservationClock(
                    new AtomicReference<>(Instant.parse("2026-09-30T20:55:00Z")),
                    ZoneId.of("Europe/Moscow"));
        }
    }

    static final class ObservationClock extends Clock {
        private final AtomicReference<Instant> instant;
        private final ZoneId zone;

        ObservationClock(AtomicReference<Instant> instant, ZoneId zone) {
            this.instant = instant;
            this.zone = zone;
        }

        void at(String value) {
            instant.set(Instant.parse(value));
        }

        @Override
        public ZoneId getZone() {
            return zone;
        }

        @Override
        public Clock withZone(ZoneId value) {
            return new ObservationClock(instant, value);
        }

        @Override
        public Instant instant() {
            return instant.get();
        }
    }

    private long archivedSignals() {
        return jdbc.queryForObject(
                "SELECT COUNT(*) FROM academic_outbox WHERE event_type='group.archived'",
                Long.class);
    }
}
