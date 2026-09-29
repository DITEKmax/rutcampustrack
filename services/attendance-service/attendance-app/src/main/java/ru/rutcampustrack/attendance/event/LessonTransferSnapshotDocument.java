package ru.rutcampustrack.attendance.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.mongodb.core.mapping.Field;

import java.time.LocalDate;
import java.time.LocalTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LessonTransferSnapshotDocument {

    @Field("lesson_id")
    private Long lessonId;
    @Field("schedule_item_id")
    private Long scheduleItemId;
    @Field("generation")
    private Long generation;
    @Field("lesson_revision")
    private Long lessonRevision;
    @Field("occurrence_revision")
    private Long occurrenceRevision;
    @Field("date")
    private LocalDate date;
    @Field("lesson_number")
    private Integer lessonNumber;
    @Field("start_time")
    private LocalTime startTime;
    @Field("end_time")
    private LocalTime endTime;
    @Field("room")
    private String room;

    public static LessonTransferSnapshotDocument from(LessonTransferRequestedEvent.Snapshot snapshot) {
        return LessonTransferSnapshotDocument.builder()
                .lessonId(snapshot.lessonId())
                .scheduleItemId(snapshot.scheduleItemId())
                .generation(snapshot.generation())
                .lessonRevision(snapshot.lessonRevision())
                .occurrenceRevision(snapshot.occurrenceRevision())
                .date(snapshot.date())
                .lessonNumber(snapshot.lessonNumber())
                .startTime(snapshot.startTime())
                .endTime(snapshot.endTime())
                .room(snapshot.room())
                .build();
    }
}
