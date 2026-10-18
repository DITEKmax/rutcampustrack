package ru.rutcampustrack.academic.notification;

import static org.assertj.core.api.Assertions.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.grpc.*;
import io.grpc.stub.MetadataUtils;
import jakarta.persistence.EntityManager;
import java.time.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.context.transaction.AfterTransaction;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronizationUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import ru.rutcampustrack.academic.assistant.AssistantService;
import ru.rutcampustrack.academic.contract.dto.assistant.*;
import ru.rutcampustrack.academic.contract.dto.group.AssignHeadmanRequest;
import ru.rutcampustrack.academic.contract.dto.user.PatchUserRequest;
import ru.rutcampustrack.academic.contract.dto.user.RoleGrantUpdateRequest;
import ru.rutcampustrack.academic.contract.dto.user.TransferStudentRequest;
import ru.rutcampustrack.academic.contract.enums.AccountStatus;
import ru.rutcampustrack.academic.contract.enums.AssistantPermission;
import ru.rutcampustrack.academic.contract.enums.RoleGrantStatus;
import ru.rutcampustrack.academic.contract.enums.UserRole;
import ru.rutcampustrack.academic.group.GroupHeadmanAssignmentService;
import ru.rutcampustrack.academic.grpc.*;
import ru.rutcampustrack.academic.homework.HomeworkBindingArchiveCoordinator;
import ru.rutcampustrack.academic.integration.AbstractAcademicIntegrationTest;
import ru.rutcampustrack.academic.repository.HomeworkRepository;
import ru.rutcampustrack.academic.security.RequestContext;
import ru.rutcampustrack.academic.user.UserService;
import ru.rutcampustrack.shared.security.InternalJwtClaims;

/** One real repository level for audience auth/date rules and transaction/outbox changes. */
@Transactional
class NotificationAudienceIT extends AbstractAcademicIntegrationTest {
    @Autowired JdbcTemplate jdbc;
    @Autowired NotificationAudienceResolver resolver;
    @Autowired AssistantService assistantService;
    @Autowired GroupHeadmanAssignmentService headmanService;
    @Autowired UserService userService;
    @Autowired RequestContext context;
    @Autowired HomeworkRepository homeworks;
    @Autowired HomeworkBindingArchiveCoordinator archive;
    @Autowired ObjectMapper mapper;
    @Autowired EntityManager entities;
    private final LocalDate day = LocalDate.of(2043, 3, 10);
    private long group,
            foreignGroup,
            subject,
            semester,
            owner,
            headman,
            helper,
            foreignStudent,
            teacher;
    private final List<Long> outboxIds = new ArrayList<>();

    @BeforeEach
    void fixture() {
        RequestContextHolder.setRequestAttributes(
                new ServletRequestAttributes(new MockHttpServletRequest()));
        group = group();
        foreignGroup = group();
        owner = student(group);
        headman = student(group);
        helper = student(group);
        foreignStudent = student(foreignGroup);
        grant(headman, "headman", group);
        jdbc.update("UPDATE users SET is_headman=true WHERE id=?", headman);
        teacher = user("teacher", null);
        grant(teacher, "teacher", null);
        subject = subject(group);
        semester =
                jdbc.queryForObject(
                        "INSERT INTO semesters(name,date_from,date_to,is_active,created_at) VALUES(?,?,?,false,NOW()) RETURNING id",
                        Long.class,
                        "Audience " + UUID.randomUUID(),
                        LocalDate.of(2043, 2, 1),
                        LocalDate.of(2043, 6, 30));
    }

    @AfterEach
    void releaseContext() {
        RequestContextHolder.resetRequestAttributes();
    }

    @AfterTransaction
    void rollbackLeavesNoSyntheticFactOrUser() {
        assertThat(jdbc.queryForObject("SELECT count(*) FROM users WHERE id=?", Long.class, owner))
                .isZero();
        for (long id : outboxIds)
            assertThat(
                            jdbc.queryForObject(
                                    "SELECT count(*) FROM academic_outbox WHERE id=?",
                                    Long.class,
                                    id))
                    .isZero();
    }

    @Test
    void oneRecipientHasUnionOfLiveRolesAndEchoes() {
        grant(headman, "teacher", null);
        assignment(headman, group, subject, "lab", day, null, "ACTIVE");
        var request =
                request("homework.published")
                        .addLessonScopes(scope(group, subject, "lab", day))
                        .build();
        var response = resolver.resolve(request);
        assertThat(response.getRequestId()).isEqualTo(request.getRequestId());
        assertThat(response.getEventType()).isEqualTo(request.getEventType());
        assertThat(audience(response))
                .containsExactlyInAnyOrderEntriesOf(
                        Map.of(
                                owner,
                                Set.of("STUDENT"),
                                helper,
                                Set.of("STUDENT"),
                                headman,
                                Set.of("STUDENT", "HEADMAN", "TEACHER")));
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "group",
                "subject",
                "semester",
                "type",
                "before",
                "until",
                "PREPARED",
                "CLOSED",
                "revoked",
                "assignmentId"
            })
    void teacherNeverReceivesForeignOrInactiveScope(String mismatch) {
        long row =
                assignment(
                        teacher,
                        group,
                        subject,
                        "lecture",
                        day,
                        mismatch.equals("until") ? day.plusDays(1) : null,
                        Set.of("PREPARED", "CLOSED").contains(mismatch) ? mismatch : "ACTIVE");
        long targetGroup = mismatch.equals("group") ? foreignGroup : group;
        long targetSubject =
                mismatch.equals("group")
                        ? subject(foreignGroup)
                        : mismatch.equals("subject") ? subject(group) : subject;
        var scope =
                scope(
                        targetGroup,
                        targetSubject,
                        mismatch.equals("type") ? "practice" : "lecture",
                        mismatch.equals("before")
                                ? day.minusDays(1)
                                : mismatch.equals("until") ? day.plusDays(1) : day)
                        .toBuilder();
        if (mismatch.equals("assignmentId")) scope.setAssignmentId(row + 100000);
        if (mismatch.equals("semester")) {
            long other =
                    jdbc.queryForObject(
                            "INSERT INTO semesters(name,date_from,date_to,is_active,created_at) VALUES(?,?,?,false,NOW()) RETURNING id",
                            Long.class,
                            "Audience foreign " + UUID.randomUUID(),
                            LocalDate.of(2044, 2, 1),
                            LocalDate.of(2044, 6, 30));
            scope.setSemesterId(other).setLessonDate(day.plusYears(1).toString());
        }
        if (mismatch.equals("revoked"))
            jdbc.update(
                    "UPDATE user_role_grants SET status='archived' WHERE user_id=? AND role='teacher'",
                    teacher);
        var request =
                request("homework.updated").setGroupId(targetGroup).addLessonScopes(scope).build();
        assertThat(audience(resolver.resolve(request))).doesNotContainKey(teacher);
    }

    @ParameterizedTest
    @ValueSource(strings = {"PREPARED", "APPLIED", "COMMITTED"})
    void unpublishedAdoptionNeverGrantsTeacherAudience(String state) {
        long assignment = assignment(teacher, group, subject, "lecture", day, null, "ACTIVE");
        UUID operation = UUID.randomUUID();
        jdbc.update(
                """
                INSERT INTO assignment_replacement_operations(operation_id,actor_id,request_key,payload_hash,target_assignment_id,
                effective_from,source_valid_until,target_valid_until,state,operation_kind,requested_cutoff)
                VALUES(?,?,?,decode(repeat('11',32),'hex'),?,?,?,?,'PREPARED','ADOPT_UNASSIGNED',?)
                """,
                operation,
                owner,
                UUID.randomUUID(),
                assignment,
                day,
                LocalDate.of(2043, 7, 1),
                LocalDate.of(2043, 7, 1),
                day);
        if (!state.equals("PREPARED"))
            jdbc.update(
                    "UPDATE assignment_replacement_operations SET state='APPLIED',schedule_receipt_state='APPLIED' WHERE operation_id=?",
                    operation);
        if (state.equals("COMMITTED"))
            jdbc.update(
                    "UPDATE assignment_replacement_operations SET state='COMMITTED',schedule_receipt_state='COMMITTED' WHERE operation_id=?",
                    operation);
        var result =
                audience(
                        resolver.resolve(
                                request("homework.updated")
                                        .addLessonScopes(
                                                scope(group, subject, "lecture", day).toBuilder()
                                                        .setAssignmentId(assignment))
                                        .build()));
        assertThat(result.containsKey(teacher)).isEqualTo(state.equals("COMMITTED"));
    }

    @Test
    void sourceAndTargetDatesSelectTheirOwnTeachers() {
        long replacement = user("teacher", null);
        grant(replacement, "teacher", null);
        assignment(teacher, group, subject, "lecture", day, day.plusDays(1), "ACTIVE");
        assignment(replacement, group, subject, "practice", day.plusDays(1), null, "ACTIVE");
        var response =
                resolver.resolve(
                        request("lesson.transferred")
                                .addLessonScopes(scope(group, subject, "lecture", day))
                                .addLessonScopes(scope(group, subject, "practice", day.plusDays(1)))
                                .build());
        assertThat(audience(response))
                .containsEntry(teacher, Set.of("TEACHER"))
                .containsEntry(replacement, Set.of("TEACHER"));
    }

    @Test
    void membershipUsesMoscowOccurredDayAndDoesNotGuessLegacyCoverage() {
        jdbc.update(
                "UPDATE student_group_history SET left_at=? WHERE user_id=?",
                day.plusDays(1),
                owner);
        jdbc.update(
                "INSERT INTO student_group_history(user_id,group_id,joined_at) VALUES(?,?,?)",
                owner,
                foreignGroup,
                day.plusDays(1));
        jdbc.update("UPDATE users SET group_id=? WHERE id=?", foreignGroup, owner);
        jdbc.update(
                "UPDATE user_role_grants SET group_id=? WHERE user_id=? AND role='student'",
                foreignGroup,
                owner);
        var before = request("lesson.started").setOccurredAt(day + "T20:59:00Z").build();
        var after = before.toBuilder().setOccurredAt(day + "T21:00:00Z").build();
        assertThat(audience(resolver.resolve(before))).containsKey(owner);
        assertThat(audience(resolver.resolve(after))).doesNotContainKey(owner);
        jdbc.update("DELETE FROM group_history_coverage WHERE group_id=?", group);
        entities.clear(); // The next RPC sees a fresh persistence context after the raw fixture
        // write.
        assertThatThrownBy(() -> resolver.resolve(before)).isInstanceOf(RuntimeException.class);
    }

    @Test
    void helperPermissionIsDerivedFromEventAndRevocationIsLive() {
        assistantRow(helper, "manage_excuses");
        var request = request("excuse.requested").setAffectedUserId(owner).build();
        assertThat(audience(resolver.resolve(request)))
                .containsExactlyInAnyOrderEntriesOf(
                        Map.of(
                                owner,
                                Set.of("STUDENT"),
                                helper,
                                Set.of("STUDENT"),
                                headman,
                                Set.of("HEADMAN")));
        assertThat(audience(resolver.resolve(request("lesson.closed").build())))
                .doesNotContainKey(helper);
        jdbc.update(
                "UPDATE headman_assistants SET permissions=ARRAY['mark_attendance']::varchar[] WHERE group_id=? AND student_id=?",
                group,
                helper);
        entities.clear();
        assertThat(audience(resolver.resolve(request))).doesNotContainKey(helper);
        assertThat(audience(resolver.resolve(request("lesson.closed").build())))
                .containsEntry(helper, Set.of("STUDENT"));
        jdbc.update(
                "UPDATE user_role_grants SET status='suspended' WHERE user_id=? AND role='student'",
                helper);
        entities.clear();
        assertThat(audience(resolver.resolve(request("lesson.closed").build())))
                .doesNotContainKey(helper);
    }

    @Test
    void cancellationsReachPrivilegedTasksButDecisionsStayPrivateAndExcludeOwner() {
        assistantRow(helper, "manage_excuses");
        var base = request("excuse.decided").setAffectedUserId(helper);
        assertThat(audience(resolver.resolve(base.setRequestOutcome("approved").build())))
                .containsExactlyInAnyOrderEntriesOf(Map.of(helper, Set.of("STUDENT")));
        assertThat(audience(resolver.resolve(base.setRequestOutcome("cancelled").build())))
                .containsExactlyInAnyOrderEntriesOf(
                        Map.of(helper, Set.of("STUDENT"), headman, Set.of("HEADMAN")));
        assertThatThrownBy(() -> resolver.resolve(base.clearRequestOutcome().build()))
                .isInstanceOf(StatusRuntimeException.class);
        assertThatThrownBy(
                        () ->
                                resolver.resolve(
                                        request("lesson.closed")
                                                .setRequestOutcome("cancelled")
                                                .build()))
                .isInstanceOf(StatusRuntimeException.class);
    }

    @Test
    void limitedAdminCapabilitiesAndPersonalGrantsDoNotResurrectRevokedAccess() {
        long groupAdmin = admin("LIMITED", "GROUPS_VIEW", "GROUPS_MANAGE");
        long semesterAdmin = admin("LIMITED", "SEMESTERS_VIEW", "SEMESTERS_MANAGE");
        long full = admin("FULL");
        assertThat(audience(resolver.resolve(request("group.renamed").build())))
                .containsEntry(groupAdmin, Set.of("ADMIN"))
                .containsEntry(full, Set.of("ADMIN"))
                .doesNotContainKey(semesterAdmin);
        assertThat(audience(resolver.resolve(request("semester.activated").clearGroupId().build())))
                .containsEntry(semesterAdmin, Set.of("ADMIN"))
                .containsEntry(full, Set.of("ADMIN"))
                .doesNotContainKey(groupAdmin);
        assertThat(
                        audience(
                                resolver.resolve(
                                        request("semester.operation.completed")
                                                .clearGroupId()
                                                .setAffectedUserId(semesterAdmin)
                                                .build())))
                .containsExactlyInAnyOrderEntriesOf(Map.of(semesterAdmin, Set.of("ADMIN")));
        jdbc.update(
                "UPDATE user_role_grants SET status='archived' WHERE user_id=? AND role='admin'",
                semesterAdmin);
        assertThat(
                        resolver.resolve(
                                        request("admin.access.changed")
                                                .clearGroupId()
                                                .setAffectedUserId(semesterAdmin)
                                                .build())
                                .getRecipientsList())
                .isEmpty();
        jdbc.update(
                "UPDATE user_role_grants SET status='archived' WHERE user_id=? AND role='student'",
                owner);
        assertThat(
                        resolver.resolve(
                                        request("headman.access.changed")
                                                .setAffectedUserId(owner)
                                                .build())
                                .getRecipientsList())
                .isEmpty();
    }

    @Test
    void invalidEventTupleAndMissingAdmissionFailClosed() throws Exception {
        assertThatThrownBy(() -> resolver.resolve(request("anything.broadcast").build()))
                .isInstanceOf(StatusRuntimeException.class);
        assignment(teacher, group, subject, "lab", day, null, "ACTIVE");
        assertThat(audience(resolver.resolve(request("homework.published").build())))
                .containsExactlyInAnyOrderEntriesOf(
                        Map.of(
                                owner,
                                Set.of("STUDENT"),
                                helper,
                                Set.of("STUDENT"),
                                headman,
                                Set.of("STUDENT", "HEADMAN")));
        assertThatThrownBy(
                        () ->
                                resolver.resolve(
                                        request("homework.published")
                                                .addLessonScopes(
                                                        scope(group, subject, "unknown", day))
                                                .build()))
                .isInstanceOf(StatusRuntimeException.class);
        assertThatThrownBy(
                        () ->
                                resolver.resolve(
                                        request("homework.published")
                                                .addLessonScopes(
                                                        scope(group, subject, "lab", day)
                                                                .toBuilder()
                                                                .clearSemesterId())
                                                .build()))
                .isInstanceOf(StatusRuntimeException.class);
        Server server =
                ServerBuilder.forPort(0)
                        .addService(
                                ServerInterceptors.intercept(
                                        new NotificationAudienceGrpcService(resolver),
                                        new NotificationAudienceServiceAdmission(
                                                "synthetic-audience")))
                        .build()
                        .start();
        ManagedChannel channel =
                ManagedChannelBuilder.forAddress("localhost", server.getPort())
                        .usePlaintext()
                        .build();
        try {
            var stub =
                    NotificationAudienceGrpcServiceGrpc.newBlockingStub(channel)
                            .withDeadlineAfter(5, TimeUnit.SECONDS);
            assertThatThrownBy(
                            () ->
                                    stub.resolveNotificationAudience(
                                            request("anything.broadcast").build()))
                    .isInstanceOfSatisfying(
                            StatusRuntimeException.class,
                            error ->
                                    assertThat(error.getStatus().getCode())
                                            .isEqualTo(Status.Code.UNAUTHENTICATED));
            Metadata metadata = new Metadata();
            metadata.put(
                    Metadata.Key.of("x-grpc-secret", Metadata.ASCII_STRING_MARSHALLER),
                    "synthetic-audience");
            var admitted =
                    stub.withInterceptors(MetadataUtils.newAttachHeadersInterceptor(metadata));
            assertThatThrownBy(
                            () ->
                                    admitted.resolveNotificationAudience(
                                            request("anything.broadcast").build()))
                    .isInstanceOfSatisfying(
                            StatusRuntimeException.class,
                            error ->
                                    assertThat(error.getStatus().getCode())
                                            .isEqualTo(Status.Code.INVALID_ARGUMENT));
            metadata.put(
                    Metadata.Key.of("x-grpc-secret", Metadata.ASCII_STRING_MARSHALLER),
                    "wrong-synthetic-token");
            var mismatched =
                    stub.withInterceptors(MetadataUtils.newAttachHeadersInterceptor(metadata));
            assertThatThrownBy(
                            () ->
                                    mismatched.resolveNotificationAudience(
                                            request("anything.broadcast").build()))
                    .isInstanceOfSatisfying(
                            StatusRuntimeException.class,
                            error ->
                                    assertThat(error.getStatus().getCode())
                                            .isEqualTo(Status.Code.UNAUTHENTICATED));
        } finally {
            channel.shutdownNow().awaitTermination(5, TimeUnit.SECONDS);
            server.shutdownNow().awaitTermination(5, TimeUnit.SECONDS);
        }
        Server unconfigured =
                ServerBuilder.forPort(0)
                        .addService(
                                ServerInterceptors.intercept(
                                        new NotificationAudienceGrpcService(resolver),
                                        new NotificationAudienceServiceAdmission("")))
                        .build()
                        .start();
        ManagedChannel unconfiguredChannel =
                ManagedChannelBuilder.forAddress("localhost", unconfigured.getPort())
                        .usePlaintext()
                        .build();
        try {
            Metadata metadata = new Metadata();
            metadata.put(
                    Metadata.Key.of("x-grpc-secret", Metadata.ASCII_STRING_MARSHALLER),
                    "synthetic-audience");
            var stub =
                    NotificationAudienceGrpcServiceGrpc.newBlockingStub(unconfiguredChannel)
                            .withDeadlineAfter(5, TimeUnit.SECONDS)
                            .withInterceptors(MetadataUtils.newAttachHeadersInterceptor(metadata));
            assertThatThrownBy(
                            () ->
                                    stub.resolveNotificationAudience(
                                            request("anything.broadcast").build()))
                    .isInstanceOfSatisfying(
                            StatusRuntimeException.class,
                            error ->
                                    assertThat(error.getStatus().getCode())
                                            .isEqualTo(Status.Code.UNAUTHENTICATED));
        } finally {
            unconfiguredChannel.shutdownNow().awaitTermination(5, TimeUnit.SECONDS);
            unconfigured.shutdownNow().awaitTermination(5, TimeUnit.SECONDS);
        }
    }

    @Test
    void unknownPersonalTargetRejectsButKnownArchivedOrRevokedTargetIsEmpty() {
        long absent = user("student", null);
        jdbc.update("DELETE FROM users WHERE id=?", absent);
        for (String event : List.of("assistant.access.changed", "admin.access.changed")) {
            assertThatThrownBy(
                            () ->
                                    resolver.resolve(
                                            request(event).setAffectedUserId(absent).build()))
                    .isInstanceOfSatisfying(
                            StatusRuntimeException.class,
                            error ->
                                    assertThat(error.getStatus().getCode())
                                            .isEqualTo(Status.Code.INVALID_ARGUMENT));
        }
        jdbc.update("UPDATE users SET status='archived' WHERE id=?", owner);
        assertThat(
                        resolver.resolve(
                                        request("assistant.access.changed")
                                                .setAffectedUserId(owner)
                                                .build())
                                .getRecipientsList())
                .isEmpty();
        jdbc.update(
                "UPDATE user_role_grants SET status='suspended' WHERE user_id=? AND role='student'",
                helper);
        assertThat(
                        resolver.resolve(
                                        request("assistant.access.changed")
                                                .setAffectedUserId(helper)
                                                .build())
                                .getRecipientsList())
                .isEmpty();
    }

    @ParameterizedTest
    // The archive parameter moved to NotificationHeadmanArchiveIT with real Auth admission/proof.
    @ValueSource(strings = {"role", "status"})
    void userHeadmanCascadesEmitOneRealAssistantRevocation(String transition) throws Exception {
        long admin = admin("FULL");
        actor(admin, "ADMIN", null, false, session(admin));
        assistantRow(helper, "manage_excuses");
        for (int repeat = 0; repeat < 2; repeat++) {
            switch (transition) {
                case "role" ->
                        userService.updateRoleGrant(
                                headman,
                                "STUDENT",
                                new RoleGrantUpdateRequest(
                                        RoleGrantStatus.SUSPENDED, null, null, null));
                case "status" ->
                        userService.patchUser(
                                headman,
                                new PatchUserRequest(
                                        null,
                                        null,
                                        null,
                                        null,
                                        null,
                                        null,
                                        null,
                                        AccountStatus.EXPELLED));
            }
        }
        beforeCommit();
        assertThat(
                        jdbc.queryForObject(
                                "SELECT is_active FROM headman_assistants WHERE group_id=? AND student_id=?",
                                Boolean.class,
                                group,
                                helper))
                .isFalse();
        assertThat(facts("assistant.access.changed", helper))
                .singleElement()
                .satisfies(
                        fact -> {
                            var payload = fact.path("payload");
                            assertThat(payload.path("change").asText()).isEqualTo("REVOKED");
                            assertThat(payload.path("before_active").asBoolean()).isTrue();
                            assertThat(payload.path("after_active").asBoolean()).isFalse();
                            assertThat(payload.path("group_id").asLong()).isEqualTo(group);
                            assertThat(payload.path("actor_user_id").asLong()).isEqualTo(admin);
                        });
    }

    @Test
    void assistantTransitionsAreTransactionalAndNoOpOrRepeatDoesNotAddFacts() throws Exception {
        actor(headman, "HEADMAN", group, true, null);
        var assistant =
                assistantService.assignAssistant(
                        new AssignAssistantRequest(
                                helper, group, List.of(AssistantPermission.MANAGE_EXCUSES)));
        assistantService.updatePermissions(
                assistant.getId(),
                new UpdateAssistantPermissionsRequest(List.of(AssistantPermission.MANAGE_EXCUSES)));
        assistantService.updatePermissions(
                assistant.getId(),
                new UpdateAssistantPermissionsRequest(
                        List.of(AssistantPermission.MARK_ATTENDANCE)));
        assistantService.revokeAssistant(assistant.getId());
        assistantService.revokeAssistant(assistant.getId());
        beforeCommit();
        var facts = facts("assistant.access.changed", helper);
        assertThat(facts).hasSize(3);
        assertThat(facts)
                .extracting(fact -> fact.path("payload").path("change").asText())
                .containsExactly("GRANTED", "PERMISSIONS_CHANGED", "REVOKED");
        assertThat(facts)
                .allSatisfy(
                        fact -> {
                            assertThat(fact.path("payload").path("actor_user_id").asLong())
                                    .isEqualTo(headman);
                            assertThat(fact.toString())
                                    .doesNotContain("last_name", "reason", "password");
                        });
    }

    @Test
    void headmanSwitchEmitsBothPersonalFactsAndOneRealHelperRevocation() throws Exception {
        long admin = admin("FULL");
        actor(admin, "ADMIN", null, false, session(admin));
        assistantRow(helper, "manage_excuses");
        headmanService.assign(group, new AssignHeadmanRequest(owner, headman));
        headmanService.assign(group, new AssignHeadmanRequest(owner, owner));
        beforeCommit();
        assertThat(facts("headman.access.changed", headman))
                .singleElement()
                .satisfies(
                        fact ->
                                assertThat(fact.path("payload").path("change").asText())
                                        .isEqualTo("REVOKED"));
        assertThat(facts("headman.access.changed", owner))
                .singleElement()
                .satisfies(
                        fact ->
                                assertThat(fact.path("payload").path("change").asText())
                                        .isEqualTo("GRANTED"));
        assertThat(facts("assistant.access.changed", helper)).hasSize(1);
    }

    @Test
    void transferSavesOnlyPersonalMembershipFactsWithoutPrivateReason() throws Exception {
        long admin = admin("FULL");
        actor(admin, "ADMIN", null, false, session(admin));
        assistantRow(owner, "manage_excuses");
        userService.transferStudent(
                owner, new TransferStudentRequest(foreignGroup, "private synthetic reason"));
        beforeCommit();
        assertThat(facts("group.membership.changed", owner))
                .singleElement()
                .satisfies(
                        fact -> {
                            var payload = fact.path("payload");
                            assertThat(payload.path("old_group_id").asLong()).isEqualTo(group);
                            assertThat(payload.path("new_group_id").asLong())
                                    .isEqualTo(foreignGroup);
                            assertThat(payload.path("effective_date").asText())
                                    .isEqualTo(
                                            LocalDate.now(ZoneId.of("Europe/Moscow")).toString());
                            assertThat(fact.toString()).doesNotContain("private synthetic reason");
                        });
        assertThat(facts("assistant.access.changed", owner))
                .singleElement()
                .satisfies(
                        fact ->
                                assertThat(fact.path("payload").path("change").asText())
                                        .isEqualTo("REVOKED"));
    }

    @Test
    void manualActiveWithdrawalEmitsOnceAndAutomaticArchiveEmitsNone() throws Exception {
        long manual = homework("ACTIVE", day);
        long automatic = homework("ACTIVE", day);
        long expired = homework("ACTIVE", LocalDate.of(2000, 1, 1));
        var content = homeworks.findById(manual).orElseThrow();
        archive.archiveExistingHomework(
                content, content.getBindingId(), content.getRequestKey(), headman, "HEADMAN", null);
        archive.archiveExistingHomework(
                content, content.getBindingId(), content.getRequestKey(), headman, "HEADMAN", null);
        var system = homeworks.findById(automatic).orElseThrow();
        archive.archiveExistingHomework(system, system.getBindingId(), system.getRequestKey());
        var past = homeworks.findById(expired).orElseThrow();
        archive.archiveExistingHomework(
                past, past.getBindingId(), past.getRequestKey(), headman, "HEADMAN", null);
        beforeCommit();
        var rows =
                jdbc.queryForList(
                        "SELECT id,payload::text FROM academic_outbox WHERE event_type='homework.withdrawn' AND (payload->'payload'->>'homework_id')::bigint IN (?,?,?) ORDER BY id",
                        manual,
                        automatic,
                        expired);
        assertThat(rows).hasSize(1);
        for (var row : rows) outboxIds.add(((Number) row.get("id")).longValue());
        var payload = mapper.readTree(rows.getFirst().get("payload").toString()).path("payload");
        assertThat(payload.path("homework_id").asLong()).isEqualTo(manual);
        assertThat(payload.path("semester_id").asLong()).isEqualTo(semester);
        assertThat(payload.path("lesson_type").asText()).isEqualTo("lab");
        assertThat(payload.path("binding_mode").asText()).isEqualTo("DATE");
    }

    private NotificationAudienceRequest.Builder request(String event) {
        return NotificationAudienceRequest.newBuilder()
                .setRequestId(UUID.randomUUID().toString())
                .setEventType(event)
                .setOccurredAt(day + "T12:00:00Z")
                .setGroupId(group);
    }

    private LessonNotificationScope scope(
            long targetGroup, long targetSubject, String type, LocalDate date) {
        return LessonNotificationScope.newBuilder()
                .setGroupId(targetGroup)
                .setSubjectId(targetSubject)
                .setSemesterId(semester)
                .setLessonType(type)
                .setLessonDate(date.toString())
                .build();
    }

    private Map<Long, Set<String>> audience(NotificationAudienceResponse response) {
        var result = new LinkedHashMap<Long, Set<String>>();
        for (var recipient : response.getRecipientsList()) {
            assertThat(result.put(recipient.getUserId(), Set.copyOf(recipient.getRolesList())))
                    .isNull();
        }
        return result;
    }

    private long group() {
        long id =
                jdbc.queryForObject(
                        "INSERT INTO groups(name,is_active,created_at) VALUES(?,true,NOW()) RETURNING id",
                        Long.class,
                        "Аудитория " + UUID.randomUUID().toString().substring(0, 12));
        jdbc.update(
                "INSERT INTO group_history_coverage(group_id,coverage_from,writer_version) VALUES(?,?,'managed_v1')",
                id,
                LocalDate.now(ZoneId.of("Europe/Moscow")).minusDays(2));
        return id;
    }

    private long user(String role, Long group) {
        return jdbc.queryForObject(
                "INSERT INTO users(login,password_hash,last_name,first_name,role,status,group_id,created_at,updated_at) VALUES(?,'$2a$10$dummy','Синтетический','Адресат',CAST(? AS user_role),'active',?,NOW(),NOW()) RETURNING id",
                Long.class,
                "aud_" + UUID.randomUUID().toString().substring(0, 16),
                role,
                group);
    }

    private long student(long group) {
        long user = user("student", group);
        grant(user, "student", group);
        jdbc.update(
                "INSERT INTO student_group_history(user_id,group_id,joined_at) VALUES(?,?,?)",
                user,
                group,
                LocalDate.now(ZoneId.of("Europe/Moscow")).minusDays(2));
        return user;
    }

    private void grant(long user, String role, Long group) {
        jdbc.update(
                "INSERT INTO user_role_grants(user_id,role,status,group_id,created_at,updated_at) VALUES(?,?,'active',?,NOW(),NOW())",
                user,
                role,
                group);
    }

    private long subject(long group) {
        long id =
                jdbc.queryForObject(
                        "INSERT INTO subjects(name,type,group_id) VALUES(?,'lecture',?) RETURNING id",
                        Long.class,
                        "Audience " + UUID.randomUUID(),
                        group);
        for (String type : List.of("lecture", "practice", "lab"))
            jdbc.update(
                    "INSERT INTO subject_lesson_types(subject_id,lesson_type) VALUES(?,CAST(? AS subject_type))",
                    id,
                    type);
        return id;
    }

    private long assignment(
            long user,
            long group,
            long subject,
            String type,
            LocalDate from,
            LocalDate until,
            String lifecycle) {
        return jdbc.queryForObject(
                "INSERT INTO assignments(teacher_id,group_id,subject_id,semester_id,lesson_type,valid_from,valid_until_exclusive,lifecycle_state) VALUES(?,?,?,?,CAST(? AS subject_type),?,?,?) RETURNING id",
                Long.class,
                user,
                group,
                subject,
                semester,
                type,
                from,
                until,
                lifecycle);
    }

    private void assistantRow(long user, String permission) {
        jdbc.update(
                "INSERT INTO headman_assistants(group_id,student_id,permissions,assigned_by,is_active,assigned_at) VALUES(?,?,ARRAY[?]::varchar[],?,true,NOW())",
                group,
                user,
                permission,
                headman);
    }

    private long admin(String mode, String... permissions) {
        long admin = user("admin", null);
        jdbc.update(
                "INSERT INTO admin_access_profiles(user_id,assigned,mode,permissions,version) VALUES(?,true,?,?::text[],1)",
                admin,
                mode,
                "{" + String.join(",", permissions) + "}");
        grant(admin, "admin", null);
        return admin;
    }

    private UUID session(long admin) {
        long grant =
                jdbc.queryForObject(
                        "SELECT id FROM user_role_grants WHERE user_id=? AND role='admin'",
                        Long.class,
                        admin);
        UUID sid = UUID.randomUUID();
        jdbc.update(
                "INSERT INTO auth_sessions(sid,user_id,active_role_grant_id,session_version,current_refresh_jti,refresh_expires_at,created_at,last_seen_at,auth_method) VALUES(?,?,?,1,?,NOW()+interval '1 day',NOW(),NOW(),'PASSWORD')",
                sid,
                admin,
                grant,
                UUID.randomUUID());
        return sid;
    }

    private void actor(long user, String role, Long group, boolean headman, UUID sid) {
        long version =
                jdbc.queryForObject("SELECT roles_version FROM users WHERE id=?", Long.class, user);
        context.setClaims(
                new InternalJwtClaims(
                        user,
                        sid == null ? UUID.randomUUID() : sid,
                        1,
                        version,
                        role,
                        "ACTIVE",
                        group,
                        headman,
                        false));
        context.setUserId(user);
        context.setRole(role.equals("ADMIN") ? UserRole.ADMIN : UserRole.STUDENT);
        context.setGroupId(group);
        context.setHeadman(headman);
    }

    private void beforeCommit() {
        TransactionSynchronizationUtils.triggerBeforeCommit(false);
    }

    private List<JsonNode> facts(String type, long user) throws Exception {
        var rows =
                jdbc.queryForList(
                        "SELECT id,payload::text FROM academic_outbox WHERE event_type=? AND (payload->'payload'->>'user_id')::bigint=? ORDER BY id",
                        type,
                        user);
        var result = new ArrayList<JsonNode>();
        for (var row : rows) {
            outboxIds.add(((Number) row.get("id")).longValue());
            result.add(mapper.readTree(row.get("payload").toString()));
        }
        return result;
    }

    private long homework(String state, LocalDate date) {
        long binding =
                jdbc.queryForObject("SELECT nextval('homeworks_id_seq')", Long.class) + 1000000000L;
        return jdbc.queryForObject(
                "INSERT INTO homeworks(group_id,subject_id,semester_id,title,published_by,lesson_date,lesson_number,binding_mode,lesson_type,binding_id,actor_id,request_key,payload_hash,publication_state) VALUES(?,?,?,'Audience homework',?,?,NULL,'DATE','LAB',?,?,?,decode(repeat('11',32),'hex'),?) RETURNING id",
                Long.class,
                group,
                subject,
                semester,
                owner,
                date,
                binding,
                owner,
                UUID.randomUUID(),
                state);
    }
}
