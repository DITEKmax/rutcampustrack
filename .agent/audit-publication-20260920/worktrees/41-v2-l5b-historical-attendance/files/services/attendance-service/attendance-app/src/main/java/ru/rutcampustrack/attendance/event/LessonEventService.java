package ru.rutcampustrack.attendance.event;

import lombok.extern.slf4j.Slf4j;
import com.mongodb.client.result.DeleteResult;
import com.mongodb.client.result.UpdateResult;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.mongodb.core.BulkOperations;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;
import ru.rutcampustrack.academic.grpc.GroupMembersResponse;
import ru.rutcampustrack.academic.grpc.StudentInfo;
import ru.rutcampustrack.attendance.checkin.AttendanceDocument;
import ru.rutcampustrack.attendance.contract.enums.AttendanceSource;
import ru.rutcampustrack.attendance.contract.enums.AttendanceStatus;
import ru.rutcampustrack.attendance.exception.AcademicServiceUnavailableException;
import ru.rutcampustrack.attendance.exception.ScheduleServiceUnavailableException;
import ru.rutcampustrack.attendance.grpc.AcademicGrpcClient;
import ru.rutcampustrack.attendance.grpc.ScheduleGrpcClient;
import ru.rutcampustrack.attendance.semester.SemesterCacheService;
import ru.rutcampustrack.schedule.grpc.LessonResponse;

import java.time.Instant;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * Business logic for lesson lifecycle event processing.
 * <p>
 * processLessonClosed: bulk-upserts attendance docs with $setOnInsert so that
 * students already marked PRESENT are untouched; unmarked students get ABSENT/AUTO_SCHEDULER.
 * <p>
 * processLessonCancelled: sets all existing attendance docs for the lesson to CANCELLED.
 * <p>
 * No @Transactional — MongoDB and RabbitMQ do not share a transaction manager.
 * No try/catch — exceptions propagate so Spring AMQP nacks the message to DLQ.
 */
@Service
@Slf4j
public class LessonEventService {

    private final MongoTemplate mongoTemplate;
    private final ScheduleGrpcClient scheduleGrpcClient;
    private final AcademicGrpcClient academicGrpcClient;
    private final SemesterCacheService semesterCacheService;
    private final TaskExecutor grpcTaskExecutor;

    public LessonEventService(MongoTemplate mongoTemplate,
                              ScheduleGrpcClient scheduleGrpcClient,
                              AcademicGrpcClient academicGrpcClient,
                              SemesterCacheService semesterCacheService,
                              @Qualifier("grpcTaskExecutor") TaskExecutor grpcTaskExecutor) {
        this.mongoTemplate = mongoTemplate;
        this.scheduleGrpcClient = scheduleGrpcClient;
        this.academicGrpcClient = academicGrpcClient;
        this.semesterCacheService = semesterCacheService;
        this.grpcTaskExecutor = grpcTaskExecutor;
    }

    public void processLessonClosed(Long lessonId, Long groupId) {
        // The Schedule snapshot is the canonical source for date, semester,
        // identity and lifecycle.  Only after this validation may Academic be
        // asked for a dated historical roster.
        LessonResponse lesson = scheduleGrpcClient.getLessonById(lessonId);
        LocalDate lessonDate = validateLessonSnapshot(lesson, lessonId, groupId);
        if ("cancelled".equalsIgnoreCase(lesson.getStatus())
                || "transferred".equalsIgnoreCase(lesson.getStatus())) {
            ensureCancellationMarker(lessonId);
            applyCancellation(lessonId);
            return;
        }

        GroupMembersResponse members = academicGrpcClient.getGroupMembers(
                lesson.getGroupId(), lessonDate, lesson.getSemesterId());
        validateRosterEcho(members, lessonDate, lesson.getSemesterId());

        // A cancellation may have won the race while the historical roster was
        // being resolved.  Do not start materialization after its marker.
        if (isCancellationMarked(lessonId)) {
            applyCancellation(lessonId);
            return;
        }

        if (members.getStudentsList().isEmpty()) {
            log.info("lesson.closed: no historical students in group {} for lesson {}, skipping",
                    lesson.getGroupId(), lessonId);
            return;
        }

        Instant now = Instant.now();
        BulkOperations bulkOps = mongoTemplate.bulkOps(BulkOperations.BulkMode.UNORDERED, AttendanceDocument.class);

        for (StudentInfo student : members.getStudentsList()) {
            Query filter = Query.query(
                    Criteria.where("lesson_id").is(lessonId)
                            .and("user_id").is(student.getUserId())
            );

            Update insert = new Update()
                    .setOnInsert("lesson_id", lessonId)
                    .setOnInsert("user_id", student.getUserId())
                    .setOnInsert("group_id", lesson.getGroupId())
                    .setOnInsert("subject_id", lesson.getSubjectId())
                    .setOnInsert("semester_id", lesson.getSemesterId())
                    .setOnInsert("lesson_number", lesson.getLessonNumber())
                    .setOnInsert("lesson_date", lessonDate)
                    .setOnInsert("status", AttendanceStatus.ABSENT)
                    .setOnInsert("source", AttendanceSource.AUTO_SCHEDULER)
                    .setOnInsert("marked_by", null)
                    .setOnInsert("created_at", now)
                    .setOnInsert("updated_at", now);

            bulkOps.upsert(filter, insert);
        }

        bulkOps.execute();
        // Cancellation is terminal for this physical id.  Reapply it after
        // materialization if the marker won the race between the pre-check and
        // the bulk upsert.
        if (isCancellationMarked(lessonId)) {
            applyCancellation(lessonId);
        }
        log.info("lesson.closed: lessonId={}, processed {} students for auto-absent",
                lessonId, members.getStudentsCount());
    }

    /**
     * D-22 / AC-08: cascade-delete attendance docs when a one-off lesson is cancelled.
     * <p>
     * Match key is the natural tuple {@code (group_id, lesson_date, lesson_number)} —
     * attendance-service does not track a separate {@code one_off_lesson_id}, so this
     * key is sufficient to identify all attendance rows belonging to the cancelled slot.
     * <p>
     * Idempotent: MongoDB {@code remove} on a non-matching filter returns
     * {@code deletedCount=0} without throwing, so duplicate event delivery is safe.
     */
    public void processOneOffLessonCancelled(Long groupId, LocalDate date, Integer lessonNumber) {
        Query filter = Query.query(
                Criteria.where("group_id").is(groupId)
                        .and("lesson_date").is(date)
                        .and("lesson_number").is(lessonNumber)
        );
        DeleteResult result = mongoTemplate.remove(filter, AttendanceDocument.class);
        log.info("lesson.one_off.cancelled: groupId={}, date={}, lessonNumber={}, deletedCount={}",
                groupId, date, lessonNumber, result.getDeletedCount());
    }

    public void processLessonCancelled(Long lessonId) {
        // The marker must be durable before any attendance update.  Retries
        // are idempotent because both operations are keyed by lesson_id.
        ensureCancellationMarker(lessonId);
        applyCancellation(lessonId);
        log.info("lesson.cancelled: lessonId={}, cancellation marker durable", lessonId);
    }

    private LocalDate validateLessonSnapshot(LessonResponse lesson, Long lessonId, Long eventGroupId) {
        if (lessonId == null || eventGroupId == null
                || lesson == null || lesson.getId() != lessonId
                || lesson.getGroupId() <= 0 || lesson.getGroupId() != eventGroupId
                || lesson.getSubjectId() <= 0 || lesson.getSemesterId() <= 0
                || lesson.getLessonNumber() <= 0 || lesson.getDate().isBlank()
                || lesson.getStatus().isBlank()) {
            throw new ScheduleServiceUnavailableException("Schedule returned a malformed lesson snapshot");
        }
        final LocalDate date;
        try {
            date = LocalDate.parse(lesson.getDate());
        } catch (RuntimeException error) {
            throw new ScheduleServiceUnavailableException("Schedule returned a malformed lesson date");
        }
        String status = lesson.getStatus().toLowerCase(Locale.ROOT);
        if (!Set.of("closed", "cancelled", "transferred").contains(status)) {
            throw new ScheduleServiceUnavailableException("Schedule returned an invalid lesson status");
        }
        return date;
    }

    private void validateRosterEcho(GroupMembersResponse members, LocalDate lessonDate, long semesterId) {
        if (members == null || !members.hasAsOfDate() || !members.hasSemesterId()
                || !lessonDate.toString().equals(members.getAsOfDate())
                || members.getSemesterId() != semesterId) {
            throw new AcademicServiceUnavailableException(
                    "Academic returned a missing or mismatched historical roster echo");
        }
        Set<Long> ids = new HashSet<>();
        for (StudentInfo student : members.getStudentsList()) {
            if (student.getUserId() <= 0 || !ids.add(student.getUserId())) {
                throw new AcademicServiceUnavailableException(
                        "Academic returned duplicate or invalid historical student identity");
            }
        }
    }

    private void ensureCancellationMarker(Long lessonId) {
        if (lessonId == null || lessonId <= 0) {
            throw new IllegalArgumentException("lessonId must be positive");
        }
        Query markerQuery = Query.query(Criteria.where("lesson_id").is(lessonId));
        Update markerUpdate = new Update()
                .setOnInsert("lesson_id", lessonId)
                .setOnInsert("marked_at", Instant.now());
        try {
            mongoTemplate.upsert(markerQuery, markerUpdate, LessonCancellationMarker.class);
        } catch (DuplicateKeyException race) {
            // Two cancellation/close consumers may race on the unique marker.
            // The winner's durable row is sufficient; only propagate if the
            // failed upsert was not followed by a visible marker.
            if (!isCancellationMarked(lessonId)) {
                throw race;
            }
        }
    }

    private boolean isCancellationMarked(Long lessonId) {
        return mongoTemplate.exists(
                Query.query(Criteria.where("lesson_id").is(lessonId)),
                LessonCancellationMarker.class);
    }

    private void applyCancellation(Long lessonId) {
        Query all = Query.query(Criteria.where("lesson_id").is(lessonId));
        Update update = new Update()
                .set("status", AttendanceStatus.CANCELLED)
                .set("updated_at", Instant.now());
        UpdateResult first = mongoTemplate.updateMulti(all, update, AttendanceDocument.class);
        Query notCancelled = Query.query(Criteria.where("lesson_id").is(lessonId)
                .and("status").ne(AttendanceStatus.CANCELLED));
        if (mongoTemplate.count(notCancelled, AttendanceDocument.class) > 0) {
            mongoTemplate.updateMulti(notCancelled, update, AttendanceDocument.class);
            if (mongoTemplate.count(notCancelled, AttendanceDocument.class) > 0) {
                throw new IllegalStateException(
                        "Cancellation postcondition failed for lesson " + lessonId);
            }
        }
        log.debug("lesson.cancelled: lessonId={}, updatedCount={}", lessonId, first.getModifiedCount());
    }

    /**
     * Cascade-delete attendance docs for lessons that were physically removed in
     * schedule-service (e.g. PLANNED rows wiped during a ScheduleItem edit).
     * Idempotent — missing ids yield 0 deletes without exception.
     * This is critical: without it, the docs would orphan, surface as duplicates
     * alongside the regenerated lessons, and inflate stats.
     */
    public void processLessonsDeleted(java.util.List<Long> lessonIds) {
        if (lessonIds == null || lessonIds.isEmpty()) return;
        Query filter = Query.query(Criteria.where("lesson_id").in(lessonIds));
        DeleteResult result = mongoTemplate.remove(filter, AttendanceDocument.class);
        log.info("lesson.deleted: lessonIds={}, deletedCount={}",
                lessonIds.size(), result.getDeletedCount());
    }

}
