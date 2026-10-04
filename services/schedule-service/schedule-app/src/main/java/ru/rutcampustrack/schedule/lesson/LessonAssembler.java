package ru.rutcampustrack.schedule.lesson;

import org.springframework.hateoas.EntityModel;
import org.springframework.hateoas.Link;
import org.springframework.stereotype.Component;
import ru.rutcampustrack.schedule.contract.dto.lesson.LessonResponse;
import ru.rutcampustrack.schedule.contract.enums.LessonStatus;
import ru.rutcampustrack.schedule.contract.enums.WeekType;
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
        return toResponse(lwi, null);
    }

    public LessonResponse toResponse(LessonWithItem lwi,
                                     LessonTransferWriter.TransferState transferState) {
        Lesson l = lwi.lesson();
        ScheduleItem si = lwi.scheduleItem();
        Short dayOfWeek = l.getDayOfWeek() != null ? l.getDayOfWeek() : si.getDayOfWeek();
        Short lessonNumber = l.getLessonNumber() != null ? l.getLessonNumber() : si.getLessonNumber();
        java.time.LocalTime startTime = l.getStartTime() != null ? l.getStartTime() : si.getStartTime();
        java.time.LocalTime endTime = l.getEndTime() != null ? l.getEndTime() : si.getEndTime();
        WeekType weekType = l.getWeekTypeSnapshot() == null
                ? si.getWeekType() : WeekType.valueOf(l.getWeekTypeSnapshot().toUpperCase());
        String room = l.resolveRoom(si == null ? null : si.getRoom());
        Long groupId = l.getGroupId() != null ? l.getGroupId() : si.getGroupId();
        Long subjectId = l.getSubjectId() != null ? l.getSubjectId() : si.getSubjectId();
        LessonResponse response = new LessonResponse(
                l.getId(),
                l.getScheduleItemId(),
                groupId,
                subjectId,
                l.getDate(),
                l.getStatus(),
                dayOfWeek,
                lessonNumber,
                startTime,
                endTime,
                weekType,
                room,
                l.isGeoBlocked(),
                l.isBlockedByHeadman(),
                l.getBlockedByUserId(),
                l.getBlockedAt(),
                l.getCancelReason(),
                l.getCancelledBy(),
                l.getCancelledAt(),
                l.getCreatedAt(),
                l.getOccurrenceId(),
                l.getAssignmentId(),
                l.getSemesterId(),
                l.getAssignedTeacherId(),
                l.getLessonType(),
                l.getGeneration(),
                l.getRevision(),
                l.isCurrent()
        );
        if (transferState != null) {
            response.setTransferState(transferState.operationId(), transferState.state());
        }
        return response;
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
