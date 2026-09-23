package ru.rutcampustrack.academic.assignment;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import ru.rutcampustrack.academic.contract.dto.assignment.AssignmentReplacementResponse;
import ru.rutcampustrack.academic.contract.dto.assignment.ReplaceAssignmentRequest;
import ru.rutcampustrack.academic.contract.enums.SubjectType;
import ru.rutcampustrack.academic.contract.enums.UserRole;
import ru.rutcampustrack.academic.contract.exception.ResourceNotFoundException;
import ru.rutcampustrack.academic.entity.Assignment;
import ru.rutcampustrack.academic.entity.AssignmentReplacementOperation;
import ru.rutcampustrack.academic.entity.Semester;
import ru.rutcampustrack.academic.entity.User;
import ru.rutcampustrack.academic.exception.AccessDeniedException;
import ru.rutcampustrack.academic.exception.BadRequestException;
import ru.rutcampustrack.academic.exception.ConflictException;
import ru.rutcampustrack.academic.grpc.ScheduleGrpcClient;
import ru.rutcampustrack.academic.repository.AssignmentRepository;
import ru.rutcampustrack.academic.repository.AssignmentReplacementOperationRepository;
import ru.rutcampustrack.academic.repository.SemesterRepository;
import ru.rutcampustrack.academic.repository.SubjectLessonTypeRepository;
import ru.rutcampustrack.academic.repository.UserRepository;
import ru.rutcampustrack.academic.repository.UserRoleGrantRepository;
import ru.rutcampustrack.academic.security.RequestContext;
import ru.rutcampustrack.schedule.grpc.AssignmentCloseReceipt;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * Coordinates the durable Academic half of assignment replacement.
 *
 * <p>Academic transactions never hold a database lock while calling Schedule.
 * The operation row and the prepared target assignment make retries safe after
 * either side has committed.</p>
 */
@Service
public class AssignmentReplacementService {

    private static final Collection<String> OPEN_STATES = List.of("PREPARED", "APPLIED");

    private final AssignmentRepository assignmentRepository;
    private final AssignmentReplacementOperationRepository operationRepository;
    private final AssignmentAuthority assignmentAuthority;
    private final SemesterRepository semesterRepository;
    private final SubjectLessonTypeRepository lessonTypeRepository;
    private final UserRepository userRepository;
    private final UserRoleGrantRepository grantRepository;
    private final RequestContext requestContext;
    private final ScheduleGrpcClient scheduleGrpcClient;
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transaction;

    public AssignmentReplacementService(AssignmentRepository assignmentRepository,
                                        AssignmentReplacementOperationRepository operationRepository,
                                        AssignmentAuthority assignmentAuthority,
                                        SemesterRepository semesterRepository,
                                        SubjectLessonTypeRepository lessonTypeRepository,
                                        UserRepository userRepository,
                                        UserRoleGrantRepository grantRepository,
                                        RequestContext requestContext,
                                        ScheduleGrpcClient scheduleGrpcClient,
                                        JdbcTemplate jdbc,
                                        PlatformTransactionManager transactionManager) {
        this.assignmentRepository = assignmentRepository;
        this.operationRepository = operationRepository;
        this.assignmentAuthority = assignmentAuthority;
        this.semesterRepository = semesterRepository;
        this.lessonTypeRepository = lessonTypeRepository;
        this.userRepository = userRepository;
        this.grantRepository = grantRepository;
        this.requestContext = requestContext;
        this.scheduleGrpcClient = scheduleGrpcClient;
        this.jdbc = jdbc;
        this.transaction = new TransactionTemplate(transactionManager);
    }

    public AssignmentReplacementResponse replace(Long sourceAssignmentId,
                                                ReplaceAssignmentRequest request) {
        long actorId = requireActor();
        UUID requestKey = request.requestKey();
        byte[] payloadHash = payloadHash(sourceAssignmentId, request);
        AssignmentReplacementOperation operation = transaction.execute(status ->
                prepare(actorId, requestKey, payloadHash, sourceAssignmentId, request));
        if (operation == null) {
            throw new ConflictException("Не удалось подготовить замену назначения");
        }

        if ("COMMITTED".equals(operation.getState())) {
            return status(operation.getOperationId());
        }
        if ("PREPARED".equals(operation.getState())) {
            AssignmentCloseReceipt receipt = scheduleGrpcClient.installAssignmentCloseCap(
                    operation.getOperationId(), operation.getSourceAssignmentId(),
                    operation.getTargetAssignmentId(), operation.getEffectiveFrom(),
                    payloadHash);
            requireReceipt(operation, receipt, "APPLIED");
            UUID operationId = operation.getOperationId();
            transaction.executeWithoutResult(tx -> activateAcademic(operationId, receipt));
            operation = operationRepository.findById(operationId).orElseThrow();
        }
        if ("APPLIED".equals(operation.getState())) {
            UUID operationId = operation.getOperationId();
            AssignmentCloseReceipt receipt = scheduleGrpcClient.commitAssignmentClose(
                    operationId, payloadHash);
            requireReceipt(operation, receipt, "COMMITTED");
            transaction.executeWithoutResult(tx -> markCommitted(operationId, receipt));
        }
        return status(operation.getOperationId());
    }

    public AssignmentReplacementResponse getStatus(UUID operationId) {
        if (operationId == null) {
            throw new BadRequestException("operationId", "Идентификатор операции обязателен");
        }
        return status(operationId);
    }

    private AssignmentReplacementOperation prepare(long actorId,
                                                   UUID requestKey,
                                                   byte[] payloadHash,
                                                   Long sourceAssignmentId,
                                                   ReplaceAssignmentRequest request) {
        AssignmentReplacementOperation existing = operationRepository
                .findByActorIdAndRequestKey(actorId, requestKey).orElse(null);
        if (existing != null) {
            if (!MessageDigest.isEqual(existing.getPayloadHash(), payloadHash)) {
                throw new ConflictException("Idempotency-Key уже использован для другой замены");
            }
            return existing;
        }
        requireHeadman();
        if (sourceAssignmentId == null || sourceAssignmentId <= 0) {
            throw new BadRequestException("id", "Идентификатор назначения должен быть положительным");
        }
        Assignment beforeLock = assignmentRepository.findById(sourceAssignmentId)
                .orElseThrow(() -> new ResourceNotFoundException("Assignment", "id", sourceAssignmentId));
        Semester semester = semesterRepository.findByIdForUpdate(beforeLock.getSemesterId())
                .orElseThrow(() -> new ResourceNotFoundException("Semester", "id", beforeLock.getSemesterId()));
        // A concurrent identical request can commit while this request waits
        // for the semester fence. Re-read the actor/key ledger before making a
        // second target assignment so the loser replays the same operation.
        existing = operationRepository.findByActorIdAndRequestKey(actorId, requestKey).orElse(null);
        if (existing != null) {
            if (!MessageDigest.isEqual(existing.getPayloadHash(), payloadHash)) {
                throw new ConflictException("Idempotency-Key уже использован для другой замены");
            }
            return existing;
        }
        Assignment source = assignmentRepository.findByIdForUpdate(sourceAssignmentId)
                .orElseThrow(() -> new ResourceNotFoundException("Assignment", "id", sourceAssignmentId));
        assertOwnGroup(source.getGroupId());
        if (!"ACTIVE".equals(source.getLifecycleState())) {
            throw new ConflictException("Исходное назначение уже закрывается или закрыто");
        }
        SubjectType lessonType = source.getLessonType();
        if (!lessonTypeRepository.existsBySubjectIdAndLessonType(source.getSubjectId(), lessonType)) {
            throw new BadRequestException("lessonType", "Тип занятия не разрешён для предмета");
        }
        LocalDate sourceEnd = AssignmentAuthority.effectiveEnd(source, semester);
        LocalDate effectiveFrom = request.effectiveFrom();
        if (effectiveFrom.isBefore(source.getValidFrom()) || effectiveFrom.isAfter(sourceEnd)) {
            throw new BadRequestException("effectiveFrom", "Дата замены должна входить в период назначения");
        }
        LocalDate targetEnd = sourceEnd;
        if (!effectiveFrom.isBefore(targetEnd)) {
            throw new BadRequestException("effectiveFrom", "После даты замены должен оставаться период назначения");
        }
        long targetTeacherId = parsePositive(request.replacementTeacherId(), "replacementTeacherId");
        if (targetTeacherId == source.getTeacherId()) {
            throw new ConflictException("Новый преподаватель совпадает с исходным");
        }
        User targetTeacher = userRepository.findById(targetTeacherId)
                .orElseThrow(() -> new ResourceNotFoundException("Teacher", "id", targetTeacherId));
        if (grantRepository.findByUserIdAndRoleAndStatus(targetTeacher.getId(),
                AssignmentAuthority.TEACHER_ROLE, AssignmentAuthority.ACTIVE_STATUS).isEmpty()) {
            throw new AccessDeniedException("У нового преподавателя нет активного права TEACHER");
        }
        assignmentRepository.findActiveOverlapping(targetTeacherId, source.getSubjectId(),
                        source.getGroupId(), source.getSemesterId(), lessonType.name().toLowerCase(),
                effectiveFrom, targetEnd)
                .ifPresent(a -> { throw new ConflictException("Новое назначение пересекается с существующим"); });
        operationRepository.findFirstBySourceAssignmentIdAndStateIn(source.getId(), OPEN_STATES)
                .ifPresent(open -> { throw new ConflictException("Для назначения уже выполняется замена"); });
        operationRepository.findFirstByTargetAssignmentIdAndStateIn(source.getId(), OPEN_STATES)
                .ifPresent(open -> { throw new ConflictException(
                        "Назначение ожидает завершения предыдущей замены"); });

        Assignment target = assignmentRepository.saveAndFlush(new Assignment(
                targetTeacherId, source.getSubjectId(), source.getGroupId(), source.getSemesterId(),
                lessonType, effectiveFrom, targetEnd, "PREPARED"));
        AssignmentReplacementOperation operation = new AssignmentReplacementOperation(
                UUID.randomUUID(), actorId, requestKey, payloadHash, source.getId(), target.getId(),
                effectiveFrom, effectiveFrom, targetEnd, "PREPARED");
        return operationRepository.saveAndFlush(operation);
    }

    private void activateAcademic(UUID operationId, AssignmentCloseReceipt receipt) {
        AssignmentReplacementOperation operationSnapshot = operationRepository.findById(operationId)
                .orElseThrow(() -> new ResourceNotFoundException("AssignmentReplacementOperation", "id", operationId));
        Assignment sourceSnapshot = assignmentRepository.findById(operationSnapshot.getSourceAssignmentId())
                .orElseThrow(() -> new ResourceNotFoundException("Assignment", "id",
                        operationSnapshot.getSourceAssignmentId()));
        Semester semester = semesterRepository.findByIdForUpdate(sourceSnapshot.getSemesterId())
                .orElseThrow(() -> new ResourceNotFoundException("Semester", "id", sourceSnapshot.getSemesterId()));
        List<Long> assignmentIds = List.of(operationSnapshot.getSourceAssignmentId(),
                        operationSnapshot.getTargetAssignmentId()).stream().sorted().toList();
        java.util.Map<Long, Assignment> lockedAssignments = new java.util.HashMap<>();
        for (Long assignmentId : assignmentIds) {
            Assignment assignment = assignmentRepository.findByIdForUpdate(assignmentId)
                    .orElseThrow(() -> new ResourceNotFoundException("Assignment", "id", assignmentId));
            lockedAssignments.put(assignmentId, assignment);
        }
        AssignmentReplacementOperation operation = operationRepository.findByIdForUpdate(operationId)
                .orElseThrow(() -> new ResourceNotFoundException("AssignmentReplacementOperation", "id", operationId));
        if ("COMMITTED".equals(operation.getState())) return;
        Assignment source = lockedAssignments.get(operation.getSourceAssignmentId());
        Assignment target = lockedAssignments.get(operation.getTargetAssignmentId());
        if ("APPLIED".equals(operation.getState())) {
            requireReceipt(operation, receipt, "APPLIED");
            assertReplacementTuple(operation, source, target, semester,
                    operation.getEffectiveFrom(), "ACTIVE");
            return;
        }
        if (!"PREPARED".equals(operation.getState())) {
            throw new ConflictException("Замена находится в неожиданном состоянии Academic");
        }
        requireReceipt(operation, receipt, "APPLIED");
        assertReplacementTuple(operation, source, target, semester,
                operation.getTargetValidUntil(), "PREPARED");
        operation.markApplied(receipt.getState(), receipt.getMovedCount(), receipt.getSkippedCount());
        operationRepository.saveAndFlush(operation);
        jdbc.queryForObject("SELECT set_config('rutcampustrack.assignment_replacement_operation_id', ?, true)",
                String.class, operationId.toString());
        if (jdbc.update("UPDATE assignments SET valid_until_exclusive = ? WHERE id = ?",
                operation.getEffectiveFrom(), operation.getSourceAssignmentId()) != 1
                || jdbc.update("UPDATE assignments SET lifecycle_state = 'ACTIVE' WHERE id = ?",
                        operation.getTargetAssignmentId()) != 1) {
            throw new ConflictException("Academic не смог активировать точное назначение замены");
        }
    }

    private void markCommitted(UUID operationId, AssignmentCloseReceipt receipt) {
        AssignmentReplacementOperation operationSnapshot = operationRepository.findById(operationId)
                .orElseThrow(() -> new ResourceNotFoundException("AssignmentReplacementOperation", "id", operationId));
        Assignment sourceSnapshot = assignmentRepository.findById(operationSnapshot.getSourceAssignmentId())
                .orElseThrow(() -> new ResourceNotFoundException("Assignment", "id",
                        operationSnapshot.getSourceAssignmentId()));
        Semester semester = semesterRepository.findByIdForUpdate(sourceSnapshot.getSemesterId())
                .orElseThrow(() -> new ResourceNotFoundException("Semester", "id", sourceSnapshot.getSemesterId()));
        List<Long> assignmentIds = List.of(operationSnapshot.getSourceAssignmentId(),
                        operationSnapshot.getTargetAssignmentId()).stream().sorted().toList();
        java.util.Map<Long, Assignment> lockedAssignments = new java.util.HashMap<>();
        for (Long assignmentId : assignmentIds) {
            Assignment assignment = assignmentRepository.findByIdForUpdate(assignmentId)
                    .orElseThrow(() -> new ResourceNotFoundException("Assignment", "id", assignmentId));
            lockedAssignments.put(assignmentId, assignment);
        }
        AssignmentReplacementOperation operation = operationRepository.findByIdForUpdate(operationId)
                .orElseThrow(() -> new ResourceNotFoundException("AssignmentReplacementOperation", "id", operationId));
        if ("COMMITTED".equals(operation.getState())) return;
        if (!"APPLIED".equals(operation.getState())) {
            throw new ConflictException("Замена ещё не подтверждена в Academic");
        }
        requireReceipt(operation, receipt, "COMMITTED");
        assertReplacementTuple(operation, lockedAssignments.get(operation.getSourceAssignmentId()),
                lockedAssignments.get(operation.getTargetAssignmentId()), semester,
                operation.getEffectiveFrom(), "ACTIVE");
        operation.markCommitted(receipt.getState(), receipt.getMovedCount(), receipt.getSkippedCount());
        operationRepository.save(operation);
    }

    private static void assertReplacementTuple(AssignmentReplacementOperation operation,
                                              Assignment source,
                                              Assignment target,
                                              Semester semester,
                                              LocalDate expectedSourceEnd,
                                              String expectedTargetState) {
        LocalDate sourceEnd = AssignmentAuthority.effectiveEnd(source, semester);
        if (!"ACTIVE".equals(source.getLifecycleState())
                || !expectedTargetState.equals(target.getLifecycleState())
                || !source.getGroupId().equals(target.getGroupId())
                || !source.getSubjectId().equals(target.getSubjectId())
                || !source.getSemesterId().equals(target.getSemesterId())
                || source.getLessonType() != target.getLessonType()
                || !target.getValidFrom().equals(operation.getEffectiveFrom())
                || !target.getValidUntilExclusive().equals(operation.getTargetValidUntil())
                || !sourceEnd.equals(expectedSourceEnd)) {
            throw new ConflictException("Academic assignment tuple changed during replacement");
        }
    }

    private AssignmentReplacementResponse status(UUID operationId) {
        return transaction.execute(status -> {
            AssignmentReplacementOperation operation = operationRepository.findById(operationId)
                    .orElseThrow(() -> new ResourceNotFoundException("AssignmentReplacementOperation", "id", operationId));
            if (requestContext.getRole() != UserRole.ADMIN
                    && !java.util.Objects.equals(requestContext.getUserId(), operation.getActorId())) {
                throw new AccessDeniedException("Операция замены принадлежит другому пользователю");
            }
            Assignment source = assignmentRepository.findById(operation.getSourceAssignmentId())
                    .orElseThrow(() -> new ResourceNotFoundException("Assignment", "id", operation.getSourceAssignmentId()));
            assertOwnGroup(source.getGroupId());
            Assignment target = assignmentRepository.findById(operation.getTargetAssignmentId())
                    .orElseThrow(() -> new ResourceNotFoundException("Assignment", "id", operation.getTargetAssignmentId()));
            return new AssignmentReplacementResponse(operation.getOperationId(), source.getId(), target.getId(),
                    source.getTeacherId(), target.getTeacherId(), source.getSubjectId(), source.getGroupId(),
                    source.getSemesterId(), source.getLessonType().name(), operation.getEffectiveFrom(),
                    operation.getSourceValidUntil(), operation.getTargetValidUntil(), operation.getState(),
                    operation.getScheduleReceiptState(),
                    operation.getScheduleMovedCount(), operation.getScheduleSkippedCount());
        });
    }

    private void requireHeadman() {
        if (!requestContext.isHeadman()) {
            throw new AccessDeniedException("Только староста может менять назначения преподавателей");
        }
    }

    private void assertOwnGroup(Long groupId) {
        if (requestContext.getRole() == UserRole.ADMIN) return;
        if (requestContext.getGroupId() == null || !requestContext.getGroupId().equals(groupId)) {
            throw new AccessDeniedException("Назначение принадлежит другой группе");
        }
    }

    private long requireActor() {
        Long actorId = requestContext.getUserId();
        if (actorId == null || actorId <= 0) throw new AccessDeniedException("Пользователь не определён");
        return actorId;
    }

    private static long parsePositive(String value, String field) {
        try {
            long parsed = Long.parseLong(value);
            if (parsed > 0) return parsed;
        } catch (RuntimeException ignored) { }
        throw new BadRequestException(field, "ID должен быть положительным");
    }

    private static void requireReceipt(AssignmentReplacementOperation operation,
                                       AssignmentCloseReceipt receipt,
                                       String expectedState) {
        if (receipt == null || !operation.getOperationId().toString().equals(receipt.getOperationId())
                || !expectedState.equals(receipt.getState())
                || receipt.getMovedCount() < 0 || receipt.getSkippedCount() < 0) {
            throw new ConflictException("Schedule не вернул подтверждение точной операции замены");
        }
        if ("APPLIED".equals(expectedState)
                && (receipt.getMovedCount() != 0 || receipt.getSkippedCount() != 0)) {
            throw new ConflictException("Schedule barrier receipt должен иметь нулевые счётчики");
        }
    }

    private static byte[] payloadHash(Long sourceId, ReplaceAssignmentRequest request) {
        String canonical = sourceId + "|" + request.replacementTeacherId() + "|"
                + request.effectiveFrom();
        try { return MessageDigest.getInstance("SHA-256").digest(canonical.getBytes(StandardCharsets.UTF_8)); }
        catch (NoSuchAlgorithmException ex) { throw new IllegalStateException("SHA-256 unavailable", ex); }
    }
}
