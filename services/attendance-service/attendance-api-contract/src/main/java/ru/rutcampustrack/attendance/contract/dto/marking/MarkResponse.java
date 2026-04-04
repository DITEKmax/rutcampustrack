package ru.rutcampustrack.attendance.contract.dto.marking;

import org.springframework.hateoas.RepresentationModel;
import ru.rutcampustrack.attendance.contract.enums.AttendanceStatus;

import java.time.Instant;

/**
 * Response DTO for manual attendance marking (D-11).
 * Extends RepresentationModel for HATEOAS _links.
 * No Lombok per contract module rules.
 */
public class MarkResponse extends RepresentationModel<MarkResponse> {

    private AttendanceStatus status;
    private Long lessonId;
    private Long userId;
    private Instant timestamp;

    public MarkResponse() {}

    public MarkResponse(AttendanceStatus status, Long lessonId, Long userId, Instant timestamp) {
        this.status = status;
        this.lessonId = lessonId;
        this.userId = userId;
        this.timestamp = timestamp;
    }

    public AttendanceStatus getStatus() {
        return status;
    }

    public void setStatus(AttendanceStatus status) {
        this.status = status;
    }

    public Long getLessonId() {
        return lessonId;
    }

    public void setLessonId(Long lessonId) {
        this.lessonId = lessonId;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(Instant timestamp) {
        this.timestamp = timestamp;
    }
}
