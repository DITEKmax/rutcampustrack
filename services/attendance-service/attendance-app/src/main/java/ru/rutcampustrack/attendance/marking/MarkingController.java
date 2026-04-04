package ru.rutcampustrack.attendance.marking;

import org.springframework.hateoas.EntityModel;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import ru.rutcampustrack.attendance.checkin.AttendanceDocument;
import ru.rutcampustrack.attendance.contract.api.MarkingApi;
import ru.rutcampustrack.attendance.contract.dto.marking.MarkRequest;
import ru.rutcampustrack.attendance.contract.dto.marking.MarkResponse;
import ru.rutcampustrack.attendance.contract.enums.UserRole;
import ru.rutcampustrack.attendance.security.RequireRole;

import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.linkTo;
import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.methodOn;

/**
 * REST controller for headman manual attendance marking (D-11).
 * Implements MarkingApi — all mappings and Swagger annotations live in the interface.
 *
 * Security: @RequireRole(STUDENT) — the headman IS a student with isHeadman=true.
 * The headman-specific authorization (isHeadman check + group match) is performed in MarkingService.
 */
@RestController
public class MarkingController implements MarkingApi {

    private final MarkingService markingService;

    public MarkingController(MarkingService markingService) {
        this.markingService = markingService;
    }

    @Override
    @RequireRole(UserRole.STUDENT)
    public ResponseEntity<EntityModel<MarkResponse>> mark(Long lessonId, Long userId, MarkRequest request) {
        AttendanceDocument doc = markingService.markAttendance(lessonId, userId, request);

        MarkResponse response = new MarkResponse(
                doc.getStatus(),
                doc.getLessonId(),
                doc.getUserId(),
                doc.getUpdatedAt()
        );

        EntityModel<MarkResponse> model = EntityModel.of(response,
                linkTo(methodOn(MarkingController.class).mark(lessonId, userId, null)).withSelfRel());

        return ResponseEntity.ok(model);
    }
}
