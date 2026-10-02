package ru.rutcampustrack.academic.homework;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import ru.rutcampustrack.academic.contract.dto.homework.*;
import ru.rutcampustrack.academic.contract.enums.*;
import ru.rutcampustrack.academic.entity.Homework;
import ru.rutcampustrack.academic.event.HomeworkBindingArchivedEventConsumer;
import ru.rutcampustrack.academic.exception.AccessDeniedException;
import ru.rutcampustrack.academic.exception.ConflictException;
import ru.rutcampustrack.academic.exception.ScheduleServiceUnavailableException;
import ru.rutcampustrack.academic.grpc.ScheduleGrpcClient;
import ru.rutcampustrack.academic.integration.InternalJwtTestConfig;
import ru.rutcampustrack.academic.repository.HomeworkRepository;
import ru.rutcampustrack.academic.security.RequestContext;
import ru.rutcampustrack.academic.semester.SemesterArchiveCommandTransaction;
import ru.rutcampustrack.schedule.grpc.*;

import java.time.LocalDate;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** One owned PostgreSQL boundary: committed edit/history/outbox and durable RPC recovery. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Import({InternalJwtTestConfig.class, HomeworkEditLifecycleIT.ClockConfiguration.class})
@Testcontainers
class HomeworkEditLifecycleIT {
    private static final AtomicInteger FIXTURE_YEAR = new AtomicInteger(2200);
    @TestConfiguration static class ClockConfiguration {
        @Bean @Primary MutableClock homeworkBoundaryClock() { return new MutableClock(); }
    }
    static class MutableClock extends Clock {
        private volatile Instant current = Instant.now();
        void set(Instant instant) { current = instant; }
        @Override public ZoneId getZone() { return ZoneId.of("Europe/Moscow"); }
        @Override public Clock withZone(ZoneId zone) { return Clock.fixed(current, zone); }
        @Override public Instant instant() { return current; }
    }
    @Container static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16")
            .withDatabaseName("homework_lifecycle").withUsername("rct_user").withPassword("rct_dev_pass");
    @DynamicPropertySource static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.autoconfigure.exclude", () -> "org.springframework.boot.autoconfigure.amqp.RabbitAutoConfiguration,"
                + "org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration,"
                + "org.springframework.boot.autoconfigure.data.redis.RedisRepositoriesAutoConfiguration");
        registry.add("spring.task.scheduling.enabled", () -> "false");
    }
    @MockitoBean RabbitTemplate rabbit;
    @MockitoBean ScheduleGrpcClient schedule;
    @MockitoBean RequestContext context;
    @MockitoBean HomeworkEditRecoveryJob recoveryJob;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager transactions;
    @Autowired HomeworkRepository repository;
    @Autowired HomeworkService service;
    @Autowired HomeworkEditPersistence persistence;
    @Autowired HomeworkEditCoordinator coordinator;
    @Autowired HomeworkBindingArchivedEventConsumer archiveConsumer;
    @Autowired SemesterArchiveCommandTransaction archiveCommands;
    @Autowired HomeworkAssembler assembler;
    @Autowired jakarta.validation.Validator validator;
    @Autowired MutableClock boundaryClock;
    long actor, publisher, group, subject, semester, binding;
    LocalDate day;
    UUID createKey;
    byte[] createHash = new byte[32];

    @BeforeEach void fixture() {
        boundaryClock.set(LocalDate.of(FIXTURE_YEAR.incrementAndGet(),5,10).atTime(12,0)
                .atZone(ZoneId.of("Europe/Moscow")).toInstant());
        day = LocalDate.now(boundaryClock).plusDays(3);
        actor = jdbc.queryForObject("SELECT id FROM users WHERE login = 'student'", Long.class);
        publisher = jdbc.queryForObject("SELECT id FROM users WHERE login = 'admin'", Long.class);
        group = jdbc.queryForObject("SELECT group_id FROM user_role_grants WHERE user_id = ? AND role = 'student'", Long.class, actor);
        jdbc.update("UPDATE user_role_grants SET status = 'active' WHERE user_id = ? AND role = 'headman'", actor);
        subject = new TransactionTemplate(transactions).execute(status -> {
            Long id = jdbc.queryForObject("INSERT INTO subjects(name,type,group_id) VALUES(?, 'lecture', ?) RETURNING id",
                    Long.class, "hw-edit-" + UUID.randomUUID(), group);
            jdbc.update("INSERT INTO subject_lesson_types(subject_id,lesson_type) VALUES(?,'lecture')", id);
            return id;
        });
        semester = jdbc.queryForObject("INSERT INTO semesters(name,date_from,date_to,is_active,created_at) VALUES(?,?,?,false,NOW()) RETURNING id",
                Long.class, "hw-edit-" + UUID.randomUUID(), day.minusDays(10), day.plusDays(30));
        binding = ThreadLocalRandom.current().nextLong(1_000_000_000L, 9_000_000_000L);
        createKey = UUID.randomUUID();
        when(context.getUserId()).thenReturn(actor);
        when(context.getGroupId()).thenReturn(group);
        when(context.getRole()).thenReturn(UserRole.STUDENT);
    }

    @AfterEach void restoreSharedAuthorityAndClock() {
        if (actor > 0) jdbc.update("UPDATE user_role_grants SET status = 'active' WHERE user_id = ? AND role = 'headman'", actor);
        boundaryClock.set(Instant.now());
    }

    @Test void nonAuthorEditsKeepCompletionReplayHistoryAndDurableUpdatedOutbox() {
        Homework homework = linkedHomework();
        completion(homework);
        var first = edit("B", 1);
        Homework accepted = service.updateHomework(homework.getId(), first);
        assertThat(accepted.getRevision()).isEqualTo(2);
        service.updateHomework(homework.getId(), edit("C", 2));
        Homework replay = service.updateHomework(homework.getId(), first);
        assertThat(replay.getTitle()).isEqualTo("B");
        assertThat(replay.getRevision()).isEqualTo(2);
        assertThat(repository.findById(homework.getId()).orElseThrow().getTitle()).isEqualTo("C");
        service.updateHomework(homework.getId(), edit("C", 3)); // distinct-key no-op
        service.updateHomework(homework.getId(), edit("A", 3)); // legitimate A → B → C → A
        assertThat(service.isCompleted(homework.getId())).isTrue();
        assertThat(repository.findById(homework.getId()).orElseThrow().getPublishedBy()).isEqualTo(publisher);
        var history = service.history(homework.getId(), PageRequest.of(0, 10));
        assertThat(history.getContent()).extracting(HomeworkHistoryResponse::revision).containsExactly(2L, 3L, 4L);
        assertThat(history.getContent()).allMatch(change -> change.actorId() == actor);
        assertThat(history.getContent().getFirst().before().title()).isEqualTo("A");
        assertThat(history.getContent().getFirst().after().title()).isEqualTo("B");
        assertThat(updatedOutbox(homework)).isEqualTo(3);
        Map<String,Object> event = jdbc.queryForMap("""
                SELECT payload ->> 'event_type' AS type, payload #>> '{payload,title}' AS title,
                       payload #>> '{payload,binding_mode}' AS mode, payload #>> '{payload,lesson_number}' AS number,
                       payload #>> '{payload,subject_id}' AS subject
                  FROM academic_outbox WHERE event_type = 'homework.updated'
                   AND payload #>> '{payload,homework_id}' = ? ORDER BY id LIMIT 1
                """, homework.getId().toString());
        assertThat(event).containsEntry("type", "homework.updated").containsEntry("title", "B")
                .containsEntry("mode", "LESSON").containsEntry("number", "1").containsEntry("subject", Long.toString(subject));
        assertThatThrownBy(() -> service.updateHomework(homework.getId(), edit("stale", 1))).isInstanceOf(ConflictException.class);
        assertThatThrownBy(() -> service.updateHomework(homework.getId(), new UpdateHomeworkRequest("bad", null, null, first.requestKey(), 1L)))
                .isInstanceOf(ConflictException.class);
        when(context.getGroupId()).thenReturn(group + 10000);
        assertThatThrownBy(() -> service.updateHomework(homework.getId(), edit("foreign", 4))).isInstanceOf(AccessDeniedException.class);
        when(context.getGroupId()).thenReturn(group);
        jdbc.update("UPDATE user_role_grants SET status = 'suspended' WHERE user_id = ? AND role = 'headman'", actor);
        assertThatThrownBy(() -> service.updateHomework(homework.getId(), first)).isInstanceOf(AccessDeniedException.class);
        assertThat(updatedOutbox(homework)).isEqualTo(3);
        archiveConsumer.onEvent(terminalEvent(homework, 701L, 701L));
        assertThat(service.history(homework.getId(), PageRequest.of(0,10)).getTotalElements()).isEqualTo(3);
        when(context.getRole()).thenReturn(UserRole.TEACHER);
        assertThatThrownBy(() -> service.history(homework.getId(), PageRequest.of(0,10))).isInstanceOf(AccessDeniedException.class);
    }

    @Test void legacyAcceptedReplayFreezesBeforeEditAndEffectiveDateCutoffKeepsHistoryReadable() {
        Long id = jdbc.queryForObject("""
                INSERT INTO homeworks(group_id,subject_id,semester_id,title,published_by,lesson_date,lesson_number,
                    binding_id,actor_id,request_key,payload_hash,publication_state,created_at,updated_at)
                VALUES(?,?,?,'legacy',?,?,1,?,?,?,?,'ACTIVE',NOW(),NOW()) RETURNING id
                """, Long.class, group, subject, semester, actor, day, binding, actor, createKey, createHash);
        service.updateHomework(id, edit("new", 1));
        assertThat(jdbc.queryForObject("SELECT create_intent_kind FROM homeworks WHERE id = ?", String.class, id)).isEqualTo("LEGACY_ACCEPTED_REPLAY");
        Homework replay = service.createHomework(new CreateHomeworkRequest("legacy", null, null, subject, group, semester, day, 1, createKey));
        assertThat(replay.getId()).isEqualTo(id);
        assertThat(replay.getTitle()).isEqualTo("new");

        LocalDate yesterday = LocalDate.now(boundaryClock).minusDays(1);
        UUID movedKey = UUID.randomUUID(), expiredKey = UUID.randomUUID();
        Homework moved = new TransactionTemplate(transactions).execute(status -> {
            Homework homework = new Homework(group, subject, semester, "moved", null, null, actor,
                    yesterday, 1, binding + 3, actor, movedKey, createHash);
            homework.activatePublication();
            homework.applyEdit(new HomeworkSnapshot("current", null, null, HomeworkBindingMode.LESSON, day, 1), java.time.OffsetDateTime.now());
            return repository.saveAndFlush(homework);
        });
        var movedOriginal = new CreateHomeworkRequest("moved", null, null, subject, group, semester, yesterday, 1, movedKey);
        assertThat(validator.validate(movedOriginal)).isEmpty();
        Homework movedReplay = service.createHomework(movedOriginal);
        assertThat(movedReplay.getId()).isEqualTo(moved.getId());
        assertThat(movedReplay.getLessonDate()).isEqualTo(day);
        Homework expiredCurrent = new TransactionTemplate(transactions).execute(status -> {
            Homework homework = new Homework(group, subject, semester, "future-original", null, null, actor,
                    day, null, binding + 4, actor, expiredKey, createHash, HomeworkBindingMode.DATE);
            homework.activatePublication();
            homework.applyEdit(new HomeworkSnapshot("past-current", null, null, HomeworkBindingMode.DATE, yesterday, null), java.time.OffsetDateTime.now());
            return repository.saveAndFlush(homework);
        });
        long countBefore = jdbc.queryForObject("SELECT count(*) FROM homeworks WHERE semester_id = ?", Long.class, semester);
        assertThatThrownBy(() -> service.createHomework(new CreateHomeworkRequest("future-original", null, null, subject, group,
                semester, day, null, expiredKey, HomeworkBindingMode.DATE))).isInstanceOf(ConflictException.class);
        assertThatThrownBy(() -> service.createHomework(new CreateHomeworkRequest("new-past", null, null, subject, group,
                semester, yesterday, 1, UUID.randomUUID()))).isInstanceOf(ru.rutcampustrack.academic.exception.BadRequestException.class);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM homeworks WHERE semester_id = ?", Long.class, semester)).isEqualTo(countBefore);
        assertThat(repository.findById(expiredCurrent.getId()).orElseThrow().getTitle()).isEqualTo("past-current");

        LocalDate cutoffDay = LocalDate.of(2026,10,2);
        Homework cutoff = new Homework(group, subject, semester, "date", null, null, publisher, cutoffDay, null,
                binding + 1, publisher, UUID.randomUUID(), createHash, HomeworkBindingMode.DATE);
        cutoff.activatePublication();
        assertThat(new HomeworkLifecycle(Clock.fixed(Instant.parse("2026-10-02T20:59:59Z"), ZoneId.of("UTC"))).archived(cutoff)).isFalse();
        assertThat(new HomeworkLifecycle(Clock.fixed(Instant.parse("2026-10-02T21:00:00Z"), ZoneId.of("UTC"))).archived(cutoff)).isTrue();
        Homework expired = new TransactionTemplate(transactions).execute(status -> {
            Homework homework = new Homework(group, subject, semester, "expired", null, null, publisher,
                    LocalDate.now(boundaryClock).minusDays(1), null, binding + 2, publisher, UUID.randomUUID(), createHash, HomeworkBindingMode.DATE);
            homework.activatePublication(); return repository.saveAndFlush(homework);
        });
        completion(expired);
        assertThat(assembler.toModel(service.getHomework(expired.getId()), true).getContent().isArchived()).isTrue();
        assertThatThrownBy(() -> service.unmarkComplete(expired.getId())).isInstanceOf(ConflictException.class);
        assertThat(service.isCompleted(expired.getId())).isTrue();
        assertThat(service.history(expired.getId(), PageRequest.of(0,10)).getContent()).isEmpty();
    }

    @Test void dateCreateNeedsNoLessonAndOriginalCreateReplaySurvivesContentEdit() {
        var request = new CreateHomeworkRequest("day", "details", null, subject, group, semester, day, null,
                UUID.randomUUID(), HomeworkBindingMode.DATE);
        when(schedule.reserveDateHomeworkBinding(eq(group), eq(subject), eq(semester), eq(day), eq(request.requestKey()), any()))
                .thenReturn(dateBinding(null, 1));
        when(schedule.confirmHomeworkBinding(eq(binding), anyLong(), eq(request.requestKey())))
                .thenAnswer(call -> dateBinding(call.getArgument(1), 2));
        Homework created = service.createHomework(request);
        completion(created);
        assertThat(created.getBindingMode()).isEqualTo(HomeworkBindingMode.DATE);
        assertThat(created.getLessonNumber()).isNull();
        service.updateHomework(created.getId(), edit("changed", 1));
        Homework replay = service.createHomework(request);
        assertThat(replay.getId()).isEqualTo(created.getId());
        assertThat(replay.getTitle()).isEqualTo("changed");
        assertThat(service.isCompleted(created.getId())).isTrue();
        verify(schedule, never()).resolveLesson(anyLong(), any(), anyInt());
        verify(schedule, never()).reserveHomeworkBinding(anyLong(), any(), anyLong(), any());
    }

    @Test void acceptedPlacementDrainsThroughArchivePrepareAfterRevocationAndSupersedesOldTransfer() {
        Homework homework = linkedHomework();
        completion(homework);
        oldTransferMarker(homework);
        HomeworkEditOperation operation = uncertainDateEdit(homework);
        var archive = archiveCommands.startOrReplay(semester, publisher, UUID.randomUUID(), SemesterArchiveAction.ARCHIVE);
        jdbc.update("UPDATE user_role_grants SET status = 'suspended' WHERE user_id = ? AND role = 'headman'", actor);
        when(schedule.continueHomeworkEdit(operation.identity())).thenReturn(Optional.of(accepted(operation)));
        when(schedule.acknowledgeHomeworkEdit(operation.identity())).thenReturn(accepted(operation).toBuilder().setState("ACKNOWLEDGED").build());
        coordinator.recover(operation);
        coordinator.recover(operation); // recovery race/exact retry
        Homework current = repository.findById(homework.getId()).orElseThrow();
        assertThat(current.getBindingMode()).isEqualTo(HomeworkBindingMode.DATE);
        assertThat(current.getLessonNumber()).isNull();
        assertThat(current.getRevision()).isEqualTo(2);
        assertThat(service.isCompleted(homework.getId())).isTrue();
        assertThat(service.history(homework.getId(), PageRequest.of(0,10)).getTotalElements()).isEqualTo(1);
        assertThat(updatedOutbox(homework)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT superseded_by_edit_operation_id FROM homework_binding_transfer_markers WHERE binding_id = ?", UUID.class, binding))
                .isEqualTo(operation.operationId());
        assertThat(persistence.find(homework.getId(), actor, operation.requestKey()).state()).isEqualTo("ACKNOWLEDGED");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM homework_edit_archive_admissions WHERE archive_operation_id = ?", Long.class, archive.getOperationId()))
                .isEqualTo(1);
    }

    @Test void dateTerminalEventWinsBeforeAcademicFinalizationWithoutSuccessfulEditOrCompletionLoss() {
        Homework homework = linkedHomework();
        completion(homework);
        HomeworkEditOperation operation = uncertainDateEdit(homework);
        archiveConsumer.onEvent(terminalEvent(homework, null, null));
        when(schedule.continueHomeworkEdit(operation.identity())).thenReturn(Optional.of(accepted(operation)));
        when(schedule.acknowledgeHomeworkEdit(operation.identity())).thenReturn(accepted(operation).toBuilder().setState("ACKNOWLEDGED").build());
        coordinator.recover(operation);
        Homework current = repository.findById(homework.getId()).orElseThrow();
        assertThat(current.getPublicationState()).isEqualTo(HomeworkPublicationState.ARCHIVED);
        assertThat(current.getTitle()).isEqualTo("A");
        assertThat(current.getRevision()).isEqualTo(1);
        assertThat(service.isCompleted(homework.getId())).isTrue();
        assertThat(service.history(homework.getId(), PageRequest.of(0,10)).getTotalElements()).isZero();
        assertThat(updatedOutbox(homework)).isZero();
        assertThat(persistence.find(homework.getId(), actor, operation.requestKey()).outcome()).isEqualTo("TERMINAL_ABORTED");
        assertThatThrownBy(() -> service.updateHomework(homework.getId(), edit("late", 1))).isInstanceOf(ConflictException.class);
    }

    @Test void missingMoveRequiresDurableAbortTombstoneBeforePreparedCommandIsCancelled() {
        Homework homework = linkedHomework();
        completion(homework);
        HomeworkEditOperation operation = uncertainDateEdit(homework);
        when(schedule.continueHomeworkEdit(operation.identity())).thenReturn(Optional.empty());
        when(schedule.abortUnacceptedHomeworkEdit(operation.identity())).thenReturn(HomeworkEditReceipt.newBuilder()
                .setIdentity(operation.identity()).setState("NOT_ACCEPTED").build());
        coordinator.recover(operation);
        verify(schedule).abortUnacceptedHomeworkEdit(operation.identity());
        verify(schedule, never()).acknowledgeHomeworkEdit(any());
        assertThat(persistence.find(homework.getId(), actor, operation.requestKey()).state()).isEqualTo("CANCELLED");
        assertThat(repository.findById(homework.getId()).orElseThrow().getTitle()).isEqualTo("A");
        assertThat(service.isCompleted(homework.getId())).isTrue();
        assertThat(updatedOutbox(homework)).isZero();
    }

    private Map<String,Object> terminalEvent(Homework homework, Long occurrence, Long lesson) {
        Map<String,Object> payload = new HashMap<>();
        payload.put("binding_id", binding); payload.put("actor_id", publisher); payload.put("request_key", createKey.toString());
        payload.put("occurrence_id", occurrence); payload.put("lesson_id", lesson); payload.put("homework_id", homework.getId());
        payload.put("binding_revision", 9L); payload.put("semester_id", semester);
        return Map.of("event_id", UUID.randomUUID().toString(), "event_type", "homework.binding.archived",
                "source", "schedule-service", "event_version", 1L, "payload", payload);
    }

    private Homework linkedHomework() {
        return new TransactionTemplate(transactions).execute(status -> {
            Homework homework = new Homework(group, subject, semester, "A", null, null, publisher, day, 1,
                    binding, publisher, createKey, createHash);
            homework.activatePublication();
            return repository.saveAndFlush(homework);
        });
    }
    private void completion(Homework homework) {
        jdbc.update("INSERT INTO homework_completions(homework_id,student_id,completed_at) VALUES(?,?,NOW())", homework.getId(), actor);
    }
    private UpdateHomeworkRequest edit(String title, long revision) { return new UpdateHomeworkRequest(title, null, null, UUID.randomUUID(), revision); }
    private long updatedOutbox(Homework homework) {
        return jdbc.queryForObject("SELECT count(*) FROM academic_outbox WHERE event_type = 'homework.updated' AND payload #>> '{payload,homework_id}' = ?",
                Long.class, homework.getId().toString());
    }
    private HomeworkBindingResponse dateBinding(Long homework, long revision) {
        var response = HomeworkBindingResponse.newBuilder().setBindingId(binding).setGroupId(group).setSubjectId(subject)
                .setSemesterId(semester).setDate(day.toString()).setBindingMode("DATE").setRevision(revision)
                .setState(homework == null ? HomeworkBindingState.HOMEWORK_BINDING_STATE_PENDING : HomeworkBindingState.HOMEWORK_BINDING_STATE_ACTIVE);
        if (homework != null) response.setHomeworkId(homework);
        return response.build();
    }
    private HomeworkEditOperation uncertainDateEdit(Homework homework) {
        var command = new UpdateHomeworkRequest("accepted-date", null, null, HomeworkBindingMode.DATE, day, null, UUID.randomUUID(), 1L);
        when(schedule.getHomeworkBinding(binding)).thenReturn(HomeworkBindingResponse.newBuilder().setBindingId(binding)
                .setHomeworkId(homework.getId()).setGroupId(group).setSubjectId(subject).setSemesterId(semester)
                .setDate(day.toString()).setLessonNumber(1).setBindingMode("LESSON").setRevision(7)
                .setOccurrenceId(701).setState(HomeworkBindingState.HOMEWORK_BINDING_STATE_ACTIVE).build());
        when(schedule.moveHomeworkBinding(any())).thenThrow(new ScheduleServiceUnavailableException("simulated lost RPC response"));
        assertThatThrownBy(() -> service.updateHomework(homework.getId(), command)).isInstanceOf(ScheduleServiceUnavailableException.class);
        return persistence.find(homework.getId(), actor, command.requestKey());
    }
    private HomeworkEditReceipt accepted(HomeworkEditOperation operation) {
        return HomeworkEditReceipt.newBuilder().setIdentity(operation.identity()).setState("APPLIED_AWAITING_ACK")
                .setAcceptedBinding(dateBinding(operation.homeworkId(), 8)).build();
    }
    private void oldTransferMarker(Homework homework) {
        jdbc.update("""
                INSERT INTO homework_binding_transfer_markers(binding_id,actor_id,request_key,binding_payload_hash,homework_id,
                    operation_id,operation_hash,occurrence_id,group_id,subject_id,semester_id,source_lesson_id,target_lesson_id,
                    source_date,source_lesson_number,target_date,target_lesson_number,state,source_event_id,batch_index)
                VALUES(?,?,?,?,?,?,?,701,?,?,?,700,701,?,1,?,1,'APPLIED',?,0)
                """, binding, publisher, createKey, createHash, homework.getId(), UUID.randomUUID(), createHash,
                group, subject, semester, day.minusDays(1), day, UUID.randomUUID());
    }
}
