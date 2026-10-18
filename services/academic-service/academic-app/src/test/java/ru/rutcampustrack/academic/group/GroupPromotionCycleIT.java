package ru.rutcampustrack.academic.group;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import ru.rutcampustrack.academic.contract.dto.group.AssignHeadmanRequest;
import ru.rutcampustrack.academic.contract.dto.user.CreateUserRequest;
import ru.rutcampustrack.academic.contract.enums.UserRole;
import ru.rutcampustrack.academic.groupavailability.GroupAvailabilityCoordinator;
import ru.rutcampustrack.academic.groupavailability.GroupAvailabilityStore;
import ru.rutcampustrack.academic.identityoperation.AcademicIdentityPendingException;
import ru.rutcampustrack.academic.integration.AbstractAcademicIntegrationTest;
import ru.rutcampustrack.academic.integration.AcademicIdentityIntegrationFixture.SelectedActor;
import ru.rutcampustrack.auth.session.model.AuthRole;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/** Real PostgreSQL proof for the completed-spring promotion cycle ledger. */
@AutoConfigureMockMvc
@org.springframework.test.context.event.RecordApplicationEvents
@Import(GroupPromotionCycleIT.PromotionTestClockConfig.class)
class GroupPromotionCycleIT extends AbstractAcademicIntegrationTest {

    private static final LocalDate CYCLE_END = LocalDate.of(2026, 6, 30);

    @Autowired private MockMvc mockMvc;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private GroupPromotionJob groupPromotionJob;
    @Autowired private GroupPromotionService groupPromotionService;
    @Autowired private ru.rutcampustrack.academic.semester.SemesterService semesterService;

    @Autowired
    private ru.rutcampustrack.academic.history.HistoricalMembershipService historicalMembership;

    @Autowired private org.springframework.test.context.event.ApplicationEvents applicationEvents;

    @MockitoSpyBean
    private ru.rutcampustrack.academic.event.DomainEventListener domainEventListener;

    @Autowired private GroupAvailabilityCoordinator coordinator;
    @Autowired private GroupAvailabilityStore groupOperations;
    @Autowired private GroupHeadmanAssignmentService headmen;
    private static final AtomicLong TELEGRAM = new AtomicLong(610000008000L);
    private SelectedActor administrator;
    private SelectedActor teacher;

    @TestConfiguration
    static class PromotionTestClockConfig {
        @Bean
        @Primary
        Clock promotionTestClock() {
            return Clock.fixed(Instant.parse("2026-09-01T00:00:00Z"), ZoneId.of("Europe/Moscow"));
        }
    }

    private String adminToken;

    @org.junit.jupiter.api.BeforeEach
    void selectDurableFullAdminSession() {
        administrator =
                identityFixture.select(identityFixture.canonical().adminId(), AuthRole.ADMIN);
        teacher = identityFixture.select(identityFixture.canonical().teacherId(), AuthRole.TEACHER);
        adminToken = administrator.internalToken();
    }

    @Test
    void singleThenMassPromotionRejectsStaleAndRetriedPlansWithoutChangingIdentityOrHistory()
            throws Exception {
        Long cycleId = prepareSpringFixture();
        Long adminId = userId("admin");
        Long teacherId = userId("teacher");

        Long firstGroup = addGroup("УИТ-151", beforeCycleEnd());
        jdbc.update(
                "UPDATE groups SET alphabetic_code = 'УИТ', numeric_code = '151', current_course ="
                        + " 1, training_duration_years = 3, duration_status = 'KNOWN' WHERE id = ?",
                firstGroup);
        Long archiveGroup = addGroup("УИТ-411", beforeCycleEnd());
        Long massGroup = addGroup("УВП-111", beforeCycleEnd());
        Long lateGroup = addGroup("РЕГ-111", afterCycleEnd());
        Long studentId = addStudent(firstGroup, "prom_s_" + System.nanoTime());
        enrollmentAt(studentId, firstGroup, CYCLE_END.minusDays(20), "promotion-cycle-it");

        completeSpring(cycleId);

        mockMvc.perform(previewRequest(teacherId, "TEACHER", "{}"))
                .andExpect(status().isForbidden());

        JsonNode singlePreview =
                response(previewRequest(adminId, "ADMIN", "{\"groupId\":" + firstGroup + "}"));
        assertThat(singlePreview.path("dryRun").asBoolean()).isTrue();
        assertThat(singlePreview.path("executed").asBoolean()).isFalse();
        assertThat(singlePreview.path("cycleSemesterId").asLong()).isEqualTo(cycleId);
        assertThat(singlePreview.path("groupId").asLong()).isEqualTo(firstGroup);
        assertThat(singlePreview.path("promoteCount").asInt()).isEqualTo(1);
        assertThat(singlePreview.path("promotedStudentCount").asLong()).isEqualTo(1L);
        assertThat(singlePreview.path("toPromote").get(0).path("id").asLong())
                .isEqualTo(firstGroup);

        String singleVersion = singlePreview.path("previewVersion").asText();
        JsonNode singleResult =
                response(executeRequest(adminId, cycleId, singleVersion, firstGroup));
        assertThat(singleResult.path("executed").asBoolean()).isTrue();
        assertThat(singleResult.path("dryRun").asBoolean()).isFalse();
        assertThat(groupName(firstGroup)).isEqualTo("УИТ-251");
        mockMvc.perform(
                        get("/academic/groups/registry")
                                .header("X-Internal-Token", adminToken)
                                .header("X-User-Id", adminId)
                                .header("X-User-Role", "ADMIN")
                                .with(signedAdmin())
                                .header("X-Group-Id", "")
                                .header("X-Is-Headman", "false")
                                .param("status", "DRAFT")
                                .param("search", "УИТ-251")
                                .param("course", "2")
                                .param("studyMode", "PART_TIME")
                                .param("educationLevel", "SPECIALIST"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].currentCourse").value(2))
                .andExpect(jsonPath("$.items[0].trainingDurationYears").value(3));
        assertThat(
                        jdbc.queryForObject(
                                "SELECT count(*) FROM student_group_history WHERE user_id = ? AND"
                                        + " group_id = ?",
                                Long.class,
                                studentId,
                                firstGroup))
                .isEqualTo(1L);

        JsonNode massPreview = response(previewRequest(adminId, "ADMIN", "{}"));
        assertThat(massPreview.path("groupId").isNull()).isTrue();
        assertThat(massPreview.path("skipped").findValuesAsText("reason"))
                .contains("ALREADY_PROCESSED", "CREATED_AFTER_CYCLE_END");
        assertThat(massPreview.path("skipped").findValuesAsText("id"))
                .contains(Long.toString(firstGroup), Long.toString(lateGroup));
        assertThat(massPreview.path("archiveCount").asInt()).isEqualTo(1);
        assertThat(massPreview.path("toArchive").get(0).path("id").asLong())
                .isEqualTo(archiveGroup);

        addGroup("УР-111", beforeCycleEnd());
        mockMvc.perform(
                        executeRequest(
                                adminId,
                                cycleId,
                                massPreview.path("previewVersion").asText(),
                                null))
                .andExpect(status().isConflict());
        assertThat(groupName(massGroup)).isEqualTo("УВП-111");
        assertThat(groupName(archiveGroup)).isEqualTo("УИТ-411");

        JsonNode refreshedMassPreview = response(previewRequest(adminId, "ADMIN", "{}"));
        assertThat(refreshedMassPreview.path("skipped").findValuesAsText("id"))
                .contains(Long.toString(firstGroup), Long.toString(lateGroup));
        assertThat(refreshedMassPreview.path("toArchive").get(0).path("id").asLong())
                .isEqualTo(archiveGroup);
        JsonNode massResult =
                response(
                        executeRequest(
                                adminId,
                                cycleId,
                                refreshedMassPreview.path("previewVersion").asText(),
                                null));
        assertThat(massResult.path("executed").asBoolean()).isTrue();
        assertThat(groupName(firstGroup)).isEqualTo("УИТ-251");
        assertThat(groupName(massGroup)).isEqualTo("УВП-211");
        assertThat(groupName(archiveGroup)).isEqualTo("УИТ-411 (выпуск 2026)");
        assertThat(groupIdByName("УИТ-251")).isEqualTo(firstGroup);
        assertThat(
                        jdbc.queryForObject(
                                "SELECT count(*) FROM student_group_history WHERE user_id = ? AND"
                                        + " group_id = ?",
                                Long.class,
                                studentId,
                                firstGroup))
                .isEqualTo(1L);

        mockMvc.perform(
                        executeRequest(
                                adminId,
                                cycleId,
                                refreshedMassPreview.path("previewVersion").asText(),
                                null))
                .andExpect(status().isConflict());
        assertThat(groupName(firstGroup)).isEqualTo("УИТ-251");
        assertThat(groupName(massGroup)).isEqualTo("УВП-211");
        assertThat(
                        jdbc.queryForObject(
                                "SELECT count(*) FROM group_promotion_cycle_record WHERE"
                                        + " cycle_semester_id = ? AND group_id = ?",
                                Long.class,
                                cycleId,
                                firstGroup))
                .isEqualTo(1L);
        assertThat(
                        jdbc.queryForObject(
                                "SELECT count(*) FROM group_promotion_cycle_record WHERE"
                                        + " cycle_semester_id = ? AND group_id = ?",
                                Long.class,
                                cycleId,
                                massGroup))
                .isEqualTo(1L);
    }

    @Test
    void automaticCatchUpReusesManualLedgerAndReplayDoesNotPromoteTwice() throws Exception {
        Long cycleId = prepareSpringFixture();
        Long adminId = userId("admin");
        Long manualGroup = addGroup("УИТ-111", beforeCycleEnd());
        Long automaticGroup = addGroup("УВП-111", beforeCycleEnd());
        Long graduatingGroup = addGroup("РЕГ-411", beforeCycleEnd());
        Long lateGroup = addGroup("РЕГ-111", afterCycleEnd());
        Long studentId = addStudent(automaticGroup, "auto_" + System.nanoTime());
        enrollmentAt(
                studentId, automaticGroup, CYCLE_END.minusDays(20), "automatic-promotion-cycle-it");

        try (var ignored = identityFixture.request(administrator)) {
            headmen.assign(automaticGroup, new AssignHeadmanRequest(studentId, null));
        }
        long headmanVersion = identityFixture.select(studentId, AuthRole.HEADMAN).rolesVersion();
        completeSpring(cycleId);

        JsonNode manualPreview =
                response(previewRequest(adminId, "ADMIN", "{\"groupId\":" + manualGroup + "}"));
        response(
                executeRequest(
                        adminId,
                        cycleId,
                        manualPreview.path("previewVersion").asText(),
                        manualGroup));
        assertThat(automaticCompletionFacts()).isEmpty();

        var pending = groupPromotionService.preview(null);
        java.util.Set<Long> skippedIds = new java.util.HashSet<>();
        pending.getSkipped().forEach(item -> skippedIds.add(item.getId()));
        pending.getConflicts().forEach(conflict -> skippedIds.addAll(conflict.getGroupIds()));

        var outboxBefore =
                jdbc.queryForList(
                        "SELECT id,event_type,payload::text FROM academic_outbox ORDER BY id");
        var groupsBefore =
                jdbc.queryForList(
                        "SELECT id,name,current_course,is_active,academic_revision FROM groups "
                                + "WHERE id IN (?,?) ORDER BY id",
                        automaticGroup,
                        graduatingGroup);
        var historyBefore =
                jdbc.queryForList(
                        "SELECT * FROM student_group_history WHERE user_id=? ORDER BY id",
                        studentId);
        var failOnce = new AtomicBoolean(true);
        doAnswer(
                        call -> {
                            call.callRealMethod(); // Actual outbox INSERT, inside the real
                            // BEFORE_COMMIT phase.
                            if (call.getArgument(0)
                                            instanceof
                                            ru.rutcampustrack.academic.event
                                                    .AdministrativeNotificationGroupPromotionCompletedEvent
                                    && failOnce.compareAndSet(true, false))
                                throw io.grpc.Status.UNAVAILABLE
                                        .withDescription("owned before-commit failure")
                                        .asRuntimeException();
                            return null;
                        })
                .when(domainEventListener)
                .onDomainEvent(any(ru.rutcampustrack.academic.event.DomainEvent.class));
        AcademicIdentityPendingException interrupted =
                catchThrowableOfType(
                        () -> groupPromotionJob.checkDueSpringCycle(),
                        AcademicIdentityPendingException.class);
        assertThat(interrupted).isNotNull();
        UUID operation = interrupted.operation().operationId();
        var accepted = groupOperations.find(operation);
        assertThat(accepted.phase()).isEqualTo(GroupAvailabilityStore.Phase.AUTH_INVALIDATED);
        assertThat(interrupted.operation().status())
                .isEqualTo(
                        ru.rutcampustrack.academic.contract.dto.IdentityOperationHttpModels.Status
                                .PENDING);
        assertThat(accepted.receipts()).isNull();
        assertThat(
                        jdbc.queryForList(
                                "SELECT id,event_type,payload::text FROM academic_outbox ORDER BY"
                                        + " id"))
                .isEqualTo(outboxBefore);
        assertThat(
                        jdbc.queryForList(
                                "SELECT id,name,current_course,is_active,academic_revision FROM"
                                        + " groups WHERE id IN (?,?) ORDER BY id",
                                automaticGroup,
                                graduatingGroup))
                .isEqualTo(groupsBefore);
        assertThat(
                        jdbc.queryForList(
                                "SELECT * FROM student_group_history WHERE user_id=? ORDER BY id",
                                studentId))
                .isEqualTo(historyBefore);
        assertThat(
                        jdbc.queryForObject(
                                "SELECT applied_auth_roles_version FROM academic_profiles WHERE"
                                        + " id=?",
                                Long.class,
                                studentId))
                .isEqualTo(headmanVersion);
        assertThat(groupName(automaticGroup)).isEqualTo("УВП-111");
        assertThat(groupName(graduatingGroup)).isEqualTo("РЕГ-411");
        assertThat(
                        jdbc.queryForObject(
                                "SELECT count(*) FROM group_promotion_cycle_record "
                                        + "WHERE cycle_semester_id=? AND group_id IN (?,?)",
                                Long.class,
                                cycleId,
                                automaticGroup,
                                graduatingGroup))
                .isZero();
        assertThat(
                        jdbc.queryForObject(
                                "SELECT count(*) FROM group_promotion_notification_cycle "
                                        + "WHERE cycle_semester_id=?",
                                Long.class,
                                cycleId))
                .isZero();
        assertThat(
                        jdbc.queryForObject(
                                "SELECT count(*) FROM academic_outbox "
                                        + "WHERE event_type='group.promotion.completed' "
                                        + "AND payload #>> '{payload,cycle_semester_id}'=?",
                                Long.class,
                                cycleId.toString()))
                .isZero();
        assertThat(
                        jdbc.queryForObject(
                                "SELECT count(*) FROM academic_group_availability_guards "
                                        + "WHERE operation_id=?",
                                Long.class,
                                operation))
                .isEqualTo(2);
        assertThat(
                        identityFixture
                                .authJdbc()
                                .queryForObject(
                                        "SELECT phase FROM auth_credential_reader_invalidations"
                                                + " WHERE operation_id=?",
                                        String.class,
                                        operation))
                .isEqualTo("INVALIDATED");
        assertThat(
                        identityFixture
                                .authJdbc()
                                .queryForObject(
                                        "SELECT roles_version FROM auth_identities WHERE id=?",
                                        Long.class,
                                        studentId))
                .isEqualTo(headmanVersion + 1);
        // ApplicationEvents also records rolled-back publications; count the recovery attempt
        // afresh.
        applicationEvents.clear();
        var recovered = coordinator.recover(operation);
        assertThat(recovered.id()).isEqualTo(operation);
        assertThat(recovered.phase()).isEqualTo(GroupAvailabilityStore.Phase.SUCCEEDED);
        assertThat(recovered.command()).isEqualTo(accepted.command());
        assertThat(recovered.auth()).isEqualTo(accepted.auth());
        assertThat(
                        jdbc.queryForObject(
                                "SELECT applied_auth_roles_version FROM academic_profiles WHERE"
                                        + " id=?",
                                Long.class,
                                studentId))
                .isEqualTo(headmanVersion + 1);
        assertThat(
                        jdbc.queryForObject(
                                "SELECT count(*) FROM academic_group_availability_guards "
                                        + "WHERE operation_id=?",
                                Long.class,
                                operation))
                .isZero();
        assertThat(
                        identityFixture
                                .authJdbc()
                                .queryForObject(
                                        "SELECT phase FROM auth_credential_reader_invalidations"
                                                + " WHERE operation_id=?",
                                        String.class,
                                        operation))
                .isEqualTo("COMMITTED");
        assertThat(
                        identityFixture
                                .authJdbc()
                                .queryForObject(
                                        "SELECT roles_version FROM auth_identities WHERE id=?",
                                        Long.class,
                                        studentId))
                .isEqualTo(headmanVersion + 1);

        assertThat(automaticCompletionFacts()).hasSize(1);
        var fact = automaticCompletionFacts().getFirst();
        assertThat(fact.cycleSemesterId()).isEqualTo(cycleId);
        assertThat(fact.promoted()).isEqualTo(1);
        assertThat(fact.graduated()).isEqualTo(1);
        assertThat(fact.skipped()).isEqualTo(skippedIds.size());
        assertThat(fact.actorUserId()).isNull();
        assertThat(
                        applicationEvents.stream(
                                        ru.rutcampustrack.academic.event.GroupRenamedEvent.class)
                                .map(
                                        event ->
                                                (ru.rutcampustrack.academic.event.GroupRenamedEvent
                                                                .Payload)
                                                        event.getPayload())
                                .filter(payload -> automaticGroup.equals(payload.groupId()))
                                .toList())
                .singleElement()
                .satisfies(
                        payload -> {
                            assertThat(payload.notificationCause())
                                    .isEqualTo("AUTOMATIC_GROUP_CYCLE");
                            assertThat(payload.cycleSemesterId()).isEqualTo(cycleId);
                        });
        assertThat(
                        applicationEvents.stream(
                                        ru.rutcampustrack.academic.event.GroupArchivedEvent.class)
                                .map(
                                        event ->
                                                (ru.rutcampustrack.academic.event.GroupArchivedEvent
                                                                .Payload)
                                                        event.getPayload())
                                .filter(payload -> graduatingGroup.equals(payload.groupId()))
                                .toList())
                .singleElement()
                .satisfies(
                        payload -> {
                            assertThat(payload.notificationCause())
                                    .isEqualTo("AUTOMATIC_GROUP_CYCLE");
                            assertThat(payload.cycleSemesterId()).isEqualTo(cycleId);
                        });

        assertThat(groupName(manualGroup)).isEqualTo("УИТ-211");
        assertThat(groupName(automaticGroup)).isEqualTo("УВП-211");
        assertThat(groupName(graduatingGroup)).isEqualTo("РЕГ-411 (выпуск 2026)");
        assertThat(groupName(lateGroup)).isEqualTo("РЕГ-111");
        assertThat(groupIdByName("УВП-211")).isEqualTo(automaticGroup);
        assertThat(
                        jdbc.queryForObject(
                                "SELECT count(*) FROM student_group_history WHERE user_id = ? AND"
                                        + " group_id = ?",
                                Long.class,
                                studentId,
                                automaticGroup))
                .isEqualTo(1L);
        for (Long groupId : List.of(manualGroup, automaticGroup, graduatingGroup)) {
            assertThat(
                            jdbc.queryForObject(
                                    "SELECT count(*) FROM group_promotion_cycle_record WHERE"
                                            + " cycle_semester_id = ? AND group_id = ?",
                                    Long.class,
                                    cycleId,
                                    groupId))
                    .isEqualTo(1L);
        }

        // Exercise replay even while the scheduler's minimum lock duration is still active.
        groupPromotionService.executeAutomaticallyIfDue();
        groupPromotionJob.checkDueSpringCycle();

        assertThat(automaticCompletionFacts()).hasSize(1);

        assertThat(groupName(automaticGroup)).isEqualTo("УВП-211");
        assertThat(groupName(graduatingGroup)).isEqualTo("РЕГ-411 (выпуск 2026)");
        assertThat(
                        jdbc.queryForObject(
                                "SELECT count(*) FROM group_promotion_cycle_record WHERE"
                                        + " cycle_semester_id = ?",
                                Long.class,
                                cycleId))
                .isEqualTo(3L);
        assertThat(
                        jdbc.queryForObject(
                                "SELECT count(*) FROM student_group_history WHERE user_id = ? AND"
                                        + " group_id = ?",
                                Long.class,
                                studentId,
                                automaticGroup))
                .isEqualTo(1L);

        // A later eligible batch in the same cycle keeps Student group facts,
        // while the durable ADMIN marker survives the first batch and replays.
        Long laterGroup = addGroup("ДОП-111", beforeCycleEnd());
        // Exercise replay even while the scheduler's minimum lock duration is still active.
        groupPromotionService.executeAutomaticallyIfDue();
        groupPromotionJob.checkDueSpringCycle();
        assertThat(groupName(laterGroup)).isEqualTo("ДОП-211");
        assertThat(automaticCompletionFacts()).hasSize(1);
        assertThat(
                        jdbc.queryForObject(
                                "SELECT count(*) FROM group_promotion_notification_cycle "
                                        + "WHERE cycle_semester_id=?",
                                Long.class,
                                cycleId))
                .isOne();
        assertThat(
                        jdbc.queryForObject(
                                "SELECT promoted FROM group_promotion_notification_cycle "
                                        + "WHERE cycle_semester_id=?",
                                Long.class,
                                cycleId))
                .isEqualTo(fact.promoted());
    }

    @Test
    void massPromotionAdjacentCoursesFlushesInVacatedNameOrderAndRetainsHistory() throws Exception {
        Long cycleId = prepareSpringFixture();
        Long adminId = userId("admin");
        Long first = addGroup("УИТ-111", beforeCycleEnd());
        Long second = addGroup("УИТ-211", beforeCycleEnd());
        Long third = addGroup("УИТ-311", beforeCycleEnd());
        Long graduating = addGroup("УИТ-411", beforeCycleEnd());
        Long studentId = addStudent(second, "adj_" + System.nanoTime());
        enrollmentAt(studentId, second, CYCLE_END.minusDays(20), "adjacent-promotion-cycle-it");

        completeSpring(cycleId);

        JsonNode preview = response(previewRequest(adminId, "ADMIN", "{}"));
        assertThat(preview.path("conflicts").size()).isZero();
        assertThat(preview.path("toPromote").size()).isEqualTo(3);
        assertThat(preview.path("toArchive").size()).isEqualTo(1);

        JsonNode result =
                response(
                        executeRequest(
                                adminId, cycleId, preview.path("previewVersion").asText(), null));

        assertThat(result.path("executed").asBoolean()).isTrue();
        assertThat(groupName(first)).isEqualTo("УИТ-211");
        assertThat(groupName(second)).isEqualTo("УИТ-311");
        assertThat(groupName(third)).isEqualTo("УИТ-411");
        assertThat(groupName(graduating)).isEqualTo("УИТ-411 (выпуск 2026)");
        assertThat(groupIdByName("УИТ-211")).isEqualTo(first);
        assertThat(groupIdByName("УИТ-311")).isEqualTo(second);
        assertThat(groupIdByName("УИТ-411")).isEqualTo(third);
        assertThat(
                        jdbc.queryForObject(
                                "SELECT count(*) FROM group_promotion_cycle_record WHERE"
                                        + " cycle_semester_id = ?",
                                Long.class,
                                cycleId))
                .isEqualTo(4L);
        assertThat(
                        jdbc.queryForObject(
                                "SELECT action FROM group_promotion_cycle_record WHERE"
                                        + " cycle_semester_id = ? AND group_id = ?",
                                String.class,
                                cycleId,
                                graduating))
                .isEqualTo("ARCHIVE");
        assertThat(
                        jdbc.queryForObject(
                                "SELECT count(*) FROM student_group_history WHERE user_id = ? AND"
                                        + " group_id = ?",
                                Long.class,
                                studentId,
                                second))
                .isEqualTo(1L);
    }

    private List<
                    ru.rutcampustrack.academic.event
                            .AdministrativeNotificationGroupPromotionCompletedEvent.Payload>
            automaticCompletionFacts() {
        return applicationEvents.stream(
                        ru.rutcampustrack.academic.event
                                .AdministrativeNotificationGroupPromotionCompletedEvent.class)
                .map(
                        event ->
                                (ru.rutcampustrack.academic.event
                                                .AdministrativeNotificationGroupPromotionCompletedEvent
                                                .Payload)
                                        event.getPayload())
                .toList();
    }

    @Test
    void durationOnlyCorrectionInvalidatesPreviewWithoutResettingCycleLedger() throws Exception {
        Long adminId = userId("admin");
        Long cycleId = prepareSpringFixture();
        Long groupId = addGroup("УИТ-111", beforeCycleEnd());
        String previewBody = "{\"groupId\":" + groupId + "}";
        completeSpring(cycleId);

        JsonNode legacyPreview = response(previewRequest(adminId, "ADMIN", previewBody));
        assertThat(legacyPreview.path("toPromote").get(0).path("to").asText()).isEqualTo("УИТ-211");
        var update =
                put("/academic/groups/" + groupId)
                        .header("X-Internal-Token", adminToken)
                        .header("X-User-Id", adminId)
                        .header("X-User-Role", "ADMIN")
                        .with(signedAdmin())
                        .header("X-Group-Id", "")
                        .header("X-Is-Headman", "false")
                        .contentType(APPLICATION_JSON);
        response(
                update.content(
                        "{\"name\":\"УИТ-111\",\"active\":true,\"trainingDurationYears\":4}"));
        mockMvc.perform(
                        executeRequest(
                                adminId,
                                cycleId,
                                legacyPreview.path("previewVersion").asText(),
                                groupId))
                .andExpect(status().isConflict());
        JsonNode fourYearPreview = response(previewRequest(adminId, "ADMIN", previewBody));
        assertThat(fourYearPreview.path("toPromote")).isEqualTo(legacyPreview.path("toPromote"));
        assertThat(fourYearPreview.path("previewVersion").asText())
                .isNotEqualTo(legacyPreview.path("previewVersion").asText());
        response(
                update.content(
                        "{\"name\":\"УИТ-111\",\"active\":true,\"trainingDurationYears\":5}"));
        mockMvc.perform(
                        executeRequest(
                                adminId,
                                cycleId,
                                fourYearPreview.path("previewVersion").asText(),
                                groupId))
                .andExpect(status().isConflict());
        assertThat(groupName(groupId)).isEqualTo("УИТ-111");
        assertThat(
                        jdbc.queryForObject(
                                "SELECT count(*) FROM group_promotion_cycle_record "
                                        + "WHERE cycle_semester_id = ? AND group_id = ?",
                                Long.class,
                                cycleId,
                                groupId))
                .isZero();
        JsonNode freshPreview = response(previewRequest(adminId, "ADMIN", previewBody));
        assertThat(freshPreview.path("toPromote")).isEqualTo(fourYearPreview.path("toPromote"));
        assertThat(freshPreview.path("previewVersion").asText())
                .isNotEqualTo(fourYearPreview.path("previewVersion").asText());
        response(
                executeRequest(
                        adminId, cycleId, freshPreview.path("previewVersion").asText(), groupId));
        assertThat(groupName(groupId)).isEqualTo("УИТ-211");
        mockMvc.perform(
                        executeRequest(
                                adminId,
                                cycleId,
                                freshPreview.path("previewVersion").asText(),
                                groupId))
                .andExpect(status().isConflict());
        assertThat(
                        jdbc.queryForObject(
                                "SELECT count(*) FROM group_promotion_cycle_record "
                                        + "WHERE cycle_semester_id = ? AND group_id = ?",
                                Long.class,
                                cycleId,
                                groupId))
                .isEqualTo(1L);
    }

    @Test
    void archivedExtensionPreservesOriginalCutoffRosterAssignmentsAndOnceOnlyPromotion()
            throws Exception {
        Long adminId = userId("admin");
        Long cycleId = prepareSpringFixture();
        Long corrected = addGroup("РМ-121", beforeCycleEnd());
        Long graduating = addGroup("УЦ-511", beforeCycleEnd());
        jdbc.update("UPDATE groups SET training_duration_years = 5 WHERE id = ?", graduating);
        Long late = addGroup("НГ-111", afterCycleEnd());
        Long studentId = addStudent(corrected, "cutoff_s_" + System.nanoTime());
        enrollmentAt(studentId, corrected, CYCLE_END.minusDays(10), "cutoff-history-it");
        jdbc.update(
                "UPDATE group_history_coverage SET coverage_from=? WHERE group_id=?",
                CYCLE_END.minusDays(10),
                corrected);
        long subjectId =
                jdbc.queryForObject(
                        "INSERT INTO subjects(name, type, group_id) "
                                + "VALUES (?, 'lecture', ?) RETURNING id",
                        Long.class,
                        "cutoff-" + System.nanoTime(),
                        corrected);
        jdbc.update(
                "INSERT INTO subject_lesson_types(subject_id, lesson_type) VALUES (?, 'lecture')",
                subjectId);
        long assignmentId =
                jdbc.queryForObject(
                        "INSERT INTO assignments(teacher_id, subject_id, group_id, "
                                + "semester_id, lesson_type, valid_from, valid_until_exclusive) "
                                + "VALUES (?, ?, ?, ?, 'lecture', ?, ?) RETURNING id",
                        Long.class,
                        userId("teacher"),
                        subjectId,
                        corrected,
                        cycleId,
                        CYCLE_END.minusDays(10),
                        CYCLE_END.plusDays(1));
        List<Long> rosterBefore =
                historicalMembership.readRoster(corrected, CYCLE_END, cycleId).students().stream()
                        .map(ru.rutcampustrack.academic.entity.User::getId)
                        .toList();
        assertThat(rosterBefore).containsExactly(studentId);

        completeSpring(cycleId);

        JsonNode invalidPlan =
                response(previewRequest(adminId, "ADMIN", "{\"groupId\":" + corrected + "}"));
        assertThat(invalidPlan.path("conflicts").get(0).path("reason").asText())
                .isEqualTo("invalid_code");
        var groupUpdate =
                put("/academic/groups/" + corrected)
                        .header("X-Internal-Token", adminToken)
                        .header("X-User-Id", adminId)
                        .header("X-User-Role", "ADMIN")
                        .with(signedAdmin())
                        .header("X-Group-Id", "")
                        .header("X-Is-Headman", "false")
                        .contentType(APPLICATION_JSON);
        mockMvc.perform(
                        groupUpdate.content(
                                "{\"name\":\"РМ-121\",\"active\":true,\"trainingDurationYears\":6}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(
                        groupUpdate.content(
                                "{\"name\":\"РМ-121\",\"active\":false,\"trainingDurationYears\":4}"))
                .andExpect(status().isBadRequest());
        assertThat(
                        jdbc.queryForObject(
                                "SELECT is_active FROM groups WHERE id = ?",
                                Boolean.class,
                                corrected))
                .isTrue();
        response(
                groupUpdate.content(
                        "{\"name\":\"РМ-121\",\"active\":true,\"trainingDurationYears\":4}"));
        JsonNode correctedPlan =
                response(previewRequest(adminId, "ADMIN", "{\"groupId\":" + corrected + "}"));
        response(
                executeRequest(
                        adminId,
                        cycleId,
                        correctedPlan.path("previewVersion").asText(),
                        corrected));
        assertThat(groupName(corrected)).isEqualTo("РМ-221");
        response(
                groupUpdate.content(
                        "{\"name\":\"РМ-221\",\"active\":true,\"trainingDurationYears\":3}"));
        assertThat(
                        jdbc.queryForObject(
                                "SELECT count(*) FROM student_group_history WHERE user_id = ? AND"
                                        + " group_id = ?",
                                Long.class,
                                studentId,
                                corrected))
                .isEqualTo(1L);
        assertThat(
                        jdbc.queryForObject(
                                "SELECT count(*) FROM group_promotion_cycle_record WHERE"
                                        + " cycle_semester_id = ? AND group_id = ?",
                                Long.class,
                                cycleId,
                                corrected))
                .isEqualTo(1L);

        jdbc.update("UPDATE semesters SET is_archived = true WHERE id = ?", cycleId);
        mockMvc.perform(
                        post("/academic/semesters/" + cycleId + "/restore")
                                .header("Idempotency-Key", java.util.UUID.randomUUID())
                                .header("X-Internal-Token", adminToken)
                                .header("X-User-Id", adminId)
                                .header("X-User-Role", "ADMIN")
                                .with(signedAdmin())
                                .header("X-Group-Id", "")
                                .header("X-Is-Headman", "false"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.field").value("dateTo"));
        assertThat(
                        jdbc.queryForObject(
                                "SELECT count(*) FROM semester_archive_operations WHERE semester_id"
                                        + " = ?",
                                Long.class,
                                cycleId))
                .isZero();
        JsonNode originalPlan = response(previewRequest(adminId, "ADMIN", "{}"));
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("name", "ignored display name");
        metadata.put("semesterType", "SPRING");
        metadata.put("academicYear", 2025);
        metadata.put("dateFrom", "2026-02-02");
        metadata.put("dateTo", "2027-12-31");
        var semesterUpdate =
                put("/academic/semesters/" + cycleId)
                        .header("X-Internal-Token", adminToken)
                        .header("X-User-Id", adminId)
                        .header("X-User-Role", "ADMIN")
                        .with(signedAdmin())
                        .header("X-Group-Id", "")
                        .header("X-Is-Headman", "false")
                        .contentType(APPLICATION_JSON);
        mockMvc.perform(semesterUpdate.content(objectMapper.writeValueAsString(metadata)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.field").value("dates"));
        metadata.put("dateFrom", "2026-02-01");
        metadata.put("semesterType", "AUTUMN");
        mockMvc.perform(semesterUpdate.content(objectMapper.writeValueAsString(metadata)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.field").value("semesterType"));
        // Legacy archive: explicitly classify and extend in one PUT; the old completed cutoff must
        // survive.
        jdbc.update(
                "UPDATE semesters SET semester_type = NULL, academic_year = NULL WHERE id = ?",
                cycleId);
        metadata.put("semesterType", "SPRING");
        response(semesterUpdate.content(objectMapper.writeValueAsString(metadata)));
        assertThat(
                        jdbc.queryForObject(
                                "SELECT semester_type FROM semesters WHERE id = ?",
                                String.class,
                                cycleId))
                .isEqualTo("SPRING");
        assertThat(
                        jdbc.queryForObject(
                                "SELECT academic_year FROM semesters WHERE id = ?",
                                Integer.class,
                                cycleId))
                .isEqualTo(2025);
        assertThat(
                        jdbc.queryForObject(
                                "SELECT is_archived AND NOT is_active AND archive_transition ="
                                    + " 'NONE' AND NOT archive_release_pending FROM semesters WHERE"
                                    + " id = ?",
                                Boolean.class,
                                cycleId))
                .isTrue();
        assertThat(
                        jdbc.queryForObject(
                                "SELECT promotion_cycle_date_to FROM semesters WHERE id = ?",
                                LocalDate.class,
                                cycleId))
                .isEqualTo(CYCLE_END);
        assertThat(
                        historicalMembership
                                .readRoster(corrected, CYCLE_END, cycleId)
                                .students()
                                .stream()
                                .map(ru.rutcampustrack.academic.entity.User::getId)
                                .toList())
                .isEqualTo(rosterBefore);
        assertThat(
                        jdbc.queryForObject(
                                "SELECT valid_from FROM assignments WHERE id = ?",
                                LocalDate.class,
                                assignmentId))
                .isEqualTo(CYCLE_END.minusDays(10));
        assertThat(
                        jdbc.queryForObject(
                                "SELECT valid_until_exclusive FROM assignments WHERE id = ?",
                                LocalDate.class,
                                assignmentId))
                .isEqualTo(CYCLE_END.plusDays(1));
        JsonNode extendedPlan = response(previewRequest(adminId, "ADMIN", "{}"));
        assertThat(extendedPlan.path("cycleSemesterId").asLong()).isEqualTo(cycleId);
        assertThat(extendedPlan.path("cycleDateTo").asText()).isEqualTo("2026-06-30");
        assertThat(extendedPlan.path("previewVersion").asText())
                .isEqualTo(originalPlan.path("previewVersion").asText());
        assertThat(extendedPlan.path("skipped").findValuesAsText("id"))
                .contains(Long.toString(corrected), Long.toString(late));
        groupPromotionJob.checkDueSpringCycle();
        assertThat(groupName(graduating)).isEqualTo("УЦ-511 (выпуск 2026)");
        assertThat(groupName(corrected)).isEqualTo("РМ-221");
        assertThat(groupName(late)).isEqualTo("НГ-111");
        metadata.put("dateTo", "2028-12-31");
        response(semesterUpdate.content(objectMapper.writeValueAsString(metadata)));
        groupPromotionJob.checkDueSpringCycle();
        assertThat(
                        jdbc.queryForObject(
                                "SELECT promotion_cycle_date_to FROM semesters WHERE id = ?",
                                LocalDate.class,
                                cycleId))
                .isEqualTo(CYCLE_END);
        assertThat(
                        jdbc.queryForObject(
                                "SELECT count(*) FROM group_promotion_cycle_record WHERE"
                                        + " cycle_semester_id = ? AND group_id = ?",
                                Long.class,
                                cycleId,
                                corrected))
                .isEqualTo(1L);
        assertThat(
                        jdbc.queryForObject(
                                "SELECT count(*) FROM group_promotion_cycle_record WHERE"
                                        + " cycle_semester_id = ? AND group_id = ?",
                                Long.class,
                                cycleId,
                                graduating))
                .isEqualTo(1L);
        var restoring = semesterService.beginRestoreTransition(cycleId);
        assertThat(restoring.isActive()).isFalse();
        assertThat(restoring.isArchived()).isTrue();
        assertThat(restoring.getArchiveTransition().name()).isEqualTo("RESTORING");
        metadata.put("dateTo", "2029-12-31");
        mockMvc.perform(semesterUpdate.content(objectMapper.writeValueAsString(metadata)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.field").value("status"));
        assertThat(
                        jdbc.queryForObject(
                                "SELECT date_to FROM semesters WHERE id = ?",
                                LocalDate.class,
                                cycleId))
                .isEqualTo(LocalDate.of(2028, 12, 31));
    }

    private Long prepareSpringFixture() {
        Long cycleId =
                jdbc.queryForObject(
                        "SELECT id FROM semesters WHERE name = 'Spring 2026' AND date_to = ? ORDER"
                                + " BY id LIMIT 1",
                        Long.class,
                        CYCLE_END);
        jdbc.update(
                "UPDATE semesters SET semester_type = 'SPRING', academic_year = 2025, "
                        + "is_active = true WHERE id = ?",
                cycleId);
        return cycleId;
    }

    private void completeSpring(long cycleId) {
        jdbc.update("UPDATE semesters SET is_active=false WHERE id=?", cycleId);
    }

    private Long addGroup(String name, OffsetDateTime createdAt) {
        Long group =
                jdbc.queryForObject(
                        "INSERT INTO groups (name, is_active, created_at, duration_status) "
                                + "VALUES (?, true, ?, 'LEGACY_UNKNOWN') RETURNING id",
                        Long.class,
                        name,
                        createdAt);
        jdbc.update(
                "INSERT INTO group_history_coverage(group_id,coverage_from,writer_version) "
                        + "SELECT ?,date_from,? FROM semesters WHERE id=?",
                group,
                ru.rutcampustrack.academic.history.HistoricalMembershipService.WRITER_VERSION,
                identityFixture.canonical().semesterId());
        return group;
    }

    private Long addStudent(Long groupId, String login) {
        return identityFixture
                .seedUser(
                        new CreateUserRequest(
                                "Тестов",
                                "Студент",
                                null,
                                UserRole.STUDENT,
                                groupId,
                                null,
                                TELEGRAM.incrementAndGet()),
                        administrator)
                .userId();
    }

    private void enrollmentAt(long user, long group, LocalDate joinedAt, String reason) {
        assertThat(
                        jdbc.update(
                                "UPDATE student_group_history SET joined_at=?,reason=? "
                                        + "WHERE user_id=? AND group_id=? AND left_at IS NULL",
                                joinedAt,
                                reason,
                                user,
                                group))
                .isEqualTo(1);
    }

    private Long userId(String login) {
        return switch (login) {
            case "admin" -> identityFixture.canonical().adminId();
            case "teacher" -> identityFixture.canonical().teacherId();
            default -> throw new IllegalArgumentException("Unknown canonical fixture actor");
        };
    }

    private String groupName(Long id) {
        return jdbc.queryForObject("SELECT name FROM groups WHERE id = ?", String.class, id);
    }

    private Long groupIdByName(String name) {
        return jdbc.queryForObject("SELECT id FROM groups WHERE name = ?", Long.class, name);
    }

    private OffsetDateTime beforeCycleEnd() {
        return CYCLE_END.minusDays(10).atTime(12, 0).atOffset(ZoneOffset.UTC);
    }

    private OffsetDateTime afterCycleEnd() {
        return CYCLE_END.plusDays(10).atTime(12, 0).atOffset(ZoneOffset.UTC);
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder
            previewRequest(Long userId, String role, String body) {
        var request =
                post("/academic/groups/promote/preview")
                        .header("X-User-Id", userId)
                        .header("X-User-Role", role)
                        .header("X-Group-Id", "")
                        .header("X-Is-Headman", "false")
                        .contentType(APPLICATION_JSON)
                        .content(body);
        return request.with(identityFixture.signed("ADMIN".equals(role) ? administrator : teacher));
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder
            executeRequest(Long userId, Long cycleId, String version, Long groupId)
                    throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("cycleSemesterId", cycleId);
        body.put("previewVersion", version);
        body.put("groupId", groupId);
        return post("/academic/groups/promote")
                .header("X-Internal-Token", adminToken)
                .header("X-User-Id", userId)
                .header("X-User-Role", "ADMIN")
                .with(signedAdmin())
                .header("X-Group-Id", "")
                .header("X-Is-Headman", "false")
                .contentType(APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body));
    }

    private JsonNode response(
            org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request)
            throws Exception {
        MvcResult result = mockMvc.perform(request).andExpect(status().isOk()).andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    @Test
    void unfinishedSpringRejectsSignedManualPreviewAndExecutionWithoutChangingDurableState()
            throws Exception {
        Long completedCycle = prepareSpringFixture();
        Long adminId = userId("admin");
        Long priorGroup = addGroup("НСР-111", beforeCycleEnd());
        Long studentId = addStudent(priorGroup, "before_spring_" + System.nanoTime());
        enrollmentAt(studentId, priorGroup, CYCLE_END.minusDays(20), "before-spring-completion-it");
        completeSpring(completedCycle);

        JsonNode completedPreview =
                response(previewRequest(adminId, "ADMIN", "{\"groupId\":" + priorGroup + "}"));
        response(
                executeRequest(
                        adminId,
                        completedCycle,
                        completedPreview.path("previewVersion").asText(),
                        priorGroup));
        // The real split operation committed its business event through BEFORE_COMMIT.
        // Retain past ledger/history/outbox facts while isolating the only current
        // SPRING candidate to one that has not ended on the existing September 1 clock.
        jdbc.update("UPDATE semesters SET semester_type = 'AUTUMN' WHERE semester_type = 'SPRING'");
        Long unfinishedCycle =
                jdbc.queryForObject(
                        "INSERT INTO semesters (name, date_from, date_to, is_active, semester_type,"
                                + " academic_year, promotion_cycle_date_to) VALUES (?, ?, ?, false,"
                                + " 'SPRING', 2026, ?) RETURNING id",
                        Long.class,
                        "unfinished-spring-" + System.nanoTime(),
                        LocalDate.of(2027, 2, 1),
                        CYCLE_END.plusYears(1),
                        CYCLE_END.plusYears(1));
        Long targetGroup = addGroup("РАН-111", beforeCycleEnd());
        var before = promotionDurableState();
        assertThat(before.values()).allSatisfy(rows -> assertThat(rows).isNotEmpty());
        long groupEventsBefore =
                applicationEvents.stream(ru.rutcampustrack.academic.event.GroupRenamedEvent.class)
                        .count();

        MvcResult unfinishedPreview =
                mockMvc.perform(
                                previewRequest(
                                        adminId, "ADMIN", "{\"groupId\":" + targetGroup + "}"))
                        .andReturn();
        assertThat(promotionDurableState().equals(before))
                .as("unfinished spring preview must preserve durable promotion state")
                .isTrue();
        // Both responses must reject the unfinished cycle. If a faulty preview issues
        // a version, exercise that real version so freshness cannot hide lost completion
        // validation in execution; otherwise retain the real, well-formed older token.
        String attemptedVersion =
                unfinishedPreview.getResponse().getStatus() == 200
                        ? objectMapper
                                .readTree(unfinishedPreview.getResponse().getContentAsString())
                                .path("previewVersion")
                                .asText()
                        : completedPreview.path("previewVersion").asText();
        mockMvc.perform(executeRequest(adminId, unfinishedCycle, attemptedVersion, targetGroup))
                .andExpect(status().isConflict());
        assertThat(unfinishedPreview.getResponse().getStatus()).isEqualTo(409);
        assertThat(promotionDurableState().equals(before))
                .as("unfinished spring execution must preserve durable promotion state")
                .isTrue();
        assertThat(
                        applicationEvents.stream(
                                        ru.rutcampustrack.academic.event.GroupRenamedEvent.class)
                                .count())
                .isEqualTo(groupEventsBefore);
        assertThat(
                        applicationEvents.stream(
                                        ru.rutcampustrack.academic.event.GroupArchivedEvent.class)
                                .count())
                .isZero();
    }

    private Map<String, List<Map<String, Object>>> promotionDurableState() {
        Map<String, List<Map<String, Object>>> state = new LinkedHashMap<>();
        for (String table :
                List.of(
                        "groups",
                        "group_promotion_cycle_record",
                        "student_group_history",
                        "academic_outbox")) {
            state.put(
                    table,
                    jdbc.queryForList(
                            "SELECT to_jsonb(t)::text AS state FROM " + table + " t ORDER BY id"));
        }
        return state;
    }
}
