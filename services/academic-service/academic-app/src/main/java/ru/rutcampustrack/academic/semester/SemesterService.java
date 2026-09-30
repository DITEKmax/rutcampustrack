package ru.rutcampustrack.academic.semester;

import jakarta.persistence.EntityManager;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import ru.rutcampustrack.academic.contract.dto.semester.CreateSemesterRequest;
import ru.rutcampustrack.academic.contract.dto.semester.UpdateSemesterRequest;
import ru.rutcampustrack.academic.contract.enums.SemesterTransition;
import ru.rutcampustrack.academic.contract.enums.SemesterType;
import ru.rutcampustrack.academic.contract.enums.SemesterDeletionPhase;
import ru.rutcampustrack.academic.contract.enums.SemesterDeletionPriorState;
import ru.rutcampustrack.academic.contract.exception.ResourceNotFoundException;
import ru.rutcampustrack.academic.entity.Semester;
import ru.rutcampustrack.academic.event.SemesterArchivedEvent;
import ru.rutcampustrack.academic.exception.BadRequestException;
import ru.rutcampustrack.academic.repository.SemesterRepository;
import ru.rutcampustrack.academic.repository.AssignmentRepository;

import ru.rutcampustrack.academic.contract.dto.semester.OverlapCheckResponse;
import ru.rutcampustrack.academic.exception.ConflictException;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.sql.PreparedStatement;
import java.util.Optional;
import java.util.UUID;

/**
 * Business logic for Semester domain: CRUD, atomic activation, and archive lifecycle state.
 */
@Service
public class SemesterService {

    private static final int MIN_ACADEMIC_YEAR = 1;
    private static final int MAX_ACADEMIC_YEAR = 9998;
    private static final int SEMESTER_STATE_LOCK_NAMESPACE = 0x53454D;
    private static final int SEMESTER_STATE_LOCK_ID = 1;

    private final SemesterRepository semesterRepository;
    private final SemesterAssembler semesterAssembler;
    private final EntityManager entityManager;
    private final ApplicationEventPublisher eventPublisher;
    private final AssignmentRepository assignmentRepository;
    private final JdbcTemplate jdbcTemplate;

    public SemesterService(SemesterRepository semesterRepository,
                           SemesterAssembler semesterAssembler,
                           EntityManager entityManager,
                           ApplicationEventPublisher eventPublisher,
                           AssignmentRepository assignmentRepository,
                           JdbcTemplate jdbcTemplate) {
        this.semesterRepository = semesterRepository;
        this.semesterAssembler = semesterAssembler;
        this.entityManager = entityManager;
        this.eventPublisher = eventPublisher;
        this.assignmentRepository = assignmentRepository;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional
    public Semester createSemester(CreateSemesterRequest request) {
        validateAcademicYear(request.semesterType(), request.academicYear());
        validateDates(request.dateFrom(), request.dateTo(), true);
        checkOverlapOrThrow(request.dateFrom(), request.dateTo(), null);

        Semester semester = new Semester();
        semester.setName(request.semesterType() == null
                ? request.name()
                : generatedName(request.semesterType(), request.academicYear()));
        semester.setDateFrom(request.dateFrom());
        semester.setDateTo(request.dateTo());
        semester.setSemesterType(request.semesterType());
        semester.setAcademicYear(request.academicYear());
        semester.setActive(false);
        semester.setCreatedAt(OffsetDateTime.now());
        return semesterRepository.save(semester);
    }

    public Semester findSemesterById(Long id) {
        return semesterRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Semester", "id", id));
    }

    public Optional<Semester> findSemesterByIdUncached(Long id) {
        return semesterRepository.findByIdUncached(id);
    }

    public Page<Semester> listSemesters(Pageable pageable) {
        return semesterRepository.findAll(pageable);
    }

    @Transactional
    public Semester updateSemester(Long id, UpdateSemesterRequest request) {
        Semester semester = findSemesterForUpdate(id);

        if (isWriteBlocked(semester)) {
            throw new ConflictException("status", id,
                    "Нельзя редактировать архивируемый или архивный семестр до восстановления");
        }

        // BUG-006-7: запрещаем редактировать завершённый семестр.
        if (semester.getDateTo().isBefore(LocalDate.now())) {
            throw new ConflictException("status", id,
                    "Нельзя редактировать завершённый семестр");
        }

        validateAcademicYear(request.semesterType(), request.academicYear());

        validateDates(request.dateFrom(), request.dateTo(), false);
        boolean datesChanged = !request.dateFrom().equals(semester.getDateFrom())
                || !request.dateTo().equals(semester.getDateTo());
        if (datesChanged && assignmentRepository.existsBySemesterId(id)) {
            throw new ConflictException("dates", id,
                    "Нельзя изменить даты семестра, на который ссылаются назначения");
        }
        checkOverlapOrThrow(request.dateFrom(), request.dateTo(), id);

        if (request.semesterType() != null) {
            semester.setSemesterType(request.semesterType());
            semester.setAcademicYear(request.academicYear());
            semester.setName(generatedName(request.semesterType(), request.academicYear()));
        } else if (semester.getSemesterType() != null || semester.getAcademicYear() != null) {
            validateAcademicYear(semester.getSemesterType(), semester.getAcademicYear());
            semester.setName(generatedName(semester.getSemesterType(), semester.getAcademicYear()));
        } else {
            // Legacy rows keep their explicit name until an administrator selects a type and year.
            semester.setName(request.name());
        }
        semester.setDateFrom(request.dateFrom());
        semester.setDateTo(request.dateTo());
        return semesterRepository.save(semester);
    }

    private void validateAcademicYear(SemesterType type, Integer year) {
        if ((type == null) != (year == null)) {
            throw new BadRequestException("academicYear", "Тип семестра и учебный год нужно указать вместе");
        }
        if (year != null && (year < MIN_ACADEMIC_YEAR || year > MAX_ACADEMIC_YEAR)) {
            throw new BadRequestException("academicYear", "Учебный год должен быть от 1 до 9998");
        }
    }

    private String generatedName(SemesterType type, Integer academicYear) {
        String season = type == SemesterType.AUTUMN ? "Осенний" : "Весенний";
        return season + " " + academicYear + "/" + (academicYear + 1);
    }

    private Semester findSemesterForUpdate(Long id) {
        return semesterRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new ResourceNotFoundException("Semester", "id", id));
    }

    /**
     * Check overlap without mutation — used by async validator on the admin
     * panel semester-dialog (BUG-006-7).
     */
    public OverlapCheckResponse checkOverlap(LocalDate from, LocalDate to, Long excludeId) {
        return semesterRepository.findFirstOverlapping(from, to, excludeId)
                .map(s -> new OverlapCheckResponse(true, s.getName()))
                .orElse(new OverlapCheckResponse(false, null));
    }

    /**
     * Structured pre-check for date sanity. A newly created semester may start
     * in the past when it is the current semester, but a fully completed
     * semester is still rejected. On update the existing completed-semester
     * guard is handled separately in {@link #updateSemester}.
     */
    private void validateDates(LocalDate from, LocalDate to, boolean requireFutureStart) {
        if (requireFutureStart && to.isBefore(LocalDate.now())) {
            throw new BadRequestException("dateTo", "Нельзя создать завершённый семестр");
        }
        if (to.isBefore(from)) {
            throw new BadRequestException("dateTo", "Дата окончания раньше даты начала");
        }
    }

    private void checkOverlapOrThrow(LocalDate from, LocalDate to, Long excludeId) {
        semesterRepository.findFirstOverlapping(from, to, excludeId).ifPresent(conflict -> {
            throw new ConflictException("dates", conflict.getId(),
                    "Даты пересекаются с семестром \"" + conflict.getName() + "\"");
        });
    }

    /**
     * Atomic and replay-safe semester activation (D-11, GSEM-03, Pitfall 5).
     * The transaction-scoped advisory lock serializes activation requests for
     * different semester rows; the target is validated before changing any state.
     */
    @CacheEvict(value = "active_semester", allEntries = true)
    @Transactional
    public Semester activateSemester(Long id) {
        lockSemesterStateTransition();
        Boolean activeDeletionPending = jdbcTemplate.queryForObject("""
                SELECT EXISTS (
                    SELECT 1 FROM semester_archive_operations
                     WHERE action = 'DELETE' AND prior_state = 'ACTIVE'
                       AND delete_phase IN ('PREPARING', 'RELEASING')
                       AND operation_state IN ('PENDING', 'ERROR')
                )
                """, Boolean.class);
        if (Boolean.TRUE.equals(activeDeletionPending)) {
            throw new ConflictException("status", id,
                    "Нельзя активировать другой семестр до завершения безопасной отмены удаления активного семестра");
        }

        // Lock and validate the target before touching the current active semester.
        Semester target = semesterRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new ResourceNotFoundException("Semester", "id", id));

        if (isWriteBlocked(target)) {
            throw new ConflictException("status", id,
                    "Нельзя активировать архивируемый или архивный семестр до восстановления");
        }

        // Replaying activation for the current semester is a successful no-op.
        Optional<Semester> previouslyActive = semesterRepository.findByIsActiveTrue();
        if (previouslyActive.map(active -> active.getId().equals(id)).orElse(false)) {
            return target;
        }

        // Deactivate before activation to satisfy the database's one-active constraint.
        semesterRepository.deactivateAllActive();

        // Bulk UPDATE bypasses the persistence context, so re-read after clearing it.
        entityManager.flush();
        entityManager.clear();

        // The outbox listener stores this only if the same transaction commits.
        previouslyActive.ifPresent(deactivated ->
                eventPublisher.publishEvent(new SemesterArchivedEvent(this, deactivated.getId())));

        // Re-read the locked target because clear() detached it.
        Semester semester = findSemesterById(id);
        semester.setActive(true);
        incrementStateVersion(semester);
        return semesterRepository.saveAndFlush(semester);
    }

    /**
     * Starts an archive transition after all semester state changes have been serialized.
     * The transition itself blocks writes; callers must obtain participant fence receipts
     * before invoking {@link #completeArchiveTransition(Long, long)}.
     */
    @CacheEvict(value = "active_semester", allEntries = true)
    @Transactional
    public Semester beginArchiveTransition(Long id) {
        lockSemesterStateTransition();
        Semester semester = findSemesterForUpdate(id);

        if (semester.isReleasePending()) {
            throw new ConflictException("status", id,
                    "Нельзя начать архивацию, пока все домены не завершили восстановление");
        }

        if (semester.isArchived() && semester.getArchiveTransition() == SemesterTransition.NONE) {
            return semester;
        }
        if (semester.getArchiveTransition() == SemesterTransition.ARCHIVING) {
            return semester;
        }
        if (semester.getArchiveTransition() != SemesterTransition.NONE) {
            throw new ConflictException("status", id,
                    "Нельзя архивировать семестр во время восстановления");
        }

        boolean wasActive = semester.isActive();
        semester.setActive(false);
        semester.setArchiveTransition(SemesterTransition.ARCHIVING);
        incrementStateVersion(semester);
        Semester transitioning = semesterRepository.saveAndFlush(semester);

        // This legacy event refreshes downstream active-semester caches on deactivation.
        // It is emitted once at the real active -> inactive transition, not on retries.
        if (wasActive) {
            eventPublisher.publishEvent(new SemesterArchivedEvent(this, semester.getId()));
        }
        return transitioning;
    }

    /** Completes archive state only after every write-domain fence has acknowledged this version. */
    @CacheEvict(value = "active_semester", allEntries = true)
    @Transactional
    public Semester completeArchiveTransition(Long id, long expectedStateVersion) {
        lockSemesterStateTransition();
        Semester semester = findSemesterForUpdate(id);

        if (semester.isArchived() && semester.getArchiveTransition() == SemesterTransition.NONE) {
            requireReplayVersion(semester, id, expectedStateVersion);
            return semester;
        }
        requireTransitionVersion(semester, id, expectedStateVersion, SemesterTransition.ARCHIVING);
        semester.setArchived(true);
        semester.setArchiveTransition(SemesterTransition.NONE);
        return semesterRepository.saveAndFlush(semester);
    }

    /** Starts restore while keeping the authoritative write block in place. */
    @Transactional
    public Semester beginRestoreTransition(Long id) {
        lockSemesterStateTransition();
        Semester semester = findSemesterForUpdate(id);

        if (!semester.isArchived() && !semester.isReleasePending()
                && semester.getArchiveTransition() == SemesterTransition.NONE) {
            return semester;
        }
        if (semester.getArchiveTransition() == SemesterTransition.RESTORING) {
            return semester;
        }
        if (semester.getArchiveTransition() != SemesterTransition.NONE
                || !semester.isArchived() || semester.isReleasePending()) {
            throw new ConflictException("status", id,
                    "Нельзя восстановить семестр во время архивации");
        }
        if (semester.isActive()) {
            throw new ConflictException("status", id,
                    "Архивный семестр должен оставаться неактивным при восстановлении");
        }

        semester.setArchiveTransition(SemesterTransition.RESTORING);
        incrementStateVersion(semester);
        return semesterRepository.saveAndFlush(semester);
    }

    /** Locks the global activation boundary and local write fence before preparing durable intent. */
    @Transactional
    public Semester lockDeletionCandidate(Long id, long originalStateVersion,
                                           SemesterDeletionPriorState priorState) {
        lockSemesterStateTransition();
        jdbcTemplate.execute((ConnectionCallback<Void>) connection -> {
            try (PreparedStatement statement = connection.prepareStatement("SELECT pg_advisory_xact_lock(?, ?)")) {
                statement.setInt(1, 5_452_097);
                statement.setInt(2, (int) (id % Integer.MAX_VALUE));
                statement.execute();
            }
            return null;
        });
        Semester semester = findSemesterForUpdate(id);
        if (semester.getStateVersion() != originalStateVersion
                || semester.getArchiveTransition() != SemesterTransition.NONE
                || semester.isReleasePending()
                || SemesterDeletionPreviewService.priorState(semester) != priorState) {
            throw new ConflictException("stateVersion", id,
                    "Состояние семестра изменилось после preview; требуется новый расчёт");
        }
        return semester;
    }

    /** Installs the new delete epoch after the user's original preview version is rechecked. */
    @CacheEvict(value = "active_semester", allEntries = true)
    @Transactional
    public Semester beginDeletionTransition(Long id, long originalStateVersion, UUID operationId) {
        lockSemesterStateTransition();
        Semester semester = findSemesterForUpdate(id);
        if (semester.getStateVersion() != originalStateVersion
                || semester.getArchiveTransition() != SemesterTransition.NONE
                || semester.isReleasePending()) {
            throw new ConflictException("stateVersion", id,
                    "Состояние семестра изменилось после preview; требуется новый расчёт");
        }
        boolean wasActive = semester.isActive();
        semester.setActive(false);
        semester.setArchiveTransition(SemesterTransition.DELETING);
        semester.setDeletionPhase(SemesterDeletionPhase.PREPARING);
        semester.setTransitionOperationId(operationId);
        incrementStateVersion(semester);
        Semester transitioning = semesterRepository.saveAndFlush(semester);
        if (wasActive) {
            eventPublisher.publishEvent(new SemesterArchivedEvent(this, semester.getId()));
        }
        return transitioning;
    }

    @Transactional
    public Semester setDeletionPhase(Long id, UUID operationId, long expectedStateVersion,
                                     SemesterDeletionPhase phase) {
        lockSemesterStateTransition();
        Semester semester = findSemesterForUpdate(id);
        requireDeletionIdentity(semester, id, operationId, expectedStateVersion);
        if (semester.getDeletionPhase() == SemesterDeletionPhase.DELETING
                && phase != SemesterDeletionPhase.DELETING) {
            throw new ConflictException("Нельзя отменить удаление после irreversible intent");
        }
        if (phase == SemesterDeletionPhase.RELEASING && semester.getDeletionPhase() != SemesterDeletionPhase.PREPARING
                && semester.getDeletionPhase() != SemesterDeletionPhase.RELEASING) {
            throw new ConflictException("Отмена удаления требует подготовительную фазу");
        }
        if (phase == SemesterDeletionPhase.DELETING && semester.getDeletionPhase() != SemesterDeletionPhase.PREPARING
                && semester.getDeletionPhase() != SemesterDeletionPhase.DELETING) {
            throw new ConflictException("Необратимое удаление требует точные sealed receipts");
        }
        semester.setDeletionPhase(phase);
        return semesterRepository.saveAndFlush(semester);
    }

    /** Restores the exact prior state only after every participant released the delete fence. */
    @CacheEvict(value = "active_semester", allEntries = true)
    @Transactional
    public Semester completeDeletionCancellation(Long id, UUID operationId, long expectedStateVersion,
                                                 SemesterDeletionPriorState priorState) {
        lockSemesterStateTransition();
        Semester semester = findSemesterForUpdate(id);
        requireDeletionIdentity(semester, id, operationId, expectedStateVersion);
        if (semester.getDeletionPhase() != SemesterDeletionPhase.RELEASING) {
            throw new ConflictException("Семестр можно восстановить только после release участников");
        }
        if (priorState == SemesterDeletionPriorState.ACTIVE
                && semesterRepository.findByIsActiveTrue().filter(active -> !active.getId().equals(id)).isPresent()) {
            throw new ConflictException("Другой семестр уже активен; восстановление активного состояния отложено");
        }
        semester.setArchived(priorState == SemesterDeletionPriorState.ARCHIVED);
        semester.setActive(priorState == SemesterDeletionPriorState.ACTIVE);
        semester.setArchiveTransition(SemesterTransition.NONE);
        semester.setDeletionPhase(null);
        semester.setTransitionOperationId(null);
        incrementStateVersion(semester);
        return semesterRepository.saveAndFlush(semester);
    }

    private static void requireDeletionIdentity(Semester semester, Long id, UUID operationId,
                                                long expectedStateVersion) {
        if (semester.getArchiveTransition() != SemesterTransition.DELETING
                || !operationId.equals(semester.getTransitionOperationId())
                || semester.getStateVersion() != expectedStateVersion) {
            throw new ConflictException("stateVersion", id,
                    "Операция удаления больше не владеет точной версией семестра");
        }
    }

    /** Completes restore after participant write fences have been released; it never activates the semester. */
    @Transactional
    public Semester completeRestoreTransition(Long id, long expectedStateVersion) {
        lockSemesterStateTransition();
        Semester semester = findSemesterForUpdate(id);

        if (!semester.isArchived() && semester.getArchiveTransition() == SemesterTransition.NONE) {
            requireReplayVersion(semester, id, expectedStateVersion);
            return semester;
        }
        requireTransitionVersion(semester, id, expectedStateVersion, SemesterTransition.RESTORING);
        if (semester.isActive()) {
            throw new ConflictException("status", id,
                    "Восстановление не может активировать семестр");
        }

        semester.setArchived(false);
        semester.setArchiveTransition(SemesterTransition.NONE);
        semester.setReleasePending(true);
        return semesterRepository.saveAndFlush(semester);
    }

    /** Clears the central restore epoch gate only after all participant release receipts are durable. */
    @CacheEvict(value = "active_semester", allEntries = true)
    @Transactional
    public Semester completeRestoreRelease(Long id, long expectedStateVersion) {
        lockSemesterStateTransition();
        Semester semester = findSemesterForUpdate(id);
        if (semester.isActive() || semester.isArchived()
                || semester.getArchiveTransition() != SemesterTransition.NONE) {
            throw new ConflictException("status", id,
                    "Центральное состояние восстановления изменилось до подтверждения release");
        }
        if (semester.getStateVersion() != expectedStateVersion) {
            throw staleTransitionVersion(id);
        }
        if (semester.isReleasePending()) {
            semester.setReleasePending(false);
            return semesterRepository.saveAndFlush(semester);
        }
        return semester;
    }

    private void requireTransitionVersion(Semester semester,
                                          Long id,
                                          long expectedStateVersion,
                                          SemesterTransition expectedTransition) {
        if (semester.getArchiveTransition() != expectedTransition) {
            throw new ConflictException("status", id,
                    "Переход состояния семестра уже изменился");
        }
        if (semester.getStateVersion() != expectedStateVersion) {
            throw staleTransitionVersion(id);
        }
    }

    private static boolean isCompletionReplayVersion(long currentVersion, long expectedVersion) {
        return expectedVersion >= 0 && currentVersion == expectedVersion;
    }

    private static void requireReplayVersion(Semester semester, Long id, long expectedStateVersion) {
        if (!isCompletionReplayVersion(semester.getStateVersion(), expectedStateVersion)) {
            throw staleTransitionVersion(id);
        }
    }

    private static ConflictException staleTransitionVersion(Long id) {
        return new ConflictException("stateVersion", id,
                "Версия состояния семестра изменилась; требуется повторно проверить блокировки");
    }

    private static boolean isWriteBlocked(Semester semester) {
        return semester.isArchived() || semester.isReleasePending()
                || semester.getArchiveTransition() != SemesterTransition.NONE;
    }

    private static void incrementStateVersion(Semester semester) {
        semester.setStateVersion(Math.addExact(semester.getStateVersion(), 1L));
    }

    private void lockSemesterStateTransition() {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("semester state lock requires an active transaction");
        }
        jdbcTemplate.execute((ConnectionCallback<Void>) connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT pg_advisory_xact_lock(?, ?)")) {
                statement.setInt(1, SEMESTER_STATE_LOCK_NAMESPACE);
                statement.setInt(2, SEMESTER_STATE_LOCK_ID);
                statement.execute();
            }
            return null;
        });
    }

}
