package ru.rutcampustrack.schedule.contract.dto.lesson;

import io.swagger.v3.oas.annotations.media.Schema;
import ru.rutcampustrack.schedule.contract.enums.LessonStatus;
import ru.rutcampustrack.schedule.contract.enums.LessonType;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;

@Schema(name = "LessonLifecycleEntryResponse")
public record LessonLifecycleEntryResponse(
        String revision,
        String action,
        LessonInfo lesson,
        @Schema(nullable = true) LessonInfo targetLesson,
        String generation,
        @Schema(nullable = true) String reason,
        String actorId,
        Instant occurredAt
) {
    public LessonLifecycleEntryResponse {
        requirePositiveDecimal(revision, "revision");
        requireUppercase(action, "action");
        Objects.requireNonNull(lesson, "lesson");
        requirePositiveDecimal(generation, "generation");
        requirePositiveDecimal(actorId, "actorId");
        Objects.requireNonNull(occurredAt, "occurredAt");
    }

    @Schema(name = "LessonLifecycleLessonInfo", requiredProperties = {
            "lessonId", "groupId", "subjectId", "startsAt", "lessonNumber", "date",
            "occurrenceId", "assignmentId", "semesterId", "teacherId", "lessonType",
            "generation", "revision", "status"
    })
    public record LessonInfo(
            String lessonId,
            String groupId,
            String subjectId,
            Instant startsAt,
            int lessonNumber,
            LocalDate date,
            String occurrenceId,
            String assignmentId,
            String semesterId,
            String teacherId,
            LessonType lessonType,
            String generation,
            String revision,
            LessonStatus status
    ) {
        public LessonInfo {
            requirePositiveDecimal(lessonId, "lessonId");
            requirePositiveDecimal(groupId, "groupId");
            requirePositiveDecimal(subjectId, "subjectId");
            Objects.requireNonNull(startsAt, "startsAt");
            if (lessonNumber < 1 || lessonNumber > 8) {
                throw new IllegalArgumentException("lessonNumber must be between 1 and 8");
            }
            Objects.requireNonNull(date, "date");
            requirePositiveDecimal(occurrenceId, "occurrenceId");
            requirePositiveDecimal(assignmentId, "assignmentId");
            requirePositiveDecimal(semesterId, "semesterId");
            requirePositiveDecimal(teacherId, "teacherId");
            Objects.requireNonNull(lessonType, "lessonType");
            requirePositiveDecimal(generation, "generation");
            requirePositiveDecimal(revision, "revision");
            Objects.requireNonNull(status, "status");
        }
    }

    private static void requirePositiveDecimal(String value, String name) {
        Objects.requireNonNull(value, name);
        if (!value.matches("[1-9][0-9]*")) {
            throw new IllegalArgumentException(name + " must be a positive decimal string");
        }
    }

    private static void requireUppercase(String value, String name) {
        Objects.requireNonNull(value, name);
        if (!value.matches("[A-Z][A-Z0-9_]*")) {
            throw new IllegalArgumentException(name + " must be an uppercase wire value");
        }
    }
}

