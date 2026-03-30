package ru.rutcampustrack.academic.semester;

import jakarta.persistence.EntityManager;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.rutcampustrack.academic.contract.dto.semester.CreateSemesterRequest;
import ru.rutcampustrack.academic.contract.dto.semester.DeleteSemesterRequest;
import ru.rutcampustrack.academic.contract.dto.semester.UpdateSemesterRequest;
import ru.rutcampustrack.academic.contract.exception.ResourceNotFoundException;
import ru.rutcampustrack.academic.entity.Semester;
import ru.rutcampustrack.academic.exception.BadRequestException;
import ru.rutcampustrack.academic.repository.SemesterRepository;

import java.time.OffsetDateTime;

/**
 * Business logic for Semester domain: CRUD, atomic activation, confirmation-guarded deletion.
 */
@Service
public class SemesterService {

    private final SemesterRepository semesterRepository;
    private final SemesterAssembler semesterAssembler;
    private final EntityManager entityManager;

    public SemesterService(SemesterRepository semesterRepository,
                           SemesterAssembler semesterAssembler,
                           EntityManager entityManager) {
        this.semesterRepository = semesterRepository;
        this.semesterAssembler = semesterAssembler;
        this.entityManager = entityManager;
    }

    @Transactional
    public Semester createSemester(CreateSemesterRequest request) {
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
        semester.setName(request.name());
        semester.setDateFrom(request.dateFrom());
        semester.setDateTo(request.dateTo());
        return semesterRepository.save(semester);
    }

    /**
     * Atomic semester activation (D-11, GSEM-03, Pitfall 5).
     * Deactivates all active semesters first, flushes to avoid constraint conflicts,
     * then activates the target semester.
     */
    @Transactional
    public Semester activateSemester(Long id) {
        // Step 1: deactivate all currently active semesters
        semesterRepository.deactivateAllActive();

        // Step 2: flush to ensure deactivation is visible before activation
        entityManager.flush();

        // Step 3: find and activate the target semester
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
