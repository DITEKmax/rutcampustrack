package ru.rutcampustrack.attendance.studentrequest.entity;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.mongodb.core.mapping.Field;

/** Saved atomically with the terminal ticket, marks, audit and decision outbox. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExcuseDecisionSelectionDocument {
    @Field("request_key")
    private String requestKey;

    @Field("expected_revision")
    private String expectedRevision;

    @Field("included_lessons")
    private List<StudentLessonSnapshotDocument> includedLessons;

    @Field("excluded_lessons")
    private List<StudentLessonSnapshotDocument> excludedLessons;
}
