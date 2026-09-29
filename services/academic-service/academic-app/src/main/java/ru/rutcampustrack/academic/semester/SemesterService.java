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
import ru.rutcampustrack.academic.contract.dto.semester.DeleteSemesterRequest;
import ru.rutcampustrack.academic.contract.dto.semester.UpdateSemesterRequest;
import ru.rutcampustrack.academic.contract.enums.SemesterType;
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

/**
 * Business logic for Semester domain: CRUD, atomic activation, confirmation-guarded deletion.
 */
@Service
public class SemesterService {

    private static final int MIN_ACADEMIC_YEAR = 1;
    private static final int MAX_ACADEMIC_YEAR = 9998;
    private static final int SEMESTER_ACTIVATION_LOCK_NAMESPACE = 0x53454D;
    private static final int SEMESTER_ACTIVATION_LOCK_ID = 1;

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

    public Page<Semester> listSemesters(Pageable pageable) {
        return semesterRepository.findAll(pageable);
    }

    @Transactional
    public Semester updateSemester(Long id, UpdateSemesterRequest request) {
        Semester semester = findSemesterForUpdate(id);

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
        lockSemesterActivation();

        // Lock and validate the target before touching the current active semester.
        Semester target = semesterRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new ResourceNotFoundException("Semester", "id", id));

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
        return semesterRepository.saveAndFlush(semester);
    }

    private void lockSemesterActivation() {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("semester activation lock requires an active transaction");
        }
        jdbcTemplate.execute((ConnectionCallback<Void>) connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT pg_advisory_xact_lock(?, ?)")) {
                statement.setInt(1, SEMESTER_ACTIVATION_LOCK_NAMESPACE);
                statement.setInt(2, SEMESTER_ACTIVATION_LOCK_ID);
                statement.execute();
            }
            return null;
        });
    }

    /**
     * Confirmation-guarded deletion (D-12, GSEM-04).
     * Requires exact match of confirmation phrase with semester name.
     */
    @Transactional
    public void deleteSemester(Long id, DeleteSemesterRequest request) {
        Semester semester = findSemesterById(id);
        if (!semester.getName().equals(request.confirmation())) {
            throw new BadRequestException("Подтверждение не совпадает с названием семестра");
        }
        semesterRepository.delete(semester);
    }
}
