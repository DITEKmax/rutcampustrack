package ru.rutcampustrack.academic.dashboard;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.grpc.StatusRuntimeException;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import ru.rutcampustrack.academic.contract.dto.IdentityOperationHttpModels;
import ru.rutcampustrack.academic.contract.dto.dashboard.DashboardDailyGrowthResponse.Status;
import ru.rutcampustrack.academic.contract.dto.group.AssignHeadmanRequest;
import ru.rutcampustrack.academic.contract.dto.user.CreateUserRequest;
import ru.rutcampustrack.academic.contract.dto.user.RoleGrantUpdateRequest;
import ru.rutcampustrack.academic.contract.dto.user.UserArchiveModels.ArchiveRequest;
import ru.rutcampustrack.academic.contract.dto.user.UserArchiveModels.RestoreRequest;
import ru.rutcampustrack.academic.contract.enums.RoleGrantStatus;
import ru.rutcampustrack.academic.contract.enums.UserRole;
import ru.rutcampustrack.academic.entity.Group;
import ru.rutcampustrack.academic.event.DashboardInvalidatedEvent;
import ru.rutcampustrack.academic.group.GroupArchivalService;
import ru.rutcampustrack.academic.group.GroupHeadmanAssignmentService;
import ru.rutcampustrack.academic.grpc.AttendanceUserImpactGrpcClient;
import ru.rutcampustrack.academic.identityoperation.AcademicIdentityReconciliationService;
import ru.rutcampustrack.academic.identityoperation.AcademicUserIdentityOperations;
import ru.rutcampustrack.academic.integration.AbstractAcademicIntegrationTest;
import ru.rutcampustrack.academic.integration.AcademicIdentityIntegrationFixture.SelectedActor;
import ru.rutcampustrack.academic.repository.GroupRepository;
import ru.rutcampustrack.academic.user.UserArchiveService;
import ru.rutcampustrack.attendance.grpc.UserImpactSnapshot;
import ru.rutcampustrack.auth.session.model.AuthRole;

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

/** Real SQL proof for readable counters and the BEFORE_COMMIT durable signal. */
@Import(DashboardInvalidationIT.ObservationClockFixture.class)
class DashboardInvalidationIT extends AbstractAcademicIntegrationTest {
    private static final String INITIAL_OBSERVATION = "2026-09-30T20:55:00Z";

    @Autowired JdbcTemplate jdbc;
    @Autowired AcademicUserIdentityOperations identities;
    @Autowired AcademicIdentityReconciliationService recovery;
    @Autowired LocalApplyObservation localApply;
    private SelectedActor admin;
    @Autowired DashboardService dashboard;
    @Autowired PlatformTransactionManager transactionManager;
    @Autowired ObjectMapper mapper;
    @Autowired GroupRepository groups;
    @Autowired GroupArchivalService archival;
    @Autowired GroupHeadmanAssignmentService headmen;
    @Autowired UserArchiveService archives;
    @Autowired ObservationClock observationClock;
    @MockitoBean AttendanceUserImpactGrpcClient attendanceImpact;

    @BeforeEach
    void startObservedMoscowDay() {
        observationClock.at(INITIAL_OBSERVATION);
        admin = identityFixture.select(identityFixture.canonical().adminId(), AuthRole.ADMIN);
    }

    @AfterEach
    void restoreClockBeforeNextCanonicalSeed() {
        // The common listener seeds with real writers before the next @BeforeEach. JUnit runs
        // this cleanup even when the current setup or test fails, so seed captures cannot inherit
        // the later Moscow day deliberately observed by the midnight/quiet-day scenarios.
        observationClock.at(INITIAL_OBSERVATION);
    }

    @Test
    void committedGrantAndEmptySignalAreAtomicAndRollbackLeavesNeitherChange() throws Exception {
        long before = dashboard.getStats().getContent().getTotalTeachers();
        long signalsBefore = signals();
        long user;
        try (var observation =
                localApply.observe(() -> assertThat(signals()).isEqualTo(signalsBefore))) {
            user = countUser("teacher", "active", null);
        }
        assertThat(dashboard.getStats().getContent().getTotalTeachers()).isEqualTo(before + 1);
        assertThat(signals()).isEqualTo(signalsBefore + 1);
        String json =
                jdbc.queryForObject(
                        "SELECT payload::text FROM academic_outbox WHERE"
                                + " event_type='dashboard.invalidated' ORDER BY id DESC LIMIT 1",
                        String.class);
        var envelope = mapper.readTree(json);
        assertThat(envelope.path("payload").isObject()).isTrue();
        assertThat(envelope.path("payload").size()).isZero();
        assertThat(envelope.path("source").asText()).isEqualTo("academic-service");
        UUID failed = rollbackTeacher(user, RoleGrantStatus.SUSPENDED);
        assertThat(teacherStatus(user)).isEqualTo("ACTIVE");
        assertThat(signals()).isEqualTo(signalsBefore + 1);
        // Rollback itself retains the old data and closes reads. A fresh accepted retry of the
        // same SUSPENDED intent then removes this teacher once; it does not restore old grants.
        reconcileTeacher(failed, user, RoleGrantStatus.SUSPENDED);
        assertThat(dashboard.getStats().getContent().getTotalTeachers()).isEqualTo(before);
        assertThat(signals()).isEqualTo(signalsBefore + 2);
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
        managedCoverage(groupId);
        long headman = countUser("student", "active", groupId);
        try (var request = identityFixture.request(admin)) {
            headmen.assign(groupId, new AssignHeadmanRequest(headman, null));
            succeeded(UUID.fromString(request.servletRequest().getHeader("Idempotency-Key")));
        }
        var tx = new TransactionTemplate(transactionManager);
        long activeBefore = dashboard.getStats().getContent().getActiveGroups();
        long signalsBefore = signals();
        long archivedBefore = archivedSignals();
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
                status -> archival.archive(groups.findByIdForUpdate(groupId).orElseThrow(), 2026));
        assertThat(groups.findById(groupId).orElseThrow().isActive()).isFalse();
        assertThat(dashboard.getStats().getContent().getActiveGroups()).isEqualTo(activeBefore - 1);
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
    }

    @Test
    void dailyGrowthKeepsClosedMoscowBaselineAcrossRolesHeadmanRestoreAndQuietDays() {
        Group group = countGroup();
        long student = countUser("student", "active", group.getId());
        long teacher = countUser("teacher", "suspended", null);
        long restored = countUser("teacher", "archived", null);
        var baseline = dashboard.getStats().getContent();
        assertThat(baseline.getDailyGrowth().status()).isEqualTo(Status.UNKNOWN_HISTORY);
        assertThat(baseline.getDailyGrowth().baselineDate()).isEqualTo(LocalDate.of(2026, 9, 29));
        assertThat(baseline.getDailyGrowth().studentsDelta()).isNull();
        assertThat(baseline.getDailyGrowth().teachersDelta()).isNull();
        assertThat(baseline.getDailyGrowth().groupsDelta()).isNull();
        var closed = closedScaleDay(LocalDate.of(2026, 9, 30));

        // 21:05 UTC is already the next Moscow day. Real HEADMAN assignment activates the group.
        observationClock.at("2026-09-30T21:05:00Z");
        try (var request = identityFixture.request(admin)) {
            headmen.assign(group.getId(), new AssignHeadmanRequest(student, null));
            succeeded(UUID.fromString(request.servletRequest().getHeader("Idempotency-Key")));
        }
        role(teacher, "TEACHER", RoleGrantStatus.ACTIVE, null);
        role(teacher, "STUDENT", RoleGrantStatus.ACTIVE, group.getId());
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
        UUID failed = rollbackTeacher(teacher, RoleGrantStatus.SUSPENDED);
        assertThat(scaleChanges()).isEqualTo(changes);
        assertThat(teacherStatus(teacher)).isEqualTo("ACTIVE");
        assertThat(closedScaleDay(LocalDate.of(2026, 9, 30))).isEqualTo(closed);
        reconcileTeacher(failed, teacher, RoleGrantStatus.SUSPENDED);
        // The explicit retry applies the original desired suspension after the proved rollback.
        assertThat(dashboard.getStats().getContent().getDailyGrowth().teachersDelta()).isZero();
        try (var request = identityFixture.request(admin, UUID.randomUUID())) {
            UUID operation = UUID.fromString(request.servletRequest().getHeader("Idempotency-Key"));
            archives.restore(restored, new RestoreRequest(operation));
            succeeded(operation);
        }
        assertThat(
                        jdbc.queryForObject(
                                "SELECT archived_at IS NULL FROM academic_profiles WHERE id=?",
                                Boolean.class,
                                restored))
                .isTrue();
        assertThat(closedScaleDay(LocalDate.of(2026, 9, 30))).isEqualTo(closed);
        assertThat(dashboard.getStats().getContent().getDailyGrowth().teachersDelta()).isZero();

        // No observations or mutations on October 2: the proven unchanged state carries forward.
        observationClock.at("2026-10-02T21:05:00Z");
        var quiet = dashboard.getStats().getContent().getDailyGrowth();
        assertThat(quiet.status()).isEqualTo(Status.AVAILABLE);
        assertThat(quiet.baselineDate()).isEqualTo(LocalDate.of(2026, 10, 2));
        assertThat(quiet.studentsDelta()).isZero();
        assertThat(quiet.teachersDelta()).isZero();
        assertThat(quiet.groupsDelta()).isZero();
        // Keep the independent quiet-day decrement on the fixture's still-active teacher;
        // the mixed-role teacher above was already suspended by its explicit retry.
        role(identityFixture.canonical().teacherId(), "TEACHER", RoleGrantStatus.SUSPENDED, null);
        assertThat(dashboard.getStats().getContent().getDailyGrowth().teachersDelta())
                .isEqualTo(-1L);
        assertThat(closedScaleDay(LocalDate.of(2026, 9, 30))).isEqualTo(closed);
    }

    @Test
    void crossingCommitAndFailedAfterCommitStayUnknownUntilACompleteDayIsObserved() {
        long teacher = countUser("teacher", "suspended", null);
        long before = dashboard.getStats().getContent().getTotalTeachers();
        observationClock.at("2026-09-30T20:59:59Z");
        try (var observation =
                localApply.observe(
                        () -> {
                            TransactionSynchronizationManager.registerSynchronization(
                                    new TransactionSynchronization() {
                                        @Override
                                        public void beforeCompletion() {
                                            // AFTER all BEFORE_COMMIT observers, BEFORE the
                                            // physical LOCAL APPLY commit.
                                            observationClock.at("2026-09-30T21:00:01Z");
                                        }
                                    });
                        })) {
            role(teacher, "TEACHER", RoleGrantStatus.ACTIVE, null);
        }
        var crossed = dashboard.getStats().getContent();
        assertThat(crossed.getTotalTeachers()).isEqualTo(before + 1);
        assertThat(crossed.getDailyGrowth().status()).isEqualTo(Status.UNKNOWN_HISTORY);
        assertThat(crossed.getDailyGrowth().baselineDate()).isEqualTo(LocalDate.of(2026, 9, 30));

        // A real SQL failure after the domain commit must preserve domain success and a pending
        // marker. NOT VALID leaves existing snapshots intact but refuses the next snapshot write.
        identityFixture
                .fixtureOwnerAcademicJdbc()
                .execute(
                        "ALTER TABLE dashboard_scale_observations ADD CONSTRAINT"
                                + " dashboard_test_capture_failure CHECK (kind <> 'SNAPSHOT') NOT"
                                + " VALID");
        Throwable primary = null;
        try {
            role(teacher, "TEACHER", RoleGrantStatus.SUSPENDED, null);
            assertThat(
                            jdbc.queryForObject(
                                    "SELECT status FROM academic_role_eligibility WHERE user_id=?"
                                            + " AND role='TEACHER'",
                                    String.class,
                                    teacher))
                    .isEqualTo("SUSPENDED");
            assertThat(
                            jdbc.queryForObject(
                                    "SELECT COUNT(*) FROM dashboard_scale_observations WHERE"
                                            + " kind='CHANGE' AND settled_at IS NULL",
                                    Long.class))
                    .isGreaterThan(0);
        } catch (RuntimeException | Error failure) {
            primary = failure;
            throw failure;
        } finally {
            try {
                identityFixture
                        .fixtureOwnerAcademicJdbc()
                        .execute(
                                "ALTER TABLE dashboard_scale_observations DROP CONSTRAINT"
                                        + " dashboard_test_capture_failure");
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
        long firstUser = countUser("teacher", "suspended", null);
        long secondUser = countUser("teacher", "suspended", null);
        var baseline = dashboard.getStats().getContent();
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
        try (var observation =
                localApply.observe(
                        () -> {
                            entered.countDown();
                            try {
                                if (!release.await(5, TimeUnit.SECONDS))
                                    throw new IllegalStateException(
                                            "Own scale writer barrier timed out");
                            } catch (InterruptedException failure) {
                                Thread.currentThread().interrupt();
                                throw new IllegalStateException(
                                        "Own scale writer interrupted", failure);
                            }
                        })) {
            role(user, "TEACHER", RoleGrantStatus.ACTIVE, null);
        }
    }

    private long scaleChanges() {
        return jdbc.queryForObject(
                "SELECT COUNT(*) FROM dashboard_scale_observations WHERE kind='CHANGE'",
                Long.class);
    }

    private java.util.Map<String, Object> closedScaleDay(LocalDate date) {
        return jdbc.queryForMap(
                "SELECT total_students,total_teachers,total_groups FROM"
                        + " dashboard_scale_observations WHERE kind='SNAPSHOT' AND snapshot_day=?",
                date);
    }

    private long countUser(String role, String status, Long groupId) {
        var request =
                new CreateUserRequest(
                        "Дашбордов",
                        "Счётчик",
                        null,
                        UserRole.valueOf(role.toUpperCase(java.util.Locale.ROOT)),
                        groupId,
                        role.equals("teacher")
                                ? "DG-" + UUID.randomUUID().toString().substring(0, 20)
                                : null,
                        role.equals("student")
                                ? 620000000000L
                                        + Math.abs(
                                                UUID.randomUUID().getLeastSignificantBits()
                                                        % 100000000L)
                                : null);
        long user = identityFixture.seedUser(request, admin).userId();
        UUID created =
                jdbc.queryForObject(
                        "SELECT t.operation_id FROM academic_identity_operation_targets t JOIN"
                                + " academic_identity_operations o ON o.operation_id=t.operation_id"
                                + " WHERE t.user_id=? AND o.kind='CREATE'",
                        UUID.class,
                        user);
        succeeded(created);
        if (status.equals("suspended")) role(user, "TEACHER", RoleGrantStatus.SUSPENDED, null);
        if (status.equals("archived")) {
            // This restore counter fixture has no active teacher responsibility or step-up need.
            role(user, "TEACHER", RoleGrantStatus.SUSPENDED, null);
            // The common fixture has real Auth/Academic only. This exact newly created target
            // has no Attendance data; the external read response is outside this test's proof.
            when(attendanceImpact.preview(user))
                    .thenReturn(
                            UserImpactSnapshot.newBuilder()
                                    .setUserId(user)
                                    .setAttendanceMarksCount(0)
                                    .setObservedAtEpochMs(Instant.now().toEpochMilli())
                                    .setSnapshotDigest("0".repeat(64))
                                    .build());
            UUID operation = UUID.randomUUID();
            try (var scope = identityFixture.request(admin, operation)) {
                var preview = archives.preview(user);
                assertThat(preview.requiresPassword()).isFalse();
                archives.archive(
                        user, new ArchiveRequest(operation, preview.previewDigest(), null));
                succeeded(operation);
            }
        }
        return user;
    }

    private void role(long user, String role, RoleGrantStatus status, Long group) {
        UUID operation = UUID.randomUUID();
        try (var request = identityFixture.request(admin, operation)) {
            var result =
                    identities.role(
                            user,
                            role,
                            new RoleGrantUpdateRequest(
                                    status,
                                    group,
                                    null,
                                    group == null ? null : 630000000000L + user));
            assertThat(result.id()).isEqualTo(operation.toString());
            succeeded(operation);
        }
    }

    private void succeeded(UUID operation) {
        assertThat(
                        jdbc.queryForObject(
                                "SELECT phase FROM academic_identity_operations WHERE"
                                        + " operation_id=?",
                                String.class,
                                operation))
                .isEqualTo("SUCCEEDED");
        // Explicit owner assertion only; authority is produced by the real Auth operation.
        assertThat(
                        identityFixture
                                .fixtureOwnerAuthJdbc()
                                .queryForObject(
                                        "SELECT phase FROM auth_identity_operations WHERE"
                                                + " operation_id=?",
                                        String.class,
                                        operation))
                .isEqualTo("COMMITTED");
    }

    private String teacherStatus(long user) {
        return jdbc.queryForObject(
                "SELECT status FROM academic_role_eligibility WHERE user_id=? AND role='TEACHER'",
                String.class,
                user);
    }

    private UUID rollbackTeacher(long user, RoleGrantStatus status) {
        UUID operation = UUID.randomUUID();
        String beforeState = academicState(user);
        String beforeHistory = membershipHistory(user);
        long before = signals();
        long beforeEvents = outboxRows();
        long beforeChanges = scaleChanges();
        try (var scope = identityFixture.request(admin, operation);
                var observation =
                        localApply.observe(
                                () -> {
                                    assertThat(signals()).isEqualTo(before);
                                    throw io.grpc.Status.ABORTED
                                            .withDescription("Own local APPLY rollback probe")
                                            .asRuntimeException();
                                })) {
            assertThatThrownBy(
                            () ->
                                    identities.role(
                                            user,
                                            "TEACHER",
                                            new RoleGrantUpdateRequest(status, null, null, null)))
                    .isInstanceOfSatisfying(
                            StatusRuntimeException.class,
                            failure ->
                                    assertThat(failure.getStatus().getCode())
                                            .isEqualTo(io.grpc.Status.Code.FAILED_PRECONDITION));
        }
        assertThat(
                        jdbc.queryForObject(
                                "SELECT phase FROM academic_identity_operations WHERE"
                                        + " operation_id=?",
                                String.class,
                                operation))
                .isEqualTo("FAILED_SAFE");
        assertThat(
                        identityFixture
                                .fixtureOwnerAuthJdbc()
                                .queryForObject(
                                        "SELECT phase FROM auth_identity_operations WHERE"
                                                + " operation_id=?",
                                        String.class,
                                        operation))
                .isEqualTo("FAILED_SAFE");
        assertThat(academicState(user)).isEqualTo(beforeState);
        assertThat(membershipHistory(user)).isEqualTo(beforeHistory);
        assertThat(signals()).isEqualTo(before);
        assertThat(outboxRows()).isEqualTo(beforeEvents);
        assertThat(scaleChanges()).isEqualTo(beforeChanges);
        assertThat(
                        jdbc.queryForObject(
                                "SELECT COUNT(*) FROM academic_identity_operation_targets WHERE"
                                    + " operation_id=? AND committed_academic_revision IS NOT NULL",
                                Long.class,
                                operation))
                .isZero();
        assertThat(
                        identityFixture
                                .fixtureOwnerAuthJdbc()
                                .queryForObject(
                                        "SELECT fence_operation_id FROM auth_identities WHERE id=?",
                                        UUID.class,
                                        user))
                .isEqualTo(operation);
        // A split rollback leaves Auth fenced; no old authority or readable registry is restored.
        assertThatThrownBy(() -> dashboard.getStats())
                .isInstanceOfSatisfying(
                        StatusRuntimeException.class,
                        failure ->
                                assertThat(failure.getStatus().getCode())
                                        .isEqualTo(io.grpc.Status.Code.UNAVAILABLE));
        assertThat(academicState(user)).isEqualTo(beforeState);
        assertThat(outboxRows()).isEqualTo(beforeEvents);
        assertThat(scaleChanges()).isEqualTo(beforeChanges);
        return operation;
    }

    private void reconcileTeacher(UUID failed, long user, RoleGrantStatus desired) {
        UUID retry = UUID.randomUUID();
        SelectedActor currentAdmin = identityFixture.select(admin.userId(), AuthRole.ADMIN);
        String history = membershipHistory(user);
        long beforeSignals = signals();
        long beforeChanges = scaleChanges();
        IdentityOperationHttpModels.Operation completed;
        try (var request = identityFixture.request(currentAdmin, retry)) {
            completed = recovery.reconcile(failed, null);
            assertThat(completed.operationId()).isEqualTo(retry);
            assertThat(completed.status()).isEqualTo(IdentityOperationHttpModels.Status.SUCCEEDED);
            succeeded(retry);
        }
        assertThat(teacherStatus(user)).isEqualTo(desired.name());
        assertThat(membershipHistory(user)).isEqualTo(history);
        assertThat(signals()).isEqualTo(beforeSignals + 1);
        assertThat(scaleChanges()).isEqualTo(beforeChanges + 1);
        assertThat(
                        jdbc.queryForObject(
                                "SELECT COUNT(*) FROM academic_identity_operation_targets WHERE"
                                        + " user_id=? AND fenced",
                                Long.class,
                                user))
                .isZero();
        assertThat(
                        identityFixture
                                .fixtureOwnerAuthJdbc()
                                .queryForObject(
                                        "SELECT fence_operation_id IS NULL FROM auth_identities"
                                                + " WHERE id=?",
                                        Boolean.class,
                                        user))
                .isTrue();
        // The existing pending CHANGE must have been captured by durable publication itself.
        assertThat(
                        jdbc.queryForObject(
                                "SELECT COUNT(*) FROM dashboard_scale_observations WHERE"
                                        + " kind='CHANGE' AND settled_at IS NULL",
                                Long.class))
                .isZero();
        String state = academicState(user);
        String observations = observationState();
        long events = outboxRows();
        try (var request = identityFixture.request(currentAdmin, retry)) {
            assertThat(recovery.reconcile(failed, null)).isEqualTo(completed);
            succeeded(retry);
        }
        assertThat(academicState(user)).isEqualTo(state);
        assertThat(membershipHistory(user)).isEqualTo(history);
        assertThat(outboxRows()).isEqualTo(events);
        assertThat(signals()).isEqualTo(beforeSignals + 1);
        assertThat(scaleChanges()).isEqualTo(beforeChanges + 1);
        assertThat(observationState()).isEqualTo(observations);
    }

    private long outboxRows() {
        return jdbc.queryForObject("SELECT COUNT(*) FROM academic_outbox", Long.class);
    }

    private String academicState(long user) {
        return jdbc.queryForObject(
                """
                SELECT jsonb_build_object('profile',to_jsonb(p),'eligibility',COALESCE(
                  (SELECT jsonb_agg(to_jsonb(e) ORDER BY e.role) FROM academic_role_eligibility e
                    WHERE e.user_id=p.id),'[]'::jsonb))::text FROM academic_profiles p WHERE p.id=?
                """,
                String.class,
                user);
    }

    private String membershipHistory(long user) {
        return jdbc.queryForObject(
                "SELECT COALESCE(jsonb_agg(to_jsonb(h) ORDER BY id),'[]'::jsonb)::text FROM"
                        + " student_group_history h WHERE user_id=?",
                String.class,
                user);
    }

    private String observationState() {
        return jdbc.queryForObject(
                "SELECT jsonb_agg(to_jsonb(o) ORDER BY id)::text FROM dashboard_scale_observations"
                        + " o",
                String.class);
    }

    private void managedCoverage(long group) {
        jdbc.update(
                "INSERT INTO"
                    + " group_history_coverage(group_id,coverage_from,writer_version,created_at)"
                    + " VALUES(?,'2026-02-01','managed_v1',NOW())",
                group);
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
        Group saved = groups.saveAndFlush(group);
        managedCoverage(saved.getId());
        return saved;
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class ObservationClockFixture {
        @Bean
        LocalApplyObservation localApplyObservation() {
            return new LocalApplyObservation();
        }

        @Bean
        @Primary
        ObservationClock dashboardObservationClock() {
            return new ObservationClock(
                    new AtomicReference<>(Instant.parse(INITIAL_OBSERVATION)),
                    ZoneId.of("Europe/Moscow"));
        }
    }

    /** Observe only the real domain event inside this thread's LOCAL APPLY transaction. */
    static final class LocalApplyObservation {
        private final ThreadLocal<Runnable> action = new ThreadLocal<>();

        Observation observe(Runnable next) {
            if (action.get() != null) throw new IllegalStateException("Nested APPLY observation");
            action.set(next);
            return () -> action.remove();
        }

        interface Observation extends AutoCloseable {
            @Override
            void close();
        }

        @EventListener
        public void onInvalidation(DashboardInvalidatedEvent event) {
            Runnable next = action.get();
            if (next != null) {
                action.remove();
                assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isTrue();
                next.run();
            }
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
