package ru.rutcampustrack.academic.subject;

import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.rutcampustrack.academic.assignment.AssignmentAuthority;
import ru.rutcampustrack.academic.contract.dto.subject.AddSubjectTeacherRequest;
import ru.rutcampustrack.academic.contract.dto.subject.CreateSubjectRequest;
import ru.rutcampustrack.academic.contract.dto.subject.InitialAssignmentRequest;
import ru.rutcampustrack.academic.contract.dto.subject.UpdateSubjectRequest;
import ru.rutcampustrack.academic.contract.enums.UserRole;
import ru.rutcampustrack.academic.contract.enums.SubjectType;
import ru.rutcampustrack.academic.contract.exception.ResourceNotFoundException;
import ru.rutcampustrack.academic.entity.Assignment;
import ru.rutcampustrack.academic.entity.Semester;
import ru.rutcampustrack.academic.entity.Subject;
import ru.rutcampustrack.academic.entity.SubjectLessonType;
import ru.rutcampustrack.academic.event.SubjectDeletedEvent;
import ru.rutcampustrack.academic.exception.AccessDeniedException;
import ru.rutcampustrack.academic.exception.AssignmentClosureNotReadyException;
import ru.rutcampustrack.academic.exception.BadRequestException;
import ru.rutcampustrack.academic.exception.ConflictException;
import ru.rutcampustrack.academic.grpc.ScheduleGrpcClient;
import ru.rutcampustrack.academic.repository.AssignmentRepository;
import ru.rutcampustrack.academic.repository.SemesterRepository;
import ru.rutcampustrack.academic.repository.SubjectLessonTypeRepository;
import ru.rutcampustrack.academic.repository.SubjectRepository;
import ru.rutcampustrack.academic.repository.UserRoleGrantRepository;
import ru.rutcampustrack.academic.security.RequestContext;
import ru.rutcampustrack.schedule.grpc.CountSubjectReferencesResponse;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

@Service
public class SubjectService {

    private static final ZoneId MOSCOW = ZoneId.of("Europe/Moscow");

    private final SubjectRepository subjectRepository;
    private final SubjectLessonTypeRepository lessonTypeRepository;
    private final AssignmentRepository assignmentRepository;
    private final SemesterRepository semesterRepository;
    private final AssignmentAuthority assignmentAuthority;
    private final UserRoleGrantRepository grantRepository;
    private final RequestContext requestContext;
    private final ScheduleGrpcClient scheduleGrpcClient;
    private final ApplicationEventPublisher eventPublisher;

    public SubjectService(SubjectRepository subjectRepository,
                          SubjectLessonTypeRepository lessonTypeRepository,
                          AssignmentRepository assignmentRepository,
                          SemesterRepository semesterRepository,
                          AssignmentAuthority assignmentAuthority,
                          UserRoleGrantRepository grantRepository,
                          RequestContext requestContext,
                          ScheduleGrpcClient scheduleGrpcClient,
                          ApplicationEventPublisher eventPublisher) {
        this.subjectRepository = subjectRepository;
        this.lessonTypeRepository = lessonTypeRepository;
        this.assignmentRepository = assignmentRepository;
        this.semesterRepository = semesterRepository;
        this.assignmentAuthority = assignmentAuthority;
        this.grantRepository = grantRepository;
        this.requestContext = requestContext;
        this.scheduleGrpcClient = scheduleGrpcClient;
        this.eventPublisher = eventPublisher;
    }

    private void requireHeadman() {
        if (!requestContext.isHeadman()) {
            throw new AccessDeniedException("Только староста может управлять предметами");
        }
    }

    private Long requireHeadmanGroupId() {
        requireHeadman();
        Long groupId = requestContext.getGroupId();
        if (groupId == null || groupId <= 0) {
            throw new AccessDeniedException("Группа старосты не определена в контексте запроса");
        }
        return groupId;
    }

    private void assertSubjectBelongsToHeadmanGroup(Subject subject, Long groupId) {
        if (!Objects.equals(subject.getGroupId(), groupId)) {
            throw new AccessDeniedException("Предмет не принадлежит вашей группе");
        }
    }

    private void assertCanReadSubject(Subject subject) {
        UserRole role = requestContext.getRole();
        if (role == UserRole.ADMIN || role == UserRole.TEACHER) {
            return;
        }
        Long ownGroupId = requestContext.getGroupId();
        if (ownGroupId == null || !ownGroupId.equals(subject.getGroupId())) {
            throw new AccessDeniedException("Предмет принадлежит другой группе");
        }
    }

    @Transactional
    public Subject createSubject(CreateSubjectRequest request) {
        Long groupId = requireHeadmanGroupId();
        if (request.teacherIds() != null && !request.teacherIds().isEmpty()) {
            throw new BadRequestException("teacherIds",
                    "Используйте initialAssignments с полными данными назначения");
        }
        List<SubjectType> lessonTypes = canonicalLessonTypes(request.type(), request.lessonTypes());
        List<InitialAssignmentRequest> initialAssignments = request.initialAssignments() == null
                ? List.of() : request.initialAssignments();

        // Every semester is locked in ascending ID order before any subject or
        // assignment row is written. This serializes create versus date edits.
        List<Long> semesterIds = initialAssignments.stream()
                .map(InitialAssignmentRequest::semesterId)
                .distinct()
                .sorted()
                .toList();
        java.util.Map<Long, Semester> lockedSemesters = new java.util.LinkedHashMap<>();
        for (Long semesterId : semesterIds) {
            lockedSemesters.put(semesterId, assignmentAuthority.lockSemester(semesterId));
        }

        Subject subject = new Subject();
        subject.setName(request.name());
        subject.setType(request.type());
        subject.setGroupId(groupId);
        Subject saved = subjectRepository.save(subject);
        lessonTypeRepository.saveAll(lessonTypes.stream()
                .map(type -> new SubjectLessonType(saved.getId(), type))
                .toList());

        for (InitialAssignmentRequest initial : initialAssignments) {
            assignmentAuthority.createWithLockedSemester(
                    initial.teacherId(), saved.getId(), groupId,
                    lockedSemesters.get(initial.semesterId()), initial.lessonType(),
                    initial.validFrom(), initial.validUntilExclusive());
        }
        return saved;
    }

    private static List<SubjectType> canonicalLessonTypes(SubjectType scalarType,
                                                           List<SubjectType> requested) {
        if (scalarType == null) {
            throw new BadRequestException("type", "Тип предмета обязателен");
        }
        if (requested == null) {
            return List.of(scalarType);
        }
        if (requested.isEmpty() || requested.size() > 3
                || requested.stream().anyMatch(Objects::isNull)) {
            throw new BadRequestException("lessonTypes", "Предмет должен иметь от 1 до 3 типов занятий");
        }
        Set<SubjectType> distinct = new LinkedHashSet<>(requested);
        if (distinct.size() != requested.size() || !distinct.contains(scalarType)) {
            throw new BadRequestException("lessonTypes",
                    "Тип предмета должен входить в список уникальных типов занятий");
        }
        return List.copyOf(distinct);
    }

    @Cacheable(value = "subject", key = "#id")
    @Transactional(readOnly = true)
    public Subject getSubject(Long id) {
        return subjectRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Subject", "id", id));
    }

    @Transactional(readOnly = true)
    public Subject getSubjectForRead(Long id) {
        Subject subject = getSubject(id);
        assertCanReadSubject(subject);
        return subject;
    }

    @Transactional(readOnly = true)
    public Page<Subject> listSubjects(Pageable pageable) {
        UserRole role = requestContext.getRole();
        if (role == UserRole.ADMIN) {
            return subjectRepository.findAll(pageable);
        }
        if (role == UserRole.TEACHER) {
            Long teacherId = requestContext.getUserId();
            if (teacherId == null) {
                return Page.empty(pageable);
            }
            if (grantRepository.findByUserIdAndRoleAndStatus(
                    teacherId, AssignmentAuthority.TEACHER_ROLE, AssignmentAuthority.ACTIVE_STATUS).isEmpty()) {
                throw new AccessDeniedException("У преподавателя нет активного права TEACHER");
            }
            Semester activeSemester = requireActiveSemester();
            LocalDate today = LocalDate.now(MOSCOW);
            if (today.isBefore(activeSemester.getDateFrom()) || today.isAfter(activeSemester.getDateTo())) {
                return Page.empty(pageable);
            }
            return subjectRepository.findAssignedToTeacher(teacherId, activeSemester.getId(), today, pageable);
        }
        Long groupId = requestContext.getGroupId();
        return groupId == null ? Page.empty(pageable) : subjectRepository.findByGroupId(groupId, pageable);
    }

    private Semester requireActiveSemester() {
        return semesterRepository.findByIsActiveTrue()
                .orElseThrow(() -> new ConflictException("Активный семестр не найден"));
    }

    @CacheEvict(value = "subject", key = "#id")
    @Transactional
    public Subject updateSubject(Long id, UpdateSubjectRequest request) {
        Long groupId = requireHeadmanGroupId();
        Subject subject = lockSubject(id);
        assertSubjectBelongsToHeadmanGroup(subject, groupId);
        List<SubjectType> requestedTypes = canonicalLessonTypes(request.type(), request.lessonTypes());
        List<SubjectLessonType> existing = lessonTypeRepository.findBySubjectId(id);
        for (SubjectLessonType row : existing) {
            if (!requestedTypes.contains(row.getLessonType())
                    && assignmentRepository.existsBySubjectIdAndLessonType(id, row.getLessonType())) {
                throw new ConflictException("Нельзя удалить тип занятия с историей назначений");
            }
        }
        Set<SubjectType> existingTypes = existing.stream()
                .map(SubjectLessonType::getLessonType).collect(java.util.stream.Collectors.toSet());
        lessonTypeRepository.deleteAll(existing.stream()
                .filter(row -> !requestedTypes.contains(row.getLessonType()))
                .toList());
        lessonTypeRepository.saveAll(requestedTypes.stream()
                .filter(type -> !existingTypes.contains(type))
                .map(type -> new SubjectLessonType(id, type)).toList());
        subject.setName(request.name());
        subject.setType(request.type());
        return subjectRepository.save(subject);
    }

    @CacheEvict(value = "subject", key = "#id")
    @Transactional
    public void deleteSubject(Long id, boolean force) {
        Long groupId = requireHeadmanGroupId();
        Subject subject = lockSubject(id);
        assertSubjectBelongsToHeadmanGroup(subject, groupId);
        if (assignmentRepository.existsBySubjectId(id)) {
            throw new AssignmentClosureNotReadyException();
        }
        CountSubjectReferencesResponse refs = scheduleGrpcClient.countSubjectReferences(id);
        if (refs.getScheduleItemsCount() > 0 || refs.getOneOffLessonsCount() > 0
                || refs.getNonPlannedLessonsCount() > 0 || refs.getTotalLessonsCount() > 0) {
            throw new ConflictException("Предмет используется расписанием и не может быть удалён");
        }
        lessonTypeRepository.deleteBySubjectId(id);
        subjectRepository.delete(subject);
        eventPublisher.publishEvent(new SubjectDeletedEvent(this, id));
    }

    @Transactional
    public Assignment addTeacher(Long subjectId, Long teacherId, AddSubjectTeacherRequest request) {
        Long groupId = requireHeadmanGroupId();
        Subject subject = lockSubject(subjectId);
        assertSubjectBelongsToHeadmanGroup(subject, groupId);
        Semester semester = assignmentAuthority.lockSemester(request.semesterId());
        if (!Objects.equals(subjectId, subject.getId())) {
            throw new ResourceNotFoundException("Subject", "id", subjectId);
        }
        return assignmentAuthority.createWithLockedSemester(
                teacherId, subjectId, groupId, semester, request.lessonType(),
                request.validFrom(), request.validUntilExclusive());
    }

    @Transactional
    public void removeTeacher(Long subjectId,
                              Long teacherId,
                              Long assignmentId,
                              LocalDate requestedEnd) {
        Long groupId = requireHeadmanGroupId();
        Subject subject = lockSubject(subjectId);
        assertSubjectBelongsToHeadmanGroup(subject, groupId);
        if (assignmentId == null || requestedEnd == null) {
            throw new BadRequestException("assignmentId/validUntilExclusive",
                    "Идентификатор назначения и дата окончания обязательны");
        }
        Assignment assignment = assignmentRepository
                .findByIdAndSubjectIdAndGroupIdAndTeacherId(assignmentId, subjectId, groupId, teacherId)
                .orElseThrow(() -> new ResourceNotFoundException("Assignment", "id", assignmentId));
        throw new AssignmentClosureNotReadyException();
    }

    private Subject lockSubject(Long id) {
        return subjectRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new ResourceNotFoundException("Subject", "id", id));
    }
}
