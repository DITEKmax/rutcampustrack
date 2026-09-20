package ru.rutcampustrack.mobilebff.student;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import ru.rutcampustrack.mobilebff.contract.api.StudentApi;
import ru.rutcampustrack.mobilebff.contract.model.StudentApiModels.*;
import ru.rutcampustrack.mobilebff.contract.model.StudentRequestApiModels;
import ru.rutcampustrack.mobilebff.contract.model.StudentRequestApiModels.Bucket;
import ru.rutcampustrack.mobilebff.contract.model.StudentRequestApiModels.Detail;
import ru.rutcampustrack.mobilebff.contract.model.StudentRequestApiModels.ExcuseRequest;
import ru.rutcampustrack.mobilebff.contract.model.StudentRequestApiModels.LateCheckinRequest;
import ru.rutcampustrack.mobilebff.contract.model.StudentRequestApiModels.Options;
import ru.rutcampustrack.mobilebff.contract.model.StudentRequestApiModels.Page;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

@RestController
public class StudentApiController implements StudentApi {
    private final StudentQueryService queries;
    private final StudentCheckinFacade checkins;
    private final StudentRequestFacade requests;
    private final ObjectMapper objectMapper;

    public StudentApiController(StudentQueryService queries, StudentCheckinFacade checkins,
                                StudentRequestFacade requests, ObjectMapper objectMapper) {
        this.queries = queries;
        this.checkins = checkins;
        this.requests = requests;
        this.objectMapper = objectMapper;
    }

    @Override
    public ResponseEntity<SessionResponse> getSession() {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(queries.session());
    }

    @Override
    public ResponseEntity<TodayResponse> getToday() {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(queries.today());
    }

    @Override
    public ResponseEntity<ScheduleResponse> getSchedule(String semesterId, String ifNoneMatch) {
        ScheduleResponse result = queries.schedule(Long.parseLong(semesterId));
        String etag = etag(result);
        if (etag.equals(ifNoneMatch)) {
            return ResponseEntity.status(304).cacheControl(CacheControl.noStore())
                    .eTag(etag).build();
        }
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).eTag(etag).body(result);
    }

    @Override
    public ResponseEntity<HomeworkResponse> getHomework(String from, String to) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(queries.homework(from, to));
    }

    @Override
    public ResponseEntity<HomeworkCompletionResponse> setHomeworkCompletion(
            String id, HomeworkCompletionRequest request) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(queries.setHomeworkCompletion(id, request));
    }

    @Override
    public ResponseEntity<CheckinAck> checkin(String lessonId, String idempotencyKey, CheckinRequest request) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(checkins.checkin(Long.parseLong(lessonId), idempotencyKey, request));
    }

    @Override
    public ResponseEntity<Page> listRequests(Bucket bucket, Integer page, Integer size) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(requests.list(bucket, page, size));
    }

    @Override
    public ResponseEntity<Options> requestOptions() {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(requests.options());
    }

    @Override
    public ResponseEntity<Detail> getRequest(String id) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(requests.get(id));
    }

    @Override
    public ResponseEntity<Detail> submitExcuse(String idempotencyKey, ExcuseRequest request,
                                               java.util.List<MultipartFile> files) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(requests.submitExcuse(idempotencyKey, request, files));
    }

    @Override
    public ResponseEntity<Detail> submitLateCheckin(String idempotencyKey, LateCheckinRequest request) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(requests.submitLateCheckin(idempotencyKey, request));
    }

    @Override
    public ResponseEntity<Detail> cancelRequest(String id) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(requests.cancel(id));
    }

    @Override
    public ResponseEntity<byte[]> downloadRequestAttachment(String id, String attachmentId) {
        StudentRequestFacade.Download download = requests.download(id, attachmentId);
        MediaType contentType;
        try {
            contentType = MediaType.parseMediaType(download.contentType());
        } catch (IllegalArgumentException invalidType) {
            contentType = MediaType.APPLICATION_OCTET_STREAM;
        }
        HttpHeaders headers = new HttpHeaders();
        headers.setCacheControl(CacheControl.noStore());
        headers.setContentType(contentType);
        headers.setContentLength(download.bytes().length);
        headers.setContentDisposition(ContentDisposition.attachment()
                .filename(download.filename(), StandardCharsets.UTF_8).build());
        headers.set("X-Content-Type-Options", "nosniff");
        return new ResponseEntity<>(download.bytes(), headers, org.springframework.http.HttpStatus.OK);
    }

    private String etag(ScheduleResponse response) {
        try {
            byte[] bytes = objectMapper.writeValueAsBytes(response);
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(bytes);
            return "\"" + HexFormat.of().formatHex(digest) + "\"";
        } catch (JsonProcessingException | NoSuchAlgorithmException error) {
            throw new IllegalStateException("Cannot build schedule ETag", error);
        }
    }
}
