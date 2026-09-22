package ru.rutcampustrack.attendance.contract.api;

import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import ru.rutcampustrack.attendance.contract.dto.headman.HeadmanRequestDecisionRequest;
import ru.rutcampustrack.attendance.contract.dto.headman.HeadmanRequestDetailResponse;
import ru.rutcampustrack.attendance.contract.dto.headman.HeadmanRequestPageResponse;

/** Unified, headman-scoped request queue for excuse and late-checkin tickets. */
@RequestMapping("/attendance/requests")
public interface HeadmanRequestApi {

    @GetMapping
    ResponseEntity<HeadmanRequestPageResponse> list(
            @RequestParam(defaultValue = "OPEN") String bucket,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String studentName,
            @RequestParam(required = false) String coverageDateFrom,
            @RequestParam(required = false) String coverageDateTo);

    @GetMapping("/{id}")
    ResponseEntity<HeadmanRequestDetailResponse> get(@PathVariable String id);

    @PostMapping("/{id}/decision")
    ResponseEntity<HeadmanRequestDetailResponse> decide(
            @PathVariable String id,
            @Valid @RequestBody HeadmanRequestDecisionRequest body);

    @GetMapping(value = "/{id}/attachments/{attachmentId}", produces = MediaType.APPLICATION_OCTET_STREAM_VALUE)
    ResponseEntity<byte[]> downloadAttachment(@PathVariable String id, @PathVariable String attachmentId);
}
