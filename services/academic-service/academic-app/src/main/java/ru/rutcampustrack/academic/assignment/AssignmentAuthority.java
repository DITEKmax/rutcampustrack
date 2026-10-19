package ru.rutcampustrack.academic.assignment;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ru.rutcampustrack.academic.contract.enums.SubjectType;
import ru.rutcampustrack.academic.contract.exception.ResourceNotFoundException;
import ru.rutcampustrack.academic.entity.Assignment;
import ru.rutcampustrack.academic.entity.Semester;
import ru.rutcampustrack.academic.entity.Subject;
import ru.rutcampustrack.academic.exception.AccessDeniedException;
import ru.rutcampustrack.academic.exception.BadRequestException;
import ru.rutcampustrack.academic.exception.ConflictException;
import ru.rutcampustrack.academic.repository.AssignmentRepository;
import ru.rutcampustrack.academic.repository.SemesterRepository;
import ru.rutcampustrack.academic.repository.SubjectLessonTypeRepository;
import ru.rutcampustrack.academic.repository.SubjectRepository;

import java.time.LocalDate;
import java.util.Objects;

/**
 * The single V25 write authority for immutable assignment rows.
 *
 * <p>Callers authenticate their actor and group before reaching this class; this class owns the
 * shared referential, grant, date and overlap checks so create-subject and add/assign-teacher paths
 * cannot drift apart.
 */
@Service
public class AssignmentAuthority {

    public static final String TEACHER_ROLE = "teacher";
    public static final String ACTIVE_STATUS = "active";

    private final AssignmentRepository assignmentRepository;
    private final SemesterRepository semesterRepository;
    private final SubjectRepository subjectRepository;
    private final SubjectLessonTypeRepository lessonTypeRepository;
    private final AssignmentTeacherEligibility teacherEligibility;
    private AssignmentClosureService closureService;
    @jakarta.persistence.PersistenceContext private jakarta.persistence.EntityManager entityManager;

    @org.springframework.beans.factory.annotation.Autowired
    public void setClosureService(AssignmentClosureService closureService) {
        this.closureService = closureService;
    }

    public AssignmentAuthority(
            AssignmentRepository assignmentRepository,
            SemesterRepository semesterRepository,
            SubjectRepository subjectRepository,
            SubjectLessonTypeRepository lessonTypeRepository,
            AssignmentTeacherEligibility teacherEligibility) {
        this.assignmentRepository = assignmentRepository;
        this.semesterRepository = semesterRepository;
        this.subjectRepository = subjectRepository;
        this.lessonTypeRepository = lessonTypeRepository;
        this.teacherEligibility = teacherEligibility;
    }

    /** Locks the semester row before any assignment validation or insert. */
    @Transactional
    public Semester lockSemester(Long semesterId) {
        if (semesterId == null || semesterId <= 0) {
            throw new BadRequestException("semesterId", "ID семестра должен быть положительным");
        }
        return semesterRepository
                .findByIdForUpdate(semesterId)
                .orElseThrow(() -> new ResourceNotFoundException("Semester", "id", semesterId));
    }

    @Transactional
    public Assignment create(
            Long teacherId,
            Long subjectId,
            Long groupId,
            Long semesterId,
            SubjectType lessonType,
            LocalDate validFrom,
            LocalDate validUntilExclusive) {
        Semester semester = lockSemester(semesterId);
        return createWithLockedSemester(
                teacherId,
                subjectId,
                groupId,
                semester,
                lessonType,
                validFrom,
                validUntilExclusive);
    }

    /** Creates after the caller has acquired the deterministic semester lock. */
    @Transactional
    public Assignment createWithLockedSemester(
            Long teacherId,
            Long subjectId,
            Long groupId,
            Semester semester,
            SubjectType lessonType,
            LocalDate validFrom,
            LocalDate validUntilExclusive) {
        return createLocked(
                teacherId,
                subjectId,
                groupId,
                semester,
                lessonType,
                validFrom,
                validUntilExclusive,
                true);
    }

    /** Only SubjectService's newly inserted subject admission uses this atomic batch. */
    @Transactional
    public void createInitialAssignmentsForNewSubject(
            Long subjectId,
            Long groupId,
            java.util.Map<Long, Semester> lockedSemesters,
            java.util.List<ru.rutcampustrack.academic.contract.dto.subject.InitialAssignmentRequest>
                    initial) {
        if (!org.springframework.transaction.support.TransactionSynchronizationManager
                        .isActualTransactionActive()
                || assignmentRepository.existsBySubjectId(subjectId)) {
            throw new ConflictException(
                    "Первичные назначения требуют нового предмета в одной транзакции");
        }
        java.util.Set<String> checked = new java.util.HashSet<>();
        for (var request : initial) {
            if (checked.add(request.semesterId() + "|" + request.lessonType())) {
                Objects.requireNonNull(closureService, "Assignment adoption coordinator")
                        .requireNoOpenTuple(
                                groupId, subjectId, request.semesterId(), request.lessonType());
            }
        }
        // Identity operations lock all target profiles in ID order. A user-supplied batch may
        // list them in the opposite order, so acquire the whole teacher set before any insert.
        java.util.SortedSet<Long> teachers = new java.util.TreeSet<>();
        for (var request : initial) {
            if (request.teacherId() == null || request.teacherId() <= 0)
                throw new BadRequestException(
                        "teacherId", "ID преподавателя должен быть положительным");
            teachers.add(request.teacherId());
        }
        for (Long teacher : teachers) teacherEligibility.requireEligible(teacher);
        java.util.List<Assignment> targets = new java.util.ArrayList<>();
        for (var request : initial)
            targets.add(
                    createLocked(
                            request.teacherId(),
                            subjectId,
                            groupId,
                            lockedSemesters.get(request.semesterId()),
                            request.lessonType(),
                            request.validFrom(),
                            request.validUntilExclusive(),
                            false));
        for (Assignment target : targets) {
            closureService.prepareAdoption(target);
            entityManager.detach(target);
        }
    }

    private Assignment createLocked(
            Long teacherId,
            Long subjectId,
            Long groupId,
            Semester semester,
            SubjectType lessonType,
            LocalDate validFrom,
            LocalDate validUntilExclusive,
            boolean independentAdmission) {
        if (teacherId == null || teacherId <= 0) {
            throw new BadRequestException(
                    "teacherId", "ID преподавателя должен быть положительным");
        }
        if (subjectId == null || subjectId <= 0) {
            throw new BadRequestException("subjectId", "ID предмета должен быть положительным");
        }
        if (groupId == null || groupId <= 0) {
            throw new BadRequestException("groupId", "ID группы должен быть положительным");
        }
        if (semester == null || semester.getId() == null) {
            throw new ResourceNotFoundException(
                    "Semester", "id", semester == null ? null : semester.getId());
        }
        if (lessonType == null) {
            throw new BadRequestException("lessonType", "Тип занятия обязателен");
        }
        Subject subject =
                subjectRepository
                        .findById(subjectId)
                        .orElseThrow(
                                () -> new ResourceNotFoundException("Subject", "id", subjectId));
        if (!Objects.equals(subject.getGroupId(), groupId)) {
            throw new AccessDeniedException("Предмет не принадлежит указанной группе");
        }
        // Unresolved legacy rows keep the existing ordinary-write contract. They are never
        // eligible for irreversible subject deletion; new subjects always have a binding.
        if (subject.getSemesterId() != null
                && !Objects.equals(subject.getSemesterId(), semester.getId())) {
            throw new ru.rutcampustrack.academic.exception.ConflictException(
                    "Предмет не привязан к этому семестру; создайте новый предмет");
        }
        if (!lessonTypeRepository.existsBySubjectIdAndLessonType(subjectId, lessonType)) {
            throw new BadRequestException("lessonType", "Тип занятия не разрешён для предмета");
        }

        teacherEligibility.requireEligible(teacherId);

        validateDates(semester, validFrom, validUntilExclusive);
        if (independentAdmission)
            Objects.requireNonNull(closureService, "Assignment adoption coordinator")
                    .requireNoOpenTuple(groupId, subjectId, semester.getId(), lessonType);
        assignmentRepository
                .findOverlapping(
                        teacherId,
                        subjectId,
                        groupId,
                        semester.getId(),
                        lessonType.name().toLowerCase(),
                        validFrom,
                        validUntilExclusive)
                .ifPresent(
                        existing -> {
                            throw new ConflictException(
                                    "Назначение преподавателя пересекается с существующим");
                        });
        Assignment assignment =
                assignmentRepository.saveAndFlush(
                        new Assignment(
                                teacherId,
                                subjectId,
                                groupId,
                                semester.getId(),
                                lessonType,
                                validFrom,
                                validUntilExclusive,
                                AssignmentLifecycle.PREPARED));
        if (independentAdmission)
            Objects.requireNonNull(closureService, "Assignment adoption coordinator")
                    .prepareAdoption(assignment);
        // Its lifecycle changes in fresh transactions after admission commits.
        // Do not retain a PREPARED instance in an open persistence context used
        // by the subsequent Subject projection.
        if (independentAdmission) entityManager.detach(assignment);
        return assignment;
    }

    public static void validateDates(
            Semester semester, LocalDate validFrom, LocalDate validUntilExclusive) {
        if (validFrom == null) {
            throw new BadRequestException("validFrom", "Дата начала обязательна");
        }
        if (semester.getDateFrom() == null || semester.getDateTo() == null) {
            throw new ConflictException("У семестра отсутствуют границы дат");
        }
        if (validFrom.isBefore(semester.getDateFrom()) || validFrom.isAfter(semester.getDateTo())) {
            throw new BadRequestException("validFrom", "Дата начала должна входить в семестр");
        }
        LocalDate effectiveEnd = semester.getDateTo().plusDays(1);
        if (validUntilExclusive != null
                && (!validUntilExclusive.isAfter(validFrom)
                        || validUntilExclusive.isAfter(effectiveEnd))) {
            throw new BadRequestException(
                    "validUntilExclusive",
                    "Дата окончания должна быть после начала и не позже конца семестра");
        }
    }

    public static LocalDate effectiveEnd(Assignment assignment, Semester semester) {
        return assignment.getValidUntilExclusive() != null
                ? assignment.getValidUntilExclusive()
                : semester.getDateTo().plusDays(1);
    }
}
