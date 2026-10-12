package ru.rutcampustrack.attendance.studentrequest.entity;

import java.time.LocalDate;
import java.time.LocalTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.mongodb.core.mapping.Field;

/** Immutable-at-write lesson data embedded in a request for archive reads. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StudentLessonSnapshotDocument {

    @Field("lesson_id")
    private Long lessonId;

    @Field("group_id")
    private Long groupId;

    @Field("subject_id")
    private Long subjectId;

    @Field("subject_name")
    private String subjectName;

    @Field("subject_type")
    private String subjectType;

    @Field("semester_id")
    private Long semesterId;

    @Field("lesson_number")
    private Integer lessonNumber;

    @Field("date")
    private LocalDate date;

    @Field("starts_at")
    private LocalTime startsAt;

    @Field("ends_at")
    private LocalTime endsAt;

    @Field("status")
    private String status;

    @Field("blocked")
    private boolean blocked;

    @Field("occurrence_id")
    private String occurrenceId;

    @Field("occurrence_revision")
    private String occurrenceRevision;

    @Field("attendance_status")
    private String attendanceStatus;

    @Field("attendance_source")
    private String attendanceSource;
}
