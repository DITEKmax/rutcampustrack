package ru.rutcampustrack.attendance.student;

import ru.rutcampustrack.attendance.contract.enums.AttendanceSource;
import ru.rutcampustrack.attendance.contract.enums.AttendanceStatus;
import ru.rutcampustrack.attendance.contract.enums.LateCheckinRequestStatus;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;

public final class StudentCheckinModels {

    private StudentCheckinModels() {}

    public record Identity(
            long userId,
            String role,
            Long groupId,
            boolean headman,
            String displayName,
            boolean readOnly) {}

    public record Lesson(
            long id,
            long groupId,
            long subjectId,
            String subjectName,
            String subjectType,
            long semesterId,
            int lessonNumber,
            LocalDate date,
            LocalTime startsAt,
            LocalTime endsAt,
            String status,
            boolean geoBlocked) {}

    public sealed interface Geo permits Coordinates, Unavailable {
        String canonicalValue();

        Diagnostics diagnostics();

        default java.util.Map<String, Object> evidence() {
            var result = new java.util.LinkedHashMap<String, Object>();
            Diagnostics d = diagnostics();
            result.put("accuracy_meters", d == null ? null : d.accuracyMeters());
            result.put("source", d == null ? null : d.source().name());
            result.put(
                    "fix_timestamp",
                    d == null || d.fixTimestamp() == null ? null : d.fixTimestamp().toString());
            result.put(
                    "received_at",
                    d == null || d.receivedAt() == null ? null : d.receivedAt().toString());
            result.put("sample_count", d == null ? null : d.sampleCount());
            result.put("acquisition_duration_ms", d == null ? null : d.acquisitionDurationMs());
            result.put(
                    "completion_reason",
                    d == null || d.completionReason() == null ? null : d.completionReason().name());
            result.put(
                    "unavailable_reason",
                    this instanceof Unavailable unavailable ? unavailable.reason() : null);
            if (this instanceof Coordinates coordinates) {
                result.put("latitude", coordinates.latitude());
                result.put("longitude", coordinates.longitude());
            }
            return result;
        }
    }

    public enum DiagnosticSource {
        BROWSER,
        TMA
    }

    public enum CompletionReason {
        QUALITY_REACHED,
        BUDGET_EXPIRED,
        PERMISSION_DENIED,
        POSITION_UNAVAILABLE,
        TIMEOUT,
        UNSUPPORTED,
        APP_DISABLED,
        WITHIN_GEOFENCE,
        ATTEMPTS_EXHAUSTED
    }

    public record Diagnostics(
            DiagnosticSource source,
            Double accuracyMeters,
            Instant fixTimestamp,
            Instant receivedAt,
            Integer sampleCount,
            Integer acquisitionDurationMs,
            CompletionReason completionReason) {
        public Diagnostics {
            if (source == null
                    || accuracyMeters != null
                            && (!Double.isFinite(accuracyMeters)
                                    || accuracyMeters < 0
                                    || accuracyMeters > 10000000)
                    || sampleCount != null && (sampleCount < 0 || sampleCount > 64)
                    || acquisitionDurationMs != null
                            && (acquisitionDurationMs < 0 || acquisitionDurationMs > 60000))
                throw new StudentCheckinException(
                        StudentCheckinException.Code.INVALID_REQUEST,
                        "Некорректная диагностика геолокации");
        }

        String canonicalValue() {
            // Fixed field order and typed values; Instant has a canonical UTC form ending in Z.
            // Explicit nulls distinguish unknown measurements from zero.
            return "\ndiagnostics:"
                    + source.name()
                    + ':'
                    + (accuracyMeters == null ? "null" : Double.toHexString(accuracyMeters))
                    + ':'
                    + fixTimestamp
                    + ':'
                    + receivedAt
                    + ':'
                    + sampleCount
                    + ':'
                    + acquisitionDurationMs
                    + ':'
                    + completionReason;
        }
    }

    public record Coordinates(double latitude, double longitude, Diagnostics diagnostics)
            implements Geo {
        public Coordinates(double latitude, double longitude) {
            this(latitude, longitude, null);
        }

        @Override
        public String canonicalValue() {
            return "coordinates:"
                    + Double.toHexString(latitude)
                    + ':'
                    + Double.toHexString(longitude)
                    + (diagnostics == null ? "" : diagnostics.canonicalValue());
        }
    }

    public record Unavailable(String reason, Diagnostics diagnostics) implements Geo {
        public Unavailable(String reason) {
            this(reason, null);
        }

        @Override
        public String canonicalValue() {
            return "unavailable:"
                    + reason
                    + (diagnostics == null ? "" : diagnostics.canonicalValue());
        }
    }

    public enum Outcome {
        PRESENT,
        PENDING_CONFIRMATION
    }

    public enum Resolution {
        GEO_CONFIRMED,
        HEADMAN_APPROVED,
        HEADMAN_REJECTED
    }

    public record Attendance(AttendanceStatus status, AttendanceSource source, Instant markedAt) {}

    public record Request(String id, LateCheckinRequestStatus status, Resolution resolution) {}

    public record Ack(
            Outcome outcome,
            long lessonId,
            Attendance attendance,
            Request request,
            Instant retryAt,
            Instant serverNow) {}
}
