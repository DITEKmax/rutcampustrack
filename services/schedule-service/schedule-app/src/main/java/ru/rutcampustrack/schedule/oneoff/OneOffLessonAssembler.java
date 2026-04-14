package ru.rutcampustrack.schedule.oneoff;

import org.springframework.hateoas.EntityModel;
import org.springframework.stereotype.Component;
import ru.rutcampustrack.schedule.contract.dto.oneoff.OneOffLessonResponse;
import ru.rutcampustrack.schedule.oneoff.entity.OneOffLesson;

import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.linkTo;
import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.methodOn;

/**
 * Converts {@link OneOffLesson} JPA entities to HATEOAS {@link EntityModel}s.
 */
@Component
public class OneOffLessonAssembler {

    public EntityModel<OneOffLessonResponse> toModel(OneOffLesson entity) {
        OneOffLessonResponse body = new OneOffLessonResponse(
                entity.getId(),
                entity.getGroupId(),
                entity.getSubjectId(),
                entity.getSemesterId(),
                entity.getDate(),
                entity.getLessonNumber(),
                entity.getClassroom(),
                entity.getCreatedBy(),
                entity.getCreatedAt()
        );
        return EntityModel.of(body,
                linkTo(methodOn(OneOffLessonController.class)
                        .deleteOneOffLesson(entity.getId())).withSelfRel());
    }
}
