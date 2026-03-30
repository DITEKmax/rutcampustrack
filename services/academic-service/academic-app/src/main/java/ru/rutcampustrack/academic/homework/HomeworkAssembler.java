package ru.rutcampustrack.academic.homework;

import org.springframework.hateoas.EntityModel;
import org.springframework.hateoas.server.RepresentationModelAssembler;
import org.springframework.stereotype.Component;
import ru.rutcampustrack.academic.contract.dto.homework.HomeworkResponse;
import ru.rutcampustrack.academic.entity.Homework;

import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.*;

@Component
public class HomeworkAssembler implements RepresentationModelAssembler<Homework, EntityModel<HomeworkResponse>> {

    @Override
    public EntityModel<HomeworkResponse> toModel(Homework homework) {
        return toModel(homework, false);
    }

    /**
     * Overload for per-student context — sets the completed flag.
     * Default toModel(Homework) sets completed=false (headman/admin views).
     */
    public EntityModel<HomeworkResponse> toModel(Homework homework, boolean completed) {
        HomeworkResponse response = new HomeworkResponse();
        response.setId(homework.getId());
        response.setTitle(homework.getTitle());
        response.setDescription(homework.getDescription());
        response.setLink(homework.getLink());
        response.setSubjectId(homework.getSubjectId());
        response.setGroupId(homework.getGroupId());
        response.setSemesterId(homework.getSemesterId());
        response.setPublishedBy(homework.getPublishedBy());
        response.setCompleted(completed);
        response.setCreatedAt(homework.getCreatedAt());

        return EntityModel.of(response,
                linkTo(methodOn(HomeworkController.class).getHomework(homework.getId())).withSelfRel()
        );
    }
}
