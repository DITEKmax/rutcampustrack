package ru.rutcampustrack.attendance.event;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import com.mongodb.client.result.DeleteResult;
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
import ru.rutcampustrack.attendance.grpc.AcademicGrpcClient;
import ru.rutcampustrack.attendance.grpc.ScheduleGrpcClient;
import ru.rutcampustrack.attendance.semester.SemesterCacheService;
import ru.rutcampustrack.schedule.grpc.LessonResponse;

import java.time.Instant;
import java.time.LocalDate;

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
@RequiredArgsConstructor
public class LessonEventService {

    private final MongoTemplate mongoTemplate;
    private final ScheduleGrpcClient scheduleGrpcClient;
    private final AcademicGrpcClient academicGrpcClient;
    private final SemesterCacheService semesterCacheService;

    public void processLessonClosed(Long lessonId, Long groupId) {
        LessonResponse lesson = scheduleGrpcClient.getLessonById(lessonId);
        GroupMembersResponse members = academicGrpcClient.getGroupMembers(groupId);

        if (members.getStudentsList().isEmpty()) {
            log.info("lesson.closed: no students in group {} for lesson {}, skipping", groupId, lessonId);
            return;
        }

        Long semesterId = semesterCacheService.getActiveSemesterId();
        if (semesterId == null) {
            log.warn("lesson.closed: semesterId is null (cache miss) for lesson {}", lessonId);
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
                    .setOnInsert("group_id", groupId)
                    .setOnInsert("subject_id", lesson.getSubjectId())
                    .setOnInsert("semester_id", semesterId)
                    .setOnInsert("lesson_number", lesson.getLessonNumber())
                    .setOnInsert("lesson_date", LocalDate.parse(lesson.getDate()))
                    .setOnInsert("status", AttendanceStatus.ABSENT)
                    .setOnInsert("source", AttendanceSource.AUTO_SCHEDULER)
                    .setOnInsert("marked_by", null)
                    .setOnInsert("created_at", now)
                    .setOnInsert("updated_at", now);

            bulkOps.upsert(filter, insert);
        }

        bulkOps.execute();
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
        Query filter = Query.query(Criteria.where("lesson_id").is(lessonId));
        Update update = new Update()
                .set("status", AttendanceStatus.CANCELLED)
                .set("updated_at", Instant.now());

        var result = mongoTemplate.updateMulti(filter, update, AttendanceDocument.class);
        log.info("lesson.cancelled: lessonId={}, updatedCount={}", lessonId, result.getModifiedCount());
    }
}
