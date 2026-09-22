package ru.rutcampustrack.attendance.studentrequest;

import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import ru.rutcampustrack.attendance.contract.api.HeadmanRequestApi;
import ru.rutcampustrack.attendance.contract.dto.headman.HeadmanRequestDecisionRequest;
import ru.rutcampustrack.attendance.contract.dto.headman.HeadmanRequestDetailResponse;
import ru.rutcampustrack.attendance.contract.dto.headman.HeadmanRequestPageResponse;
import ru.rutcampustrack.attendance.contract.enums.UserRole;
import ru.rutcampustrack.attendance.security.RequireRole;
import ru.rutcampustrack.attendance.security.RequestContext;
import ru.rutcampustrack.attendance.studentrequest.StudentRequestModels.AttachmentDownload;
import ru.rutcampustrack.attendance.studentrequest.StudentRequestModels.Identity;

import java.nio.charset.StandardCharsets;

@RestController
public class HeadmanRequestController implements HeadmanRequestApi {

    private final HeadmanRequestService service;
    private final RequestContext requestContext;

    public HeadmanRequestController(HeadmanRequestService service, RequestContext requestContext) {
        this.service = service;
        this.requestContext = requestContext;
    }

    @Override
    @RequireRole(UserRole.STUDENT)
    public ResponseEntity<HeadmanRequestPageResponse> list(String bucket, int page, int size, String type,
                                                            String studentName, String coverageDateFrom,
                                                            String coverageDateTo) {
        return ResponseEntity.ok(service.list(identity(), bucket, page, size, type, studentName,
                coverageDateFrom, coverageDateTo));
    }

    @Override
    @RequireRole(UserRole.STUDENT)
    public ResponseEntity<HeadmanRequestDetailResponse> get(String id) {
        return ResponseEntity.ok(service.get(identity(), id));
    }

    @Override
    @RequireRole(UserRole.STUDENT)
    public ResponseEntity<HeadmanRequestDetailResponse> decide(String id, HeadmanRequestDecisionRequest body) {
        return ResponseEntity.ok(service.decide(identity(), id, body));
    }

    @Override
    @RequireRole(UserRole.STUDENT)
    public ResponseEntity<byte[]> downloadAttachment(String id, String attachmentId) {
        AttachmentDownload download = service.downloadAttachment(identity(), id, attachmentId);
        MediaType mediaType = MediaType.APPLICATION_OCTET_STREAM;
        if (download.contentType() != null && !download.contentType().isBlank()) {
            try {
                mediaType = MediaType.parseMediaType(download.contentType());
            } catch (IllegalArgumentException ignored) {
                // Keep the safe binary fallback for a historical/invalid MIME value.
            }
        }
        String filename = download.filename() == null || download.filename().isBlank()
                ? "attachment" : download.filename().replaceAll("[\\r\\n\\\"]", "_");
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(mediaType);
        headers.setContentLength(download.bytes() == null ? 0 : download.bytes().length);
        headers.setContentDisposition(ContentDisposition.attachment()
                .filename(filename, StandardCharsets.UTF_8).build());
        return ResponseEntity.ok().headers(headers).body(download.bytes());
    }

    private Identity identity() {
        Long userId = requestContext.getUserId();
        return new Identity(userId == null ? 0L : userId, requestContext.getRole(),
                requestContext.getGroupId(), requestContext.isHeadman());
    }
}
