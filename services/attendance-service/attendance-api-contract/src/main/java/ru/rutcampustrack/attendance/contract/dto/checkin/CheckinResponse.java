package ru.rutcampustrack.attendance.contract.dto.checkin;

import io.swagger.v3.oas.annotations.media.Schema;
import org.springframework.hateoas.RepresentationModel;
import ru.rutcampustrack.attendance.contract.enums.AttendanceStatus;

import java.time.Instant;

/**
 * Response DTO for geo-checkin (D-03).
 * Extends RepresentationModel for HATEOAS _links.
 * No Lombok per contract module rules.
 */
@Schema(description = "Результат геоотметки студента: присвоенный статус, ID пары, время отметки")
public class CheckinResponse extends RepresentationModel<CheckinResponse> {

    private AttendanceStatus status;
    private Long lessonId;
    private Instant timestamp;

    public CheckinResponse() {}

    public CheckinResponse(AttendanceStatus status, Long lessonId, Instant timestamp) {
        this.status = status;
        this.lessonId = lessonId;
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

    public Instant getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(Instant timestamp) {
        this.timestamp = timestamp;
    }
}
