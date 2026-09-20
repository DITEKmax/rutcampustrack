package ru.rutcampustrack.mobilebff.student;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import ru.rutcampustrack.mobilebff.contract.api.StudentApi;
import ru.rutcampustrack.mobilebff.contract.model.StudentApiModels.*;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

@RestController
public class StudentApiController implements StudentApi {
    private final StudentQueryService queries;
    private final StudentCheckinFacade checkins;
    private final ObjectMapper objectMapper;

    public StudentApiController(StudentQueryService queries, StudentCheckinFacade checkins, ObjectMapper objectMapper) {
        this.queries = queries;
        this.checkins = checkins;
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
            return ResponseEntity.status(304).cacheControl(CacheControl.noCache().cachePrivate())
                    .eTag(etag).build();
        }
        return ResponseEntity.ok().cacheControl(CacheControl.noCache().cachePrivate()).eTag(etag).body(result);
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
        return ResponseEntity.ok(checkins.checkin(Long.parseLong(lessonId), idempotencyKey, request));
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
