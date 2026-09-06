package ru.rutcampustrack.attendance.student;

import ru.rutcampustrack.attendance.contract.enums.AttendanceSource;
import ru.rutcampustrack.attendance.contract.enums.AttendanceStatus;
import ru.rutcampustrack.attendance.contract.enums.LateCheckinRequestStatus;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;

public final class StudentCheckinModels {

    private StudentCheckinModels() {
    }

    public record Identity(long userId, String role, Long groupId, boolean headman, String displayName) {
    }

    public record Lesson(
            long id,
            long groupId,
            long subjectId,
            long semesterId,
            int lessonNumber,
            LocalDate date,
            LocalTime startsAt,
            LocalTime endsAt,
            String status,
            boolean geoBlocked
    ) {
    }

    public sealed interface Geo permits Coordinates, Unavailable {
        String canonicalValue();
    }

    public record Coordinates(double latitude, double longitude) implements Geo {
        @Override
        public String canonicalValue() {
            return "coordinates:" + Double.toHexString(latitude) + ':' + Double.toHexString(longitude);
        }
    }

    public record Unavailable(String reason) implements Geo {
        @Override
        public String canonicalValue() {
            return "unavailable:" + reason;
        }
    }

    public enum Outcome { PRESENT, PENDING_CONFIRMATION }

    public enum Resolution { GEO_CONFIRMED, HEADMAN_APPROVED, HEADMAN_REJECTED }

    public record Attendance(
            AttendanceStatus status,
            AttendanceSource source,
            Instant markedAt
    ) {
    }

    public record Request(
            String id,
            LateCheckinRequestStatus status,
            Resolution resolution
    ) {
    }

    public record Ack(
            Outcome outcome,
            long lessonId,
            Attendance attendance,
            Request request,
            Instant retryAt,
            Instant serverNow
    ) {
    }
}
