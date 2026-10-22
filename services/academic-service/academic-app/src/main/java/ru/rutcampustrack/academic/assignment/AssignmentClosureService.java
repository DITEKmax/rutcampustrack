package ru.rutcampustrack.academic.assignment;

import io.grpc.Status;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import ru.rutcampustrack.academic.contract.exception.ResourceNotFoundException;
import ru.rutcampustrack.academic.entity.Assignment;
import ru.rutcampustrack.academic.entity.AssignmentReplacementOperation;
import ru.rutcampustrack.academic.entity.Semester;
import ru.rutcampustrack.academic.exception.AccessDeniedException;
import ru.rutcampustrack.academic.exception.AssignmentAdoptionPendingException;
import ru.rutcampustrack.academic.exception.BadRequestException;
import ru.rutcampustrack.academic.exception.ConflictException;
import ru.rutcampustrack.academic.exception.ScheduleServiceUnavailableException;
import ru.rutcampustrack.academic.grpc.ScheduleGrpcClient;
import ru.rutcampustrack.academic.repository.AssignmentReplacementOperationRepository;
import ru.rutcampustrack.academic.repository.AssignmentRepository;
import ru.rutcampustrack.academic.repository.SemesterRepository;
import ru.rutcampustrack.academic.security.RequestContext;
import ru.rutcampustrack.schedule.grpc.AssignmentCloseReceipt;

/** Durable close/adopt coordinator. Remote calls never run while Academic locks are held. */
@Service
public class AssignmentClosureService {
    private static final Logger log = LoggerFactory.getLogger(AssignmentClosureService.class);
    private static final List<AssignmentOperationState> OPEN =
            List.of(AssignmentOperationState.PREPARED, AssignmentOperationState.APPLIED);
    private final AssignmentRepository assignments;
    private final AssignmentReplacementOperationRepository operations;
    private final SemesterRepository semesters;
    private final RequestContext context;
    private final ScheduleGrpcClient schedule;
    private final JdbcTemplate jdbc;
    private final AssignmentTeacherEligibility teacherEligibility;
    private final TransactionTemplate transaction;
    private final TransactionTemplate outsideTransaction;

    public AssignmentClosureService(
            AssignmentRepository assignments,
            AssignmentReplacementOperationRepository operations,
            SemesterRepository semesters,
            RequestContext context,
            ScheduleGrpcClient schedule,
            JdbcTemplate jdbc,
            AssignmentTeacherEligibility teacherEligibility,
            PlatformTransactionManager transactionManager) {
        this.assignments = assignments;
        this.operations = operations;
        this.semesters = semesters;
        this.context = context;
        this.schedule = schedule;
        this.jdbc = jdbc;
        this.teacherEligibility = teacherEligibility;
        this.transaction = new TransactionTemplate(transactionManager);
        this.transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.outsideTransaction = new TransactionTemplate(transactionManager);
        this.outsideTransaction.setPropagationBehavior(
                TransactionDefinition.PROPAGATION_NOT_SUPPORTED);
    }

    public void close(Long id, Long subjectId, Long teacherId, LocalDate cutoff, UUID requestKey) {
        long actor = actor();
        if (id == null || id <= 0 || cutoff == null || requestKey == null) {
            throw new BadRequestException(
                    "assignment/date/key", "Назначение, дата и ключ обязательны");
        }
        byte[] hash = hash("CLOSE_UNASSIGN|" + id + "|" + cutoff);
        AssignmentReplacementOperation operation =
                transaction.execute(
                        tx -> {
                            Assignment before = requiredAssignment(id);
                            requireTuple(before, subjectId, teacherId);
                            AssignmentReplacementOperation existing =
                                    acceptedClosure(actor, requestKey, id, hash);
                            if (existing != null) {
                                if (existing.getOperationKind()
                                                != AssignmentOperationKind.CLOSE_UNASSIGN
                                        || !MessageDigest.isEqual(
                                                hash, existing.getPayloadHash())) {
                                    throw new ru.rutcampustrack.academic.exception
                                            .AssignmentIdempotencyConflictException();
                                }
                                lockSemester(before.getSemesterId());
                                return existing;
                            }
                            Semester semester = lockSemester(before.getSemesterId());
                            Assignment source = assignments.findByIdForUpdate(id).orElseThrow();
                            requireTuple(source, subjectId, teacherId);
                            existing = acceptedClosure(actor, requestKey, id, hash);
                            if (existing != null) {
                                if (existing.getOperationKind()
                                                != AssignmentOperationKind.CLOSE_UNASSIGN
                                        || !MessageDigest.isEqual(
                                                hash, existing.getPayloadHash())) {
                                    throw new ru.rutcampustrack.academic.exception
                                            .AssignmentIdempotencyConflictException();
                                }
                                return existing;
                            }
                            if (cutoff.isBefore(LocalDate.now(ZoneId.of("Europe/Moscow")))) {
                                throw new BadRequestException(
                                        "validUntilExclusive",
                                        "Дата снятия не может быть в прошлом");
                            }
                            if (source.getLifecycleState() != AssignmentLifecycle.ACTIVE)
                                throw new ConflictException("Назначение уже закрыто");
                            requireNoOpenTuple(
                                    source.getGroupId(),
                                    source.getSubjectId(),
                                    source.getSemesterId(),
                                    source.getLessonType());
                            operations
                                    .findFirstBySourceAssignmentIdAndStateIn(id, OPEN)
                                    .ifPresent(
                                            o -> {
                                                throw new ConflictException(
                                                        "Назначение уже меняется");
                                            });
                            operations
                                    .findFirstByTargetAssignmentIdAndStateIn(id, OPEN)
                                    .ifPresent(
                                            o -> {
                                                throw new ConflictException(
                                                        "Назначение ещё не подтверждено");
                                            });
                            LocalDate end = AssignmentAuthority.effectiveEnd(source, semester);
                            if (cutoff.isAfter(end))
                                throw new BadRequestException(
                                        "validUntilExclusive", "Дата вне периода назначения");
                            LocalDate cap =
                                    cutoff.isBefore(source.getValidFrom())
                                            ? source.getValidFrom()
                                            : cutoff;
                            return operations.saveAndFlush(
                                    AssignmentReplacementOperation.lifecycle(
                                            UUID.randomUUID(),
                                            actor,
                                            requestKey,
                                            hash,
                                            id,
                                            null,
                                            cap,
                                            end,
                                            AssignmentOperationKind.CLOSE_UNASSIGN,
                                            cutoff));
                        });
        try {
            resume(operation.getOperationId());
        } catch (RuntimeException failure) {
            warnPending(operation.getOperationId(), "CLOSE_UNASSIGN", "RESUME_CLOSE", failure);
            throw new ru.rutcampustrack.academic.exception.AssignmentClosurePendingException(
                    id, requestKey);
        }
    }

    private AssignmentReplacementOperation acceptedClosure(
            long actor, UUID key, Long source, byte[] hash) {
        AssignmentReplacementOperation own =
                operations.findByActorIdAndRequestKey(actor, key).orElse(null);
        List<AssignmentReplacementOperation> candidates =
                own == null
                        ? operations.findBySourceAssignmentIdAndRequestKeyAndOperationKind(
                                source, key, AssignmentOperationKind.CLOSE_UNASSIGN)
                        : List.of(own);
        if (candidates.size() > 1) throw new ConflictException("Ключ снятия неоднозначен");
        if (candidates.isEmpty()) return null;
        AssignmentReplacementOperation existing = candidates.get(0);
        if (existing.getOperationKind() != AssignmentOperationKind.CLOSE_UNASSIGN
                || !Objects.equals(source, existing.getSourceAssignmentId())
                || !MessageDigest.isEqual(hash, existing.getPayloadHash())) {
            throw new ru.rutcampustrack.academic.exception.AssignmentIdempotencyConflictException();
        }
        return existing;
    }

    /**
     * Caller holds the semester admission lock; accepted receipts bypass this NEW-command barrier.
     */
    public void requireNoOpenTuple(
            Long groupId,
            Long subjectId,
            Long semesterId,
            ru.rutcampustrack.academic.contract.enums.SubjectType lessonType) {
        Boolean open =
                jdbc.queryForObject(
                        """
                SELECT EXISTS (
                    SELECT 1 FROM assignment_replacement_operations operation
                    JOIN assignments binding ON binding.id=COALESCE(operation.source_assignment_id, operation.target_assignment_id)
                    WHERE operation.state IN ('PREPARED','APPLIED') AND binding.group_id=?
                      AND binding.subject_id=? AND binding.semester_id=? AND binding.lesson_type::text=?)
                """,
                        Boolean.class,
                        groupId,
                        subjectId,
                        semesterId,
                        lessonType.name().toLowerCase());
        if (Boolean.TRUE.equals(open))
            throw new ConflictException(
                    "Изменение назначения для этого типа занятия ещё не завершено");
    }

    /**
     * Called within the one admission transaction, after the positive target row has been flushed.
     */
    public void prepareAdoption(Assignment target) {
        long actor = actor();
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException(
                    "Adoption admission requires its assignment transaction");
        }
        UUID operationId = UUID.randomUUID();
        LocalDate end =
                AssignmentAuthority.effectiveEnd(
                        target, semesters.findById(target.getSemesterId()).orElseThrow());
        byte[] hash =
                hash(
                        "ADOPT_UNASSIGNED|"
                                + target.getId()
                                + "|"
                                + target.getTeacherId()
                                + "|"
                                + target.getSubjectId()
                                + "|"
                                + target.getGroupId()
                                + "|"
                                + target.getSemesterId()
                                + "|"
                                + target.getLessonType()
                                + "|"
                                + target.getValidFrom()
                                + "|"
                                + end);
        operations.saveAndFlush(
                AssignmentReplacementOperation.lifecycle(
                        operationId,
                        actor,
                        operationId,
                        hash,
                        null,
                        target.getId(),
                        target.getValidFrom(),
                        end,
                        AssignmentOperationKind.ADOPT_UNASSIGNED,
                        target.getValidFrom()));
        // No request context or managed target is needed after the admission commits.
        Long subjectId = target.getSubjectId();
        TransactionSynchronizationManager.registerSynchronization(
                new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        try {
                            AssignmentClosureService.this.resume(operationId);
                        } catch (RuntimeException failure) {
                            warnPending(
                                    operationId,
                                    "ADOPT_UNASSIGNED",
                                    "AFTER_COMMIT_ADOPTION",
                                    failure);
                            throw pending(subjectId, actor);
                        }
                    }
                });
    }

    public Assignment resumeAdoption(Long targetId, UUID operationId) {
        actor();
        Assignment target = requiredAssignment(targetId);
        requireTuple(target, null, null);
        AssignmentReplacementOperation operation =
                operations
                        .findByTargetAssignmentIdAndOperationKind(
                                targetId, AssignmentOperationKind.ADOPT_UNASSIGNED)
                        .orElseThrow(
                                () ->
                                        new ResourceNotFoundException(
                                                "AssignmentOperation", "target", targetId));
        if (operation.getOperationKind() != AssignmentOperationKind.ADOPT_UNASSIGNED
                || !Objects.equals(targetId, operation.getTargetAssignmentId())
                || operationId != null && !Objects.equals(operationId, operation.getOperationId()))
            throw new AccessDeniedException("Чужая операция назначения");
        byte[] targetHash =
                hash(
                        "ADOPT_UNASSIGNED|"
                                + target.getId()
                                + "|"
                                + target.getTeacherId()
                                + "|"
                                + target.getSubjectId()
                                + "|"
                                + target.getGroupId()
                                + "|"
                                + target.getSemesterId()
                                + "|"
                                + target.getLessonType()
                                + "|"
                                + target.getValidFrom()
                                + "|"
                                + operation.getTargetValidUntil());
        if (!Objects.equals(target.getValidFrom(), operation.getEffectiveFrom())
                || !MessageDigest.isEqual(targetHash, operation.getPayloadHash())) {
            throw new ConflictException("Операция не соответствует принятому назначению");
        }
        transaction.executeWithoutResult(tx -> lockSemester(target.getSemesterId()));
        try {
            resume(operation.getOperationId());
        } catch (RuntimeException failure) {
            warnPending(operation.getOperationId(), "ADOPT_UNASSIGNED", "RESUME_ADOPTION", failure);
            throw new AssignmentAdoptionPendingException(
                    target.getSubjectId(),
                    List.of(
                            new AssignmentAdoptionPendingException.PendingAdoption(
                                    targetId, operation.getOperationId())));
        }
        return requiredAssignment(targetId);
    }

    /**
     * Only code-owned identifiers and a single source location; never pass a Throwable to the
     * logger.
     */
    private static void warnPending(
            UUID operationId, String kind, String phase, RuntimeException failure) {
        Throwable cause = failure;
        for (int depth = 0;
                depth < 8 && cause.getCause() != null && cause.getCause() != cause;
                depth++) {
            cause = cause.getCause();
        }
        String applicationFrame = "NONE";
        StackTraceElement[] frames = cause.getStackTrace();
        for (int index = 0; index < Math.min(frames.length, 32); index++) {
            StackTraceElement frame = frames[index];
            if (frame.getClassName().startsWith("ru.rutcampustrack.")
                    && frame.getFileName() != null
                    && frame.getFileName().matches("[A-Za-z0-9_.$-]{1,128}")
                    && frame.getMethodName().matches("[A-Za-z0-9_.$<>-]{1,128}")) {
                applicationFrame =
                        frame.getFileName()
                                + "#"
                                + frame.getMethodName()
                                + ":"
                                + frame.getLineNumber();
                break;
            }
        }
        log.warn(
                "Assignment lifecycle pending operationId={} operationKind={} phase={} failureClass={} grpcStatus={} applicationFrame={}",
                operationId,
                kind,
                phase,
                cause.getClass().getName(),
                Status.fromThrowable(failure).getCode().name(),
                applicationFrame);
    }

    public List<ru.rutcampustrack.academic.contract.dto.subject.PendingAdoptionResponse>
            pendingSubject(Long subjectId) {
        if (context.getInternalToken() == null
                || context.getInternalToken().isBlank()
                || !context.isHeadman()
                || context.getGroupId() == null
                || context.getUserId() == null) return null;
        return transaction.execute(
                tx ->
                        jdbc.query(
                                """
                SELECT operation.target_assignment_id,operation.operation_id,operation.state,
                       target.teacher_id,target.subject_id,target.group_id,target.semester_id,target.lesson_type::text,
                       target.valid_from,target.valid_until_exclusive
                  FROM assignment_replacement_operations operation JOIN assignments target ON target.id=operation.target_assignment_id
                 WHERE target.subject_id=? AND target.group_id=? AND operation.operation_kind='ADOPT_UNASSIGNED'
                   AND operation.state IN ('PREPARED','APPLIED') ORDER BY operation.operation_id
                """,
                                (rs, row) ->
                                        new ru.rutcampustrack.academic.contract.dto.subject
                                                .PendingAdoptionResponse(
                                                rs.getLong(1),
                                                rs.getObject(2, UUID.class),
                                                rs.getString(3),
                                                rs.getLong(4),
                                                rs.getLong(5),
                                                rs.getLong(6),
                                                rs.getLong(7),
                                                ru.rutcampustrack.academic.contract.enums
                                                        .SubjectType.valueOf(
                                                        rs.getString(8)
                                                                .toUpperCase(
                                                                        java.util.Locale.ROOT)),
                                                rs.getObject(9, LocalDate.class),
                                                rs.getObject(10, LocalDate.class)),
                                subjectId,
                                context.getGroupId()));
    }

    public List<ru.rutcampustrack.academic.contract.dto.subject.PendingClosureResponse>
            pendingClosures(Long subjectId) {
        if (context.getInternalToken() == null
                || context.getInternalToken().isBlank()
                || !context.isHeadman()
                || context.getGroupId() == null
                || context.getUserId() == null) return null;
        return transaction.execute(
                tx ->
                        jdbc.query(
                                """
                SELECT operation.operation_id,source.id,operation.request_key,operation.requested_cutoff,
                       source.teacher_id,source.subject_id,source.group_id,source.semester_id,source.lesson_type::text
                  FROM assignment_replacement_operations operation JOIN assignments source ON source.id=operation.source_assignment_id
                 WHERE source.subject_id=? AND source.group_id=? AND operation.operation_kind='CLOSE_UNASSIGN'
                   AND operation.state IN ('PREPARED','APPLIED') ORDER BY operation.operation_id
                """,
                                (rs, row) ->
                                        new ru.rutcampustrack.academic.contract.dto.subject
                                                .PendingClosureResponse(
                                                rs.getObject(1, UUID.class),
                                                rs.getLong(2),
                                                rs.getObject(3, UUID.class),
                                                rs.getObject(4, LocalDate.class),
                                                rs.getLong(5),
                                                rs.getLong(6),
                                                rs.getLong(7),
                                                rs.getLong(8),
                                                ru.rutcampustrack.academic.contract.enums
                                                        .SubjectType.valueOf(
                                                        rs.getString(9)
                                                                .toUpperCase(
                                                                        java.util.Locale.ROOT))),
                                subjectId,
                                context.getGroupId()));
    }

    private void resume(UUID operationId) {
        // afterCommit callbacks still have the completed transaction bound.
        // Suspend it explicitly before making RPCs; each local phase uses
        // REQUIRES_NEW and commits before the next remote phase.
        outsideTransaction.executeWithoutResult(tx -> resumeOutsideTransaction(operationId));
    }

    private void resumeOutsideTransaction(UUID operationId) {
        AssignmentReplacementOperation operation = readOperation(operationId);
        if (operation.getState() == AssignmentOperationState.COMMITTED) return;
        if (operation.getState() == AssignmentOperationState.PREPARED) {
            AssignmentCloseReceipt receipt =
                    schedule.installAssignmentLifecycle(
                            operationId,
                            operation.getSourceAssignmentId(),
                            operation.getTargetAssignmentId(),
                            operation.getEffectiveFrom(),
                            operation.getPayloadHash(),
                            operation.getOperationKind().getCode());
            requireReceipt(operation, receipt, "APPLIED");
            transaction.executeWithoutResult(tx -> applyAcademic(operationId, receipt));
            operation = readOperation(operationId);
        }
        if (operation.getState() == AssignmentOperationState.APPLIED) {
            AssignmentCloseReceipt receipt =
                    schedule.commitAssignmentClose(operationId, operation.getPayloadHash());
            requireReceipt(operation, receipt, "COMMITTED");
            transaction.executeWithoutResult(
                    tx -> {
                        AssignmentReplacementOperation locked =
                                operations.findByIdForUpdate(operationId).orElseThrow();
                        if (locked.getState() == AssignmentOperationState.APPLIED) {
                            locked.markCommitted(
                                    receipt.getState(),
                                    receipt.getMovedCount(),
                                    receipt.getSkippedCount());
                            operations.saveAndFlush(locked);
                        }
                    });
        }
    }

    private void applyAcademic(UUID operationId, AssignmentCloseReceipt receipt) {
        AssignmentReplacementOperation before = operations.findById(operationId).orElseThrow();
        Long assignmentId =
                before.getSourceAssignmentId() != null
                        ? before.getSourceAssignmentId()
                        : before.getTargetAssignmentId();
        Assignment snapshot = requiredAssignment(assignmentId);
        lockSemester(snapshot.getSemesterId());
        Assignment assignment = assignments.findByIdForUpdate(assignmentId).orElseThrow();
        AssignmentReplacementOperation operation =
                operations.findByIdForUpdate(operationId).orElseThrow();
        if (operation.getState() != AssignmentOperationState.PREPARED) return;
        requireReceipt(operation, receipt, "APPLIED");
        if (operation.getOperationKind() == AssignmentOperationKind.ADOPT_UNASSIGNED)
            teacherEligibility.requireEligible(assignment.getTeacherId());
        operation.markApplied(receipt.getState(), 0, 0);
        operations.saveAndFlush(operation);
        jdbc.queryForObject(
                "SELECT set_config('rutcampustrack.assignment_replacement_operation_id', ?, true)",
                String.class,
                operationId.toString());
        if (operation.getOperationKind() == AssignmentOperationKind.CLOSE_UNASSIGN) {
            if (!operation.getRequestedCutoff().isAfter(assignment.getValidFrom())) {
                jdbc.update(
                        "UPDATE assignments SET lifecycle_state = 'CLOSED' WHERE id = ?",
                        assignmentId);
            } else {
                jdbc.update(
                        "UPDATE assignments SET valid_until_exclusive = ?, lifecycle_state='CLOSED' WHERE id = ?",
                        operation.getEffectiveFrom(),
                        assignmentId);
            }
        } else {
            jdbc.update(
                    "UPDATE assignments SET lifecycle_state = 'ACTIVE' WHERE id = ?", assignmentId);
        }
    }

    private AssignmentReplacementOperation readOperation(UUID id) {
        // afterCommit still has the completed admission's resources bound to the thread.
        // Every local read/write gets a fresh transaction; remote calls remain outside it.
        return transaction.execute(tx -> operations.findById(id).orElseThrow());
    }

    private AssignmentAdoptionPendingException pending(Long subjectId, long actor) {
        List<AssignmentAdoptionPendingException.PendingAdoption> pending =
                transaction.execute(
                        tx ->
                                jdbc.query(
                                        """
                SELECT operation.target_assignment_id, operation.operation_id
                  FROM assignment_replacement_operations operation JOIN assignments target ON target.id = operation.target_assignment_id
                 WHERE operation.actor_id = ? AND target.subject_id = ? AND operation.operation_kind = 'ADOPT_UNASSIGNED'
                   AND operation.state <> 'COMMITTED' ORDER BY operation.operation_id
                """,
                                        (rs, row) ->
                                                new AssignmentAdoptionPendingException
                                                        .PendingAdoption(
                                                        rs.getLong(1), rs.getObject(2, UUID.class)),
                                        actor,
                                        subjectId));
        return new AssignmentAdoptionPendingException(subjectId, pending);
    }

    private Semester lockSemester(Long id) {
        Semester semester = semesters.findByIdForUpdate(id).orElseThrow();
        Boolean writable =
                jdbc.queryForObject(
                        "SELECT NOT is_archived AND archive_transition = 'NONE' AND NOT archive_release_pending AND deletion_phase IS NULL FROM semesters WHERE id = ?",
                        Boolean.class,
                        id);
        if (!Boolean.TRUE.equals(writable))
            throw new ConflictException("Семестр закрыт для изменений");
        return semester;
    }

    private Assignment requiredAssignment(Long id) {
        return assignments
                .findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Assignment", "id", id));
    }

    private long actor() {
        if (context.getInternalToken() == null
                || context.getInternalToken().isBlank()
                || !context.isHeadman()
                || context.getUserId() == null
                || context.getUserId() <= 0
                || context.getGroupId() == null
                || context.getGroupId() <= 0)
            throw new AccessDeniedException("Нужно право старосты своей группы");
        return context.getUserId();
    }

    private void requireTuple(Assignment assignment, Long subjectId, Long teacherId) {
        if (!Objects.equals(assignment.getGroupId(), context.getGroupId()))
            throw new AccessDeniedException("Чужая группа");
        if (subjectId != null && !subjectId.equals(assignment.getSubjectId())
                || teacherId != null && !teacherId.equals(assignment.getTeacherId())) {
            throw new ResourceNotFoundException("Assignment", "id", assignment.getId());
        }
    }

    private static void requireReceipt(
            AssignmentReplacementOperation operation,
            AssignmentCloseReceipt receipt,
            String state) {
        if (receipt == null
                || !operation.getOperationId().toString().equals(receipt.getOperationId())
                || !state.equals(receipt.getState())
                || receipt.getMovedCount() < 0
                || receipt.getSkippedCount() < 0
                || "APPLIED".equals(state)
                        && (receipt.getMovedCount() != 0 || receipt.getSkippedCount() != 0)) {
            throw new ScheduleServiceUnavailableException(
                    "Не подтверждена точная операция назначения");
        }
    }

    private static byte[] hash(String canonical) {
        try {
            return MessageDigest.getInstance("SHA-256")
                    .digest(canonical.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException failure) {
            throw new IllegalStateException("SHA-256 unavailable", failure);
        }
    }
}
