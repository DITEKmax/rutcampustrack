package ru.rutcampustrack.academic.semester;

import jakarta.persistence.EntityManager;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.rutcampustrack.academic.contract.dto.semester.CreateSemesterRequest;
import ru.rutcampustrack.academic.contract.dto.semester.DeleteSemesterRequest;
import ru.rutcampustrack.academic.contract.dto.semester.UpdateSemesterRequest;
import ru.rutcampustrack.academic.contract.exception.ResourceNotFoundException;
import ru.rutcampustrack.academic.entity.Semester;
import ru.rutcampustrack.academic.event.SemesterArchivedEvent;
import ru.rutcampustrack.academic.exception.BadRequestException;
import ru.rutcampustrack.academic.repository.SemesterRepository;

import ru.rutcampustrack.academic.contract.dto.semester.OverlapCheckResponse;
import ru.rutcampustrack.academic.exception.ConflictException;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Optional;

/**
 * Business logic for Semester domain: CRUD, atomic activation, confirmation-guarded deletion.
 */
@Service
public class SemesterService {

    private final SemesterRepository semesterRepository;
    private final SemesterAssembler semesterAssembler;
    private final EntityManager entityManager;
    private final ApplicationEventPublisher eventPublisher;

    public SemesterService(SemesterRepository semesterRepository,
                           SemesterAssembler semesterAssembler,
                           EntityManager entityManager,
                           ApplicationEventPublisher eventPublisher) {
        this.semesterRepository = semesterRepository;
        this.semesterAssembler = semesterAssembler;
        this.entityManager = entityManager;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public Semester createSemester(CreateSemesterRequest request) {
        validateDates(request.dateFrom(), request.dateTo(), true);
        checkOverlapOrThrow(request.dateFrom(), request.dateTo(), null);

        Semester semester = new Semester();
        semester.setName(request.name());
        semester.setDateFrom(request.dateFrom());
        semester.setDateTo(request.dateTo());
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
        Semester semester = findSemesterById(id);

        // BUG-006-7: запрещаем редактировать завершённый семестр.
        if (semester.getDateTo().isBefore(LocalDate.now())) {
            throw new ConflictException("status", id,
                    "Нельзя редактировать завершённый семестр");
        }

        validateDates(request.dateFrom(), request.dateTo(), false);
        checkOverlapOrThrow(request.dateFrom(), request.dateTo(), id);

        semester.setName(request.name());
        semester.setDateFrom(request.dateFrom());
        semester.setDateTo(request.dateTo());
        return semesterRepository.save(semester);
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
     * Structured pre-check for date sanity. On create we additionally enforce
     * {@code dateFrom >= today} per BUG-006-7; on update we allow the existing
     * past-starting semester to be renamed as long as {@code dateTo >= today}
     * (handled separately in {@link #updateSemester}).
     */
    private void validateDates(LocalDate from, LocalDate to, boolean requireFutureStart) {
        if (requireFutureStart && from.isBefore(LocalDate.now())) {
            throw new BadRequestException("dateFrom", "Нельзя создать семестр в прошлом");
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
     * Atomic semester activation (D-11, GSEM-03, Pitfall 5).
     * Deactivates all active semesters first, flushes to avoid constraint conflicts,
     * then activates the target semester.
     */
    @CacheEvict(value = "active_semester", allEntries = true)
    @Transactional
    public Semester activateSemester(Long id) {
        // Step 1: capture currently active semester before deactivation
        Optional<Semester> previouslyActive = semesterRepository.findByIsActiveTrue();

        // Step 2: deactivate all currently active semesters
        semesterRepository.deactivateAllActive();

        // Step 3: flush bulk UPDATE then clear persistence context so subsequent
        // findById re-reads from DB (bulk UPDATE bypasses entity cache — stale isActive)
        entityManager.flush();
        entityManager.clear();

        // Step 4: publish semester.archived event for the deactivated semester
        previouslyActive.ifPresent(deactivated ->
                eventPublisher.publishEvent(new SemesterArchivedEvent(this, deactivated.getId())));

        // Step 5: find and activate the target semester (re-read from DB after clear)
        Semester semester = findSemesterById(id);
        semester.setActive(true);
        return semesterRepository.saveAndFlush(semester);
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
