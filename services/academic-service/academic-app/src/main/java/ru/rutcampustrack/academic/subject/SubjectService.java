package ru.rutcampustrack.academic.subject;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.rutcampustrack.academic.contract.dto.subject.CreateSubjectRequest;
import ru.rutcampustrack.academic.contract.dto.subject.UpdateSubjectRequest;
import ru.rutcampustrack.academic.contract.enums.UserRole;
import ru.rutcampustrack.academic.entity.Semester;
import ru.rutcampustrack.academic.entity.Subject;
import ru.rutcampustrack.academic.entity.TeacherSubjectGroup;
import ru.rutcampustrack.academic.exception.AccessDeniedException;
import ru.rutcampustrack.academic.exception.ConflictException;
import ru.rutcampustrack.academic.repository.SemesterRepository;
import ru.rutcampustrack.academic.repository.SubjectRepository;
import ru.rutcampustrack.academic.repository.TeacherSubjectGroupRepository;
import ru.rutcampustrack.academic.security.RequestContext;
import ru.rutcampustrack.academic.contract.exception.ResourceNotFoundException;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Service
public class SubjectService {

    private final SubjectRepository subjectRepository;
    private final TeacherSubjectGroupRepository tsgRepository;
    private final SemesterRepository semesterRepository;
    private final RequestContext requestContext;

    public SubjectService(SubjectRepository subjectRepository,
                          TeacherSubjectGroupRepository tsgRepository,
                          SemesterRepository semesterRepository,
                          RequestContext requestContext) {
        this.subjectRepository = subjectRepository;
        this.tsgRepository = tsgRepository;
        this.semesterRepository = semesterRepository;
        this.requestContext = requestContext;
    }

    private void requireHeadman() {
        if (!requestContext.isHeadman()) {
            throw new AccessDeniedException("Только староста может управлять предметами");
        }
    }

    private Long requireHeadmanGroupId() {
        requireHeadman();
        Long groupId = requestContext.getGroupId();
        if (groupId == null) {
            throw new AccessDeniedException("Группа старосты не определена в контексте запроса");
        }
        return groupId;
    }

    private Long requireActiveSemesterId() {
        Semester active = semesterRepository.findByIsActiveTrue()
                .orElseThrow(() -> new ConflictException("Активный семестр не найден"));
        return active.getId();
    }

    private void assertSubjectBelongsToHeadmanGroup(Subject subject, Long headmanGroupId) {
        if (!Objects.equals(subject.getGroupId(), headmanGroupId)) {
            throw new AccessDeniedException("Предмет не принадлежит вашей группе");
        }
    }

    /**
     * Phase 60-01 / D-02: атомарное создание предмета + N записей
     * teacher_subject_groups на активный семестр. При сбое одного insert —
     * полный откат транзакции.
     */
    @Transactional
    public Subject createSubject(CreateSubjectRequest request) {
        Long groupId = requireHeadmanGroupId();

        Subject subject = new Subject();
        subject.setName(request.name());
        subject.setType(request.type());
        subject.setGroupId(groupId);
        Subject saved = subjectRepository.save(subject);

        List<Long> teacherIds = request.teacherIds();
        if (teacherIds != null && !teacherIds.isEmpty()) {
            Long semesterId = requireActiveSemesterId();
            List<TeacherSubjectGroup> assignments = new ArrayList<>(teacherIds.size());
            for (Long teacherId : teacherIds) {
                assignments.add(new TeacherSubjectGroup(teacherId, saved.getId(), groupId, semesterId));
            }
            tsgRepository.saveAll(assignments);
        }

        return saved;
    }

    @Transactional(readOnly = true)
    public Subject getSubject(Long id) {
        return subjectRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Subject", "id", id));
    }

    /**
     * Phase 60-01: ADMIN видит все предметы; HEADMAN — только своей группы.
     */
    @Transactional(readOnly = true)
    public Page<Subject> listSubjects(Pageable pageable) {
        if (requestContext.getRole() == UserRole.ADMIN) {
            return subjectRepository.findAll(pageable);
        }
        Long groupId = requestContext.getGroupId();
        if (groupId == null) {
            return Page.empty(pageable);
        }
        return subjectRepository.findByGroupId(groupId, pageable);
    }

    @Transactional
    public Subject updateSubject(Long id, UpdateSubjectRequest request) {
        Long groupId = requireHeadmanGroupId();
        Subject subject = getSubject(id);
        assertSubjectBelongsToHeadmanGroup(subject, groupId);
        // groupId неизменяем после создания (D-02)
        subject.setName(request.name());
        subject.setType(request.type());
        return subjectRepository.save(subject);
    }

    @Transactional
    public void deleteSubject(Long id) {
        Long groupId = requireHeadmanGroupId();
        Subject subject = getSubject(id);
        assertSubjectBelongsToHeadmanGroup(subject, groupId);
        subjectRepository.delete(subject);
    }

    /**
     * Phase 60-01 / D-19: добавить преподавателя к предмету текущей группы старосты
     * на активный семестр. Идемпотентность нарушается осознанно — повторный
     * вызов возвращает 409 Conflict (UNIQUE constraint).
     */
    @Transactional
    public void addTeacher(Long subjectId, Long teacherId) {
        Long groupId = requireHeadmanGroupId();
        Subject subject = getSubject(subjectId);
        assertSubjectBelongsToHeadmanGroup(subject, groupId);

        Long semesterId = requireActiveSemesterId();

        tsgRepository.findByTeacherIdAndSubjectIdAndGroupIdAndSemesterId(
                        teacherId, subjectId, groupId, semesterId)
                .ifPresent(existing -> {
                    throw new ConflictException(
                            "Преподаватель уже назначен на предмет в текущем семестре");
                });

        tsgRepository.save(new TeacherSubjectGroup(teacherId, subjectId, groupId, semesterId));
    }

    /**
     * Phase 60-01 / D-19: удалить назначение преподавателя на предмет в активном
     * семестре. Если назначения нет — 404.
     */
    @Transactional
    public void removeTeacher(Long subjectId, Long teacherId) {
        Long groupId = requireHeadmanGroupId();
        Subject subject = getSubject(subjectId);
        assertSubjectBelongsToHeadmanGroup(subject, groupId);

        Long semesterId = requireActiveSemesterId();

        var assignment = tsgRepository.findByTeacherIdAndSubjectIdAndGroupIdAndSemesterId(
                        teacherId, subjectId, groupId, semesterId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "TeacherSubjectGroup",
                        "teacher/subject/group/semester",
                        teacherId + "/" + subjectId + "/" + groupId + "/" + semesterId));

        tsgRepository.delete(assignment);
    }
}
