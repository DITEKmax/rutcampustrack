package ru.rutcampustrack.academic.homework;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
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
import ru.rutcampustrack.academic.exception.HomeworkPublicationPendingException;
import ru.rutcampustrack.academic.exception.ScheduleServiceUnavailableException;
import ru.rutcampustrack.academic.grpc.ScheduleGrpcClient;
import ru.rutcampustrack.academic.integration.InternalJwtTestConfig;
import ru.rutcampustrack.academic.repository.HomeworkRepository;
import ru.rutcampustrack.academic.security.RequestContext;
import ru.rutcampustrack.academic.semester.SemesterArchiveCommandTransaction;
import ru.rutcampustrack.schedule.grpc.*;
import ru.rutcampustrack.shared.outbox.OutboxStorage;

/** One owned PostgreSQL boundary: committed edit/history/outbox and durable RPC recovery. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Import({InternalJwtTestConfig.class, HomeworkEditLifecycleIT.ClockConfiguration.class})
@Testcontainers
class HomeworkEditLifecycleIT {
    private static final AtomicInteger FIXTURE_YEAR = new AtomicInteger(2200);

    @TestConfiguration
    static class ClockConfiguration {
        @Bean
        @Primary
        MutableClock homeworkBoundaryClock() {
            return new MutableClock();
        }
    }

    static class MutableClock extends Clock {
        private volatile Instant current = Instant.now();

        void set(Instant instant) {
            current = instant;
        }

        @Override
        public ZoneId getZone() {
            return ZoneId.of("Europe/Moscow");
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return Clock.fixed(current, zone);
        }

        @Override
        public Instant instant() {
            return current;
        }
    }

    @Container
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16")
                    .withDatabaseName("homework_lifecycle")
                    .withUsername("rct_user")
                    .withPassword("rct_dev_pass");

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add(
                "spring.autoconfigure.exclude",
                () ->
                        "org.springframework.boot.autoconfigure.amqp.RabbitAutoConfiguration,"
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
    @Autowired HomeworkPublicationPersistence publications;
    @Autowired ObjectMapper mapper;

    @MockitoSpyBean(name = "outboxStorage")
    OutboxStorage actionOutbox;

    long actor, publisher, group, subject, semester, binding;
    Long ownedAssistantId;
    LocalDate day;
    UUID createKey;
    byte[] createHash = new byte[32];

    @BeforeEach
    void fixture() {
        boundaryClock.set(
                LocalDate.of(FIXTURE_YEAR.incrementAndGet(), 5, 10)
                        .atTime(12, 0)
                        .atZone(ZoneId.of("Europe/Moscow"))
                        .toInstant());
        day = LocalDate.now(boundaryClock).plusDays(3);
        actor = jdbc.queryForObject("SELECT id FROM users WHERE login = 'student'", Long.class);
        publisher = jdbc.queryForObject("SELECT id FROM users WHERE login = 'admin'", Long.class);
        group =
                jdbc.queryForObject(
                        "SELECT group_id FROM user_role_grants WHERE user_id = ? AND role = 'student'",
                        Long.class,
                        actor);
        jdbc.update(
                "UPDATE user_role_grants SET status = 'active' WHERE user_id = ? AND role = 'headman'",
                actor);
        subject =
                new TransactionTemplate(transactions)
                        .execute(
                                status -> {
                                    Long id =
                                            jdbc.queryForObject(
                                                    "INSERT INTO subjects(name,type,group_id) VALUES(?, 'lecture', ?) RETURNING id",
                                                    Long.class,
                                                    "hw-edit-" + UUID.randomUUID(),
                                                    group);
                                    jdbc.update(
                                            "INSERT INTO subject_lesson_types(subject_id,lesson_type) VALUES(?,'lecture')",
                                            id);
                                    return id;
                                });
        semester =
                jdbc.queryForObject(
                        "INSERT INTO semesters(name,date_from,date_to,is_active,created_at) VALUES(?,?,?,false,NOW()) RETURNING id",
                        Long.class,
                        "hw-edit-" + UUID.randomUUID(),
                        day.minusDays(10),
                        day.plusDays(30));
        binding = ThreadLocalRandom.current().nextLong(1_000_000_000L, 9_000_000_000L);
        createKey = UUID.randomUUID();
        when(context.getUserId()).thenReturn(actor);
        when(context.getGroupId()).thenReturn(group);
        when(context.getRole()).thenReturn(UserRole.STUDENT);
    }

    @AfterEach
    void restoreSharedAuthorityAndClock() {
        if (ownedAssistantId != null)
            jdbc.update(
                    "UPDATE headman_assistants SET is_active=false WHERE id=?", ownedAssistantId);
        if (actor > 0)
            jdbc.update(
                    "UPDATE user_role_grants SET status = 'active' WHERE user_id = ? AND role = 'headman'",
                    actor);
        boundaryClock.set(Instant.now());
    }

    @Test
    void nonAuthorEditsKeepCompletionReplayHistoryAndDurableUpdatedOutbox() {
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
        assertThat(repository.findById(homework.getId()).orElseThrow().getPublishedBy())
                .isEqualTo(publisher);
        var history = service.history(homework.getId(), PageRequest.of(0, 10));
        assertThat(history.getContent())
                .extracting(HomeworkHistoryResponse::revision)
                .containsExactly(2L, 3L, 4L);
        assertThat(history.getContent()).allMatch(change -> change.actorId() == actor);
        String authorName =
                jdbc.queryForObject(
                        "SELECT last_name, first_name, middle_name FROM users WHERE id = ?",
                        (rs, index) -> {
                            var user = new ru.rutcampustrack.academic.entity.User();
                            user.setLastName(rs.getString(1));
                            user.setFirstName(rs.getString(2));
                            user.setMiddleName(rs.getString(3));
                            return user.getDisplayName();
                        },
                        actor);
        assertThat(history.getContent())
                .extracting(HomeworkHistoryResponse::actorDisplayName)
                .containsOnly(authorName);
        assertThat(history.getContent().getFirst().before().title()).isEqualTo("A");
        assertThat(history.getContent().getFirst().after().title()).isEqualTo("B");
        assertThat(updatedOutbox(homework)).isEqualTo(3);
        Map<String, Object> event =
                jdbc.queryForMap(
                        """
                SELECT payload ->> 'event_type' AS type, payload #>> '{payload,title}' AS title,
                       payload #>> '{payload,binding_mode}' AS mode, payload #>> '{payload,lesson_number}' AS number,
                       payload #>> '{payload,subject_id}' AS subject
                  FROM academic_outbox WHERE event_type = 'homework.updated'
                   AND payload #>> '{payload,homework_id}' = ? ORDER BY id LIMIT 1
                """,
                        homework.getId().toString());
        assertThat(event)
                .containsEntry("type", "homework.updated")
                .containsEntry("title", "B")
                .containsEntry("mode", "LESSON")
                .containsEntry("number", "1")
                .containsEntry("subject", Long.toString(subject));
        assertThatThrownBy(() -> service.updateHomework(homework.getId(), edit("stale", 1)))
                .isInstanceOf(ConflictException.class);
        assertThatThrownBy(
                        () ->
                                service.updateHomework(
                                        homework.getId(),
                                        new UpdateHomeworkRequest(
                                                "bad", null, null, first.requestKey(), 1L)))
                .isInstanceOf(ConflictException.class);
        when(context.getGroupId()).thenReturn(group + 10000);
        assertThatThrownBy(() -> service.updateHomework(homework.getId(), edit("foreign", 4)))
                .isInstanceOf(AccessDeniedException.class);
        when(context.getGroupId()).thenReturn(group);
        jdbc.update(
                "UPDATE user_role_grants SET status = 'suspended' WHERE user_id = ? AND role = 'headman'",
                actor);
        assertThatThrownBy(() -> service.updateHomework(homework.getId(), first))
                .isInstanceOf(AccessDeniedException.class);
        assertThat(updatedOutbox(homework)).isEqualTo(3);
        archiveConsumer.onEvent(terminalEvent(homework, 701L, 701L));
        assertThat(service.history(homework.getId(), PageRequest.of(0, 10)).getTotalElements())
                .isEqualTo(3);
        when(context.getRole()).thenReturn(UserRole.TEACHER);
        assertThatThrownBy(() -> service.history(homework.getId(), PageRequest.of(0, 10)))
                .isInstanceOf(AccessDeniedException.class);
        when(context.getRole()).thenReturn(UserRole.STUDENT);
        // Historical actor IDs have no users FK; an absent author must not remove the history row.
        jdbc.update(
                """
                INSERT INTO homework_edit_history
                    (homework_id, semester_id, revision, actor_id, occurred_at, action, before_snapshot, after_snapshot)
                SELECT homework_id, semester_id, 5, ?, occurred_at, action, before_snapshot, after_snapshot
                  FROM homework_edit_history WHERE homework_id = ? AND revision = 4
                """,
                Long.MAX_VALUE,
                homework.getId());
        var lastPage = service.history(homework.getId(), PageRequest.of(1, 2));
        assertThat(lastPage.getTotalElements()).isEqualTo(4);
        assertThat(lastPage.getContent())
                .extracting(HomeworkHistoryResponse::revision)
                .containsExactly(4L, 5L);
        assertThat(lastPage.getContent().getFirst().actorDisplayName()).isEqualTo(authorName);
        assertThat(lastPage.getContent().getLast().actorId()).isEqualTo(Long.MAX_VALUE);
        assertThat(lastPage.getContent().getLast().actorDisplayName()).isNull();
    }

    @Test
    void legacyAcceptedReplayFreezesBeforeEditAndEffectiveDateCutoffKeepsHistoryReadable() {
        var legacyIntent =
                new HomeworkCreateIntent(
                        group,
                        subject,
                        semester,
                        "legacy",
                        null,
                        null,
                        HomeworkBindingMode.LESSON,
                        day,
                        1);
        assertThat(
                        new org.hibernate.type.format.jackson.JacksonJsonFormatMapper()
                                .toString(legacyIntent, HomeworkCreateIntent.class))
                .contains("\"lessonDate\":\"" + day + "\"");
        Long id =
                jdbc.queryForObject(
                        """
                INSERT INTO homeworks(group_id,subject_id,semester_id,title,published_by,lesson_date,lesson_number,
                    binding_id,actor_id,request_key,payload_hash,publication_state,created_at,updated_at)
                VALUES(?,?,?,'legacy',?,?,1,?,?,?,?,'ACTIVE',NOW(),NOW()) RETURNING id
                """,
                        Long.class,
                        group,
                        subject,
                        semester,
                        actor,
                        day,
                        binding,
                        actor,
                        createKey,
                        createHash);
        service.updateHomework(id, edit("new", 1));
        assertThat(
                        jdbc.queryForObject(
                                "SELECT create_intent_kind FROM homeworks WHERE id = ?",
                                String.class,
                                id))
                .isEqualTo("LEGACY_ACCEPTED_REPLAY");
        Homework replay =
                service.createHomework(
                        new CreateHomeworkRequest(
                                "legacy", null, null, subject, group, semester, day, 1, createKey));
        assertThat(replay.getId()).isEqualTo(id);
        assertThat(replay.getTitle()).isEqualTo("new");

        LocalDate yesterday = LocalDate.now(boundaryClock).minusDays(1);
        UUID movedKey = UUID.randomUUID(), expiredKey = UUID.randomUUID();
        Homework moved =
                new TransactionTemplate(transactions)
                        .execute(
                                status -> {
                                    Homework homework =
                                            new Homework(
                                                    group,
                                                    subject,
                                                    semester,
                                                    "moved",
                                                    null,
                                                    null,
                                                    actor,
                                                    yesterday,
                                                    1,
                                                    binding + 3,
                                                    actor,
                                                    movedKey,
                                                    createHash);
                                    homework.activatePublication();
                                    homework.applyEdit(
                                            new HomeworkSnapshot(
                                                    "current",
                                                    null,
                                                    null,
                                                    HomeworkBindingMode.LESSON,
                                                    day,
                                                    1),
                                            java.time.OffsetDateTime.now(boundaryClock));
                                    return repository.saveAndFlush(homework);
                                });
        var movedOriginal =
                new CreateHomeworkRequest(
                        "moved", null, null, subject, group, semester, yesterday, 1, movedKey);
        assertThat(validator.validate(movedOriginal)).isEmpty();
        Homework movedReplay = service.createHomework(movedOriginal);
        assertThat(movedReplay.getId()).isEqualTo(moved.getId());
        assertThat(movedReplay.getLessonDate()).isEqualTo(day);
        Homework expiredCurrent =
                new TransactionTemplate(transactions)
                        .execute(
                                status -> {
                                    Homework homework =
                                            new Homework(
                                                    group,
                                                    subject,
                                                    semester,
                                                    "future-original",
                                                    null,
                                                    null,
                                                    actor,
                                                    day,
                                                    null,
                                                    binding + 4,
                                                    actor,
                                                    expiredKey,
                                                    createHash,
                                                    HomeworkBindingMode.DATE);
                                    homework.activatePublication();
                                    homework.applyEdit(
                                            new HomeworkSnapshot(
                                                    "past-current",
                                                    null,
                                                    null,
                                                    HomeworkBindingMode.DATE,
                                                    yesterday,
                                                    null),
                                            java.time.OffsetDateTime.now(boundaryClock));
                                    return repository.saveAndFlush(homework);
                                });
        long countBefore =
                jdbc.queryForObject(
                        "SELECT count(*) FROM homeworks WHERE semester_id = ?",
                        Long.class,
                        semester);
        assertThatThrownBy(
                        () ->
                                service.createHomework(
                                        new CreateHomeworkRequest(
                                                "future-original",
                                                null,
                                                null,
                                                subject,
                                                group,
                                                semester,
                                                day,
                                                null,
                                                expiredKey,
                                                HomeworkBindingMode.DATE)))
                .isInstanceOf(ConflictException.class);
        assertThatThrownBy(
                        () ->
                                service.createHomework(
                                        new CreateHomeworkRequest(
                                                "new-past",
                                                null,
                                                null,
                                                subject,
                                                group,
                                                semester,
                                                yesterday,
                                                1,
                                                UUID.randomUUID())))
                .isInstanceOf(ru.rutcampustrack.academic.exception.BadRequestException.class);
        assertThat(
                        jdbc.queryForObject(
                                "SELECT count(*) FROM homeworks WHERE semester_id = ?",
                                Long.class,
                                semester))
                .isEqualTo(countBefore);
        assertThat(repository.findById(expiredCurrent.getId()).orElseThrow().getTitle())
                .isEqualTo("past-current");

        LocalDate cutoffDay = LocalDate.of(2026, 10, 2);
        Homework cutoff =
                new Homework(
                        group,
                        subject,
                        semester,
                        "date",
                        null,
                        null,
                        publisher,
                        cutoffDay,
                        null,
                        binding + 1,
                        publisher,
                        UUID.randomUUID(),
                        createHash,
                        HomeworkBindingMode.DATE);
        cutoff.activatePublication();
        assertThat(
                        new HomeworkLifecycle(
                                        Clock.fixed(
                                                Instant.parse("2026-10-02T20:59:59Z"),
                                                ZoneId.of("UTC")))
                                .archived(cutoff))
                .isFalse();
        assertThat(
                        new HomeworkLifecycle(
                                        Clock.fixed(
                                                Instant.parse("2026-10-02T21:00:00Z"),
                                                ZoneId.of("UTC")))
                                .archived(cutoff))
                .isTrue();
        Homework expired =
                new TransactionTemplate(transactions)
                        .execute(
                                status -> {
                                    Homework homework =
                                            new Homework(
                                                    group,
                                                    subject,
                                                    semester,
                                                    "expired",
                                                    null,
                                                    null,
                                                    publisher,
                                                    LocalDate.now(boundaryClock).minusDays(1),
                                                    null,
                                                    binding + 2,
                                                    publisher,
                                                    UUID.randomUUID(),
                                                    createHash,
                                                    HomeworkBindingMode.DATE);
                                    homework.activatePublication();
                                    return repository.saveAndFlush(homework);
                                });
        completion(expired);
        assertThat(
                        assembler
                                .toModel(service.getHomework(expired.getId()), true)
                                .getContent()
                                .isArchived())
                .isTrue();
        assertThatThrownBy(() -> service.unmarkComplete(expired.getId()))
                .isInstanceOf(ConflictException.class);
        assertThat(service.isCompleted(expired.getId())).isTrue();
        assertThat(service.history(expired.getId(), PageRequest.of(0, 10)).getContent()).isEmpty();
    }

    @Test
    void dateCreateNeedsNoLessonAndOriginalCreateReplaySurvivesContentEdit() throws Exception {
        var request =
                new CreateHomeworkRequest(
                        "day",
                        "details",
                        null,
                        subject,
                        group,
                        semester,
                        day,
                        null,
                        UUID.randomUUID(),
                        HomeworkBindingMode.DATE,
                        SubjectType.LECTURE,
                        null,
                        null);
        when(schedule.reserveDateHomeworkBinding(
                        eq(group),
                        eq(subject),
                        eq(semester),
                        eq(day),
                        eq(request.requestKey()),
                        any()))
                .thenReturn(dateBinding(null, 1));
        when(schedule.confirmHomeworkBinding(eq(binding), anyLong(), eq(request.requestKey())))
                .thenAnswer(call -> dateBinding(call.getArgument(1), 2));
        Homework created = service.createHomework(request);
        completion(created);
        assertThat(created.getBindingMode()).isEqualTo(HomeworkBindingMode.DATE);
        assertThat(created.getLessonNumber()).isNull();
        assertThat(repository.findById(created.getId()).orElseThrow().getLessonType())
                .isEqualTo(SubjectType.LECTURE);
        assertThat(assembler.toModel(created).getContent().getLessonType())
                .isEqualTo(SubjectType.LECTURE);
        assertThat(service.createHomework(request).getId()).isEqualTo(created.getId());
        assertThatThrownBy(
                        () ->
                                service.createHomework(
                                        new CreateHomeworkRequest(
                                                request.title(),
                                                request.description(),
                                                request.link(),
                                                subject,
                                                group,
                                                semester,
                                                day,
                                                null,
                                                request.requestKey(),
                                                HomeworkBindingMode.DATE,
                                                SubjectType.LAB,
                                                null,
                                                null)))
                .isInstanceOf(ConflictException.class);
        service.updateHomework(created.getId(), edit("changed", 1));
        Homework replay = service.createHomework(request);
        assertThat(replay.getId()).isEqualTo(created.getId());
        assertThat(replay.getTitle()).isEqualTo("changed");
        assertThat(replay.getLessonType()).isEqualTo(SubjectType.LECTURE);
        var changeType =
                new UpdateHomeworkRequest(
                        "changed",
                        "details",
                        null,
                        null,
                        null,
                        null,
                        UUID.randomUUID(),
                        2L,
                        SubjectType.LAB,
                        null,
                        null);
        service.updateHomework(created.getId(), changeType);
        assertThat(repository.findById(created.getId()).orElseThrow().getLessonType())
                .isEqualTo(SubjectType.LAB);
        assertThat(service.updateHomework(created.getId(), changeType).getRevision()).isEqualTo(3);
        assertThatThrownBy(
                        () ->
                                service.updateHomework(
                                        created.getId(),
                                        new UpdateHomeworkRequest(
                                                "changed",
                                                "details",
                                                null,
                                                null,
                                                null,
                                                null,
                                                changeType.requestKey(),
                                                2L,
                                                SubjectType.PRACTICE,
                                                null,
                                                null)))
                .isInstanceOf(ConflictException.class);
        var typeHistory =
                service.history(created.getId(), PageRequest.of(0, 10)).getContent().getLast();
        assertThat(typeHistory.before().lessonType()).isEqualTo(SubjectType.LECTURE);
        assertThat(typeHistory.after().lessonType()).isEqualTo(SubjectType.LAB);
        assertThat(service.isCompleted(created.getId())).isTrue();
        assertThat(actions(created)).hasSize(3);
        for (String action : actions(created)) {
            assertThat(
                            ru.rutcampustrack.academic.events.EventSchemaValidator.validate(
                                    "homework.action-recorded.json", action))
                    .isEmpty();
            var payload = mapper.readTree(action).path("payload");
            assertThat(payload.path("lesson_id").isNull()).isTrue();
            assertThat(payload.path("after").path("occurrence_id").isNull()).isTrue();
        }
        verify(schedule, never()).resolveLesson(anyLong(), any(), anyInt());
        verify(schedule, never()).reserveHomeworkBinding(anyLong(), any(), anyLong(), any());
    }

    @Test
    void acceptedPlacementDrainsThroughArchivePrepareAfterRevocationAndSupersedesOldTransfer()
            throws Exception {
        Homework homework = linkedHomework();
        completion(homework);
        oldTransferMarker(homework);
        becomeAssistant();
        HomeworkEditOperation operation = uncertainDateEdit(homework);
        assertThat(operation.actorRole()).isEqualTo("ASSISTANT");
        // The archive command is a separate admitted ADMIN request; the edit
        // retains its original ASSISTANT actor even after that grant is revoked.
        UUID adminSession = UUID.randomUUID();
        long adminGrant =
                jdbc.queryForObject(
                        "SELECT id FROM user_role_grants WHERE user_id=? AND role='admin' AND status='active'",
                        Long.class,
                        publisher);
        long rolesVersion =
                jdbc.queryForObject(
                        "SELECT roles_version FROM users WHERE id=?", Long.class, publisher);
        long sessionVersion = 1L;
        jdbc.update(
                """
                INSERT INTO auth_sessions(sid,user_id,active_role_grant_id,session_version,current_refresh_jti,
                    refresh_expires_at,created_at,last_seen_at,auth_method)
                VALUES (?,?,?,?,?,NOW()+INTERVAL '1 day',NOW(),NOW(),'PASSWORD')
                """,
                adminSession,
                publisher,
                adminGrant,
                sessionVersion,
                UUID.randomUUID());
        when(context.getClaims())
                .thenReturn(
                        new ru.rutcampustrack.shared.security.InternalJwtClaims(
                                publisher,
                                adminSession,
                                sessionVersion,
                                rolesVersion,
                                "ADMIN",
                                "ACTIVE",
                                null,
                                false,
                                false));
        when(context.getUserId()).thenReturn(publisher);
        when(context.getRole()).thenReturn(UserRole.ADMIN);
        var archive =
                archiveCommands.startOrReplay(
                        semester, publisher, UUID.randomUUID(), SemesterArchiveAction.ARCHIVE);
        jdbc.update(
                "UPDATE user_role_grants SET status = 'suspended' WHERE user_id = ? AND role = 'headman'",
                actor);
        jdbc.update("UPDATE headman_assistants SET is_active=false WHERE id=?", ownedAssistantId);
        when(context.getUserId()).thenReturn(publisher);
        when(context.getRole()).thenReturn(UserRole.ADMIN);
        when(schedule.continueHomeworkEdit(operation.identity()))
                .thenReturn(Optional.of(accepted(operation)));
        when(schedule.acknowledgeHomeworkEdit(operation.identity()))
                .thenReturn(accepted(operation).toBuilder().setState("ACKNOWLEDGED").build());
        coordinator.recover(operation);
        coordinator.recover(operation); // recovery race/exact retry
        when(context.getUserId()).thenReturn(actor);
        when(context.getRole()).thenReturn(UserRole.STUDENT);
        var action = mapper.readTree(actions(homework).getFirst()).path("payload").path("actor");
        assertThat(action.path("user_id").asLong()).isEqualTo(actor);
        assertThat(action.path("role").asText()).isEqualTo("ASSISTANT");
        Homework current = repository.findById(homework.getId()).orElseThrow();
        assertThat(current.getBindingMode()).isEqualTo(HomeworkBindingMode.DATE);
        assertThat(current.getLessonNumber()).isNull();
        assertThat(current.getRevision()).isEqualTo(2);
        assertThat(service.isCompleted(homework.getId())).isTrue();
        assertThat(service.history(homework.getId(), PageRequest.of(0, 10)).getTotalElements())
                .isEqualTo(1);
        assertThat(updatedOutbox(homework)).isEqualTo(1);
        assertThat(
                        jdbc.queryForObject(
                                "SELECT superseded_by_edit_operation_id FROM homework_binding_transfer_markers WHERE binding_id = ?",
                                UUID.class,
                                binding))
                .isEqualTo(operation.operationId());
        assertThat(persistence.find(homework.getId(), actor, operation.requestKey()).state())
                .isEqualTo("ACKNOWLEDGED");
        assertThat(
                        jdbc.queryForObject(
                                "SELECT count(*) FROM homework_edit_archive_admissions WHERE archive_operation_id = ?",
                                Long.class,
                                archive.getOperationId()))
                .isEqualTo(1);
    }

    @Test
    void dateTerminalEventWinsBeforeAcademicFinalizationWithoutSuccessfulEditOrCompletionLoss() {
        Homework homework = linkedHomework();
        completion(homework);
        HomeworkEditOperation operation = uncertainDateEdit(homework);
        archiveConsumer.onEvent(terminalEvent(homework, null, null));
        when(schedule.continueHomeworkEdit(operation.identity()))
                .thenReturn(Optional.of(accepted(operation)));
        when(schedule.acknowledgeHomeworkEdit(operation.identity()))
                .thenReturn(accepted(operation).toBuilder().setState("ACKNOWLEDGED").build());
        coordinator.recover(operation);
        Homework current = repository.findById(homework.getId()).orElseThrow();
        assertThat(current.getPublicationState()).isEqualTo(HomeworkPublicationState.ARCHIVED);
        assertThat(current.getTitle()).isEqualTo("A");
        assertThat(current.getRevision()).isEqualTo(1);
        assertThat(service.isCompleted(homework.getId())).isTrue();
        assertThat(service.history(homework.getId(), PageRequest.of(0, 10)).getTotalElements())
                .isZero();
        assertThat(updatedOutbox(homework)).isZero();
        assertThat(persistence.find(homework.getId(), actor, operation.requestKey()).outcome())
                .isEqualTo("TERMINAL_ABORTED");
        assertThatThrownBy(() -> service.updateHomework(homework.getId(), edit("late", 1)))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void missingMoveRequiresDurableAbortTombstoneBeforePreparedCommandIsCancelled() {
        Homework homework = linkedHomework();
        completion(homework);
        HomeworkEditOperation operation = uncertainDateEdit(homework);
        when(schedule.continueHomeworkEdit(operation.identity())).thenReturn(Optional.empty());
        when(schedule.abortUnacceptedHomeworkEdit(operation.identity()))
                .thenReturn(
                        HomeworkEditReceipt.newBuilder()
                                .setIdentity(operation.identity())
                                .setState("NOT_ACCEPTED")
                                .build());
        coordinator.recover(operation);
        verify(schedule).abortUnacceptedHomeworkEdit(operation.identity());
        verify(schedule, never()).acknowledgeHomeworkEdit(any());
        assertThat(persistence.find(homework.getId(), actor, operation.requestKey()).state())
                .isEqualTo("CANCELLED");
        assertThat(repository.findById(homework.getId()).orElseThrow().getTitle()).isEqualTo("A");
        assertThat(service.isCompleted(homework.getId())).isTrue();
        assertThat(updatedOutbox(homework)).isZero();
    }

    private Map<String, Object> terminalEvent(Homework homework, Long occurrence, Long lesson) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("binding_id", binding);
        payload.put("actor_id", publisher);
        payload.put("request_key", createKey.toString());
        payload.put("occurrence_id", occurrence);
        payload.put("lesson_id", lesson);
        payload.put("homework_id", homework.getId());
        payload.put("binding_revision", 9L);
        payload.put("semester_id", semester);
        return Map.of(
                "event_id",
                UUID.randomUUID().toString(),
                "event_type",
                "homework.binding.archived",
                "source",
                "schedule-service",
                "event_version",
                1L,
                "payload",
                payload);
    }

    @Test
    void archiveSelectorFreshAuthorityStablePagingAndReadOnlyWithoutChangingDefaultRead() {
        Homework terminal = linkedHomework();
        completion(terminal);
        Homework archivedActual =
                publications.archive(
                        terminal.getId(),
                        actor,
                        createKey,
                        binding,
                        linkedBinding(terminal.getId(), 2).toBuilder()
                                .setState(HomeworkBindingState.HOMEWORK_BINDING_STATE_ARCHIVED)
                                .build());
        Homework expired =
                new TransactionTemplate(transactions)
                        .execute(
                                status -> {
                                    Homework item =
                                            new Homework(
                                                    group,
                                                    subject,
                                                    semester,
                                                    "expired",
                                                    null,
                                                    null,
                                                    actor,
                                                    LocalDate.now(boundaryClock).minusDays(1),
                                                    null,
                                                    binding + 1,
                                                    actor,
                                                    UUID.randomUUID(),
                                                    createHash,
                                                    HomeworkBindingMode.DATE);
                                    item.activatePublication();
                                    return repository.saveAndFlush(item);
                                });
        Homework pending =
                new TransactionTemplate(transactions)
                        .execute(
                                status ->
                                        repository.saveAndFlush(
                                                new Homework(
                                                        group,
                                                        subject,
                                                        semester,
                                                        "pending",
                                                        null,
                                                        null,
                                                        actor,
                                                        day,
                                                        1,
                                                        binding + 2,
                                                        actor,
                                                        UUID.randomUUID(),
                                                        createHash)));
        assertThat(service.getHomework(terminal.getId(), true).getId()).isEqualTo(terminal.getId());
        assertThat(service.isCompleted(terminal.getId())).isTrue();
        assertThat(assembler.toModel(archivedActual, false, true).getRequiredLink("self").getHref())
                .contains("archived=true");
        assertThatThrownBy(() -> service.getHomework(terminal.getId()))
                .isInstanceOf(
                        ru.rutcampustrack.academic.contract.exception.ResourceNotFoundException
                                .class);
        assertThat(service.getHomework(expired.getId()).getId())
                .isEqualTo(expired.getId()); // prior default unchanged
        assertThat(
                        new HomeworkController(service, assembler)
                                .getHomework(terminal.getId(), true)
                                .getBody()
                                .getRequiredLink("self")
                                .getHref())
                .contains("archived=true");
        assertThatThrownBy(() -> service.getHomework(pending.getId(), true))
                .isInstanceOf(
                        ru.rutcampustrack.academic.contract.exception.ResourceNotFoundException
                                .class);
        assertThat(service.listHomeworks(group, semester, PageRequest.of(0, 1), true).getContent())
                .extracting(Homework::getId)
                .containsExactly(terminal.getId());
        assertThat(service.listHomeworks(group, semester, PageRequest.of(1, 1), true).getContent())
                .extracting(Homework::getId)
                .containsExactly(expired.getId());
        var pageAssembler =
                new org.springframework.data.web.PagedResourcesAssembler<HomeworkResponse>(
                        new org.springframework.data.web
                                .HateoasPageableHandlerMethodArgumentResolver(),
                        org.springframework.web.util.UriComponentsBuilder.fromPath(
                                        "/academic/homeworks")
                                .queryParam("archived", true)
                                .build());
        var collection =
                new HomeworkController(service, assembler)
                        .listHomeworks(group, semester, true, PageRequest.of(0, 10), pageAssembler)
                        .getBody();
        assertThat(collection.getContent())
                .allSatisfy(
                        item ->
                                assertThat(item.getContent().getRequiredLink("self").getHref())
                                        .contains("archived=true"));

        assertThatThrownBy(() -> service.updateHomework(terminal.getId(), edit("write", 1)))
                .isInstanceOf(ConflictException.class);
        assertThatThrownBy(() -> service.markComplete(expired.getId()))
                .isInstanceOf(ConflictException.class);
        when(context.getGroupId()).thenReturn(group + 10000);
        assertThatThrownBy(() -> service.getHomework(terminal.getId(), true))
                .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(
                        () -> service.listHomeworks(group, semester, PageRequest.of(0, 10), true))
                .isInstanceOf(AccessDeniedException.class);
        when(context.getGroupId()).thenReturn(group);
        jdbc.update(
                "UPDATE user_role_grants SET status = 'suspended' WHERE user_id = ? AND role = 'headman'",
                actor);
        assertThatThrownBy(() -> service.getHomework(terminal.getId(), true))
                .isInstanceOf(AccessDeniedException.class);
        jdbc.update(
                "INSERT INTO headman_assistants(group_id, student_id, permissions, is_active,assigned_by) VALUES(?,?,ARRAY['manage_homework'],true,?)",
                group,
                actor,
                publisher);
        assertThat(service.getHomework(terminal.getId(), true).getId()).isEqualTo(terminal.getId());
        jdbc.update(
                "UPDATE headman_assistants SET is_active=false WHERE group_id=? AND student_id=?",
                group,
                actor);
        assertThatThrownBy(
                        () -> service.listHomeworks(group, semester, PageRequest.of(0, 10), true))
                .isInstanceOf(AccessDeniedException.class);
        // Ordinary same-group student / read-only admin can still follow their default expired DATE
        // self link.
        var controller = new HomeworkController(service, assembler);
        var defaultExpired = controller.getHomework(expired.getId(), false).getBody();
        assertThat(defaultExpired.getContent().isArchived()).isTrue();
        assertThat(defaultExpired.getRequiredLink("self").getHref()).contains("archived=false");
        assertThat(controller.getHomework(expired.getId(), false).getBody().getContent().getId())
                .isEqualTo(expired.getId());
        assertThat(assembler.toModel(expired, false).getRequiredLink("self").getHref())
                .contains("archived=false");
        assertThatThrownBy(() -> controller.getHomework(expired.getId(), true))
                .isInstanceOf(AccessDeniedException.class);
        when(context.getRole()).thenReturn(UserRole.ADMIN);
        assertThat(
                        controller
                                .getHomework(expired.getId(), false)
                                .getBody()
                                .getRequiredLink("self")
                                .getHref())
                .contains("archived=false");
        assertThatThrownBy(() -> controller.getHomework(expired.getId(), true))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void selectedLessonGuardRejectsStaleBeforeWritesAndAcceptedReplayIgnoresLaterScheduleVersion() {
        var lesson =
                LessonResponse.newBuilder()
                        .setGroupId(group)
                        .setSubjectId(subject)
                        .setSemesterId(semester)
                        .setDate(day.toString())
                        .setLessonNumber(1)
                        .setOccurrenceId(701)
                        .setRevision(5)
                        .setLessonType("lecture")
                        .build();
        when(schedule.resolveLesson(group, day, 1)).thenReturn(Optional.of(lesson));
        var key = UUID.randomUUID();
        var stale =
                new CreateHomeworkRequest(
                        "guard",
                        null,
                        null,
                        subject,
                        group,
                        semester,
                        day,
                        1,
                        key,
                        HomeworkBindingMode.LESSON,
                        SubjectType.LECTURE,
                        701L,
                        4L);
        ConflictException staleFailure =
                catchThrowableOfType(() -> service.createHomework(stale), ConflictException.class);
        assertThat(staleFailure).isNotNull();
        var problem =
                new ru.rutcampustrack.academic.exception.GlobalExceptionHandler()
                        .handleConflict(
                                staleFailure,
                                new org.springframework.mock.web.MockHttpServletRequest(
                                        "POST", "/academic/homeworks"));
        assertThat(problem.getStatusCode().value()).isEqualTo(409);
        assertThat(problem.getHeaders().getContentType())
                .isEqualTo(org.springframework.http.MediaType.APPLICATION_PROBLEM_JSON);
        var problemJson = mapper.valueToTree(problem.getBody());
        assertThat(problemJson.path("extras").path("code").asText())
                .isEqualTo("HOMEWORK_SELECTION_STALE");
        assertThat(problemJson.path("extras").path("admission").asText())
                .isEqualTo("CURRENT_ATTEMPT_REJECTED_BEFORE_ADMISSION");
        assertThat(problemJson.path("extras").size())
                .isEqualTo(2); // no global definitelyNotAccepted promise
        assertThat(problemJson.path("type").asText()).endsWith("/conflict");
        assertThat(repository.findByActorIdAndRequestKey(actor, key)).isEmpty();
        verify(schedule, never()).reserveHomeworkBinding(anyLong(), any(), anyLong(), any());
        var spoof =
                new CreateHomeworkRequest(
                        "guard",
                        null,
                        null,
                        subject,
                        group,
                        semester,
                        day,
                        1,
                        key,
                        HomeworkBindingMode.LESSON,
                        SubjectType.LAB,
                        701L,
                        5L);
        assertThatThrownBy(() -> service.createHomework(spoof))
                .isInstanceOf(ru.rutcampustrack.academic.exception.BadRequestException.class);
        var valid =
                new CreateHomeworkRequest(
                        "guard",
                        null,
                        null,
                        subject,
                        group,
                        semester,
                        day,
                        1,
                        key,
                        HomeworkBindingMode.LESSON,
                        SubjectType.LECTURE,
                        701L,
                        5L);
        var authority =
                linkedBinding(1, 1).toBuilder()
                        .setCurrentLesson(
                                linkedBinding(1, 1).getCurrentLesson().toBuilder()
                                        .setRevision(5)
                                        .setLessonType("lecture"))
                        .build();
        when(schedule.reserveHomeworkBinding(eq(701L), eq(key), eq(5L), any()))
                .thenReturn(
                        authority.toBuilder()
                                .clearHomeworkId()
                                .setState(HomeworkBindingState.HOMEWORK_BINDING_STATE_PENDING)
                                .build());
        when(schedule.confirmHomeworkBinding(eq(binding), anyLong(), eq(key)))
                .thenAnswer(
                        call ->
                                authority.toBuilder()
                                        .setHomeworkId(call.<Long>getArgument(1))
                                        .setRevision(2)
                                        .build());
        Homework saved = service.createHomework(valid);
        assertThat(repository.findById(saved.getId()).orElseThrow().getLessonType())
                .isEqualTo(SubjectType.LECTURE);
        when(schedule.resolveLesson(group, day, 1))
                .thenReturn(Optional.of(lesson.toBuilder().setRevision(6).build()));
        assertThat(service.createHomework(valid).getId()).isEqualTo(saved.getId());
        assertThatThrownBy(
                        () ->
                                service.createHomework(
                                        new CreateHomeworkRequest(
                                                "guard",
                                                null,
                                                null,
                                                subject,
                                                group,
                                                semester,
                                                day,
                                                1,
                                                key,
                                                HomeworkBindingMode.LESSON,
                                                SubjectType.LECTURE,
                                                701L,
                                                6L)))
                .isInstanceOf(ConflictException.class);
        var staleEdit =
                new UpdateHomeworkRequest(
                        "no-write",
                        null,
                        null,
                        HomeworkBindingMode.LESSON,
                        day,
                        1,
                        UUID.randomUUID(),
                        1L,
                        SubjectType.LECTURE,
                        701L,
                        5L);
        assertThatThrownBy(() -> service.updateHomework(saved.getId(), staleEdit))
                .isInstanceOf(ConflictException.class)
                .satisfies(
                        failure ->
                                assertThat(((ConflictException) failure).getExtras())
                                        .containsEntry("code", "HOMEWORK_SELECTION_STALE"));
        assertThat(repository.findById(saved.getId()).orElseThrow().getRevision()).isEqualTo(1);
        assertThat(service.history(saved.getId(), PageRequest.of(0, 10)).getContent()).isEmpty();
        verify(schedule, never()).moveHomeworkBinding(any());
        var validEdit =
                new UpdateHomeworkRequest(
                        "edited",
                        null,
                        null,
                        HomeworkBindingMode.LESSON,
                        day,
                        1,
                        UUID.randomUUID(),
                        1L,
                        SubjectType.LECTURE,
                        701L,
                        6L);
        when(schedule.getHomeworkBinding(binding))
                .thenReturn(
                        authority.toBuilder().setHomeworkId(saved.getId()).setRevision(2).build());
        when(schedule.moveHomeworkBinding(any()))
                .thenAnswer(
                        call -> {
                            MoveHomeworkBindingRequest move = call.getArgument(0);
                            assertThat(move.getTargetOccurrenceId()).isEqualTo(701);
                            assertThat(move.getExpectedLessonRevision()).isEqualTo(6);
                            return HomeworkEditReceipt.newBuilder()
                                    .setIdentity(move.getIdentity())
                                    .setState("APPLIED_AWAITING_ACK")
                                    .setAcceptedBinding(
                                            authority.toBuilder()
                                                    .setHomeworkId(saved.getId())
                                                    .setRevision(3)
                                                    .setCurrentLesson(
                                                            authority.getCurrentLesson().toBuilder()
                                                                    .setRevision(6)))
                                    .build();
                        });
        Homework edited = service.updateHomework(saved.getId(), validEdit);
        assertThat(edited.getRevision()).isEqualTo(2);
        assertThat(edited.getLessonType()).isEqualTo(SubjectType.LECTURE);
        when(schedule.resolveLesson(group, day, 1))
                .thenReturn(Optional.of(lesson.toBuilder().setRevision(7).build()));
        assertThat(service.updateHomework(saved.getId(), validEdit).getRevision()).isEqualTo(2);
        assertThatThrownBy(
                        () ->
                                service.updateHomework(
                                        saved.getId(),
                                        new UpdateHomeworkRequest(
                                                "edited",
                                                null,
                                                null,
                                                HomeworkBindingMode.LESSON,
                                                day,
                                                1,
                                                validEdit.requestKey(),
                                                1L,
                                                SubjectType.LECTURE,
                                                701L,
                                                7L)))
                .isInstanceOf(ConflictException.class);
        assertThat(repository.findById(saved.getId()).orElseThrow().getTitle()).isEqualTo("edited");
        assertThat(service.history(saved.getId(), PageRequest.of(0, 10)).getTotalElements())
                .isEqualTo(1);
        verify(schedule, times(1)).moveHomeworkBinding(any());
    }

    @Test
    void legacyEditHashAndNullTypeRemainStable() throws Exception {
        var command = edit("legacy", 1);
        var oldJson =
                mapper.createObjectNode()
                        .put("title", command.title())
                        .putNull("description")
                        .putNull("link")
                        .putNull("bindingMode")
                        .putNull("lessonDate")
                        .putNull("lessonNumber")
                        .put("requestKey", command.requestKey().toString())
                        .put("expectedRevision", 1L);
        assertThat(new HomeworkEditHistory(jdbc, mapper).commandHash(command))
                .isEqualTo(
                        java.security.MessageDigest.getInstance("SHA-256")
                                .digest(mapper.writeValueAsBytes(oldJson)));
        Homework legacy = linkedHomework();
        service.updateHomework(legacy.getId(), command);
        assertThat(repository.findById(legacy.getId()).orElseThrow().getLessonType()).isNull();
    }

    private Homework linkedHomework() {
        return new TransactionTemplate(transactions)
                .execute(
                        status -> {
                            Homework homework =
                                    new Homework(
                                            group,
                                            subject,
                                            semester,
                                            "A",
                                            null,
                                            null,
                                            publisher,
                                            day,
                                            1,
                                            binding,
                                            publisher,
                                            createKey,
                                            createHash);
                            homework.activatePublication();
                            return repository.saveAndFlush(homework);
                        });
    }

    @Test
    void nonAuthorAssistantDeletePreservesCreatorHistoryAndActualActorOutboxOnReplay()
            throws Exception {
        Homework homework = linkedHomework();
        assertThat(publisher).isNotEqualTo(actor);
        completion(homework);
        service.updateHomework(homework.getId(), edit("B", 1));
        var history = service.history(homework.getId(), PageRequest.of(0, 10)).getContent();
        assertThat(history).hasSize(1);
        List<String> before = actions(homework);
        becomeAssistant();

        when(context.getGroupId()).thenReturn(group + 1);
        assertThatThrownBy(() -> service.deleteHomework(homework.getId()))
                .isInstanceOf(AccessDeniedException.class);
        when(context.getGroupId()).thenReturn(group);
        assertThat(repository.findById(homework.getId()).orElseThrow().getPublicationState())
                .isEqualTo(HomeworkPublicationState.ACTIVE);
        assertThat(actions(homework)).isEqualTo(before);
        verify(schedule, never()).archiveHomeworkBinding(anyLong(), anyLong(), any());

        when(schedule.archiveHomeworkBinding(binding, homework.getId(), createKey))
                .thenReturn(
                        linkedBinding(homework.getId(), 2).toBuilder()
                                .setState(HomeworkBindingState.HOMEWORK_BINDING_STATE_ARCHIVED)
                                .build());
        service.deleteHomework(homework.getId());
        Homework archived = repository.findById(homework.getId()).orElseThrow();
        assertThat(archived.getPublicationState()).isEqualTo(HomeworkPublicationState.ARCHIVED);
        assertThat(archived.getPublishedBy()).isEqualTo(publisher);
        assertThat(archived.getActorId()).isEqualTo(publisher);
        assertThat(archived.getArchiveActionActorId()).isEqualTo(actor);
        assertThat(archived.getArchiveActionActorRole()).isEqualTo("ASSISTANT");
        assertThat(archived.getTitle()).isEqualTo("B");
        assertThat(archived.getRevision()).isEqualTo(2);
        assertThat(service.isCompleted(homework.getId())).isTrue();
        assertThat(service.history(homework.getId(), PageRequest.of(0, 10)).getContent())
                .isEqualTo(history);
        List<String> archivedActions = actions(homework);
        assertThat(archivedActions).hasSize(before.size() + 1).startsWithElementsOf(before);
        String action = archivedActions.getLast();
        assertThat(
                        ru.rutcampustrack.academic.events.EventSchemaValidator.validate(
                                "homework.action-recorded.json", action))
                .isEmpty();
        var payload = mapper.readTree(action).path("payload");
        assertThat(payload.path("action").asText()).isEqualTo("HOMEWORK_ARCHIVED");
        assertThat(payload.path("actor").path("user_id").asLong()).isEqualTo(actor);
        assertThat(payload.path("actor").path("role").asText()).isEqualTo("ASSISTANT");
        assertThat(payload.path("before").path("publication_state").asText()).isEqualTo("ACTIVE");
        assertThat(payload.path("after").path("publication_state").asText()).isEqualTo("ARCHIVED");
        List<String> withdrawn =
                jdbc.queryForList(
                        "SELECT payload::text FROM academic_outbox WHERE event_type = 'homework.withdrawn' AND payload #>> '{payload,homework_id}' = ? ORDER BY id",
                        String.class,
                        homework.getId().toString());
        assertThat(withdrawn).hasSize(1);
        assertThat(
                        mapper.readTree(withdrawn.getFirst())
                                .path("payload")
                                .path("actor_user_id")
                                .asLong())
                .isEqualTo(actor);

        service.deleteHomework(homework.getId());
        jdbc.update("UPDATE headman_assistants SET is_active=false WHERE id=?", ownedAssistantId);
        assertThatThrownBy(() -> service.deleteHomework(homework.getId()))
                .isInstanceOf(AccessDeniedException.class);
        assertThat(actions(homework)).isEqualTo(archivedActions);
        assertThat(
                        jdbc.queryForList(
                                "SELECT payload::text FROM academic_outbox WHERE event_type = 'homework.withdrawn' AND payload #>> '{payload,homework_id}' = ? ORDER BY id",
                                String.class,
                                homework.getId().toString()))
                .isEqualTo(withdrawn);
        assertThat(service.history(homework.getId(), PageRequest.of(0, 10)).getContent())
                .isEqualTo(history);
        assertThat(repository.findById(homework.getId()).orElseThrow().getPublishedBy())
                .isEqualTo(publisher);
        verify(schedule, times(1)).archiveHomeworkBinding(binding, homework.getId(), createKey);
    }

    @Test
    void producerActionsKeepActualIdsAndReplayNoopArchiveAreAppendOnce() throws Exception {
        var lesson =
                LessonResponse.newBuilder()
                        .setGroupId(group)
                        .setSubjectId(subject)
                        .setSemesterId(semester)
                        .setDate(day.toString())
                        .setLessonNumber(1)
                        .setOccurrenceId(701)
                        .setRevision(5)
                        .setLessonType("lecture")
                        .build();
        when(schedule.resolveLesson(group, day, 1)).thenReturn(Optional.of(lesson));
        when(schedule.reserveHomeworkBinding(eq(701L), eq(createKey), eq(5L), any()))
                .thenReturn(
                        linkedBinding(1, 1).toBuilder()
                                .clearHomeworkId()
                                .setState(HomeworkBindingState.HOMEWORK_BINDING_STATE_PENDING)
                                .build());
        when(schedule.confirmHomeworkBinding(eq(binding), anyLong(), eq(createKey)))
                .thenThrow(new ScheduleServiceUnavailableException("synthetic lost confirmation"))
                .thenAnswer(call -> linkedBinding(call.getArgument(1), 2));
        var create =
                new CreateHomeworkRequest(
                        "A",
                        null,
                        null,
                        subject,
                        group,
                        semester,
                        day,
                        1,
                        createKey,
                        HomeworkBindingMode.LESSON,
                        SubjectType.LECTURE,
                        701L,
                        5L);
        assertThatThrownBy(() -> service.createHomework(create))
                .isInstanceOf(HomeworkPublicationPendingException.class);
        Homework pending = repository.findByActorIdAndRequestKey(actor, createKey).orElseThrow();
        assertThat(pending.getCreateActorRole()).isEqualTo("HEADMAN");
        becomeAssistant(); // Completing the accepted create must not rewrite its authority.
        Homework created = service.createHomework(create);
        HomeworkBindingResponse accepted = linkedBinding(created.getId(), 2);
        assertThat(
                        mapper.readTree(actions(created).getFirst())
                                .path("payload")
                                .path("actor")
                                .path("role")
                                .asText())
                .isEqualTo("HEADMAN");
        assertThat(created.getPlacementLessonId()).isEqualTo(700L);
        var command = edit("B", 1);
        service.updateHomework(created.getId(), command);
        List<String> beforeReplay = actions(created);
        jdbc.update(
                "UPDATE user_role_grants SET status='active' WHERE user_id=? AND role='headman'",
                actor);
        service.updateHomework(created.getId(), command);
        service.updateHomework(created.getId(), edit("B", 2));
        assertThat(actions(created)).isEqualTo(beforeReplay).hasSize(2);
        HomeworkBindingResponse archived =
                accepted.toBuilder()
                        .setRevision(3)
                        .setState(HomeworkBindingState.HOMEWORK_BINDING_STATE_ARCHIVED)
                        .build();
        when(schedule.archiveHomeworkBinding(binding, created.getId(), createKey))
                .thenThrow(
                        new ScheduleServiceUnavailableException("synthetic lost archive response"))
                .thenReturn(archived);
        assertThatThrownBy(() -> service.deleteHomework(created.getId()))
                .isInstanceOf(ScheduleServiceUnavailableException.class);
        assertThat(repository.findById(created.getId()).orElseThrow().getArchiveActionActorRole())
                .isEqualTo("HEADMAN");
        jdbc.update(
                "UPDATE user_role_grants SET status='suspended' WHERE user_id=? AND role='headman'",
                actor);
        service.deleteHomework(
                created.getId()); // Current assistant continues the first admitted headman intent.
        service.deleteHomework(created.getId());
        List<String> rows = actions(created);
        assertThat(rows).hasSize(3);
        for (String row : rows) {
            assertThat(
                            ru.rutcampustrack.academic.events.EventSchemaValidator.validate(
                                    "homework.action-recorded.json", row))
                    .isEmpty();
            var payload = mapper.readTree(row).path("payload");
            assertThat(payload.path("lesson_id").asLong()).isEqualTo(700);
            assertThat(payload.path("after").path("occurrence_id").asLong()).isEqualTo(701);
        }
        var editAction = mapper.readTree(rows.get(1)).path("payload");
        assertThat(editAction.path("before").path("title").asText()).isEqualTo("A");
        assertThat(editAction.path("after").path("title").asText()).isEqualTo("B");
        assertThat(editAction.path("actor").path("user_id").asLong()).isEqualTo(actor);
        assertThat(editAction.path("actor").path("role").asText()).isEqualTo("ASSISTANT");
        var archivedAction = mapper.readTree(rows.get(2)).path("payload");
        assertThat(archivedAction.path("action").asText()).isEqualTo("HOMEWORK_ARCHIVED");
        assertThat(archivedAction.path("before").path("publication_state").asText())
                .isEqualTo("ACTIVE");
        assertThat(archivedAction.path("after").path("publication_state").asText())
                .isEqualTo("ARCHIVED");
        assertThat(archivedAction.path("actor").path("role").asText()).isEqualTo("HEADMAN");
        assertThat(service.history(created.getId(), PageRequest.of(0, 10)).getContent()).hasSize(1);

        // No role is invented for an already accepted legacy pending publication.
        UUID legacyKey = UUID.randomUUID();
        Homework legacy =
                publications.persistPending(
                        group,
                        subject,
                        semester,
                        "legacy",
                        null,
                        null,
                        publisher,
                        day,
                        1,
                        binding + 1,
                        legacyKey,
                        createHash);
        publications.activate(
                legacy.getId(),
                publisher,
                legacyKey,
                binding + 1,
                createHash,
                accepted.toBuilder()
                        .setHomeworkId(legacy.getId())
                        .setBindingId(binding + 1)
                        .build());
        assertThat(
                        mapper.readTree(actions(legacy).getFirst())
                                .path("payload")
                                .path("actor")
                                .path("role")
                                .isNull())
                .isTrue();
        assertThatThrownBy(
                        () ->
                                jdbc.update(
                                        "UPDATE homeworks SET create_actor_role='HEADMAN' WHERE id=?",
                                        legacy.getId()))
                .isInstanceOf(org.springframework.dao.DataAccessException.class);
        jdbc.update("UPDATE headman_assistants SET is_active=false WHERE id=?", ownedAssistantId);
        assertThatThrownBy(() -> publications.prepareManualArchive(legacy.getId()))
                .isInstanceOf(AccessDeniedException.class);
        assertThat(repository.findById(legacy.getId()).orElseThrow().getArchiveActionActorId())
                .isNull();
        jdbc.update("UPDATE headman_assistants SET is_active=true WHERE id=?", ownedAssistantId);
        publications.prepareManualArchive(legacy.getId());
        Map<String, Object> terminal = new HashMap<>();
        terminal.put("binding_id", binding + 1);
        terminal.put("actor_id", publisher);
        terminal.put("request_key", legacyKey.toString());
        terminal.put("homework_id", legacy.getId());
        terminal.put("occurrence_id", 701L);
        terminal.put("lesson_id", 700L);
        terminal.put("binding_revision", 9L);
        terminal.put("semester_id", semester);
        archiveConsumer.onEvent(
                Map.of(
                        "event_id",
                        UUID.randomUUID().toString(),
                        "event_type",
                        "homework.binding.archived",
                        "source",
                        "schedule-service",
                        "event_version",
                        1L,
                        "payload",
                        terminal));
        assertThat(actions(legacy)).hasSize(2);
        assertThat(
                        mapper.readTree(actions(legacy).getLast())
                                .path("payload")
                                .path("actor")
                                .isNull())
                .isTrue();
    }

    private void becomeAssistant() {
        jdbc.update(
                "UPDATE user_role_grants SET status='suspended' WHERE user_id=? AND role='headman'",
                actor);
        ownedAssistantId =
                jdbc.queryForObject(
                        """
                INSERT INTO headman_assistants(group_id,student_id,permissions,is_active,assigned_by)
                VALUES(?,?,ARRAY['manage_homework'],true,?) RETURNING id
                """,
                        Long.class,
                        group,
                        actor,
                        publisher);
    }

    @Test
    void actionOutboxFailureRollsBackContentRevisionReceiptAndImmutableHistory() {
        Homework homework = linkedHomework();
        doThrow(new IllegalStateException("synthetic homework action failure"))
                .when(actionOutbox)
                .save(eq("homework.action-recorded"), anyString());
        assertThatThrownBy(() -> service.updateHomework(homework.getId(), edit("B", 1)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("synthetic homework action failure");
        Homework actual = repository.findById(homework.getId()).orElseThrow();
        assertThat(actual.getTitle()).isEqualTo("A");
        assertThat(actual.getRevision()).isEqualTo(1);
        assertThat(
                        jdbc.queryForObject(
                                "SELECT count(*) FROM homework_edit_receipts WHERE homework_id = ?",
                                Long.class,
                                homework.getId()))
                .isZero();
        assertThat(service.history(homework.getId(), PageRequest.of(0, 10)).getContent()).isEmpty();
        assertThat(actions(homework)).isEmpty();
        assertThat(updatedOutbox(homework)).isZero();
    }

    private List<String> actions(Homework homework) {
        return jdbc.queryForList(
                "SELECT payload::text FROM academic_outbox WHERE event_type = 'homework.action-recorded' AND payload #>> '{payload,homework_id}' = ? ORDER BY id",
                String.class,
                homework.getId().toString());
    }

    private HomeworkBindingResponse linkedBinding(long homework, long revision) {
        return HomeworkBindingResponse.newBuilder()
                .setBindingId(binding)
                .setHomeworkId(homework)
                .setGroupId(group)
                .setSubjectId(subject)
                .setSemesterId(semester)
                .setDate(day.toString())
                .setLessonNumber(1)
                .setBindingMode("LESSON")
                .setRevision(revision)
                .setOccurrenceId(701)
                .setState(HomeworkBindingState.HOMEWORK_BINDING_STATE_ACTIVE)
                .setCurrentLesson(
                        LessonInfo.newBuilder()
                                .setLessonId(700)
                                .setOccurrenceId(701)
                                .setGroupId(group)
                                .setSubjectId(subject)
                                .setSemesterId(semester)
                                .setDate(day.toString())
                                .setLessonNumber(1)
                                .setRevision(1))
                .build();
    }

    private void completion(Homework homework) {
        jdbc.update(
                "INSERT INTO homework_completions(homework_id,student_id,completed_at) VALUES(?,?,NOW())",
                homework.getId(),
                actor);
    }

    private UpdateHomeworkRequest edit(String title, long revision) {
        return new UpdateHomeworkRequest(title, null, null, UUID.randomUUID(), revision);
    }

    private long updatedOutbox(Homework homework) {
        return jdbc.queryForObject(
                "SELECT count(*) FROM academic_outbox WHERE event_type = 'homework.updated' AND payload #>> '{payload,homework_id}' = ?",
                Long.class,
                homework.getId().toString());
    }

    private HomeworkBindingResponse dateBinding(Long homework, long revision) {
        var response =
                HomeworkBindingResponse.newBuilder()
                        .setBindingId(binding)
                        .setGroupId(group)
                        .setSubjectId(subject)
                        .setSemesterId(semester)
                        .setDate(day.toString())
                        .setBindingMode("DATE")
                        .setRevision(revision)
                        .setState(
                                homework == null
                                        ? HomeworkBindingState.HOMEWORK_BINDING_STATE_PENDING
                                        : HomeworkBindingState.HOMEWORK_BINDING_STATE_ACTIVE);
        if (homework != null) response.setHomeworkId(homework);
        return response.build();
    }

    private HomeworkEditOperation uncertainDateEdit(Homework homework) {
        var command =
                new UpdateHomeworkRequest(
                        "accepted-date",
                        null,
                        null,
                        HomeworkBindingMode.DATE,
                        day,
                        null,
                        UUID.randomUUID(),
                        1L);
        when(schedule.getHomeworkBinding(binding))
                .thenReturn(
                        HomeworkBindingResponse.newBuilder()
                                .setBindingId(binding)
                                .setHomeworkId(homework.getId())
                                .setGroupId(group)
                                .setSubjectId(subject)
                                .setSemesterId(semester)
                                .setDate(day.toString())
                                .setLessonNumber(1)
                                .setBindingMode("LESSON")
                                .setRevision(7)
                                .setOccurrenceId(701)
                                .setState(HomeworkBindingState.HOMEWORK_BINDING_STATE_ACTIVE)
                                .setCurrentLesson(
                                        LessonInfo.newBuilder()
                                                .setLessonId(700)
                                                .setOccurrenceId(701)
                                                .setGroupId(group)
                                                .setSubjectId(subject)
                                                .setSemesterId(semester)
                                                .setDate(day.toString())
                                                .setLessonNumber(1)
                                                .setRevision(1))
                                .build());
        when(schedule.moveHomeworkBinding(any()))
                .thenThrow(new ScheduleServiceUnavailableException("simulated lost RPC response"));
        assertThatThrownBy(() -> service.updateHomework(homework.getId(), command))
                .isInstanceOf(ScheduleServiceUnavailableException.class);
        return persistence.find(homework.getId(), actor, command.requestKey());
    }

    private HomeworkEditReceipt accepted(HomeworkEditOperation operation) {
        return HomeworkEditReceipt.newBuilder()
                .setIdentity(operation.identity())
                .setState("APPLIED_AWAITING_ACK")
                .setAcceptedBinding(dateBinding(operation.homeworkId(), 8))
                .build();
    }

    private void oldTransferMarker(Homework homework) {
        jdbc.update(
                """
                INSERT INTO homework_binding_transfer_markers(binding_id,actor_id,request_key,binding_payload_hash,homework_id,
                    operation_id,operation_hash,occurrence_id,group_id,subject_id,semester_id,source_lesson_id,target_lesson_id,
                    source_date,source_lesson_number,target_date,target_lesson_number,state,source_event_id,batch_index)
                VALUES(?,?,?,?,?,?,?,701,?,?,?,700,701,?,1,?,1,'APPLIED',?,0)
                """,
                binding,
                publisher,
                createKey,
                createHash,
                homework.getId(),
                UUID.randomUUID(),
                createHash,
                group,
                subject,
                semester,
                day.minusDays(1),
                day,
                UUID.randomUUID());
    }
}
