package ru.rutcampustrack.attendance.checkin;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;
import ru.rutcampustrack.attendance.contract.enums.AttendanceSource;
import ru.rutcampustrack.attendance.contract.enums.AttendanceStatus;

import java.time.Instant;
import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "attendances")
public class AttendanceDocument {

    @Id
    private String id;

    @Field("lesson_id")
    private Long lessonId;

    @Field("user_id")
    private Long userId;

    @Field("group_id")
    private Long groupId;

    @Field("subject_id")
    private Long subjectId;

    @Field("semester_id")
    private Long semesterId;

    @Field("lesson_number")
    private Integer lessonNumber;

    @Field("lesson_date")
    private LocalDate lessonDate;

    @Field("status")
    private AttendanceStatus status;

    @Field("source")
    private AttendanceSource source;

    @Field("marked_by")
    private Long markedBy;

    @Field("created_at")
    private Instant createdAt;

    @Field("updated_at")
    private Instant updatedAt;
}
