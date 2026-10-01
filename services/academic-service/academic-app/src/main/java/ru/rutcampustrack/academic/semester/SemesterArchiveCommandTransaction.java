package ru.rutcampustrack.academic.semester;

import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.cache.annotation.CacheEvict;
import ru.rutcampustrack.academic.contract.dto.semester.SemesterDeletionCounts;
import ru.rutcampustrack.academic.contract.enums.SemesterDeletionPhase;
import ru.rutcampustrack.academic.contract.enums.SemesterDeletionPriorState;
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
import java.time.OffsetDateTime;

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
    public SemesterArchiveOperation startDelete(long semesterId, long actorId, UUID idempotencyKey,
                                                UUID operationId,
                                                SemesterDeletionPreviewService.Snapshot preview) {
        lockRequestKey(idempotencyKey);
        SemesterArchiveOperation existing = operationRepository.findByIdempotencyKey(idempotencyKey)
                .orElse(null);
        if (existing != null) {
            requireSameDeleteRequest(existing, semesterId, actorId, preview.digest());
            return existing;
        }
        semesterService.lockDeletionCandidate(semesterId, preview.stateVersion(), preview.priorState());
        operationRepository.findTopBySemesterIdOrderByCreatedAtDescOperationIdDesc(semesterId)
                .filter(operation -> operation.getOperationState() != SemesterArchiveOperationState.COMPLETED)
                .ifPresent(operation -> {
                    throw new ConflictException("status", semesterId,
                            "Предыдущая команда перехода семестра ещё не завершена");
                });

        SemesterArchiveOperation operation = new SemesterArchiveOperation(
                operationId, idempotencyKey, actorId, semesterId, SemesterArchiveAction.DELETE);
        operation.setStateVersion(Math.addExact(preview.stateVersion(), 1L));
        operation.setTransition(SemesterTransition.DELETING);
        operation.setActive(false);
        operation.setArchived(preview.priorState() == SemesterDeletionPriorState.ARCHIVED);
        operation.setReleasePending(false);
        operation.setDeletePhase(SemesterDeletionPhase.PREPARING);
        operation.setPriorState(preview.priorState());
        operation.setSemesterName(preview.semesterName());
        operation.setOriginalStateVersion(preview.stateVersion());
        operation.setPreviewDigest(preview.digest());
        operation.setAcademicParticipantDigest(preview.academicDigest());
        operation.setScheduleParticipantDigest(preview.scheduleDigest());
        operation.setAttendanceParticipantDigest(preview.attendanceDigest());
        operation.setDeletionCounts(preview.counts());
        operation.setPrepareExpiresAt(OffsetDateTime.now().plusMinutes(2));
        operation.setAcademic(SemesterArchiveParticipantStatus.PENDING);
        operation.setSchedule(SemesterArchiveParticipantStatus.PENDING);
        operation.setAttendance(SemesterArchiveParticipantStatus.PENDING);
        SemesterArchiveOperation saved = operationRepository.saveAndFlush(operation);
        academicBarrier.installDeletion(operationId, semesterId, operation.getStateVersion());
        semesterService.beginDeletionTransition(semesterId, preview.stateVersion(), operationId);
        publishParticipantCommand(saved, SemesterArchiveParticipantCommand.PREPARE_DELETE);
        return saved;
    }

    private static void requireSameDeleteRequest(SemesterArchiveOperation operation, long semesterId,
                                                 long actorId, String previewDigest) {
        if (operation.getAction() != SemesterArchiveAction.DELETE
                || operation.getSemesterId() != semesterId || operation.getActorId() != actorId
                || !previewDigest.equals(operation.getPreviewDigest())) {
            throw new ConflictException("idempotencyKey", operation.getIdempotencyKey(),
                    "Ключ идемпотентности уже закреплён за другой командой удаления");
        }
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

        if (operation.getAction() == SemesterArchiveAction.DELETE) {
            throw new ConflictException("Удаление требует ACK с точным digest и participant counts");
        }

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
    public SemesterArchiveOperation recordDeleteParticipant(UUID operationId, long semesterId, long stateVersion,
                                                             Participant participant,
                                                             SemesterArchiveParticipantCommand command,
                                                             SemesterArchiveParticipantStatus status,
                                                             String blockingReason, String participantDigest,
                                                             SemesterDeletionCounts counts) {
        SemesterArchiveOperation operation = operationRepository.findByIdForUpdate(operationId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown archive operation " + operationId));
        if (operation.getAction() != SemesterArchiveAction.DELETE
                || operation.getSemesterId() != semesterId || operation.getStateVersion() != stateVersion) {
            throw new ConflictException("stateVersion", operationId,
                    "Participant deletion receipt does not match the operation identity");
        }
        if (operation.getOperationState() == SemesterArchiveOperationState.COMPLETED) return operation;
        requireDeleteReceiptStatus(command, status);
        if (participantDigest == null || !participantDigest.matches("[0-9a-fA-F]{64}") || counts == null
                || counts.scheduleTemplates() < 0 || counts.oneOffLessons() < 0 || counts.lessons() < 0
                || counts.assignments() < 0 || counts.homeworks() < 0 || counts.attendanceMarks() < 0
                || counts.studentRequests() < 0) {
            throw new ConflictException("Participant deletion receipt has invalid digest or counts");
        }
        validateParticipantCounts(participant, counts);
        if ((operation.getDeletePhase() == SemesterDeletionPhase.RELEASING
                || operation.getDeletePhase() == SemesterDeletionPhase.DELETING)
                && (command == SemesterArchiveParticipantCommand.PREPARE_DELETE
                || command == SemesterArchiveParticipantCommand.SEAL_DELETE)) {
            // Trusted exact receipts from the superseded preparation must not regress the current phase.
            return operation;
        }
        requireDeleteReceiptPhase(operation, command);
        if (participant == Participant.ATTENDANCE
                && command == SemesterArchiveParticipantCommand.SEAL_DELETE
                && status == SemesterArchiveParticipantStatus.PENDING
                && "ATTENDANCE_DELETE_PREVIEW_CHANGED".equals(blockingReason)
                && operation.getDeletePhase() == SemesterDeletionPhase.PREPARING
                && !operation.isIrreversibleIntent()) {
            if (operation.getAttendanceParticipantDigest().equalsIgnoreCase(participantDigest)) {
                throw new ConflictException("Attendance stale preview receipt must report a changed digest");
            }
            return beginDeleteRelease(operationId, "STALE_PREVIEW");
        }
        if (command == SemesterArchiveParticipantCommand.COMMIT_DELETE) {
            String expectedDigest = switch (participant) {
                case ACADEMIC -> operation.getAcademicParticipantDigest();
                case SCHEDULE -> operation.getScheduleParticipantDigest();
                case ATTENDANCE -> operation.getAttendanceParticipantDigest();
            };
            SemesterDeletionCounts expectedCounts = switch (participant) {
                case ACADEMIC -> new SemesterDeletionCounts(0, 0, 0,
                        operation.getAssignmentsCount(), operation.getHomeworksCount(), 0, 0);
                case SCHEDULE -> new SemesterDeletionCounts(operation.getScheduleTemplatesCount(),
                        operation.getOneOffLessonsCount(), operation.getLessonsCount(), 0, 0, 0, 0);
                case ATTENDANCE -> new SemesterDeletionCounts(0, 0, 0, 0, 0,
                        operation.getAttendanceMarksCount(), operation.getStudentRequestsCount());
            };
            if (!expectedDigest.equals(participantDigest) || !expectedCounts.equals(counts)) {
                throw new ConflictException("COMMIT receipt differs from the sealed deletion snapshot");
            }
        }
        if (command == SemesterArchiveParticipantCommand.PREPARE_DELETE
                || command == SemesterArchiveParticipantCommand.SEAL_DELETE) {
            if (status == SemesterArchiveParticipantStatus.READY) {
                participant.set(operation, SemesterArchiveParticipantStatus.READY);
            }
            if (command == SemesterArchiveParticipantCommand.SEAL_DELETE
                    && status == SemesterArchiveParticipantStatus.READY) {
                setSealed(operation, participant, true);
            }
        } else if (command == SemesterArchiveParticipantCommand.RELEASE_DELETE) {
            if (status == SemesterArchiveParticipantStatus.RELEASED) {
                participant.set(operation, SemesterArchiveParticipantStatus.RELEASED);
            } else if (participant.get(operation) == SemesterArchiveParticipantStatus.NOT_STARTED) {
                participant.set(operation, SemesterArchiveParticipantStatus.RELEASE_PENDING);
            }
        } else if (command == SemesterArchiveParticipantCommand.COMMIT_DELETE) {
            if (status == SemesterArchiveParticipantStatus.DELETED) {
                participant.set(operation, SemesterArchiveParticipantStatus.DELETED);
            }
        }
        if (blockingReason != null && !blockingReason.isBlank()) {
            operation.setBlockingReason(normalizeReason(blockingReason));
        } else if (status != SemesterArchiveParticipantStatus.PENDING) {
            operation.setBlockingReason(null);
        }
        operation.setOperationState(SemesterArchiveOperationState.PENDING);
        operation.setRetryable(true);
        return operationRepository.saveAndFlush(operation);
    }

    private static void requireDeleteReceiptStatus(SemesterArchiveParticipantCommand command,
                                                   SemesterArchiveParticipantStatus status) {
        boolean allowedStatus = switch (command) {
            case PREPARE_DELETE, SEAL_DELETE -> status == SemesterArchiveParticipantStatus.PENDING
                    || status == SemesterArchiveParticipantStatus.READY;
            case RELEASE_DELETE -> status == SemesterArchiveParticipantStatus.PENDING
                    || status == SemesterArchiveParticipantStatus.RELEASED;
            case COMMIT_DELETE -> status == SemesterArchiveParticipantStatus.PENDING
                    || status == SemesterArchiveParticipantStatus.DELETED;
            default -> false;
        };
        if (!allowedStatus) {
            throw new ConflictException("Participant deletion receipt has an invalid command or status");
        }
    }

    private static void requireDeleteReceiptPhase(SemesterArchiveOperation operation,
                                                  SemesterArchiveParticipantCommand command) {
        boolean allowedPhase = switch (operation.getDeletePhase()) {
            case PREPARING -> command == SemesterArchiveParticipantCommand.PREPARE_DELETE
                    || command == SemesterArchiveParticipantCommand.SEAL_DELETE;
            case RELEASING -> command == SemesterArchiveParticipantCommand.RELEASE_DELETE;
            case DELETING -> operation.isIrreversibleIntent()
                    && command == SemesterArchiveParticipantCommand.COMMIT_DELETE;
            default -> false;
        };
        if (!allowedPhase) {
            throw new ConflictException("Participant deletion receipt does not match the current operation phase");
        }
    }

    private static void validateParticipantCounts(Participant participant, SemesterDeletionCounts counts) {
        boolean invalid = switch (participant) {
            case ACADEMIC -> counts.scheduleTemplates() != 0 || counts.oneOffLessons() != 0
                    || counts.lessons() != 0 || counts.attendanceMarks() != 0 || counts.studentRequests() != 0;
            case SCHEDULE -> counts.assignments() != 0 || counts.homeworks() != 0
                    || counts.attendanceMarks() != 0 || counts.studentRequests() != 0;
            case ATTENDANCE -> counts.scheduleTemplates() != 0 || counts.oneOffLessons() != 0
                    || counts.lessons() != 0 || counts.assignments() != 0 || counts.homeworks() != 0;
        };
        if (invalid) throw new ConflictException("Participant deletion receipt contains counts for another domain");
    }

    private static void setSealed(SemesterArchiveOperation operation, Participant participant, boolean sealed) {
        switch (participant) {
            case ACADEMIC -> operation.setAcademicSealed(sealed);
            case SCHEDULE -> operation.setScheduleSealed(sealed);
            case ATTENDANCE -> operation.setAttendanceSealed(sealed);
        }
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
    public SemesterArchiveOperation beginDeleteRelease(UUID operationId, String reason) {
        SemesterArchiveOperation operation = operationRepository.findByIdForUpdate(operationId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown semester deletion " + operationId));
        if (operation.getAction() != SemesterArchiveAction.DELETE || operation.isIrreversibleIntent()) {
            throw new ConflictException("Безопасная отмена допустима только до irreversible intent");
        }
        if (operation.getDeletePhase() == SemesterDeletionPhase.RELEASING) return operation;
        if (operation.getDeletePhase() != SemesterDeletionPhase.PREPARING) {
            throw new ConflictException("Операция больше не находится в подготовительной фазе");
        }
        operation.setDeletePhase(SemesterDeletionPhase.RELEASING);
        operation.setCancelReason(reason);
        operation.setAcademic(SemesterArchiveParticipantStatus.RELEASE_PENDING);
        operation.setSchedule(SemesterArchiveParticipantStatus.RELEASE_PENDING);
        operation.setAttendance(SemesterArchiveParticipantStatus.RELEASE_PENDING);
        operation.setAcademicSealed(false);
        operation.setScheduleSealed(false);
        operation.setAttendanceSealed(false);
        operation.setBlockingReason(null);
        operation.setOperationState(SemesterArchiveOperationState.PENDING);
        operation.setRetryable(true);
        SemesterArchiveOperation saved = operationRepository.saveAndFlush(operation);
        semesterService.setDeletionPhase(operation.getSemesterId(), operationId,
                operation.getStateVersion(), SemesterDeletionPhase.RELEASING);
        publishParticipantCommand(saved, SemesterArchiveParticipantCommand.RELEASE_DELETE);
        return saved;
    }

    /** Irreversible intent and Attendance COMMIT command share one SQL/outbox transaction. */
    @Transactional
    public SemesterArchiveOperation beginIrreversibleDelete(UUID operationId) {
        SemesterArchiveOperation operation = operationRepository.findByIdForUpdate(operationId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown semester deletion " + operationId));
        if (operation.getAction() != SemesterArchiveAction.DELETE) {
            throw new ConflictException("Это не операция удаления семестра");
        }
        if (operation.isIrreversibleIntent()) return operation;
        if (operation.getDeletePhase() != SemesterDeletionPhase.PREPARING
                || operation.getAcademic() != SemesterArchiveParticipantStatus.READY
                || operation.getSchedule() != SemesterArchiveParticipantStatus.READY
                || operation.getAttendance() != SemesterArchiveParticipantStatus.READY
                || !operation.isAcademicSealed() || !operation.isScheduleSealed()
                || !operation.isAttendanceSealed()) {
            throw new ConflictException("Необратимое удаление требует всех точных sealed receipts");
        }
        operation.setDeletePhase(SemesterDeletionPhase.DELETING);
        operation.setIrreversibleIntent(true);
        operation.setBlockingReason(null);
        operation.setOperationState(SemesterArchiveOperationState.PENDING);
        operation.setRetryable(true);
        SemesterArchiveOperation saved = operationRepository.saveAndFlush(operation);
        semesterService.setDeletionPhase(operation.getSemesterId(), operationId,
                operation.getStateVersion(), SemesterDeletionPhase.DELETING);
        publishParticipantCommand(saved, SemesterArchiveParticipantCommand.COMMIT_DELETE);
        return saved;
    }

    @Transactional
    @CacheEvict(value = "active_semester", allEntries = true)
    public SemesterArchiveOperation completeDelete(UUID operationId) {
        SemesterArchiveOperation operation = operationRepository.findByIdForUpdate(operationId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown semester deletion " + operationId));
        if (operation.getOperationState() == SemesterArchiveOperationState.COMPLETED) return operation;
        if (operation.getAction() != SemesterArchiveAction.DELETE
                || operation.getDeletePhase() != SemesterDeletionPhase.DELETING
                || !operation.isIrreversibleIntent()
                || operation.getSchedule() != SemesterArchiveParticipantStatus.DELETED
                || operation.getAttendance() != SemesterArchiveParticipantStatus.DELETED) {
            throw new ConflictException("Academic может удалить семестр только после точных удалённых receipts");
        }
        operation.setAcademic(SemesterArchiveParticipantStatus.DELETED);
        operationRepository.saveAndFlush(operation);
        academicBarrier.deleteOwnDomainAndSemester(operation.getOperationId(), operation.getSemesterId(),
                operation.getStateVersion(), operation.getAcademicParticipantDigest());
        operation.setDeletePhase(SemesterDeletionPhase.COMPLETED);
        operation.setOperationState(SemesterArchiveOperationState.COMPLETED);
        operation.setRetryable(false);
        operation.setActive(false);
        operation.setArchived(false);
        operation.setTransition(SemesterTransition.NONE);
        operation.setReleasePending(false);
        operation.setBlockingReason(null);
        return operationRepository.saveAndFlush(operation);
    }

    @Transactional
    @CacheEvict(value = "active_semester", allEntries = true)
    public SemesterArchiveOperation completeDeleteCancellation(UUID operationId) {
        SemesterArchiveOperation operation = operationRepository.findByIdForUpdate(operationId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown semester deletion " + operationId));
        if (operation.getOperationState() == SemesterArchiveOperationState.COMPLETED) return operation;
        if (operation.getAction() != SemesterArchiveAction.DELETE
                || operation.getDeletePhase() != SemesterDeletionPhase.RELEASING
                || operation.isIrreversibleIntent()
                || operation.getAcademic() != SemesterArchiveParticipantStatus.RELEASED
                || operation.getSchedule() != SemesterArchiveParticipantStatus.RELEASED
                || operation.getAttendance() != SemesterArchiveParticipantStatus.RELEASED) {
            throw new ConflictException("Отмена требует точных RELEASED receipts всех участников");
        }
        Semester semester = semesterService.completeDeletionCancellation(operation.getSemesterId(),
                operation.getOperationId(), operation.getStateVersion(), operation.getPriorState());
        operation.setDeletePhase(SemesterDeletionPhase.CANCELLED);
        operation.setOperationState(SemesterArchiveOperationState.COMPLETED);
        operation.setRetryable(false);
        operation.setActive(semester.isActive());
        operation.setArchived(semester.isArchived());
        operation.setTransition(SemesterTransition.NONE);
        operation.setReleasePending(false);
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
                operation.getStateVersion(), command,
                operation.getAction() == SemesterArchiveAction.DELETE
                        ? operation.getAttendanceParticipantDigest() : null));
    }

    private static SemesterArchiveParticipantCommand initialCommand(SemesterArchiveAction action) {
        return action == SemesterArchiveAction.ARCHIVE
                ? SemesterArchiveParticipantCommand.PREPARE_ARCHIVE
                : SemesterArchiveParticipantCommand.PREPARE_RESTORE;
    }

    private static SemesterArchiveParticipantCommand retryCommand(SemesterArchiveOperation operation) {
        if (operation.getAction() == SemesterArchiveAction.DELETE) {
            return switch (operation.getDeletePhase()) {
                case RELEASING -> SemesterArchiveParticipantCommand.RELEASE_DELETE;
                case DELETING -> SemesterArchiveParticipantCommand.COMMIT_DELETE;
                case PREPARING -> operation.isAcademicSealed() && operation.isScheduleSealed()
                        ? SemesterArchiveParticipantCommand.SEAL_DELETE
                        : SemesterArchiveParticipantCommand.PREPARE_DELETE;
                default -> SemesterArchiveParticipantCommand.PREPARE_DELETE;
            };
        }
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
        if (action == SemesterArchiveAction.DELETE) {
            return next == SemesterArchiveParticipantStatus.PENDING
                    || next == SemesterArchiveParticipantStatus.READY
                    || next == SemesterArchiveParticipantStatus.RELEASE_PENDING
                    || next == SemesterArchiveParticipantStatus.RELEASED
                    || next == SemesterArchiveParticipantStatus.DELETED;
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
            if (action == SemesterArchiveAction.DELETE) {
                return switch (status) {
                    case NOT_STARTED -> 0;
                    case PENDING -> 1;
                    case READY -> 2;
                    case RELEASE_PENDING -> 3;
                    case RELEASED -> 4;
                    case DELETED -> 5;
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
