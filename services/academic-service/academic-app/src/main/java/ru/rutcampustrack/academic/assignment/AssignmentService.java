package ru.rutcampustrack.academic.assignment;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.rutcampustrack.academic.contract.dto.assignment.AssignTeacherRequest;
import ru.rutcampustrack.academic.contract.enums.UserRole;
import ru.rutcampustrack.academic.contract.exception.ResourceNotFoundException;
import ru.rutcampustrack.academic.entity.Assignment;
import ru.rutcampustrack.academic.entity.Semester;
import ru.rutcampustrack.academic.entity.Subject;
import ru.rutcampustrack.academic.entity.User;
import ru.rutcampustrack.academic.exception.AccessDeniedException;
import ru.rutcampustrack.academic.exception.AssignmentClosureNotReadyException;
import ru.rutcampustrack.academic.exception.BadRequestException;
import ru.rutcampustrack.academic.repository.AssignmentRepository;
import ru.rutcampustrack.academic.repository.SemesterRepository;
import ru.rutcampustrack.academic.repository.SubjectRepository;
import ru.rutcampustrack.academic.repository.UserRepository;
import ru.rutcampustrack.academic.repository.UserRoleGrantRepository;
import ru.rutcampustrack.academic.security.RequestContext;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.List;

@Service
public class AssignmentService {

    private static final ZoneId MOSCOW = ZoneId.of("Europe/Moscow");

    private final AssignmentRepository assignmentRepository;
    private final AssignmentAuthority assignmentAuthority;
    private final UserRepository userRepository;
    private final SemesterRepository semesterRepository;
    private final SubjectRepository subjectRepository;
    private final UserRoleGrantRepository grantRepository;
    private final RequestContext requestContext;

    public AssignmentService(AssignmentRepository assignmentRepository,
                             AssignmentAuthority assignmentAuthority,
                             UserRepository userRepository,
                             SemesterRepository semesterRepository,
                             SubjectRepository subjectRepository,
                             UserRoleGrantRepository grantRepository,
                             RequestContext requestContext) {
        this.assignmentRepository = assignmentRepository;
        this.assignmentAuthority = assignmentAuthority;
        this.userRepository = userRepository;
        this.semesterRepository = semesterRepository;
        this.subjectRepository = subjectRepository;
        this.grantRepository = grantRepository;
        this.requestContext = requestContext;
    }

    private void requireHeadman() {
        if (!requestContext.isHeadman()) {
            throw new AccessDeniedException("Только староста может управлять назначениями преподавателей");
        }
    }

    private void assertOwnGroup(Long groupId) {
        if (requestContext.getRole() == UserRole.ADMIN) {
            return;
        }
        Long ownGroupId = requestContext.getGroupId();
        if (ownGroupId == null || !ownGroupId.equals(groupId)) {
            throw new AccessDeniedException("Назначение принадлежит другой группе");
        }
    }

    @Transactional
    public Assignment assignTeacher(AssignTeacherRequest request) {
        requireHeadman();
        assertOwnGroup(request.groupId());
        if (request.lessonType() == null || request.validFrom() == null) {
            throw new BadRequestException("lessonType/validFrom", "Тип занятия и дата начала обязательны");
        }
        User teacher = userRepository.findByEmployeeNumber(request.employeeNumber())
                .orElseThrow(() -> new ResourceNotFoundException("Teacher", "employeeNumber", request.employeeNumber()));
        Subject subject = subjectRepository.findById(request.subjectId())
                .orElseThrow(() -> new ResourceNotFoundException("Subject", "id", request.subjectId()));
        if (!subject.getGroupId().equals(request.groupId())) {
            throw new AccessDeniedException("Предмет не принадлежит вашей группе");
        }
        return assignmentAuthority.create(
                teacher.getId(), request.subjectId(), request.groupId(), request.semesterId(),
                request.lessonType(), request.validFrom(), request.validUntilExclusive());
    }

    @Transactional(readOnly = true)
    public Page<Assignment> listAssignments(Long groupId, Long semesterId, Pageable pageable) {
        assertOwnGroup(groupId);
        List<Assignment> list = assignmentRepository.findByGroupIdAndSemesterId(groupId, semesterId)
                .stream()
                .filter(a -> "ACTIVE".equals(a.getLifecycleState()))
                .sorted(Comparator.comparing(Assignment::getSemesterId)
                        .thenComparing(a -> a.getLessonType().name())
                        .thenComparing(Assignment::getTeacherId)
                        .thenComparing(Assignment::getValidFrom)
                        .thenComparing(Assignment::getId))
                .toList();
        return page(list, pageable);
    }

    @Transactional
    public void removeAssignment(Long id, LocalDate requestedEnd) {
        requireHeadman();
        if (requestedEnd == null) {
            throw new BadRequestException("validUntilExclusive", "Дата окончания обязательна");
        }
        Assignment assignment = assignmentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Assignment", "id", id));
        assertOwnGroup(assignment.getGroupId());
        throw new AssignmentClosureNotReadyException();
    }

    @Transactional(readOnly = true)
    public Page<Assignment> getMyAssignments(Pageable pageable) {
        Long teacherId = requestContext.getUserId();
        if (teacherId == null || teacherId <= 0) {
            throw new AccessDeniedException("Идентификатор преподавателя не определён");
        }
        if (grantRepository.findByUserIdAndRoleAndStatus(
                teacherId, AssignmentAuthority.TEACHER_ROLE, AssignmentAuthority.ACTIVE_STATUS).isEmpty()) {
            throw new AccessDeniedException("У преподавателя нет активного права TEACHER");
        }
        Semester activeSemester = semesterRepository.findByIsActiveTrue()
                .orElseThrow(() -> new ResourceNotFoundException("Semester", "isActive", true));
        LocalDate today = LocalDate.now(MOSCOW);
        if (today.isBefore(activeSemester.getDateFrom()) || today.isAfter(activeSemester.getDateTo())) {
            return Page.empty(pageable);
        }
        List<Assignment> list = assignmentRepository.findByTeacherIdAndSemesterId(teacherId, activeSemester.getId())
                .stream()
                .filter(a -> "ACTIVE".equals(a.getLifecycleState()))
                .filter(a -> !a.getValidFrom().isAfter(today))
                .filter(a -> today.isBefore(AssignmentAuthority.effectiveEnd(a, activeSemester)))
                .sorted(Comparator.comparing(Assignment::getSemesterId)
                        .thenComparing(a -> a.getLessonType().name())
                        .thenComparing(Assignment::getTeacherId)
                        .thenComparing(Assignment::getValidFrom)
                        .thenComparing(Assignment::getId))
                .toList();
        return page(list, pageable);
    }

    private static Page<Assignment> page(List<Assignment> list, Pageable pageable) {
        int start = (int) Math.min(pageable.getOffset(), list.size());
        int end = Math.min(start + pageable.getPageSize(), list.size());
        return new PageImpl<>(list.subList(start, end), pageable, list.size());
    }
}
