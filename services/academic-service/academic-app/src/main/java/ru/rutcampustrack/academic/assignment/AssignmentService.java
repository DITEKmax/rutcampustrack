package ru.rutcampustrack.academic.assignment;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.rutcampustrack.academic.contract.dto.assignment.AssignTeacherRequest;
import ru.rutcampustrack.academic.contract.enums.UserRole;
import ru.rutcampustrack.academic.contract.exception.ResourceNotFoundException;
import ru.rutcampustrack.academic.entity.Semester;
import ru.rutcampustrack.academic.entity.TeacherSubjectGroup;
import ru.rutcampustrack.academic.entity.User;
import ru.rutcampustrack.academic.exception.AccessDeniedException;
import ru.rutcampustrack.academic.exception.BadRequestException;
import ru.rutcampustrack.academic.exception.ConflictException;
import ru.rutcampustrack.academic.repository.SemesterRepository;
import ru.rutcampustrack.academic.repository.TeacherSubjectGroupRepository;
import ru.rutcampustrack.academic.repository.UserRepository;
import ru.rutcampustrack.academic.security.RequestContext;

import java.util.List;

@Service
public class AssignmentService {

    private final TeacherSubjectGroupRepository assignmentRepository;
    private final UserRepository userRepository;
    private final SemesterRepository semesterRepository;
    private final RequestContext requestContext;

    public AssignmentService(TeacherSubjectGroupRepository assignmentRepository,
                              UserRepository userRepository,
                              SemesterRepository semesterRepository,
                              RequestContext requestContext) {
        this.assignmentRepository = assignmentRepository;
        this.userRepository = userRepository;
        this.semesterRepository = semesterRepository;
        this.requestContext = requestContext;
    }

    private void requireHeadman() {
        if (!requestContext.isHeadman()) {
            throw new AccessDeniedException("Только староста может управлять назначениями преподавателей");
        }
    }

    @Transactional
    public TeacherSubjectGroup assignTeacher(AssignTeacherRequest request) {
        requireHeadman();

        // Find teacher by employee number
        User teacher = userRepository.findByEmployeeNumber(request.employeeNumber())
                .orElseThrow(() -> new ResourceNotFoundException("Teacher", "employeeNumber", request.employeeNumber()));

        // Validate teacher role
        if (teacher.getRole() != UserRole.TEACHER) {
            throw new BadRequestException("Пользователь с табельным номером " + request.employeeNumber() + " не является преподавателем");
        }

        // Check no existing assignment
        assignmentRepository.findByTeacherIdAndSubjectIdAndGroupIdAndSemesterId(
                teacher.getId(), request.subjectId(), request.groupId(), request.semesterId())
                .ifPresent(existing -> {
                    throw new ConflictException("Назначение уже существует для данного преподавателя, предмета, группы и семестра");
                });

        TeacherSubjectGroup assignment = new TeacherSubjectGroup(
                teacher.getId(), request.subjectId(), request.groupId(), request.semesterId()
        );
        return assignmentRepository.save(assignment);
    }

    @Transactional(readOnly = true)
    public Page<TeacherSubjectGroup> listAssignments(Long groupId, Long semesterId, Pageable pageable) {
        List<TeacherSubjectGroup> list = assignmentRepository.findByGroupIdAndSemesterId(groupId, semesterId);
        int start = (int) pageable.getOffset();
        int end = Math.min(start + pageable.getPageSize(), list.size());
        List<TeacherSubjectGroup> page = start >= list.size() ? List.of() : list.subList(start, end);
        return new PageImpl<>(page, pageable, list.size());
    }

    @Transactional
    public void removeAssignment(Long id) {
        requireHeadman();
        TeacherSubjectGroup assignment = assignmentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Assignment", "id", id));
        assignmentRepository.delete(assignment);
    }

    @Transactional(readOnly = true)
    public Page<TeacherSubjectGroup> getMyAssignments(Pageable pageable) {
        Long teacherId = requestContext.getUserId();
        Semester activeSemester = semesterRepository.findByIsActiveTrue()
                .orElseThrow(() -> new ResourceNotFoundException("Semester", "isActive", true));
        List<TeacherSubjectGroup> list = assignmentRepository.findByTeacherIdAndSemesterId(teacherId, activeSemester.getId());
        int start = (int) pageable.getOffset();
        int end = Math.min(start + pageable.getPageSize(), list.size());
        List<TeacherSubjectGroup> page = start >= list.size() ? List.of() : list.subList(start, end);
        return new PageImpl<>(page, pageable, list.size());
    }
}
