package ru.rutcampustrack.attendance.audit;

import java.time.Instant;
import java.util.Map;
import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

/** Permanent domain history. Deliberately has no TTL and is separate from outbox/inbox cleanup. */
@Data
@Document("lesson_actions")
@CompoundIndex(name = "lesson_action_page", def = "{'lesson_id':1,'occurred_at':-1,'_id':-1}")
public class LessonActionDocument {
    @Id private String id;

    @Field("lesson_id")
    private Long lessonId;

    @Field("group_id")
    private Long groupId;

    @Field("semester_id")
    private Long semesterId;

    @Field("occurred_at")
    private Instant occurredAt;

    private String source;
    private Map<String, Object> payload;
}
