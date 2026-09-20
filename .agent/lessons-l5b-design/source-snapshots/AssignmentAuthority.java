package ru.rutcampustrack.academic.assignment;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.rutcampustrack.academic.contract.enums.SubjectType;
import ru.rutcampustrack.academic.contract.exception.ResourceNotFoundException;
import ru.rutcampustrack.academic.entity.Assignment;
import ru.rutcampustrack.academic.entity.Semester;
import ru.rutcampustrack.academic.entity.Subject;
import ru.rutcampustrack.academic.entity.User;
import ru.rutcampustrack.academic.exception.AccessDeniedException;
import ru.rutcampustrack.academic.exception.BadRequestException;
import ru.rutcampustrack.academic.exception.ConflictException;
import ru.rutcampustrack.academic.repository.AssignmentRepository;
import ru.rutcampustrack.academic.repository.SemesterRepository;
import ru.rutcampustrack.academic.repository.SubjectLessonTypeRepository;
import ru.rutcampustrack.academic.repository.SubjectRepository;
import ru.rutcampustrack.academic.repository.UserRepository;
import ru.rutcampustrack.academic.repository.UserRoleGrantRepository;

import java.time.LocalDate;
import java.util.Objects;

/**
 * The single V25 write authority for immutable assignment rows.
 *
 * <p>Callers authenticate their actor and group before reaching this class;
 * this class owns the shared referential, grant, date and overlap checks so
 * create-subject and add/assign-teacher paths cannot drift apart.</p>
 */
@Service
public class AssignmentAuthority {

    public static final String TEACHER_ROLE = "teacher";
    public static final String ACTIVE_STATUS = "active";

    private final AssignmentRepository assignmentRepository;
    private final SemesterRepository semesterRepository;
    private final SubjectRepository subjectRepository;
    private final SubjectLessonTypeRepository lessonTypeRepository;
    private final UserRepository userRepository;
    private final UserRoleGrantRepository grantRepository;

    public AssignmentAuthority(AssignmentRepository assignmentRepository,
                               SemesterRepository semesterRepository,
                               SubjectRepository subjectRepository,
                               SubjectLessonTypeRepository lessonTypeRepository,
                               UserRepository userRepository,
                               UserRoleGrantRepository grantRepository) {
        this.assignmentRepository = assignmentRepository;
        this.semesterRepository = semesterRepository;
        this.subjectRepository = subjectRepository;
        this.lessonTypeRepository = lessonTypeRepository;
        this.userRepository = userRepository;
        this.grantRepository = grantRepository;
    }

    /** Locks the semester row before any assignment validation or insert. */
    @Transactional
    public Semester lockSemester(Long semesterId) {
        if (semesterId == null || semesterId <= 0) {
            throw new BadRequestException("semesterId", "ID семестра должен быть положительным");
        }
        return semesterRepository.findByIdForUpdate(semesterId)
                .orElseThrow(() -> new ResourceNotFoundException("Semester", "id", semesterId));
    }

    @Transactional
    public Assignment create(Long teacherId,
                             Long subjectId,
                             Long groupId,
                             Long semesterId,
                             SubjectType lessonType,
                             LocalDate validFrom,
                             LocalDate validUntilExclusive) {
        Semester semester = lockSemester(semesterId);
        return createWithLockedSemester(teacherId, subjectId, groupId, semester,
                lessonType, validFrom, validUntilExclusive);
    }

    /** Creates after the caller has acquired the deterministic semester lock. */
    @Transactional
    public Assignment createWithLockedSemester(Long teacherId,
                                               Long subjectId,
                                               Long groupId,
                                               Semester semester,
                                               SubjectType lessonType,
                                               LocalDate validFrom,
                                               LocalDate validUntilExclusive) {
        if (teacherId == null || teacherId <= 0) {
            throw new BadRequestException("teacherId", "ID преподавателя должен быть положительным");
        }
        if (subjectId == null || subjectId <= 0) {
            throw new BadRequestException("subjectId", "ID предмета должен быть положительным");
        }
        if (groupId == null || groupId <= 0) {
            throw new BadRequestException("groupId", "ID группы должен быть положительным");
        }
        if (semester == null || semester.getId() == null) {
            throw new ResourceNotFoundException("Semester", "id", semester == null ? null : semester.getId());
        }
        if (lessonType == null) {
            throw new BadRequestException("lessonType", "Тип занятия обязателен");
        }
        Subject subject = subjectRepository.findById(subjectId)
                .orElseThrow(() -> new ResourceNotFoundException("Subject", "id", subjectId));
        if (!Objects.equals(subject.getGroupId(), groupId)) {
            throw new AccessDeniedException("Предмет не принадлежит указанной группе");
        }
        if (!lessonTypeRepository.existsBySubjectIdAndLessonType(subjectId, lessonType)) {
            throw new BadRequestException("lessonType", "Тип занятия не разрешён для предмета");
        }

        User teacher = userRepository.findById(teacherId)
                .orElseThrow(() -> new ResourceNotFoundException("Teacher", "id", teacherId));
        if (grantRepository.findByUserIdAndRoleAndStatus(teacher.getId(), TEACHER_ROLE, ACTIVE_STATUS)
                .isEmpty()) {
            throw new AccessDeniedException("У преподавателя нет активного права TEACHER");
        }

        validateDates(semester, validFrom, validUntilExclusive);
        assignmentRepository.findOverlapping(
                        teacherId, subjectId, groupId, semester.getId(), lessonType.name().toLowerCase(),
                        validFrom, validUntilExclusive)
                .ifPresent(existing -> {
                    throw new ConflictException("Назначение преподавателя пересекается с существующим");
                });
        return assignmentRepository.save(new Assignment(
                teacherId, subjectId, groupId, semester.getId(), lessonType,
                validFrom, validUntilExclusive));
    }

    public static void validateDates(Semester semester,
                                     LocalDate validFrom,
                                     LocalDate validUntilExclusive) {
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
                && (!validUntilExclusive.isAfter(validFrom) || validUntilExclusive.isAfter(effectiveEnd))) {
            throw new BadRequestException("validUntilExclusive",
                    "Дата окончания должна быть после начала и не позже конца семестра");
        }
    }

    public static LocalDate effectiveEnd(Assignment assignment, Semester semester) {
        return assignment.getValidUntilExclusive() != null
                ? assignment.getValidUntilExclusive()
                : semester.getDateTo().plusDays(1);
    }
}
