package ru.rutcampustrack.attendance.latecheckin;

import org.springframework.data.domain.Page;
import org.springframework.hateoas.EntityModel;
import org.springframework.hateoas.PagedModel;
import org.springframework.stereotype.Component;
import ru.rutcampustrack.attendance.contract.dto.latecheckin.LateCheckinRequestResponse;
import ru.rutcampustrack.attendance.latecheckin.entity.LateCheckinRequest;

import java.util.List;

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

    public PagedModel<EntityModel<LateCheckinRequestResponse>> toPagedModel(Page<LateCheckinRequest> page) {
        List<EntityModel<LateCheckinRequestResponse>> models = page.getContent().stream()
                .map(this::toModel)
                .toList();
        PagedModel.PageMetadata metadata = new PagedModel.PageMetadata(
                page.getSize(),
                page.getNumber(),
                page.getTotalElements(),
                page.getTotalPages()
        );
        return PagedModel.of(models, metadata);
    }
}
