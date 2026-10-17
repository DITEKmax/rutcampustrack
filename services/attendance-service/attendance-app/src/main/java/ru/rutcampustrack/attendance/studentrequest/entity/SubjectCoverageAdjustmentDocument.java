package ru.rutcampustrack.attendance.studentrequest.entity;

import org.springframework.data.mongodb.core.mapping.Field;

import java.time.Instant;
import java.util.List;

/** Immutable evidence of one subject-scoped coverage subtraction; never a new decision. */
public record SubjectCoverageAdjustmentDocument(
        @Field("operation_id") String operationId,
        @Field("subject_id") long subjectId,
        @Field("actor_id") long actorId,
        @Field("occurred_at") Instant occurredAt,
        @Field("removed_lesson_ids") List<Long> removedLessonIds,
        @Field("remaining_lesson_ids") List<Long> remainingLessonIds,
        @Field("before_snapshots") List<StudentLessonSnapshotDocument> beforeSnapshots,
        @Field("before_original_ids") List<Long> beforeOriginalIds,
        @Field("before_decision_selection")
                ExcuseDecisionSelectionDocument beforeDecisionSelection) {}
