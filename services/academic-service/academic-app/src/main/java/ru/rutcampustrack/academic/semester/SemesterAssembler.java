package ru.rutcampustrack.academic.semester;

import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.linkTo;
import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.methodOn;

import org.springframework.hateoas.EntityModel;
import org.springframework.hateoas.server.RepresentationModelAssembler;
import org.springframework.stereotype.Component;
import ru.rutcampustrack.academic.contract.dto.semester.SemesterResponse;
import ru.rutcampustrack.academic.entity.Semester;

/** Assembles Semester entity into HATEOAS EntityModel<SemesterResponse>. */
@Component
public class SemesterAssembler
        implements RepresentationModelAssembler<Semester, EntityModel<SemesterResponse>> {

    @Override
    public EntityModel<SemesterResponse> toModel(Semester entity) {
        SemesterResponse response = toResponse(entity);
        return EntityModel.of(
                response,
                linkTo(methodOn(SemesterController.class).getSemester(entity.getId()))
                        .withSelfRel());
    }

    public SemesterResponse toResponse(Semester entity) {
        SemesterResponse response =
                new SemesterResponse(
                        entity.getId(),
                        entity.getName(),
                        entity.getDateFrom(),
                        entity.getDateTo(),
                        entity.isActive(),
                        entity.getCreatedAt(),
                        entity.getSemesterType(),
                        entity.getAcademicYear(),
                        entity.isArchived(),
                        entity.getArchiveTransition(),
                        entity.getStateVersion(),
                        entity.isReleasePending());
        response.setActivationScheduled(entity.isActivationScheduled());
        return response;
    }
}
