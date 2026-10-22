package ru.rutcampustrack.schedule.oneoff;

import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.linkTo;
import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.methodOn;

import java.time.ZoneOffset;
import org.springframework.hateoas.EntityModel;
import org.springframework.stereotype.Component;
import ru.rutcampustrack.schedule.contract.dto.oneoff.OneOffLessonResponse;
import ru.rutcampustrack.schedule.oneoff.projection.OneOffCurrentLessonProjection;

/** Converts {@link OneOffCurrentLessonProjection} snapshots to HATEOAS {@link EntityModel}s. */
@Component
public class OneOffLessonAssembler {

    public EntityModel<OneOffLessonResponse> toModel(OneOffCurrentLessonProjection entity) {
        OneOffLessonResponse body =
                new OneOffLessonResponse(
                        entity.getId(),
                        entity.getPhysicalLessonId(),
                        entity.getGroupId(),
                        entity.getSubjectId(),
                        entity.getSemesterId(),
                        entity.getDate(),
                        entity.getLessonNumber(),
                        entity.getClassroom(),
                        entity.getCreatedBy(),
                        entity.getCreatedAt().atOffset(ZoneOffset.UTC));
        return EntityModel.of(
                body,
                linkTo(methodOn(OneOffLessonController.class).deleteOneOffLesson(entity.getId()))
                        .withSelfRel());
    }
}
