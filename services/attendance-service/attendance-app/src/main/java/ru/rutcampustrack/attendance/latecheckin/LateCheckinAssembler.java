package ru.rutcampustrack.attendance.latecheckin;

import org.springframework.hateoas.EntityModel;
import org.springframework.stereotype.Component;
import ru.rutcampustrack.attendance.contract.dto.latecheckin.LateCheckinRequestResponse;
import ru.rutcampustrack.attendance.latecheckin.entity.LateCheckinRequest;

@Component
public class LateCheckinAssembler {

    public EntityModel<LateCheckinRequestResponse> toModel(LateCheckinRequest request) {
        LateCheckinRequestResponse response = new LateCheckinRequestResponse(
                request.getId(),
                request.getStudentId(),
                request.getGroupId(),
                request.getLessonId(),
                request.getStudentName(),
                request.getStatus() == null ? null : request.getStatus().name().toLowerCase(),
                request.getDecisionBy(),
                request.getDecisionAt(),
                request.getCreatedAt(),
                request.getUpdatedAt()
        );
        return EntityModel.of(response);
    }
}
