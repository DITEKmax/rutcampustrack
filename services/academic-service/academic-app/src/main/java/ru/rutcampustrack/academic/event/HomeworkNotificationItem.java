package ru.rutcampustrack.academic.event;

import com.fasterxml.jackson.annotation.JsonProperty;

public record HomeworkNotificationItem(
        @JsonProperty("homework_id") Long homeworkId,
        @JsonProperty("subject_id") Long subjectId,
        @JsonProperty("subject_name") String subjectName,
        String title,
        String description,
        String link,
        @JsonProperty("has_link") boolean hasLink,
        @JsonProperty("lesson_date") String lessonDate,
        @JsonProperty("lesson_number") Integer lessonNumber
) {
}
