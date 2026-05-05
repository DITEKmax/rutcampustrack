package ru.rutcampustrack.notification.reminder;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "reminder_attendance_state")
public class ReminderAttendanceStateDocument {

    @Id
    private String id;

    @Field("lesson_id")
    private Long lessonId;

    @Field("user_id")
    private Long userId;

    @Field("status")
    private String status;

    @Field("marked_at")
    private Instant markedAt;
}
