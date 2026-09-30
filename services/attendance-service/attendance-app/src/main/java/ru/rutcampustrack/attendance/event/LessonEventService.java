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
import org.springframework.transaction.annotation.Transactional;
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
import ru.rutcampustrack.attendance.student.PairWriteCoordinator;
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
 * Lifecycle mutations share the Mongo transaction with their lesson-level
 * fence. Exceptions propagate so Spring AMQP nacks the message to DLQ.
 */
@Service
@Slf4j
public class LessonEventService {

    private final MongoTemplate mongoTemplate;
    private final ScheduleGrpcClient scheduleGrpcClient;
    private final AcademicGrpcClient academicGrpcClient;
    private final SemesterCacheService semesterCacheService;
    private final TaskExecutor grpcTaskExecutor;
    private final PairWriteCoordinator pairWriteCoordinator;

    @org.springframework.beans.factory.annotation.Autowired
    public LessonEventService(MongoTemplate mongoTemplate,
                              ScheduleGrpcClient scheduleGrpcClient,
                              AcademicGrpcClient academicGrpcClient,
                              SemesterCacheService semesterCacheService,
                              @Qualifier("grpcTaskExecutor") TaskExecutor grpcTaskExecutor,
                              PairWriteCoordinator pairWriteCoordinator) {
        this.mongoTemplate = mongoTemplate;
        this.scheduleGrpcClient = scheduleGrpcClient;
        this.academicGrpcClient = academicGrpcClient;
        this.semesterCacheService = semesterCacheService;
        this.grpcTaskExecutor = grpcTaskExecutor;
        this.pairWriteCoordinator = pairWriteCoordinator;
    }

    /** Source-compatible constructor for focused service tests. */
    public LessonEventService(MongoTemplate mongoTemplate,
                              ScheduleGrpcClient scheduleGrpcClient,
                              AcademicGrpcClient academicGrpcClient,
                              SemesterCacheService semesterCacheService,
                              @Qualifier("grpcTaskExecutor") TaskExecutor grpcTaskExecutor) {
        this(mongoTemplate, scheduleGrpcClient, academicGrpcClient, semesterCacheService,
                grpcTaskExecutor, new PairWriteCoordinator(mongoTemplate));
    }

    /** Performs external reads before the event transaction and any local fence acquisition. */
    public LessonClosedSnapshot prepareLessonClosed(Long lessonId, Long groupId, Long eventSemesterId) {
        requirePositive(lessonId, "lessonId");
        requirePositive(groupId, "groupId");
        requirePositive(eventSemesterId, "semesterId");
        LessonResponse lesson = scheduleGrpcClient.getLessonById(lessonId);
        LocalDate lessonDate = validateLessonSnapshot(lesson, lessonId, groupId, eventSemesterId);
        GroupMembersResponse members = null;
        if (!"transferred".equalsIgnoreCase(lesson.getStatus())
                && !"cancelled".equalsIgnoreCase(lesson.getStatus())) {
            members = academicGrpcClient.getGroupMembers(
                    lesson.getGroupId(), lessonDate, lesson.getSemesterId());
            validateRosterEcho(members, lessonDate, lesson.getSemesterId());
        }
        return new LessonClosedSnapshot(lessonId, groupId, eventSemesterId, lesson, lessonDate, members);
    }

    /** Applies only local Mongo state; called with the event claim and receipt transaction active. */
    @Transactional(transactionManager = "mongoTransactionManager")
    public void applyLessonClosed(LessonClosedSnapshot snapshot) {
        Long lessonId = snapshot.lessonId();
        Long groupId = snapshot.groupId();
        Long eventSemesterId = snapshot.semesterId();
        LessonResponse lesson = snapshot.lesson();
        LocalDate lessonDate = snapshot.lessonDate();
        GroupMembersResponse members = snapshot.members();

        if ("transferred".equalsIgnoreCase(lesson.getStatus())) {
            pairWriteCoordinator.lockAcceptedScheduleEffectLessons(
                    eventSemesterId, java.util.List.of(lessonId), groupId, Instant.now());
            log.info("lesson.closed: transferred source {}, leaving attendance unchanged", lessonId);
            return;
        }

        pairWriteCoordinator.lockAcceptedScheduleEffectLessons(
                eventSemesterId, java.util.List.of(lessonId), groupId, Instant.now());
        validateLessonAttendanceSemesterScope(lessonId, eventSemesterId);
        validateCancellationMarkerSemesterScope(lessonId, eventSemesterId);
        resolveLegacyLessonAttendanceScope(lessonId, eventSemesterId);

        if ("cancelled".equalsIgnoreCase(lesson.getStatus())) {
            ensureCancellationMarker(lessonId, eventSemesterId);
            applyCancellation(lessonId, eventSemesterId);
            return;
        }

        // A cancellation may have won the race while the historical roster was
        // being resolved.  Do not start materialization after its marker.
        if (isCancellationMarked(lessonId)) {
            applyCancellation(lessonId, eventSemesterId);
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
            applyCancellation(lessonId, eventSemesterId);
        }
        log.info("lesson.closed: lessonId={}, processed {} students for auto-absent",
                lessonId, members.getStudentsCount());
    }

    /** Compatibility overload resolves the exact lesson scope from Schedule, never the active semester. */
    public void processLessonClosed(Long lessonId, Long groupId, Long eventSemesterId) {
        applyLessonClosed(prepareLessonClosed(lessonId, groupId, eventSemesterId));
    }

    public void processLessonClosed(Long lessonId, Long groupId) {
        LessonResponse lesson = scheduleGrpcClient.getLessonById(lessonId);
        if (lesson == null || lesson.getSemesterId() <= 0) {
            throw new ScheduleServiceUnavailableException("Schedule returned a lesson without a positive semester");
        }
        processLessonClosed(lessonId, groupId, lesson.getSemesterId());
    }

    public record LessonClosedSnapshot(Long lessonId, Long groupId, Long semesterId,
                                       LessonResponse lesson, LocalDate lessonDate,
                                       GroupMembersResponse members) { }

    /** Validate the local scope for a tracked close before entering its nested transaction. */
    public void preflightLessonClosed(LessonClosedSnapshot snapshot) {
        if (snapshot == null || snapshot.lesson() == null) {
            throw new IllegalArgumentException("lesson.closed snapshot is required");
        }
        Long lessonId = snapshot.lessonId();
        Long groupId = snapshot.groupId();
        Long semesterId = snapshot.semesterId();
        requirePositive(lessonId, "lessonId");
        requirePositive(groupId, "groupId");
        requirePositive(semesterId, "semesterId");
        pairWriteCoordinator.lockAcceptedScheduleEffectLessons(
                semesterId, java.util.List.of(lessonId), groupId, Instant.now());
        if ("transferred".equalsIgnoreCase(snapshot.lesson().getStatus())) {
            return;
        }
        validateLessonAttendanceSemesterScope(lessonId, semesterId);
        validateCancellationMarkerSemesterScope(lessonId, semesterId);
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
    @Transactional(transactionManager = "mongoTransactionManager")
    public void processOneOffLessonCancelled(Long semesterId, Long groupId, LocalDate date, Integer lessonNumber) {
        requirePositive(semesterId, "semesterId");
        requirePositive(groupId, "groupId");
        if (date == null || lessonNumber == null || lessonNumber <= 0) {
            throw new IllegalArgumentException("one-off lesson scope is incomplete");
        }
        pairWriteCoordinator.lockAcceptedScheduleEffectLessons(
                semesterId, java.util.List.of(), groupId, Instant.now());
        validateOneOffAttendanceSemesterScope(semesterId, groupId, date, lessonNumber);
        Query filter = Query.query(new Criteria().andOperator(
                Criteria.where("group_id").is(groupId),
                Criteria.where("lesson_date").is(date),
                Criteria.where("lesson_number").is(lessonNumber),
                Criteria.where("semester_id").is(semesterId)));
        DeleteResult result = mongoTemplate.remove(filter, AttendanceDocument.class);
        log.info("lesson.one_off.cancelled: groupId={}, date={}, lessonNumber={}, deletedCount={}",
                groupId, date, lessonNumber, result.getDeletedCount());
    }

    /** Acquires the event fence and validates every natural-key match before the nested delete transaction. */
    public void preflightOneOffLessonCancelled(Long semesterId, Long groupId,
                                               LocalDate date, Integer lessonNumber) {
        requirePositive(semesterId, "semesterId");
        requirePositive(groupId, "groupId");
        if (date == null || lessonNumber == null || lessonNumber <= 0) {
            throw new IllegalArgumentException("one-off lesson scope is incomplete");
        }
        pairWriteCoordinator.lockAcceptedScheduleEffectLessons(
                semesterId, java.util.List.of(), groupId, Instant.now());
        validateOneOffAttendanceSemesterScope(semesterId, groupId, date, lessonNumber);
    }

    /** Compatibility overload infers the exact scope only from matching persisted attendance rows. */
    @Transactional(transactionManager = "mongoTransactionManager")
    public void processOneOffLessonCancelled(Long groupId, LocalDate date, Integer lessonNumber) {
        Query filter = Query.query(Criteria.where("group_id").is(groupId)
                .and("lesson_date").is(date).and("lesson_number").is(lessonNumber));
        java.util.List<AttendanceDocument> matches = mongoTemplate.find(filter, AttendanceDocument.class);
        java.util.Set<Long> semesters = matches.stream().map(AttendanceDocument::getSemesterId)
                .filter(java.util.Objects::nonNull).collect(java.util.stream.Collectors.toSet());
        if (semesters.size() != 1 || matches.stream().anyMatch(row -> row.getSemesterId() == null)) {
            throw new ScheduleServiceUnavailableException("One-off lesson semester cannot be resolved authoritatively");
        }
        processOneOffLessonCancelled(semesters.iterator().next(), groupId, date, lessonNumber);
    }

    @Transactional(transactionManager = "mongoTransactionManager")
    public void processLessonCancelled(Long lessonId, Long semesterId) {
        requirePositive(lessonId, "lessonId");
        requirePositive(semesterId, "semesterId");
        pairWriteCoordinator.lockAcceptedScheduleEffectLessons(
                semesterId, java.util.List.of(lessonId), null, Instant.now());
        if (pairWriteCoordinator.isTransferredSource(lessonId)) {
            log.info("lesson.cancelled: transferred source {}, leaving moved attendance unchanged", lessonId);
            return;
        }
        validateLessonAttendanceSemesterScope(lessonId, semesterId);
        validateCancellationMarkerSemesterScope(lessonId, semesterId);
        resolveLegacyLessonAttendanceScope(lessonId, semesterId);
        // The marker must be durable before any attendance update.  Retries
        // are idempotent because both operations are keyed by lesson_id.
        ensureCancellationMarker(lessonId, semesterId);
        applyCancellation(lessonId, semesterId);
        log.info("lesson.cancelled: lessonId={}, cancellation marker durable", lessonId);
    }

    /** Acquires the event fences and validates attendance plus marker scope before a nested transaction runs. */
    public void preflightLessonCancelled(Long lessonId, Long semesterId) {
        requirePositive(lessonId, "lessonId");
        requirePositive(semesterId, "semesterId");
        pairWriteCoordinator.lockAcceptedScheduleEffectLessons(
                semesterId, java.util.List.of(lessonId), null, Instant.now());
        if (pairWriteCoordinator.isTransferredSource(lessonId)) {
            return;
        }
        validateLessonAttendanceSemesterScope(lessonId, semesterId);
        validateCancellationMarkerSemesterScope(lessonId, semesterId);
    }

    @Transactional(transactionManager = "mongoTransactionManager")
    public void processLessonCancelled(Long lessonId) {
        LessonResponse lesson = scheduleGrpcClient.getLessonById(lessonId);
        if (lesson == null || lesson.getSemesterId() <= 0) {
            throw new ScheduleServiceUnavailableException("Schedule returned a lesson without a positive semester");
        }
        processLessonCancelled(lessonId, lesson.getSemesterId());
    }

    private LocalDate validateLessonSnapshot(LessonResponse lesson, Long lessonId,
                                             Long eventGroupId, Long eventSemesterId) {
        if (lessonId == null || eventGroupId == null
                || lesson == null || lesson.getId() != lessonId
                || lesson.getGroupId() <= 0 || lesson.getGroupId() != eventGroupId
                || lesson.getSubjectId() <= 0 || lesson.getSemesterId() <= 0
                || lesson.getSemesterId() != eventSemesterId
                || lesson.getLessonNumber() <= 0 || lesson.getDate() == null || lesson.getDate().isBlank()
                || lesson.getStatus() == null || lesson.getStatus().isBlank()) {
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

    private void ensureCancellationMarker(Long lessonId, Long semesterId) {
        if (lessonId == null || lessonId <= 0) {
            throw new IllegalArgumentException("lessonId must be positive");
        }
        Query markerQuery = Query.query(Criteria.where("lesson_id").is(lessonId));
        // Reject a marker from another semester before the upsert can create or
        // modify any domain state in a transaction that must persist an ERROR receipt.
        validateCancellationMarkerSemesterScope(lessonId, semesterId);
        Update markerUpdate = new Update()
                .setOnInsert("lesson_id", lessonId)
                .setOnInsert("semester_id", semesterId)
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
        LessonCancellationMarker marker = mongoTemplate.findOne(markerQuery, LessonCancellationMarker.class);
        if (marker == null) {
            throw new IllegalStateException("Cancellation marker disappeared during event processing");
        }
        if (marker.getSemesterId() != null && !semesterId.equals(marker.getSemesterId())) {
            throw new IllegalStateException("Cancellation marker scope changed while the lesson fence was held");
        }
        if (marker.getSemesterId() == null) {
            mongoTemplate.updateFirst(markerQuery,
                    new Update().set("semester_id", semesterId), LessonCancellationMarker.class);
        }
    }

    private boolean isCancellationMarked(Long lessonId) {
        return mongoTemplate.exists(
                Query.query(Criteria.where("lesson_id").is(lessonId)),
                LessonCancellationMarker.class);
    }

    private void applyCancellation(Long lessonId, Long semesterId) {
        Query all = Query.query(Criteria.where("lesson_id").is(lessonId));
        Update update = new Update()
                .set("status", AttendanceStatus.CANCELLED)
                .set("semester_id", semesterId)
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
    @Transactional(transactionManager = "mongoTransactionManager")
    public void processLessonsDeleted(java.util.List<Long> lessonIds, Long semesterId) {
        if (lessonIds == null || lessonIds.isEmpty()) return;
        requirePositive(semesterId, "semesterId");
        if (lessonIds.stream().anyMatch(id -> id == null || id <= 0)) {
            throw new IllegalArgumentException("lesson.deleted contains invalid lesson ids");
        }
        pairWriteCoordinator.lockAcceptedScheduleEffectLessons(semesterId, lessonIds, null, Instant.now());
        validateDeletedAttendanceSemesterScopes(lessonIds, semesterId);
        Query filter = Query.query(Criteria.where("lesson_id").in(lessonIds));
        DeleteResult result = mongoTemplate.remove(filter, AttendanceDocument.class);
        log.info("lesson.deleted: lessonIds={}, deletedCount={}",
                lessonIds.size(), result.getDeletedCount());
    }

    /** Acquires all event fences and validates the complete batch before the nested cascade delete. */
    public void preflightLessonsDeleted(java.util.List<Long> lessonIds, Long semesterId) {
        if (lessonIds == null || lessonIds.isEmpty()) return;
        requirePositive(semesterId, "semesterId");
        if (lessonIds.stream().anyMatch(id -> id == null || id <= 0)) {
            throw new IllegalArgumentException("lesson.deleted contains invalid lesson ids");
        }
        pairWriteCoordinator.lockAcceptedScheduleEffectLessons(semesterId, lessonIds, null, Instant.now());
        validateDeletedAttendanceSemesterScopes(lessonIds, semesterId);
    }

    @Transactional(transactionManager = "mongoTransactionManager")
    public void processLessonsDeleted(java.util.List<Long> lessonIds) {
        if (lessonIds == null || lessonIds.isEmpty()) return;
        java.util.List<AttendanceDocument> rows = mongoTemplate.find(
                Query.query(Criteria.where("lesson_id").in(lessonIds)), AttendanceDocument.class);
        java.util.Set<Long> semesters = rows.stream().map(AttendanceDocument::getSemesterId)
                .filter(java.util.Objects::nonNull).collect(java.util.stream.Collectors.toSet());
        if (semesters.size() != 1 || rows.stream().anyMatch(row -> row.getSemesterId() == null)) {
            throw new ScheduleServiceUnavailableException("Deleted lessons semester cannot be resolved authoritatively");
        }
        processLessonsDeleted(lessonIds, semesters.iterator().next());
    }

    private void resolveLegacyLessonAttendanceScope(Long lessonId, Long semesterId) {
        validateLessonAttendanceSemesterScope(lessonId, semesterId);
        Query legacy = Query.query(Criteria.where("lesson_id").is(lessonId)
                .and("semester_id").is(null));
        mongoTemplate.updateMulti(legacy, new Update().set("semester_id", semesterId), AttendanceDocument.class);
    }

    private void validateLessonAttendanceSemesterScope(Long lessonId, Long semesterId) {
        Query conflicting = Query.query(new Criteria().andOperator(
                Criteria.where("lesson_id").is(lessonId),
                Criteria.where("semester_id").ne(semesterId),
                Criteria.where("semester_id").exists(true)));
        if (mongoTemplate.exists(conflicting, AttendanceDocument.class)) {
            throw new SemesterArchiveEffectRejectedException("ATTENDANCE_SCOPE_MISMATCH",
                    "Schedule lesson event conflicts with an attendance row semester");
        }
    }

    private void validateCancellationMarkerSemesterScope(Long lessonId, Long semesterId) {
        LessonCancellationMarker marker = mongoTemplate.findOne(
                Query.query(Criteria.where("lesson_id").is(lessonId)), LessonCancellationMarker.class);
        if (marker != null && marker.getSemesterId() != null
                && !semesterId.equals(marker.getSemesterId())) {
            throw new SemesterArchiveEffectRejectedException("ATTENDANCE_SCOPE_MISMATCH",
                    "Cancellation marker belongs to another semester");
        }
    }

    private void validateDeletedAttendanceSemesterScopes(java.util.List<Long> lessonIds, Long semesterId) {
        for (AttendanceDocument document : mongoTemplate.find(
                Query.query(Criteria.where("lesson_id").in(lessonIds)), AttendanceDocument.class)) {
            if (document.getSemesterId() != null && !semesterId.equals(document.getSemesterId())) {
                throw new SemesterArchiveEffectRejectedException("ATTENDANCE_SCOPE_MISMATCH",
                        "Deleted lesson event conflicts with an attendance row semester");
            }
        }
    }

    private void validateOneOffAttendanceSemesterScope(Long semesterId, Long groupId,
                                                        LocalDate date, Integer lessonNumber) {
        Query naturalKey = Query.query(
                Criteria.where("group_id").is(groupId)
                        .and("lesson_date").is(date)
                        .and("lesson_number").is(lessonNumber)
        );
        java.util.List<AttendanceDocument> matches = mongoTemplate.find(naturalKey, AttendanceDocument.class);
        if (matches.stream().anyMatch(row -> !java.util.Objects.equals(row.getSemesterId(), semesterId))) {
            throw new SemesterArchiveEffectRejectedException("ATTENDANCE_SCOPE_MISMATCH",
                    "One-off cancellation matched an attendance row without the exact semester scope");
        }
    }

    private static void requirePositive(Long value, String field) {
        if (value == null || value <= 0) throw new IllegalArgumentException(field + " must be positive");
    }

}
