package ru.rutcampustrack.academic.assignment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.grpc.Status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import ru.rutcampustrack.academic.contract.dto.user.RoleGrantUpdateRequest;
import ru.rutcampustrack.academic.contract.enums.RoleGrantStatus;
import ru.rutcampustrack.academic.grpc.ScheduleGrpcClient;
import ru.rutcampustrack.academic.identityoperation.AcademicIdentityCoordinator;
import ru.rutcampustrack.academic.identityoperation.AcademicIdentityPendingException;
import ru.rutcampustrack.academic.identityoperation.AcademicUserIdentityOperations;
import ru.rutcampustrack.academic.identityoperation.AuthIdentityOperationClient;
import ru.rutcampustrack.academic.identityoperation.JdbcAcademicIdentityOperationStore;
import ru.rutcampustrack.academic.integration.AbstractAssignmentAuthorityIT;
import ru.rutcampustrack.auth.session.model.AuthRole;
import ru.rutcampustrack.authoperations.grpc.CommitIdentityOperationRequest;
import ru.rutcampustrack.schedule.grpc.AssignmentCloseReceipt;

import java.time.LocalDate;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/** Real own-DB admission/APPLY barriers; only remote failure timing is injected. */
@AutoConfigureMockMvc
class AssignmentTeacherEligibilityIT extends AbstractAssignmentAuthorityIT {
    @Autowired private MockMvc mvc;
    @Autowired private AcademicUserIdentityOperations identities;
    @Autowired private AcademicIdentityCoordinator coordinator;
    @MockitoSpyBean private JdbcAcademicIdentityOperationStore identityStore;
    @MockitoSpyBean private AuthIdentityOperationClient auth;
    @MockitoSpyBean private AssignmentTeacherEligibility eligibility;
    @MockitoBean private ScheduleGrpcClient schedule;
    private Fixture fixture;

    @BeforeEach
    void fixture() {
        fixture = newFixture("assignment-local-eligibility");
        successfulAdoptionReceipts(schedule);
    }

    @Test
    void localEligibilityRejectsStaleActiveDirectoryAtAllAdmissionEntrypoints() throws Exception {
        long subject = insertSubject(fixture.groupId(), "local suspended teacher", "lecture");
        long source = createAssignment(subject, fixture.employee1());
        // This isolated Academic fixture keeps the registry ACTIVE and has no pending operation,
        // making the eligibility check itself causal rather than masked by the pending fence.
        jdbcTemplate.update(
                "UPDATE academic_role_eligibility SET status='SUSPENDED' WHERE user_id=? AND"
                        + " role='TEACHER'",
                fixture.teacher2Id());
        assertDirectoryActive(fixture.teacher2Id());
        postAssignment(subject, fixture.employee2()).andExpect(status().isForbidden());
        replace(source, UUID.randomUUID()).andExpect(status().isForbidden());
        mvc.perform(
                        headman(
                                MockMvcRequestBuilders.post(
                                                "/academic/subjects/{subject}/teachers/{teacher}",
                                                subject,
                                                fixture.teacher2Id())
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(
                                                """
{"semesterId":%d,"lessonType":"LECTURE","validFrom":"%s"}
"""
                                                        .formatted(fixture.semesterId(), from()))))
                .andExpect(status().isForbidden());
        String subjectName = "ineligible initial " + UUID.randomUUID();
        mvc.perform(
                        headman(
                                MockMvcRequestBuilders.post("/academic/subjects")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(
                                                """
{"name":"%s","type":"LECTURE","lessonTypes":["LECTURE"],
 "initialAssignments":[{"teacherId":%d,"semesterId":%d,"lessonType":"LECTURE","validFrom":"%s"}]}
"""
                                                        .formatted(
                                                                subjectName,
                                                                fixture.teacher2Id(),
                                                                fixture.semesterId(),
                                                                from()))))
                .andExpect(status().isForbidden());
        assertThat(
                        jdbcTemplate.queryForObject(
                                "SELECT count(*) FROM subjects WHERE name=?",
                                Long.class,
                                subjectName))
                .isZero();
        assertThat(assignmentCount(subject)).isEqualTo(1);
        assertThat(
                        jdbcTemplate.queryForObject(
                                "SELECT count(*) FROM assignment_replacement_operations WHERE"
                                        + " source_assignment_id=?",
                                Long.class,
                                source))
                .isZero();
    }

    @Test
    void actualLocalCommitAndAuthOutageKeepStaleRegistryFromAdmittingTeacher() throws Exception {
        long subject = insertSubject(fixture.groupId(), "local commit outage", "lecture");
        long source = createAssignment(subject, fixture.employee1());
        UUID identity = UUID.randomUUID();
        doAnswer(
                        call -> {
                            if (call.getArgument(0, CommitIdentityOperationRequest.class)
                                    .getOperationId()
                                    .equals(identity.toString()))
                                throw Status.UNAVAILABLE
                                        .withDescription("synthetic Auth commit outage")
                                        .asRuntimeException();
                            return call.callRealMethod();
                        })
                .when(auth)
                .commit(any());
        suspendPending(identity);
        assertThat(identityStore.find(identity.toString()).phase())
                .isEqualTo(AcademicIdentityCoordinator.Phase.LOCAL_COMMITTED);
        assertThat(localStatus()).isEqualTo("SUSPENDED");
        assertDirectoryActive(fixture.teacher2Id());
        postAssignment(subject, fixture.employee2()).andExpect(status().isServiceUnavailable());
        replace(source, UUID.randomUUID()).andExpect(status().isServiceUnavailable());
        assertThat(assignmentCount(subject)).isEqualTo(1);
        finishIdentity(identity);
        postAssignment(subject, fixture.employee2()).andExpect(status().isForbidden());
        assertThat(assignmentCount(subject)).isEqualTo(1);
    }

    @Test
    void pendingPreparedIdentityDeniesAdmissionBeforeEligibilityChanges() throws Exception {
        long subject = insertSubject(fixture.groupId(), "prepared identity", "lecture");
        long source = createAssignment(subject, fixture.employee1());
        UUID identity = UUID.randomUUID();
        stopBeforeApply(identity);
        suspendPending(identity);
        assertThat(identityStore.find(identity.toString()).phase())
                .isEqualTo(AcademicIdentityCoordinator.Phase.AUTH_PREPARED);
        assertThat(localStatus()).isEqualTo("ACTIVE");
        assertDirectoryActive(fixture.teacher2Id());
        postAssignment(subject, fixture.employee2()).andExpect(status().isServiceUnavailable());
        replace(source, UUID.randomUUID()).andExpect(status().isServiceUnavailable());
        assertThat(assignmentCount(subject)).isEqualTo(1);
        finishIdentity(identity);
    }

    @Test
    void finalAdoptionRechecksIdentityAndRetryKeepsTheOriginalTarget() throws Exception {
        long subject = insertSubject(fixture.groupId(), "adoption identity transition", "lecture");
        UUID identity = UUID.randomUUID();
        stopBeforeApply(identity);
        var first = new AtomicBoolean(true);
        doAnswer(
                        call -> {
                            assertThat(
                                            TransactionSynchronizationManager
                                                    .isActualTransactionActive())
                                    .isFalse();
                            if (first.getAndSet(false)) suspendPending(identity);
                            return receipt(call.getArgument(0), "APPLIED");
                        })
                .when(schedule)
                .installAssignmentLifecycle(
                        any(), any(), any(), any(), any(), eq("ADOPT_UNASSIGNED"));
        postAssignment(subject, fixture.employee2()).andExpect(status().isServiceUnavailable());
        long target = target(subject);
        assertPrepared(target);
        resume(target).andExpect(status().isServiceUnavailable());
        finishIdentity(identity);
        // A completed suspension still cannot activate the accepted PREPARED row.
        resume(target).andExpect(status().isServiceUnavailable());
        assertPrepared(target);
        setTeacherStatus(fixture.teacher2Id(), RoleGrantStatus.ACTIVE);
        resume(target).andExpect(status().isOk());
        assertThat(assignmentCount(subject)).isEqualTo(1);
        assertThat(lifecycle(target)).isEqualTo("ACTIVE");
        assertThat(operationState(target)).isEqualTo("COMMITTED");
    }

    @Test
    void replacementRechecksAfterScheduleBarrierAndPreservesSourceUntilEligible() throws Exception {
        long subject =
                insertSubject(fixture.groupId(), "replacement identity transition", "lecture");
        long source = createAssignment(subject, fixture.employee1());
        UUID identity = UUID.randomUUID(), key = UUID.randomUUID();
        stopBeforeApply(identity);
        var first = new AtomicBoolean(true);
        doAnswer(
                        call -> {
                            assertThat(
                                            TransactionSynchronizationManager
                                                    .isActualTransactionActive())
                                    .isFalse();
                            if (first.getAndSet(false)) suspendPending(identity);
                            return receipt(call.getArgument(0), "APPLIED");
                        })
                .when(schedule)
                .installAssignmentCloseCap(any(), anyLong(), anyLong(), any(), any());
        replace(source, key).andExpect(status().isServiceUnavailable());
        long target =
                jdbcTemplate.queryForObject(
                        "SELECT target_assignment_id FROM assignment_replacement_operations WHERE"
                                + " request_key=?",
                        Long.class,
                        key);
        assertPrepared(target);
        assertThat(
                        jdbcTemplate.queryForObject(
                                "SELECT valid_until_exclusive FROM assignments WHERE id=?",
                                LocalDate.class,
                                source))
                .isEqualTo(end());
        finishIdentity(identity);
        replace(source, key).andExpect(status().isForbidden());
        assertPrepared(target);
        setTeacherStatus(fixture.teacher2Id(), RoleGrantStatus.ACTIVE);
        replace(source, key).andExpect(status().isCreated());
        assertThat(operationState(target)).isEqualTo("COMMITTED");
        assertThat(assignmentCount(subject)).isEqualTo(2);
        assertThat(
                        jdbcTemplate.queryForObject(
                                "SELECT valid_until_exclusive FROM assignments WHERE id=?",
                                LocalDate.class,
                                source))
                .isEqualTo(from().plusDays(3));
    }

    @Test
    void activeTeacherEligibilityIsIndependentOfScalarProfileStatus() throws Exception {
        long subject = insertSubject(fixture.groupId(), "role eligibility", "lecture");
        jdbcTemplate.update(
                "UPDATE academic_profiles SET directory_status='SUSPENDED' WHERE id=?",
                fixture.teacher2Id());
        assertThat(localStatus()).isEqualTo("ACTIVE");
        postAssignment(subject, fixture.employee2()).andExpect(status().isCreated());
        assertThat(lifecycle(target(subject))).isEqualTo("ACTIVE");
    }

    @Test
    void preparedFenceWaitsForAdmissionProfileLockThenFinalAdoptionSeesFence() throws Exception {
        long subject = insertSubject(fixture.groupId(), "prepared profile lock race", "lecture");
        UUID identity = UUID.randomUUID();
        stopBeforeApply(identity);
        var admissionChecked = new CountDownLatch(1);
        var releaseAdmission = new CountDownLatch(1);
        var preparedAttempt = new CountDownLatch(1);
        var identityPending = new CountDownLatch(1);
        var preparedPid = new AtomicInteger();
        var admissionPid = new AtomicInteger();
        var first = new AtomicBoolean(true);
        doAnswer(
                        call -> {
                            Object result = call.callRealMethod();
                            if (call.getArgument(0, Long.class) == fixture.teacher2Id()
                                    && first.getAndSet(false)) {
                                admissionPid.set(
                                        jdbcTemplate.queryForObject(
                                                "SELECT pg_backend_pid()", Integer.class));
                                admissionChecked.countDown();
                                assertThat(releaseAdmission.await(20, TimeUnit.SECONDS)).isTrue();
                            }
                            return result;
                        })
                .when(eligibility)
                .requireEligible(anyLong());
        doAnswer(
                        call -> {
                            if (call.getArgument(0, AcademicIdentityCoordinator.Operation.class)
                                    .id()
                                    .equals(identity.toString())) {
                                assertThat(
                                                TransactionSynchronizationManager
                                                        .isActualTransactionActive())
                                        .isTrue();
                                preparedPid.set(
                                        jdbcTemplate.queryForObject(
                                                "SELECT pg_backend_pid()", Integer.class));
                                preparedAttempt.countDown();
                            }
                            return call.callRealMethod();
                        })
                .when(identityStore)
                .prepared(any(), any());
        doAnswer(
                        call -> {
                            assertThat(
                                            TransactionSynchronizationManager
                                                    .isActualTransactionActive())
                                    .isFalse();
                            assertThat(identityPending.await(20, TimeUnit.SECONDS)).isTrue();
                            return receipt(call.getArgument(0), "APPLIED");
                        })
                .when(schedule)
                .installAssignmentLifecycle(
                        any(), any(), any(), any(), any(), eq("ADOPT_UNASSIGNED"));
        var workers = Executors.newFixedThreadPool(2);
        try {
            var assignment =
                    workers.submit(() -> postAssignment(subject, fixture.employee2()).andReturn());
            assertThat(admissionChecked.await(20, TimeUnit.SECONDS)).isTrue();
            var suspension =
                    workers.submit(
                            () -> {
                                try {
                                    suspendPending(identity);
                                } finally {
                                    identityPending.countDown();
                                }
                            });
            assertThat(preparedAttempt.await(20, TimeUnit.SECONDS)).isTrue();
            awaitBlockedBy(preparedPid.get(), admissionPid.get());
            assertThat(
                            jdbcTemplate.queryForObject(
                                    "SELECT fenced FROM academic_identity_operation_targets WHERE"
                                            + " operation_id=?",
                                    Boolean.class,
                                    identity))
                    .isFalse();
            releaseAdmission.countDown();
            suspension.get(20, TimeUnit.SECONDS);
            assertThat(assignment.get(20, TimeUnit.SECONDS).getResponse().getStatus())
                    .isEqualTo(503);
            assertPrepared(target(subject));
            verify(schedule, never()).commitAssignmentClose(any(), any());
            finishIdentity(identity);
        } finally {
            releaseAdmission.countDown();
            identityPending.countDown();
            workers.shutdownNow();
            assertThat(workers.awaitTermination(20, TimeUnit.SECONDS)).isTrue();
        }
    }

    @Test
    void reversedInitialTeacherBatchAndActualTwoTargetIdentityPrepareDoNotDeadlock()
            throws Exception {
        var administrator =
                identityFixture.select(identityFixture.canonical().adminId(), AuthRole.ADMIN);
        // Use another membership group: a new subject's group FK must not mask the profile race
        // by blocking identity acceptance's group lock before it reaches prepared().
        long identityGroup = insertGroup("batch-" + UUID.randomUUID().toString().substring(0, 20));
        for (long teacher : java.util.List.of(fixture.teacher1Id(), fixture.teacher2Id())) {
            try (var scope = identityFixture.request(administrator)) {
                identities.role(
                        teacher,
                        "STUDENT",
                        new RoleGrantUpdateRequest(
                                RoleGrantStatus.ACTIVE,
                                identityGroup,
                                null,
                                850000000000L + teacher));
            }
        }
        try (var scope = identityFixture.request(administrator)) {
            identities.headman(identityGroup, fixture.teacher1Id(), null, false, null);
        }
        identityFixture.catchUpCatalog();
        assertThat(fixture.teacher1Id()).isLessThan(fixture.teacher2Id());
        UUID identity = UUID.randomUUID();
        stopBeforeApply(identity);
        var firstProfileLocked = new CountDownLatch(1);
        var releaseBatch = new CountDownLatch(1);
        var preparedAttempt = new CountDownLatch(1);
        var identityPending = new CountDownLatch(1);
        var batchPid = new AtomicInteger();
        var preparedPid = new AtomicInteger();
        var first = new AtomicBoolean(true);
        doAnswer(
                        call -> {
                            Object result = call.callRealMethod();
                            long teacher = call.getArgument(0);
                            if ((teacher == fixture.teacher1Id() || teacher == fixture.teacher2Id())
                                    && first.getAndSet(false)) {
                                batchPid.set(
                                        jdbcTemplate.queryForObject(
                                                "SELECT pg_backend_pid()", Integer.class));
                                firstProfileLocked.countDown();
                                assertThat(releaseBatch.await(20, TimeUnit.SECONDS)).isTrue();
                            }
                            return result;
                        })
                .when(eligibility)
                .requireEligible(anyLong());
        doAnswer(
                        call -> {
                            if (call.getArgument(0, AcademicIdentityCoordinator.Operation.class)
                                    .id()
                                    .equals(identity.toString())) {
                                assertThat(
                                                TransactionSynchronizationManager
                                                        .isActualTransactionActive())
                                        .isTrue();
                                assertThat(
                                                call.getArgument(
                                                                1,
                                                                ru.rutcampustrack.authoperations
                                                                        .grpc
                                                                        .IdentityOperationResult
                                                                        .class)
                                                        .getTargetsList())
                                        .hasSize(2);
                                preparedPid.set(
                                        jdbcTemplate.queryForObject(
                                                "SELECT pg_backend_pid()", Integer.class));
                                preparedAttempt.countDown();
                            }
                            return call.callRealMethod();
                        })
                .when(identityStore)
                .prepared(any(), any());
        doAnswer(
                        call -> {
                            assertThat(
                                            TransactionSynchronizationManager
                                                    .isActualTransactionActive())
                                    .isFalse();
                            assertThat(identityPending.await(20, TimeUnit.SECONDS)).isTrue();
                            return receipt(call.getArgument(0), "APPLIED");
                        })
                .when(schedule)
                .installAssignmentLifecycle(
                        any(), any(), any(), any(), any(), eq("ADOPT_UNASSIGNED"));
        String name = "reversed initial " + UUID.randomUUID();
        var workers = Executors.newFixedThreadPool(2);
        try {
            // Deliberately B,A while the identity writer locks the same targets A,B.
            var batch =
                    workers.submit(
                            () ->
                                    mvc.perform(
                                                    headman(
                                                            MockMvcRequestBuilders.post(
                                                                            "/academic/subjects")
                                                                    .contentType(
                                                                            MediaType
                                                                                    .APPLICATION_JSON)
                                                                    .content(
                                                                            """
{"name":"%s","type":"LECTURE","lessonTypes":["LECTURE","PRACTICE"],
 "initialAssignments":[
   {"teacherId":%d,"semesterId":%d,"lessonType":"LECTURE","validFrom":"%s"},
   {"teacherId":%d,"semesterId":%d,"lessonType":"PRACTICE","validFrom":"%s"}]}
"""
                                                                                    .formatted(
                                                                                            name,
                                                                                            fixture
                                                                                                    .teacher2Id(),
                                                                                            fixture
                                                                                                    .semesterId(),
                                                                                            from(),
                                                                                            fixture
                                                                                                    .teacher1Id(),
                                                                                            fixture
                                                                                                    .semesterId(),
                                                                                            from()))))
                                            .andReturn());
            assertThat(firstProfileLocked.await(20, TimeUnit.SECONDS)).isTrue();
            var replacement =
                    workers.submit(
                            () -> {
                                try (var scope = identityFixture.request(administrator, identity)) {
                                    assertThatThrownBy(
                                                    () ->
                                                            identities.headman(
                                                                    identityGroup,
                                                                    fixture.teacher2Id(),
                                                                    fixture.teacher1Id(),
                                                                    true,
                                                                    null))
                                            .isInstanceOf(AcademicIdentityPendingException.class);
                                } finally {
                                    identityPending.countDown();
                                }
                            });
            assertThat(preparedAttempt.await(20, TimeUnit.SECONDS)).isTrue();
            awaitBlockedBy(preparedPid.get(), batchPid.get());
            releaseBatch.countDown();
            replacement.get(20, TimeUnit.SECONDS);
            assertThat(batch.get(20, TimeUnit.SECONDS).getResponse().getStatus()).isEqualTo(503);
            long subject =
                    jdbcTemplate.queryForObject(
                            "SELECT id FROM subjects WHERE name=?", Long.class, name);
            assertThat(
                            jdbcTemplate.queryForList(
                                    "SELECT lifecycle_state FROM assignments WHERE subject_id=?",
                                    String.class,
                                    subject))
                    .containsExactly("PREPARED", "PREPARED");
            finishIdentity(identity);
        } finally {
            releaseBatch.countDown();
            identityPending.countDown();
            workers.shutdownNow();
            assertThat(workers.awaitTermination(20, TimeUnit.SECONDS)).isTrue();
        }
    }

    private void awaitBlockedBy(int waiter, int blocker) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(15);
        var poll = new CountDownLatch(1);
        while (!Boolean.TRUE.equals(
                jdbcTemplate.queryForObject(
                        "SELECT ? = ANY(pg_blocking_pids(?))", Boolean.class, blocker, waiter))) {
            assertThat(System.nanoTime())
                    .as("prepared() must wait for the actual assignment profile lock")
                    .isLessThan(deadline);
            poll.await(20, TimeUnit.MILLISECONDS);
        }
    }

    private void stopBeforeApply(UUID identity) {
        doAnswer(
                        call -> {
                            if (call.getArgument(0, AcademicIdentityCoordinator.Operation.class)
                                    .id()
                                    .equals(identity.toString()))
                                throw Status.UNAVAILABLE
                                        .withDescription("synthetic stop before local APPLY")
                                        .asRuntimeException();
                            return call.callRealMethod();
                        })
                .when(identityStore)
                .apply(any());
    }

    private void suspendPending(UUID identity) {
        var administrator =
                identityFixture.select(identityFixture.canonical().adminId(), AuthRole.ADMIN);
        try (var scope = identityFixture.request(administrator, identity)) {
            assertThatThrownBy(
                            () ->
                                    identities.role(
                                            fixture.teacher2Id(),
                                            "TEACHER",
                                            new RoleGrantUpdateRequest(
                                                    RoleGrantStatus.SUSPENDED, null, null, null)))
                    .isInstanceOf(AcademicIdentityPendingException.class);
        }
    }

    private void finishIdentity(UUID identity) {
        doCallRealMethod().when(identityStore).apply(any());
        doCallRealMethod().when(auth).commit(any());
        assertThat(coordinator.recover(identity.toString()).phase())
                .isEqualTo(AcademicIdentityCoordinator.Phase.SUCCEEDED);
    }

    private String localStatus() {
        return jdbcTemplate.queryForObject(
                "SELECT status FROM academic_role_eligibility WHERE user_id=? AND role='TEACHER'",
                String.class,
                fixture.teacher2Id());
    }

    private void assertDirectoryActive(long teacher) {
        assertThat(
                        jdbcTemplate.queryForObject(
                                "SELECT status FROM academic_role_directory WHERE user_id=? AND"
                                        + " role='teacher'",
                                String.class,
                                teacher))
                .isEqualTo("active");
    }

    private void assertPrepared(long target) {
        assertThat(lifecycle(target)).isEqualTo("PREPARED");
        assertThat(operationState(target)).isEqualTo("PREPARED");
    }

    private String lifecycle(long target) {
        return jdbcTemplate.queryForObject(
                "SELECT lifecycle_state FROM assignments WHERE id=?", String.class, target);
    }

    private String operationState(long target) {
        return jdbcTemplate.queryForObject(
                "SELECT state FROM assignment_replacement_operations WHERE target_assignment_id=?",
                String.class,
                target);
    }

    private long target(long subject) {
        return jdbcTemplate.queryForObject(
                "SELECT id FROM assignments WHERE subject_id=? ORDER BY id DESC LIMIT 1",
                Long.class,
                subject);
    }

    private LocalDate from() {
        return fixture.dateFrom().plusDays(1);
    }

    private LocalDate end() {
        return from().plusDays(15);
    }

    private long createAssignment(long subject, String employee) throws Exception {
        postAssignment(subject, employee).andExpect(status().isCreated());
        return target(subject);
    }

    private ResultActions postAssignment(long subject, String employee) throws Exception {
        return mvc.perform(
                headman(
                        MockMvcRequestBuilders.post("/academic/assignments")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        """
{"employeeNumber":"%s","subjectId":%d,"groupId":%d,"semesterId":%d,
 "lessonType":"LECTURE","validFrom":"%s","validUntilExclusive":"%s"}
"""
                                                .formatted(
                                                        employee,
                                                        subject,
                                                        fixture.groupId(),
                                                        fixture.semesterId(),
                                                        from(),
                                                        end()))));
    }

    private ResultActions replace(long source, UUID key) throws Exception {
        return mvc.perform(
                headman(
                        MockMvcRequestBuilders.post("/academic/assignments/{id}/replace", source)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        """
{"replacementTeacherId":"%d","effectiveFrom":"%s","requestKey":"%s"}
"""
                                                .formatted(
                                                        fixture.teacher2Id(),
                                                        from().plusDays(3),
                                                        key))));
    }

    private ResultActions resume(long target) throws Exception {
        return mvc.perform(
                headman(
                        MockMvcRequestBuilders.post(
                                "/academic/assignments/{id}/resume-adoption", target)));
    }

    private MockHttpServletRequestBuilder headman(MockHttpServletRequestBuilder request) {
        return signedHeadman(request, fixture.headmanId(), fixture.groupId());
    }

    private static AssignmentCloseReceipt receipt(UUID operation, String state) {
        return AssignmentCloseReceipt.newBuilder()
                .setOperationId(operation.toString())
                .setState(state)
                .build();
    }
}
