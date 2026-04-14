package ru.rutcampustrack.academic.subject;

import org.springframework.hateoas.EntityModel;
import org.springframework.hateoas.server.RepresentationModelAssembler;
import org.springframework.stereotype.Component;
import ru.rutcampustrack.academic.contract.dto.subject.SubjectResponse;
import ru.rutcampustrack.academic.entity.Subject;
import ru.rutcampustrack.academic.entity.TeacherSubjectGroup;
import ru.rutcampustrack.academic.repository.SemesterRepository;
import ru.rutcampustrack.academic.repository.TeacherSubjectGroupRepository;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.*;

@Component
public class SubjectAssembler implements RepresentationModelAssembler<Subject, EntityModel<SubjectResponse>> {

    private final TeacherSubjectGroupRepository tsgRepository;
    private final SemesterRepository semesterRepository;

    public SubjectAssembler(TeacherSubjectGroupRepository tsgRepository,
                            SemesterRepository semesterRepository) {
        this.tsgRepository = tsgRepository;
        this.semesterRepository = semesterRepository;
    }

    @Override
    public EntityModel<SubjectResponse> toModel(Subject subject) {
        SubjectResponse response = toResponse(subject);
        return EntityModel.of(response,
                linkTo(methodOn(SubjectController.class).getSubject(subject.getId())).withSelfRel()
        );
    }

    public SubjectResponse toResponse(Subject subject) {
        return new SubjectResponse(
                subject.getId(),
                subject.getName(),
                subject.getType(),
                subject.getGroupId(),
                loadTeacherIds(subject),
                subject.getCreatedAt()
        );
    }

    /**
     * Phase 60-01 / D-19: список преподавателей предмета на активный семестр.
     * Если активного семестра нет — возвращаем пустой список (без ошибки).
     */
    private List<Long> loadTeacherIds(Subject subject) {
        Optional<Long> semesterId = semesterRepository.findByIsActiveTrue()
                .map(s -> s.getId());
        if (semesterId.isEmpty() || subject.getGroupId() == null) {
            return Collections.emptyList();
        }
        return tsgRepository.findByGroupIdAndSemesterId(subject.getGroupId(), semesterId.get())
                .stream()
                .filter(tsg -> tsg.getSubjectId().equals(subject.getId()))
                .map(TeacherSubjectGroup::getTeacherId)
                .toList();
    }
}
