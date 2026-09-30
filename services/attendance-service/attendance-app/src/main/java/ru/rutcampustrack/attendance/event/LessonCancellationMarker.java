package ru.rutcampustrack.attendance.event;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.time.Instant;

/** Durable terminal cancellation authority keyed by the physical lesson id. */
@Document(collection = "lesson_cancellation_markers")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class LessonCancellationMarker {

    @Id
    private String id;

    @Indexed(unique = true)
    @Field("lesson_id")
    private Long lessonId;

    @Field("semester_id")
    private Long semesterId;

    @Field("marked_at")
    private Instant markedAt;
}
