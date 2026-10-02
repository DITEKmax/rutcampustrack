package ru.rutcampustrack.academic.homework;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.context.ApplicationEventPublisher;
import ru.rutcampustrack.academic.contract.dto.homework.HomeworkSnapshot;
import ru.rutcampustrack.academic.contract.dto.homework.UpdateHomeworkRequest;
import ru.rutcampustrack.academic.contract.enums.HomeworkBindingMode;
import ru.rutcampustrack.academic.contract.enums.HomeworkPublicationState;
import ru.rutcampustrack.academic.contract.exception.ResourceNotFoundException;
import ru.rutcampustrack.academic.entity.Homework;
import ru.rutcampustrack.academic.event.HomeworkUpdatedEvent;
import ru.rutcampustrack.academic.exception.AccessDeniedException;
import ru.rutcampustrack.academic.exception.ConflictException;
import ru.rutcampustrack.academic.repository.*;
import ru.rutcampustrack.academic.security.RequestContext;
import ru.rutcampustrack.academic.semester.AcademicSemesterArchiveBarrierTransaction;
import ru.rutcampustrack.schedule.grpc.HomeworkBindingResponse;
import ru.rutcampustrack.schedule.grpc.HomeworkEditReceipt;

import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.*;

/** Each method commits before a Schedule RPC is made; continuation replays only durable input. */
@Service
public class HomeworkEditPersistence {
    private final HomeworkRepository homeworks;
    private final HomeworkBindingArchiveCoordinator locks;
    private final HomeworkBindingTransferCoordinator transfers;
    private final AcademicSemesterArchiveBarrierTransaction barrier;
    private final UserRoleGrantRepository grants;
    private final HeadmanAssistantRepository assistants;
    private final RequestContext context;
    private final HomeworkEditHistory history;
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    public HomeworkEditPersistence(HomeworkRepository homeworks, HomeworkBindingArchiveCoordinator locks,
                                   HomeworkBindingTransferCoordinator transfers, AcademicSemesterArchiveBarrierTransaction barrier,
                                   UserRoleGrantRepository grants, HeadmanAssistantRepository assistants, RequestContext context,
                                   HomeworkEditHistory history, JdbcTemplate jdbc, ObjectMapper mapper,
                                   ApplicationEventPublisher events, Clock clock) {
        this.homeworks = homeworks; this.locks = locks; this.transfers = transfers; this.barrier = barrier;
        this.grants = grants; this.assistants = assistants; this.context = context; this.history = history;
        this.jdbc = jdbc; this.mapper = mapper; this.events = events; this.clock = clock;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Homework currentForEdit(long id) {
        return authorizedHomework(id);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Homework replay(long id, UpdateHomeworkRequest request) {
        Homework current = authorizedHomework(id);
        return history.replay(current, context.getUserId(), request.requestKey(), history.commandHash(request));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Homework content(long id, UpdateHomeworkRequest request) {
        Homework current = authorizedHomework(id);
        byte[] hash = history.commandHash(request);
        Homework replay = history.replay(current, context.getUserId(), request.requestKey(), hash);
        if (replay != null) return replay;
        rejectOtherPending(current.getId(), null);
        if (current.getRevision() != request.expectedRevision()) throw stale();
        HomeworkSnapshot before = current.snapshot();
        HomeworkSnapshot after = new HomeworkSnapshot(request.title(), request.description(), request.link(),
                current.getBindingMode(), current.getLessonDate(), current.getLessonNumber());
        boolean changed = !before.equals(after);
        current.captureLegacyAcceptedReplay();
        OffsetDateTime now = OffsetDateTime.now(clock);
        if (changed) current.applyEdit(after, now);
        history.record(current, context.getUserId(), request.requestKey(), hash, before, now, changed);
        homeworks.saveAndFlush(current);
        if (changed) publish(current);
        return current;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public HomeworkEditOperation prepare(long id, UpdateHomeworkRequest request, HomeworkSnapshot desired,
                                         HomeworkBindingResponse sourceBinding, Long occurrence, Long lessonRevision) {
        Homework current = authorizedHomework(id);
        byte[] hash = history.commandHash(request);
        HomeworkEditOperation existing = find(id, context.getUserId(), request.requestKey());
        if (existing != null) { verifyHash(existing, hash); return existing; }
        rejectOtherPending(id, null);
        if (current.getRevision() != request.expectedRevision()) throw stale();
        if (sourceBinding.getBindingId() != current.getBindingId()
                || !sourceBinding.getDate().equals(current.getLessonDate().toString())
                || sourceBinding.getLessonNumber() != (current.getLessonNumber() == null ? 0 : current.getLessonNumber())
                || !sourceBinding.getBindingMode().equals(current.getBindingMode().name())
                || sourceBinding.getGroupId() != current.getGroupId() || sourceBinding.getSubjectId() != current.getSubjectId()
                || sourceBinding.getSemesterId() != current.getSemesterId()) throw new ConflictException("Schedule projection is still changing");
        transfers.requireNoPendingPublicationMarker(current);
        UUID operation = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO homework_edit_operations
                    (operation_id, homework_id, binding_id, actor_id, request_key, command_hash,
                     group_id, subject_id, semester_id, expected_revision, expected_binding_revision,
                     request, before_snapshot, desired_snapshot, target_occurrence_id, target_lesson_revision)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?::jsonb, ?::jsonb, ?, ?)
                """, operation, id, current.getBindingId(), context.getUserId(), request.requestKey(), hash,
                current.getGroupId(), current.getSubjectId(), current.getSemesterId(), request.expectedRevision(), sourceBinding.getRevision(),
                json(request), json(current.snapshot()), json(desired), occurrence, lessonRevision);
        return read(operation);
    }

    @Transactional(readOnly = true)
    public HomeworkEditOperation find(long id, long actor, UUID key) {
        List<Map<String, Object>> rows = jdbc.queryForList("SELECT * FROM homework_edit_operations WHERE homework_id = ? AND actor_id = ? AND request_key = ?", id, actor, key);
        return rows.isEmpty() ? null : map(rows.getFirst());
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Homework finalizeReceipt(HomeworkEditOperation routed, HomeworkEditReceipt receipt) {
        if (!receipt.getIdentity().equals(routed.identity())) throw new ConflictException("Schedule edit identity differs from admitted command");
        barrier.lockHomeworkEditContinuation(routed.operationId());
        locks.lock(routed.bindingId());
        HomeworkEditOperation operation = read(routed.operationId());
        Homework current = homeworks.findById(operation.homeworkId()).orElseThrow(() -> new ConflictException("accepted edit content is missing"));
        if ("CANCELLED".equals(operation.state())) throw new ConflictException("unaccepted edit is cancelled; use a new requestKey");
        if (!"PREPARED".equals(operation.state())) return current.recordedResult(operation.result(), operation.resultRevision());
        if ("NOT_ACCEPTED".equals(receipt.getState())) {
            jdbc.update("UPDATE homework_edit_operations SET state = 'CANCELLED', outcome = 'NOT_ACCEPTED', finalized_at = now() WHERE operation_id = ?",
                    operation.operationId());
            return current;
        }
        if (!List.of("APPLIED_AWAITING_ACK", "ACKNOWLEDGED").contains(receipt.getState()) || !receipt.hasAcceptedBinding()) {
            throw new ConflictException("Schedule did not return a durable accepted edit result");
        }
        HomeworkBindingResponse accepted = receipt.getAcceptedBinding();
        if (accepted.getBindingId() != operation.bindingId() || !accepted.hasHomeworkId()
                || accepted.getHomeworkId() != operation.homeworkId() || accepted.getGroupId() != operation.groupId()
                || accepted.getSubjectId() != operation.subjectId() || accepted.getSemesterId() != operation.semesterId()
                || !accepted.getBindingMode().equals(operation.desired().bindingMode().name())
                || !accepted.getDate().equals(operation.desired().lessonDate().toString())
                || accepted.getLessonNumber() != (operation.desired().lessonNumber() == null ? 0 : operation.desired().lessonNumber())
                || accepted.getRevision() != operation.expectedBindingRevision() + 1
                || accepted.getOccurrenceId() != (operation.targetOccurrence() == null ? 0 : operation.targetOccurrence())
                || operation.desired().bindingMode() == HomeworkBindingMode.DATE && accepted.hasCurrentLesson()) {
            throw new ConflictException("accepted placement differs from prepared input");
        }
        boolean terminal = current.getPublicationState() == HomeworkPublicationState.ARCHIVED
                || (operation.desired().bindingMode() == HomeworkBindingMode.DATE
                    && operation.desired().lessonDate().isBefore(LocalDate.now(clock.withZone(ZoneId.of("Europe/Moscow")))))
                || locks.findMarker(current.getBindingId(), current.getActorId(), current.getRequestKey()).isPresent();
        HomeworkSnapshot result = terminal ? current.snapshot() : operation.desired();
        long resultRevision = terminal ? current.getRevision() : current.getRevision() + 1;
        if (!terminal && current.getRevision() != operation.expectedRevision()) throw new ConflictException("accepted edit encountered changed content revision");
        jdbc.queryForObject("SELECT set_config('rutcampustrack.homework_edit_operation_id', ?, true)", String.class, operation.operationId().toString());
        jdbc.update("""
                UPDATE homework_edit_operations SET state = 'FINALIZED', outcome = ?, result_snapshot = ?::jsonb,
                    result_revision = ?, finalized_at = now() WHERE operation_id = ?
                """, terminal ? "TERMINAL_ABORTED" : "APPLIED", json(result), resultRevision, operation.operationId());
        if (!terminal) {
            jdbc.queryForObject("SELECT set_config('rutcampustrack.homework_edit_operation_id', ?, true)", String.class, operation.operationId().toString());
            current.captureLegacyAcceptedReplay();
            OffsetDateTime now = OffsetDateTime.now(clock);
            current.applyEdit(result, now);
            history.record(current, operation.actorId(), operation.requestKey(), operation.hash(), operation.before(), now, true);
            transfers.supersedeAppliedMarker(current, operation.operationId());
            homeworks.saveAndFlush(current);
            publish(current);
        }
        return current;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void acknowledged(UUID id) {
        HomeworkEditOperation operation = read(id);
        barrier.lockHomeworkEditContinuation(id);
        locks.lock(operation.bindingId());
        jdbc.update("UPDATE homework_edit_operations SET state = 'ACKNOWLEDGED' WHERE operation_id = ? AND state = 'FINALIZED'", id);
    }

    @Transactional(readOnly = true)
    public List<HomeworkEditOperation> pending() {
        return jdbc.queryForList("""
                SELECT * FROM homework_edit_operations WHERE state IN ('PREPARED', 'FINALIZED') AND next_attempt_at <= now()
                 ORDER BY created_at LIMIT 32
                """).stream().map(this::map).toList();
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void retryLater(UUID id, String failure) {
        jdbc.update("""
                UPDATE homework_edit_operations SET attempts = attempts + 1, last_failure = ?,
                    next_attempt_at = now() + make_interval(secs => least(300, 5 * (attempts + 1)))
                 WHERE operation_id = ? AND state IN ('PREPARED', 'FINALIZED')
                """, failure, id);
    }

    private Homework authorizedHomework(long id) {
        HomeworkManagementAuthorization.require(context, grants, assistants);
        Homework current = homeworks.findById(id).orElseThrow(() -> new ResourceNotFoundException("Homework", "id", id));
        if (!Objects.equals(current.getGroupId(), context.getGroupId())) throw new AccessDeniedException("ДЗ принадлежит другой группе");
        locks.lockAndRefresh(current);
        HomeworkManagementAuthorization.require(context, grants, assistants);
        if (current.getPublicationState() != HomeworkPublicationState.ACTIVE || isExpired(current)) {
            throw new ConflictException("Архивное или ожидающее публикации ДЗ недоступно для изменения");
        }
        return current;
    }
    private boolean isExpired(Homework current) {
        return new HomeworkLifecycle(clock).expiredDate(current);
    }
    private void rejectOtherPending(long homework, UUID allowed) {
        Boolean pending = jdbc.queryForObject("SELECT EXISTS (SELECT 1 FROM homework_edit_operations WHERE homework_id = ? AND state IN ('PREPARED', 'FINALIZED') AND (?::uuid IS NULL OR operation_id <> ?::uuid))",
                Boolean.class, homework, allowed, allowed);
        if (Boolean.TRUE.equals(pending)) throw new ConflictException("ДЗ ожидает завершения уже принятого изменения");
    }
    private void publish(Homework current) {
        events.publishEvent(new HomeworkUpdatedEvent(this, current.getId(), current.getGroupId(), current.getSubjectId(),
                current.getTitle(), current.getDescription(), current.getLink(), current.getLessonDate().toString(), current.getLessonNumber()));
    }
    private HomeworkEditOperation read(UUID id) { return map(jdbc.queryForMap("SELECT * FROM homework_edit_operations WHERE operation_id = ?", id)); }
    private HomeworkEditOperation map(Map<String, Object> row) {
        return new HomeworkEditOperation((UUID) row.get("operation_id"), n(row,"homework_id"), n(row,"binding_id"), n(row,"actor_id"),
                (UUID) row.get("request_key"), (byte[]) row.get("command_hash"), n(row,"group_id"), n(row,"subject_id"), n(row,"semester_id"),
                n(row,"expected_revision"), n(row,"expected_binding_revision"), decode(row,"request",UpdateHomeworkRequest.class),
                decode(row,"before_snapshot",HomeworkSnapshot.class), decode(row,"desired_snapshot",HomeworkSnapshot.class),
                nullable(row,"target_occurrence_id"), nullable(row,"target_lesson_revision"), row.get("state").toString(),
                row.get("outcome") == null ? null : row.get("outcome").toString(), decode(row,"result_snapshot",HomeworkSnapshot.class), nullable(row,"result_revision"));
    }
    private static void verifyHash(HomeworkEditOperation operation, byte[] hash) {
        if (!Arrays.equals(operation.hash(), hash)) throw new ConflictException("requestKey уже использован для другого изменения ДЗ");
    }
    private static ConflictException stale() { return new ConflictException("ДЗ уже изменено; обнови текущую версию"); }
    private static long n(Map<String,Object> row,String key) { return ((Number) row.get(key)).longValue(); }
    private static Long nullable(Map<String,Object> row,String key) { return row.get(key)==null ? null : n(row,key); }
    private String json(Object value) { try { return mapper.writeValueAsString(value); } catch(JsonProcessingException invalid) { throw new IllegalStateException(invalid); } }
    private <T> T decode(Map<String,Object> row,String field,Class<T> type) {
        if(row.get(field)==null) return null;
        try { return mapper.readValue(row.get(field).toString(),type); } catch(JsonProcessingException invalid) { throw new IllegalStateException(invalid); }
    }
}
