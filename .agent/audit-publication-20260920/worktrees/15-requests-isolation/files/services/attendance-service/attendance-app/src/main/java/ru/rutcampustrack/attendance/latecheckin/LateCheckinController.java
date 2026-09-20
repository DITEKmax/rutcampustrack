package ru.rutcampustrack.attendance.latecheckin;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.hateoas.CollectionModel;
import org.springframework.hateoas.EntityModel;
import org.springframework.hateoas.PagedModel;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import ru.rutcampustrack.attendance.contract.api.LateCheckinApi;
import ru.rutcampustrack.attendance.contract.dto.latecheckin.LateCheckinDecisionRequest;
import ru.rutcampustrack.attendance.contract.dto.latecheckin.LateCheckinRequestResponse;
import ru.rutcampustrack.attendance.contract.enums.LateCheckinRequestStatus;
import ru.rutcampustrack.attendance.contract.enums.UserRole;
import ru.rutcampustrack.attendance.latecheckin.entity.LateCheckinRequest;
import ru.rutcampustrack.attendance.exception.LegacyEndpointRetiredException;
import ru.rutcampustrack.attendance.security.RequireRole;
import ru.rutcampustrack.attendance.security.RequestContext;
import ru.rutcampustrack.attendance.studentrequest.StudentRequestModels;
import ru.rutcampustrack.attendance.studentrequest.StudentRequestService;

import java.util.List;

@RestController
public class LateCheckinController implements LateCheckinApi {

    private final LateCheckinService service;
    private final LateCheckinAssembler assembler;
    private final StudentRequestService studentRequestService;
    private final RequestContext requestContext;

    public LateCheckinController(LateCheckinService service, LateCheckinAssembler assembler,
                                 StudentRequestService studentRequestService, RequestContext requestContext) {
        this.service = service;
        this.assembler = assembler;
        this.studentRequestService = studentRequestService;
        this.requestContext = requestContext;
    }

    @Override
    @RequireRole(UserRole.STUDENT)
    public ResponseEntity<EntityModel<LateCheckinRequestResponse>> createRequest(Long lessonId) {
        throw new LegacyEndpointRetiredException();
    }

    @Override
    @RequireRole(UserRole.STUDENT)
    public ResponseEntity<CollectionModel<EntityModel<LateCheckinRequestResponse>>> listPending() {
        List<LateCheckinRequest> requests = service.listPendingForHeadman();
        List<EntityModel<LateCheckinRequestResponse>> models = requests.stream()
                .map(assembler::toModel)
                .toList();
        return ResponseEntity.ok(CollectionModel.of(models));
    }

    @Override
    @RequireRole(UserRole.STUDENT)
    public ResponseEntity<PagedModel<EntityModel<LateCheckinRequestResponse>>> listGroupRequests(
            Long groupId, Pageable pageable, LateCheckinRequestStatus status) {
        Page<LateCheckinRequest> page = service.listGroupRequestsForHeadman(groupId, pageable, status);
        return ResponseEntity.ok(assembler.toPagedModel(page));
    }

    @Override
    @RequireRole(UserRole.STUDENT)
    public ResponseEntity<EntityModel<LateCheckinRequestResponse>> decideRequest(
            String requestId, LateCheckinDecisionRequest body) {
        studentRequestService.decideLateCheckin(identity(), requestId, body.approved());
        LateCheckinRequest request = service.getRequestById(requestId);
        return ResponseEntity.ok(assembler.toModel(request));
    }

    private StudentRequestModels.Identity identity() {
        Long userId = requestContext.getUserId();
        return new StudentRequestModels.Identity(userId == null ? 0L : userId,
                requestContext.getRole(), requestContext.getGroupId(), requestContext.isHeadman());
    }
}
