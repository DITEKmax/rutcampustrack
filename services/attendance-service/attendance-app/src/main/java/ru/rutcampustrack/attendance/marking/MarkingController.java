package ru.rutcampustrack.attendance.marking;

import org.springframework.hateoas.EntityModel;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import ru.rutcampustrack.attendance.checkin.AttendanceDocument;
import ru.rutcampustrack.attendance.contract.api.MarkingApi;
import ru.rutcampustrack.attendance.contract.dto.marking.MarkBatchRequest;
import ru.rutcampustrack.attendance.contract.dto.marking.MarkBatchResponse;
import ru.rutcampustrack.attendance.contract.dto.marking.MarkRequest;
import ru.rutcampustrack.attendance.contract.dto.marking.MarkResponse;
import ru.rutcampustrack.attendance.contract.enums.UserRole;
import ru.rutcampustrack.attendance.security.RequireRole;

import java.util.List;

import static java.nio.charset.StandardCharsets.UTF_8;

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

    @Override
    @RequireRole(UserRole.STUDENT)
    public ResponseEntity<EntityModel<MarkResponse>> markWithFile(
            Long lessonId, Long userId, MarkRequest request, MultipartFile file) {
        AttendanceDocument doc = markingService.markAttendance(lessonId, userId, request, file);
        MarkResponse response = new MarkResponse(
                doc.getStatus(), doc.getLessonId(), doc.getUserId(), doc.getUpdatedAt());
        EntityModel<MarkResponse> model = EntityModel.of(response,
                linkTo(methodOn(MarkingController.class).mark(lessonId, userId, null)).withSelfRel());
        return ResponseEntity.ok(model);
    }

    @Override
    @RequireRole(UserRole.STUDENT)
    public ResponseEntity<Void> clear(Long lessonId, Long userId) {
        markingService.clearAttendance(lessonId, userId);
        return ResponseEntity.noContent().build();
    }

    @Override
    @RequireRole(UserRole.STUDENT)
    public ResponseEntity<byte[]> downloadAttachment(Long lessonId, Long userId) {
        AttendanceAttachmentService.AttachmentDownload download =
                markingService.downloadAttachment(lessonId, userId);
        ContentDisposition disposition = ContentDisposition.attachment()
                .filename(download.name(), UTF_8)
                .build();
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(download.contentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .body(download.bytes());
    }

    @Override
    @RequireRole(UserRole.STUDENT)
    public ResponseEntity<MarkBatchResponse> markBatch(MarkBatchRequest request) {
        List<AttendanceDocument> docs = markingService.markBatch(request);

        List<MarkResponse> items = docs.stream()
                .map(doc -> new MarkResponse(
                        doc.getStatus(),
                        doc.getLessonId(),
                        doc.getUserId(),
                        doc.getUpdatedAt()))
                .toList();

        MarkBatchResponse response = new MarkBatchResponse(items, items.size());
        response.add(linkTo(methodOn(MarkingController.class).markBatch(null)).withSelfRel());

        return ResponseEntity.ok(response);
    }
}
