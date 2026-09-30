package ru.rutcampustrack.academic.semester;

import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.rutcampustrack.academic.contract.enums.SemesterArchiveAction;
import ru.rutcampustrack.academic.contract.enums.SemesterArchiveParticipantStatus;
import ru.rutcampustrack.academic.contract.enums.SemesterArchiveOperationState;
import ru.rutcampustrack.academic.contract.enums.SemesterArchiveParticipantCommand;
import ru.rutcampustrack.academic.contract.enums.SemesterTransition;
import ru.rutcampustrack.academic.entity.Semester;
import ru.rutcampustrack.academic.entity.SemesterArchiveOperation;
import ru.rutcampustrack.academic.event.SemesterArchiveParticipantCommandEvent;
import ru.rutcampustrack.academic.exception.ConflictException;
import ru.rutcampustrack.academic.repository.SemesterArchiveOperationRepository;

import java.sql.PreparedStatement;
import java.util.List;
import java.util.UUID;

/** Commits the replay identity and authoritative transition together before any participant RPC. */
@Service
public class SemesterArchiveCommandTransaction {

    private static final int IDEMPOTENCY_LOCK_NAMESPACE = 0x534152;

    private final JdbcTemplate jdbcTemplate;
    private final SemesterArchiveOperationRepository operationRepository;
    private final SemesterService semesterService;
    private final AcademicSemesterArchiveBarrierTransaction academicBarrier;
    private final ApplicationEventPublisher eventPublisher;

    public SemesterArchiveCommandTransaction(JdbcTemplate jdbcTemplate,
                                             SemesterArchiveOperationRepository operationRepository,
                                             SemesterService semesterService,
                                             AcademicSemesterArchiveBarrierTransaction academicBarrier,
                                             ApplicationEventPublisher eventPublisher) {
        this.jdbcTemplate = jdbcTemplate;
        this.operationRepository = operationRepository;
        this.semesterService = semesterService;
        this.academicBarrier = academicBarrier;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public SemesterArchiveOperation startOrReplay(long semesterId,
                                                   long actorId,
                                                   UUID idempotencyKey,
                                                   SemesterArchiveAction action) {
        lockRequestKey(idempotencyKey);
        SemesterArchiveOperation existing = operationRepository.findByIdempotencyKey(idempotencyKey)
                .orElse(null);
        if (existing != null) {
            if (existing.getActorId() != actorId
                    || existing.getSemesterId() != semesterId
                    || existing.getAction() != action) {
                throw new ConflictException("idempotencyKey", idempotencyKey,
                        "Ключ идемпотентности уже закреплён за другой командой");
            }
            if (existing.getOperationState()
                    != ru.rutcampustrack.academic.contract.enums.SemesterArchiveOperationState.COMPLETED
                    && existing.isRetryable()) {
                publishParticipantCommand(existing, retryCommand(existing));
            }
            return existing;
        }

        operationRepository.findTopBySemesterIdOrderByCreatedAtDescOperationIdDesc(semesterId)
                .filter(operation -> operation.getOperationState()
                        != ru.rutcampustrack.academic.contract.enums.SemesterArchiveOperationState.COMPLETED)
                .ifPresent(operation -> {
                    throw new ConflictException("status", semesterId,
                            "Предыдущая команда архивации ещё не завершена");
                });

        Semester before = semesterService.findSemesterByIdUncached(semesterId)
                .orElseThrow(() -> new ru.rutcampustrack.academic.contract.exception.ResourceNotFoundException(
                        "Semester", "id", semesterId));
        if (action == SemesterArchiveAction.ARCHIVE
                && (before.isArchived() || before.getArchiveTransition() != SemesterTransition.NONE
                || before.isReleasePending())) {
            throw new ConflictException("status", semesterId,
                    "Семестр уже архивирован или участвует в другом переходе");
        }
        if (action == SemesterArchiveAction.RESTORE
                && (!before.isArchived() || before.isActive() || before.isReleasePending()
                || before.getArchiveTransition() != SemesterTransition.NONE)) {
            throw new ConflictException("status", semesterId,
                    "Восстановить можно только завершённый архивный семестр");
        }
        Semester semester = action == SemesterArchiveAction.ARCHIVE
                ? semesterService.beginArchiveTransition(semesterId)
                : semesterService.beginRestoreTransition(semesterId);

        SemesterArchiveOperation operation = new SemesterArchiveOperation(
                UUID.randomUUID(), idempotencyKey, actorId, semesterId, action);
        copyAuthoritySnapshot(operation, semester);
        academicBarrier.install(operation.getOperationId(), semesterId,
                operation.getStateVersion(), action);
        operation.setAcademic(action == SemesterArchiveAction.ARCHIVE
                ? SemesterArchiveParticipantStatus.PENDING
                : SemesterArchiveParticipantStatus.PREPARED_RESTORE);
        operation.setSchedule(SemesterArchiveParticipantStatus.PENDING);
        operation.setAttendance(SemesterArchiveParticipantStatus.PENDING);
        SemesterArchiveOperation saved = operationRepository.saveAndFlush(operation);
        publishParticipantCommand(saved, initialCommand(action));
        return saved;
    }

    @Transactional
    public void enqueueRestoreRelease(UUID operationId) {
        SemesterArchiveOperation operation = operationRepository.findByIdForUpdate(operationId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown archive operation " + operationId));
        if (operation.getAction() != SemesterArchiveAction.RESTORE
                || !operation.isReleasePending()) {
            throw new ConflictException("status", operationId,
                    "Команда release требует завершённого центрального перехода восстановления");
        }
        publishParticipantCommand(operation, SemesterArchiveParticipantCommand.RELEASE_RESTORE);
    }

    @Transactional
    public SemesterArchiveOperation findForUpdate(UUID operationId) {
        return operationRepository.findByIdForUpdate(operationId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown archive operation " + operationId));
    }

    @Transactional
    public SemesterArchiveOperation saveProgress(SemesterArchiveOperation operation) {
        return operationRepository.saveAndFlush(operation);
    }

    @Transactional
    public SemesterArchiveOperation recordParticipant(UUID operationId,
                                                       Participant participant,
                                                       SemesterArchiveParticipantStatus status,
                                                       String blockingReason) {
        SemesterArchiveOperation operation = operationRepository.findByIdForUpdate(operationId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown archive operation " + operationId));
        if (operation.getOperationState() == SemesterArchiveOperationState.COMPLETED) return operation;
        SemesterArchiveParticipantStatus current = participant.get(operation);
        if (!isAllowed(operation.getAction(), current, status)) {
            throw new ConflictException("status", operationId,
                    "Участник архивации прислал неподходящее состояние перехода");
        }
        if (participant.rank(operation.getAction(), status)
                >= participant.rank(operation.getAction(), current)) {
            participant.set(operation, status);
        }
        if (blockingReason != null && !blockingReason.isBlank()) {
            operation.setBlockingReason(blockingReason);
        } else if (status == participant.get(operation)) {
            operation.setBlockingReason(null);
        }
        operation.setOperationState(SemesterArchiveOperationState.PENDING);
        operation.setRetryable(true);
        return operationRepository.saveAndFlush(operation);
    }

    @Transactional
    public SemesterArchiveOperation recordAttendanceAcknowledgement(
            UUID operationId, long semesterId, long stateVersion,
            SemesterArchiveParticipantCommand command,
            SemesterArchiveParticipantStatus status, String blockingReason) {
        SemesterArchiveOperation operation = operationRepository.findByIdForUpdate(operationId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown archive operation " + operationId));
        if (operation.getSemesterId() != semesterId || operation.getStateVersion() != stateVersion) {
            throw new ConflictException("stateVersion", operationId,
                    "Attendance прислал receipt для другой версии операции");
        }
        if (operation.getOperationState() == SemesterArchiveOperationState.COMPLETED) return operation;

        boolean allowed;
        if (operation.getAction() == SemesterArchiveAction.ARCHIVE) {
            allowed = command == SemesterArchiveParticipantCommand.PREPARE_ARCHIVE
                    && status == SemesterArchiveParticipantStatus.PENDING
                    || command == SemesterArchiveParticipantCommand.SEAL_ARCHIVE
                    && operation.getSchedule() == SemesterArchiveParticipantStatus.READY
                    && operation.getAcademic() == SemesterArchiveParticipantStatus.READY
                    && (status == SemesterArchiveParticipantStatus.PENDING
                    || status == SemesterArchiveParticipantStatus.READY);
        } else {
            allowed = command == SemesterArchiveParticipantCommand.PREPARE_RESTORE
                    && !operation.isReleasePending()
                    && (status == SemesterArchiveParticipantStatus.PENDING
                    || status == SemesterArchiveParticipantStatus.PREPARED_RESTORE)
                    || command == SemesterArchiveParticipantCommand.RELEASE_RESTORE
                    && operation.isReleasePending()
                    && (status == SemesterArchiveParticipantStatus.PENDING
                    || status == SemesterArchiveParticipantStatus.RELEASED);
        }
        if (!allowed) {
            // A delayed prepare receipt cannot regress a later seal/release epoch.
            if (operation.getAction() == SemesterArchiveAction.ARCHIVE
                    && command == SemesterArchiveParticipantCommand.PREPARE_ARCHIVE
                    && operation.getAttendance() == SemesterArchiveParticipantStatus.READY
                    || operation.getAction() == SemesterArchiveAction.RESTORE
                    && command == SemesterArchiveParticipantCommand.PREPARE_RESTORE
                    && operation.isReleasePending()) {
                return operation;
            }
            throw new ConflictException("status", operationId,
                    "Attendance прислал receipt для недопустимой команды или фазы");
        }
        SemesterArchiveParticipantStatus current = operation.getAttendance();
        if (Participant.ATTENDANCE.rank(operation.getAction(), status)
                >= Participant.ATTENDANCE.rank(operation.getAction(), current)) {
            operation.setAttendance(status);
        }
        if (blockingReason != null && !blockingReason.isBlank()) {
            operation.setBlockingReason(normalizeReason(blockingReason));
        } else if (status == operation.getAttendance()) {
            operation.setBlockingReason(null);
        }
        operation.setOperationState(SemesterArchiveOperationState.PENDING);
        operation.setRetryable(true);
        return operationRepository.saveAndFlush(operation);
    }

    @Transactional
    public SemesterArchiveOperation markRetrying(UUID operationId) {
        SemesterArchiveOperation operation = operationRepository.findByIdForUpdate(operationId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown archive operation " + operationId));
        if (operation.getOperationState() != SemesterArchiveOperationState.COMPLETED) {
            operation.setOperationState(SemesterArchiveOperationState.PENDING);
            operation.setRetryable(true);
            operation.setBlockingReason(null);
            operationRepository.saveAndFlush(operation);
        }
        return operation;
    }

    @Transactional
    public SemesterArchiveOperation markRetryableError(UUID operationId, String reason) {
        SemesterArchiveOperation operation = operationRepository.findByIdForUpdate(operationId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown archive operation " + operationId));
        if (operation.getOperationState() != SemesterArchiveOperationState.COMPLETED) {
            operation.setOperationState(SemesterArchiveOperationState.ERROR);
            operation.setRetryable(true);
            operation.setBlockingReason(normalizeReason(reason));
            operationRepository.saveAndFlush(operation);
        }
        return operation;
    }

    @Transactional
    public void enqueueParticipantCommand(UUID operationId, SemesterArchiveParticipantCommand command) {
        SemesterArchiveOperation operation = operationRepository.findByIdForUpdate(operationId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown archive operation " + operationId));
        if (operation.getOperationState() != SemesterArchiveOperationState.COMPLETED) {
            publishParticipantCommand(operation, command);
        }
    }

    @Transactional
    public SemesterArchiveOperation completeArchive(UUID operationId) {
        SemesterArchiveOperation operation = operationRepository.findByIdForUpdate(operationId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown archive operation " + operationId));
        if (operation.getOperationState() == SemesterArchiveOperationState.COMPLETED) return operation;
        if (operation.getAction() != SemesterArchiveAction.ARCHIVE
                || operation.getSchedule() != SemesterArchiveParticipantStatus.READY
                || operation.getAcademic() != SemesterArchiveParticipantStatus.READY
                || operation.getAttendance() != SemesterArchiveParticipantStatus.READY) {
            throw new ConflictException("status", operationId,
                    "Архивация завершает только после трёх READY receipts");
        }
        Semester semester = semesterService.completeArchiveTransition(
                operation.getSemesterId(), operation.getStateVersion());
        copyAuthoritySnapshot(operation, semester);
        operation.setOperationState(SemesterArchiveOperationState.COMPLETED);
        operation.setRetryable(false);
        operation.setBlockingReason(null);
        return operationRepository.saveAndFlush(operation);
    }

    /** Central restore completion and Attendance release command commit together. */
    @Transactional
    public SemesterArchiveOperation beginRestoreRelease(UUID operationId) {
        SemesterArchiveOperation operation = operationRepository.findByIdForUpdate(operationId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown archive operation " + operationId));
        if (operation.getAction() != SemesterArchiveAction.RESTORE) {
            throw new ConflictException("status", operationId, "Это не команда восстановления");
        }
        if (operation.isReleasePending()) return operation;
        if (operation.getSchedule() != SemesterArchiveParticipantStatus.PREPARED_RESTORE
                || operation.getAcademic() != SemesterArchiveParticipantStatus.PREPARED_RESTORE
                || operation.getAttendance() != SemesterArchiveParticipantStatus.PREPARED_RESTORE) {
            throw new ConflictException("status", operationId,
                    "Восстановление требует трёх PREPARED_RESTORE receipts");
        }
        Semester semester = semesterService.completeRestoreTransition(
                operation.getSemesterId(), operation.getStateVersion());
        copyAuthoritySnapshot(operation, semester);
        operation.setSchedule(SemesterArchiveParticipantStatus.RELEASE_PENDING);
        operation.setAcademic(SemesterArchiveParticipantStatus.RELEASE_PENDING);
        operation.setAttendance(SemesterArchiveParticipantStatus.RELEASE_PENDING);
        operation.setOperationState(SemesterArchiveOperationState.PENDING);
        operation.setRetryable(true);
        operation.setBlockingReason(null);
        SemesterArchiveOperation saved = operationRepository.saveAndFlush(operation);
        publishParticipantCommand(saved, SemesterArchiveParticipantCommand.RELEASE_RESTORE);
        return saved;
    }

    @Transactional
    public SemesterArchiveOperation completeRestoreRelease(UUID operationId) {
        SemesterArchiveOperation operation = operationRepository.findByIdForUpdate(operationId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown archive operation " + operationId));
        if (operation.getOperationState() == SemesterArchiveOperationState.COMPLETED) return operation;
        if (operation.getAction() != SemesterArchiveAction.RESTORE
                || operation.getSchedule() != SemesterArchiveParticipantStatus.RELEASED
                || operation.getAcademic() != SemesterArchiveParticipantStatus.RELEASED
                || operation.getAttendance() != SemesterArchiveParticipantStatus.RELEASED) {
            throw new ConflictException("status", operationId,
                    "Restore gate освобождается только после трёх RELEASED receipts");
        }
        Semester semester = semesterService.completeRestoreRelease(
                operation.getSemesterId(), operation.getStateVersion());
        copyAuthoritySnapshot(operation, semester);
        operation.setOperationState(SemesterArchiveOperationState.COMPLETED);
        operation.setRetryable(false);
        operation.setBlockingReason(null);
        return operationRepository.saveAndFlush(operation);
    }

    @Transactional
    public SemesterArchiveOperation find(UUID operationId) {
        return operationRepository.findById(operationId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown archive operation " + operationId));
    }

    @Transactional(readOnly = true)
    public List<UUID> retryableOperationIds() {
        return operationRepository.findTop8ByRetryableTrueAndOperationStateInOrderByUpdatedAtAsc(
                        List.of(SemesterArchiveOperationState.PENDING, SemesterArchiveOperationState.ERROR))
                .stream().map(SemesterArchiveOperation::getOperationId).toList();
    }

    private void lockRequestKey(UUID key) {
        int first = (int) (key.getMostSignificantBits() ^ (key.getMostSignificantBits() >>> 32));
        int second = (int) (key.getLeastSignificantBits() ^ (key.getLeastSignificantBits() >>> 32));
        jdbcTemplate.execute((ConnectionCallback<Void>) connection -> {
            try (PreparedStatement statement = connection.prepareStatement("SELECT pg_advisory_xact_lock(?, ?)")) {
                statement.setInt(1, IDEMPOTENCY_LOCK_NAMESPACE);
                statement.setInt(2, first ^ second);
                statement.execute();
            }
            return null;
        });
    }

    private static void copyAuthoritySnapshot(SemesterArchiveOperation operation, Semester semester) {
        operation.setStateVersion(semester.getStateVersion());
        operation.setTransition(semester.getArchiveTransition());
        operation.setActive(semester.isActive());
        operation.setArchived(semester.isArchived());
        operation.setReleasePending(semester.isReleasePending());
    }

    private void publishParticipantCommand(SemesterArchiveOperation operation,
                                           SemesterArchiveParticipantCommand command) {
        eventPublisher.publishEvent(new SemesterArchiveParticipantCommandEvent(
                operation.getOperationId(), operation.getSemesterId(),
                operation.getStateVersion(), command));
    }

    private static SemesterArchiveParticipantCommand initialCommand(SemesterArchiveAction action) {
        return action == SemesterArchiveAction.ARCHIVE
                ? SemesterArchiveParticipantCommand.PREPARE_ARCHIVE
                : SemesterArchiveParticipantCommand.PREPARE_RESTORE;
    }

    private static SemesterArchiveParticipantCommand retryCommand(SemesterArchiveOperation operation) {
        if (operation.getAction() == SemesterArchiveAction.RESTORE && operation.isReleasePending()) {
            return SemesterArchiveParticipantCommand.RELEASE_RESTORE;
        }
        return initialCommand(operation.getAction());
    }

    private static boolean isAllowed(SemesterArchiveAction action,
                                     SemesterArchiveParticipantStatus current,
                                     SemesterArchiveParticipantStatus next) {
        if (action == SemesterArchiveAction.ARCHIVE) {
            return next == SemesterArchiveParticipantStatus.PENDING
                    || next == SemesterArchiveParticipantStatus.READY;
        }
        return next == SemesterArchiveParticipantStatus.PENDING
                || next == SemesterArchiveParticipantStatus.PREPARED_RESTORE
                || next == SemesterArchiveParticipantStatus.RELEASE_PENDING
                || next == SemesterArchiveParticipantStatus.RELEASED;
    }

    private static String normalizeReason(String reason) {
        if (reason == null || reason.isBlank()) return "Участник архивации временно недоступен";
        String trimmed = reason.trim();
        return trimmed.length() <= 1000 ? trimmed : trimmed.substring(0, 1000);
    }

    public enum Participant {
        ACADEMIC {
            @Override SemesterArchiveParticipantStatus get(SemesterArchiveOperation operation) { return operation.getAcademic(); }
            @Override void set(SemesterArchiveOperation operation, SemesterArchiveParticipantStatus status) { operation.setAcademic(status); }
        },
        SCHEDULE {
            @Override SemesterArchiveParticipantStatus get(SemesterArchiveOperation operation) { return operation.getSchedule(); }
            @Override void set(SemesterArchiveOperation operation, SemesterArchiveParticipantStatus status) { operation.setSchedule(status); }
        },
        ATTENDANCE {
            @Override SemesterArchiveParticipantStatus get(SemesterArchiveOperation operation) { return operation.getAttendance(); }
            @Override void set(SemesterArchiveOperation operation, SemesterArchiveParticipantStatus status) { operation.setAttendance(status); }
        };
        abstract SemesterArchiveParticipantStatus get(SemesterArchiveOperation operation);
        abstract void set(SemesterArchiveOperation operation, SemesterArchiveParticipantStatus status);
        int rank(SemesterArchiveAction action, SemesterArchiveParticipantStatus status) {
            if (action == SemesterArchiveAction.ARCHIVE) {
                return switch (status) {
                    case NOT_STARTED -> 0;
                    case PENDING -> 1;
                    case READY -> 2;
                    default -> -1;
                };
            }
            return switch (status) {
                case NOT_STARTED -> 0;
                case PENDING -> 1;
                case PREPARED_RESTORE -> 2;
                case RELEASE_PENDING -> 3;
                case RELEASED -> 4;
                default -> -1;
            };
        }
    }
}
