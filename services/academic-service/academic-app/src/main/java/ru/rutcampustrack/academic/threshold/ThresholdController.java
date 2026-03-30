package ru.rutcampustrack.academic.threshold;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PagedResourcesAssembler;
import org.springframework.hateoas.EntityModel;
import org.springframework.hateoas.PagedModel;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import ru.rutcampustrack.academic.contract.api.ThresholdApi;
import ru.rutcampustrack.academic.contract.dto.threshold.ResolvedThresholdResponse;
import ru.rutcampustrack.academic.contract.dto.threshold.SetThresholdRequest;
import ru.rutcampustrack.academic.contract.dto.threshold.ThresholdResponse;
import ru.rutcampustrack.academic.contract.enums.UserRole;
import ru.rutcampustrack.academic.entity.AttendanceThreshold;
import ru.rutcampustrack.academic.security.RequireRole;

@RestController
public class ThresholdController implements ThresholdApi {

    private final ThresholdService thresholdService;
    private final ThresholdAssembler thresholdAssembler;
    private final PagedResourcesAssembler<AttendanceThreshold> pagedAssembler;

    public ThresholdController(ThresholdService thresholdService,
                                ThresholdAssembler thresholdAssembler,
                                PagedResourcesAssembler<AttendanceThreshold> pagedAssembler) {
        this.thresholdService = thresholdService;
        this.thresholdAssembler = thresholdAssembler;
        this.pagedAssembler = pagedAssembler;
    }

    @Override
    @RequireRole({UserRole.ADMIN})
    public ResponseEntity<EntityModel<ThresholdResponse>> setGlobalThreshold(SetThresholdRequest request) {
        AttendanceThreshold threshold = thresholdService.setGlobalThreshold(request);
        return ResponseEntity.ok(thresholdAssembler.toModel(threshold));
    }

    @Override
    @RequireRole({UserRole.STUDENT})
    public ResponseEntity<EntityModel<ThresholdResponse>> setGroupThreshold(SetThresholdRequest request) {
        AttendanceThreshold threshold = thresholdService.setGroupThreshold(request);
        return ResponseEntity.ok(thresholdAssembler.toModel(threshold));
    }

    @Override
    @RequireRole({UserRole.STUDENT})
    public ResponseEntity<EntityModel<ThresholdResponse>> setSubjectThreshold(SetThresholdRequest request,
                                                                                Long subjectId) {
        AttendanceThreshold threshold = thresholdService.setSubjectThreshold(request, subjectId);
        return ResponseEntity.ok(thresholdAssembler.toModel(threshold));
    }

    @Override
    @RequireRole({UserRole.ADMIN, UserRole.STUDENT, UserRole.TEACHER})
    public ResponseEntity<EntityModel<ResolvedThresholdResponse>> resolveThreshold(Long groupId, Long subjectId) {
        ResolvedThresholdResponse response = thresholdService.resolveThreshold(groupId, subjectId);
        return ResponseEntity.ok(EntityModel.of(response));
    }

    @Override
    @RequireRole({UserRole.ADMIN})
    public ResponseEntity<PagedModel<EntityModel<ThresholdResponse>>> listThresholds(Pageable pageable) {
        Page<AttendanceThreshold> page = thresholdService.listThresholds(pageable);
        return ResponseEntity.ok(pagedAssembler.toModel(page, thresholdAssembler));
    }
}
