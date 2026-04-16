package ru.rutcampustrack.schedule.lesson;

import org.springframework.hateoas.EntityModel;
import org.springframework.hateoas.Link;
import org.springframework.stereotype.Component;
import ru.rutcampustrack.schedule.contract.dto.lesson.LessonResponse;
import ru.rutcampustrack.schedule.contract.enums.LessonStatus;
import ru.rutcampustrack.schedule.item.entity.ScheduleItem;
import ru.rutcampustrack.schedule.lesson.entity.Lesson;

import java.util.ArrayList;
import java.util.List;

/**
 * HATEOAS assembler that converts LessonWithItem pairs to EntityModel<LessonResponse>.
 * Populates all fields from both Lesson and ScheduleItem entities (VIEW-02, D-16).
 * Adds conditional HATEOAS action links based on current lesson status.
 */
@Component
public class LessonAssembler {

    /**
     * Converts a LessonWithItem to a LessonResponse (without HATEOAS wrapping).
     * Used when building pages — the caller wraps in EntityModel separately.
     */
    public LessonResponse toResponse(LessonWithItem lwi) {
        Lesson l = lwi.lesson();
        ScheduleItem si = lwi.scheduleItem();
        return new LessonResponse(
                l.getId(),
                l.getScheduleItemId(),
                si.getGroupId(),
                si.getSubjectId(),
                l.getDate(),
                l.getStatus(),
                si.getDayOfWeek(),
                si.getLessonNumber(),
                si.getStartTime(),
                si.getEndTime(),
                si.getWeekType(),
                si.getRoom(),
                l.isGeoBlocked(),
                l.isBlockedByHeadman(),
                l.getBlockedByUserId(),
                l.getBlockedAt(),
                l.getCancelReason(),
                l.getCreatedAt()
        );
    }

    /**
     * Converts a LessonWithItem to an EntityModel with conditional HATEOAS links.
     * - self: always present
     * - cancel: only if lesson is PLANNED
     * - restore: only if lesson is CANCELLED
     * - geo-block: always present
     */
    public EntityModel<LessonResponse> toModel(LessonWithItem lwi) {
        LessonResponse response = toResponse(lwi);
        Lesson l = lwi.lesson();

        List<Link> links = new ArrayList<>();
        links.add(Link.of("/schedule/lessons/" + l.getId()).withSelfRel());

        if (l.getStatus() == LessonStatus.PLANNED) {
            links.add(Link.of("/schedule/lessons/" + l.getId() + "/cancel").withRel("cancel"));
        }
        if (l.getStatus() == LessonStatus.CANCELLED) {
            links.add(Link.of("/schedule/lessons/" + l.getId() + "/restore").withRel("restore"));
        }
        links.add(Link.of("/schedule/lessons/" + l.getId() + "/geo-block").withRel("geo-block"));
        if (l.isBlockedByHeadman()) {
            links.add(Link.of("/schedule/lessons/" + l.getId() + "/blockage").withRel("unblock"));
        } else {
            links.add(Link.of("/schedule/lessons/" + l.getId() + "/blockage").withRel("block"));
        }

        return EntityModel.of(response, links);
    }
}
