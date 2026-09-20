package ru.rutcampustrack.attendance.excuse;

import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.hateoas.EntityModel;
import org.springframework.hateoas.PagedModel;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import ru.rutcampustrack.attendance.contract.api.ExcuseApi;
import ru.rutcampustrack.attendance.contract.dto.excuse.CreateExcuseRequest;
import ru.rutcampustrack.attendance.contract.dto.excuse.ExcuseTicketResponse;
import ru.rutcampustrack.attendance.contract.dto.excuse.UpdateExcuseStatusRequest;
import ru.rutcampustrack.attendance.contract.enums.ExcuseTicketStatus;
import ru.rutcampustrack.attendance.contract.enums.UserRole;
import ru.rutcampustrack.attendance.excuse.entity.ExcuseTicket;
import ru.rutcampustrack.attendance.exception.LegacyEndpointRetiredException;
import ru.rutcampustrack.attendance.security.RequireRole;
import ru.rutcampustrack.attendance.security.RequestContext;
import ru.rutcampustrack.attendance.studentrequest.StudentRequestModels;
import ru.rutcampustrack.attendance.studentrequest.StudentRequestService;

/**
 * REST controller for excuse tickets (Phase 59, D-04..D-09).
 * Implements {@link ExcuseApi} — all mappings/Swagger annotations live in the contract interface.
 *
 * Per CLAUDE.md: controller only delegates to {@link ExcuseService} and wraps responses in
 * HATEOAS via {@link ExcuseAssembler}. All business logic, security, and exception handling
 * is centralised in the service + {@code GlobalExceptionHandler}.
 *
 * D-09: every endpoint requires STUDENT role (headman is STUDENT + is_headman=true).
 */
@RestController
public class ExcuseController implements ExcuseApi {

    private final ExcuseService excuseService;
    private final ExcuseAssembler excuseAssembler;
    private final StudentRequestService studentRequestService;
    private final RequestContext requestContext;

    public ExcuseController(ExcuseService excuseService, ExcuseAssembler excuseAssembler,
                            StudentRequestService studentRequestService, RequestContext requestContext) {
        this.excuseService = excuseService;
        this.excuseAssembler = excuseAssembler;
        this.studentRequestService = studentRequestService;
        this.requestContext = requestContext;
    }

    @Override
    @RequireRole(UserRole.STUDENT)
    public ResponseEntity<EntityModel<ExcuseTicketResponse>> createExcuse(
            @Valid @RequestBody CreateExcuseRequest request) {
        throw new LegacyEndpointRetiredException();
    }

    @Override
    @RequireRole(UserRole.STUDENT)
    public ResponseEntity<EntityModel<ExcuseTicketResponse>> createExcuseWithFile(
            @Valid CreateExcuseRequest request, MultipartFile file) {
        throw new LegacyEndpointRetiredException();
    }

    @Override
    @RequireRole(UserRole.STUDENT)
    public ResponseEntity<PagedModel<EntityModel<ExcuseTicketResponse>>> getMyTickets(
            Pageable pageable, ExcuseTicketStatus status) {
        Page<ExcuseTicket> page = excuseService.getMyTickets(pageable, status);
        return ResponseEntity.ok(excuseAssembler.toPagedModel(page));
    }

    @Override
    @RequireRole(UserRole.STUDENT)
    public ResponseEntity<PagedModel<EntityModel<ExcuseTicketResponse>>> getGroupTickets(
            Long groupId, Pageable pageable, ExcuseTicketStatus status) {
        Page<ExcuseTicket> page = excuseService.getGroupTickets(groupId, pageable, status);
        return ResponseEntity.ok(excuseAssembler.toPagedModel(page));
    }

    @Override
    @RequireRole(UserRole.STUDENT)
    public ResponseEntity<EntityModel<ExcuseTicketResponse>> getTicketById(String id) {
        ExcuseTicket ticket = excuseService.getTicketById(id);
        return ResponseEntity.ok(excuseAssembler.toModel(ticket));
    }

    @Override
    @RequireRole(UserRole.STUDENT)
    public ResponseEntity<EntityModel<ExcuseTicketResponse>> updateStatus(
            String id, @Valid @RequestBody UpdateExcuseStatusRequest request) {
        studentRequestService.decideExcuse(identity(), id,
                request.status() == ExcuseTicketStatus.APPROVED, request.decisionComment());
        ExcuseTicket ticket = excuseService.getTicketById(id);
        return ResponseEntity.ok(excuseAssembler.toModel(ticket));
    }

    private StudentRequestModels.Identity identity() {
        Long userId = requestContext.getUserId();
        return new StudentRequestModels.Identity(userId == null ? 0L : userId,
                requestContext.getRole(), requestContext.getGroupId(), requestContext.isHeadman());
    }
}
