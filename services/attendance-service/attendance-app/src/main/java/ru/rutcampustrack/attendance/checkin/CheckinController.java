package ru.rutcampustrack.attendance.checkin;

import jakarta.validation.Valid;
import org.springframework.hateoas.EntityModel;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import ru.rutcampustrack.attendance.contract.api.CheckinApi;
import ru.rutcampustrack.attendance.contract.dto.checkin.CheckinRequest;
import ru.rutcampustrack.attendance.contract.dto.checkin.CheckinResponse;
import ru.rutcampustrack.attendance.contract.enums.UserRole;
import ru.rutcampustrack.attendance.security.RequireRole;

import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.linkTo;
import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.methodOn;

/**
 * REST controller for geo-checkin (D-06).
 * Implements CheckinApi — all mappings defined in the interface.
 * Per CLAUDE.md: controller only delegates and wraps in HATEOAS.
 */
@RestController
public class CheckinController implements CheckinApi {

    private final CheckinService checkinService;

    public CheckinController(CheckinService checkinService) {
        this.checkinService = checkinService;
    }

    @Override
    @RequireRole(UserRole.STUDENT)
    public ResponseEntity<EntityModel<CheckinResponse>> checkin(@Valid @RequestBody CheckinRequest request) {
        AttendanceDocument doc = checkinService.checkin(request);

        CheckinResponse response = new CheckinResponse(doc.getStatus(), doc.getLessonId(), doc.getCreatedAt());
        EntityModel<CheckinResponse> model = EntityModel.of(response);
        model.add(linkTo(methodOn(CheckinController.class).checkin(null)).withSelfRel());

        return ResponseEntity.status(HttpStatus.CREATED).body(model);
    }
}
