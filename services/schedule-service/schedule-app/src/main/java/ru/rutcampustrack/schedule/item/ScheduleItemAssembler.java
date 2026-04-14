package ru.rutcampustrack.schedule.item;

import org.springframework.hateoas.EntityModel;
import org.springframework.stereotype.Component;
import ru.rutcampustrack.schedule.contract.dto.item.ScheduleItemResponse;
import ru.rutcampustrack.schedule.item.entity.ScheduleItem;

import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.linkTo;
import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.methodOn;

/**
 * HATEOAS assembler that converts ScheduleItem entities to EntityModel<ScheduleItemResponse>
 * with self and collection links.
 */
@Component
public class ScheduleItemAssembler {

    /**
     * Converts a ScheduleItem entity to an EntityModel with HATEOAS links.
     */
    public EntityModel<ScheduleItemResponse> toModel(ScheduleItem item) {
        ScheduleItemResponse response = new ScheduleItemResponse(
                item.getId(),
                item.getGroupId(),
                item.getSubjectId(),
                item.getSemesterId(),
                item.getDayOfWeek(),
                item.getLessonNumber(),
                item.getStartTime(),
                item.getEndTime(),
                item.getWeekType(),
                item.getRoom(),
                item.isActive(),
                item.getCreatedAt()
        );

        return EntityModel.of(response,
                linkTo(methodOn(ScheduleItemController.class)
                        .getScheduleItem(item.getId())).withSelfRel(),
                linkTo(methodOn(ScheduleItemController.class)
                        .listScheduleItems(item.getGroupId(), item.getSemesterId(), null, null))
                        .withRel("collection")
        );
    }
}
