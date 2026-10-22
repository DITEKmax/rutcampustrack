package ru.rutcampustrack.attendance.contract.dto.report;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public record LessonActionsResponse(List<Entry> items, String nextCursor) {
    public record Entry(
            String id,
            Instant occurredAt,
            String action,
            Person actor,
            Person student,
            Map<String, Object> before,
            Map<String, Object> after,
            Geo geo,
            Map<String, Object> ticket,
            Map<String, Object> homework,
            String source,
            String outcome,
            String errorCode) {}

    public record Person(long userId, String role, String displayName) {}

    public record Geo(
            Double distanceMeters,
            Double radiusMeters,
            Double accuracyMeters,
            String verdict,
            Double latitude,
            Double longitude) {}
}
