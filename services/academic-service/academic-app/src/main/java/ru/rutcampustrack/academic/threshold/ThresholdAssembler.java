package ru.rutcampustrack.academic.threshold;

import org.springframework.hateoas.EntityModel;
import org.springframework.hateoas.server.RepresentationModelAssembler;
import org.springframework.stereotype.Component;
import ru.rutcampustrack.academic.contract.dto.threshold.ThresholdResponse;
import ru.rutcampustrack.academic.entity.AttendanceThreshold;

import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.*;

@Component
public class ThresholdAssembler implements RepresentationModelAssembler<AttendanceThreshold, EntityModel<ThresholdResponse>> {

    @Override
    public EntityModel<ThresholdResponse> toModel(AttendanceThreshold threshold) {
        ThresholdResponse response = toResponse(threshold);
        return EntityModel.of(response,
                linkTo(methodOn(ThresholdController.class).listThresholds(null, null)).withSelfRel()
        );
    }

    public ThresholdResponse toResponse(AttendanceThreshold threshold) {
        ThresholdResponse response = new ThresholdResponse();
        response.setId(threshold.getId());
        response.setGroupId(threshold.getGroupId());
        response.setSubjectId(threshold.getSubjectId());
        response.setMinPercentage(threshold.getThresholdPct());
        // updatedAt not stored separately - use createdAt as updated
        response.setUpdatedAt(threshold.getCreatedAt());
        return response;
    }
}
