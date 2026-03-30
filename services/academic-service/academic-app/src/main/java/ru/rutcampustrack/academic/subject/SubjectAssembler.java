package ru.rutcampustrack.academic.subject;

import org.springframework.hateoas.EntityModel;
import org.springframework.hateoas.server.RepresentationModelAssembler;
import org.springframework.stereotype.Component;
import ru.rutcampustrack.academic.contract.dto.subject.SubjectResponse;
import ru.rutcampustrack.academic.entity.Subject;

import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.*;

@Component
public class SubjectAssembler implements RepresentationModelAssembler<Subject, EntityModel<SubjectResponse>> {

    @Override
    public EntityModel<SubjectResponse> toModel(Subject subject) {
        SubjectResponse response = new SubjectResponse(
                subject.getId(),
                subject.getName(),
                subject.getType(),
                subject.getCreatedAt()
        );
        return EntityModel.of(response,
                linkTo(methodOn(SubjectController.class).getSubject(subject.getId())).withSelfRel()
        );
    }
}
