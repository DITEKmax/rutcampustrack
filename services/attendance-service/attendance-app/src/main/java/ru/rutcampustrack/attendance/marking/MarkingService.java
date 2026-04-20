package ru.rutcampustrack.attendance.marking;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;
import ru.rutcampustrack.academic.grpc.GroupMembersResponse;
import ru.rutcampustrack.attendance.checkin.AttendanceDocument;
import ru.rutcampustrack.attendance.contract.dto.marking.MarkBatchItem;
import ru.rutcampustrack.attendance.contract.dto.marking.MarkBatchRequest;
import ru.rutcampustrack.attendance.contract.dto.marking.MarkRequest;
import ru.rutcampustrack.attendance.contract.enums.AttendanceSource;
import ru.rutcampustrack.attendance.contract.enums.AttendanceStatus;
import ru.rutcampustrack.attendance.event.AttendanceEventPublisher;
import ru.rutcampustrack.attendance.exception.AccessDeniedException;
import ru.rutcampustrack.attendance.exception.BadRequestException;
import ru.rutcampustrack.attendance.grpc.AcademicGrpcClient;
import ru.rutcampustrack.attendance.grpc.ScheduleGrpcClient;
import ru.rutcampustrack.attendance.security.RequestContext;
import ru.rutcampustrack.attendance.semester.SemesterCacheService;
import ru.rutcampustrack.schedule.grpc.LessonResponse;
import ru.rutcampustrack.shared.observability.AsyncGrpcUtils;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * Orchestrates headman manual marking of student attendance.
 *
 * Authorization checks (D-12, D-13):
 * 1. Caller must be headman (requestContext.isHeadman())
 * 2. Lesson must belong to headman's group
 * 3. Target student must be a member of headman's group
 *
 * Write strategy (D-15): MongoTemplate upsert with $set/$setOnInsert so that
 * immutable fields (lesson_id, user_id, group_id, subject_id, semester_id, etc.)
 * are only written on the first insert and mutable fields (status, source, marked_by,
 * updated_at) are always overwritten.
 *
 * Events (INFRA-06): publishes attendance.marked after successful upsert.
 */
@Service
@RequiredArgsConstructor
public class MarkingService {

    /** Statuses that a headman is allowed to set. CANCELLED is system-only. */
    private static final Set<AttendanceStatus> ALLOWED_STATUSES = Set.of(
            AttendanceStatus.PRESENT,
            AttendanceStatus.ABSENT,
            AttendanceStatus.EXCUSED,
            AttendanceStatus.FREE_ATTENDANCE
    );

    private final ScheduleGrpcClient scheduleGrpcClient;
    private final AcademicGrpcClient academicGrpcClient;
    private final MongoTemplate mongoTemplate;
    private final AttendanceEventPublisher eventPublisher;
    private final SemesterCacheService semesterCacheService;
    private final RequestContext requestContext;
    @Qualifier("grpcTaskExecutor")
    private final TaskExecutor grpcTaskExecutor;

    /**
     * Marks attendance for a student on a lesson.
     *
     * @param lessonId the lesson to mark attendance for
     * @param userId   the student whose attendance is being set
     * @param request  contains the desired AttendanceStatus
     * @return the persisted or updated AttendanceDocument
     */
    public AttendanceDocument markAttendance(Long lessonId, Long userId, MarkRequest request) {
        // D-14: Validate that status is not CANCELLED (system-only)
        if (!ALLOWED_STATUSES.contains(request.status())) {
            throw new BadRequestException(
                    "Недопустимый статус: " + request.status() + ". CANCELLED устанавливается только системой");
        }

        // D-12: Only headman can mark attendance
        if (!requestContext.isHeadman()) {
            throw new AccessDeniedException("Только староста может отмечать посещаемость");
        }

        // D-12: Get the lesson and verify it belongs to the headman's group
        LessonResponse lesson = scheduleGrpcClient.getLessonById(lessonId);
        if (!requestContext.getGroupId().equals(lesson.getGroupId())) {
            throw new AccessDeniedException("Нельзя отмечать студентов чужой группы");
        }

        // D-13: Verify target student is a member of the headman's group
        GroupMembersResponse members = academicGrpcClient.getGroupMembers(requestContext.getGroupId());
        boolean studentInGroup = members.getStudentsList().stream()
                .anyMatch(s -> s.getUserId() == userId);
        if (!studentInGroup) {
            throw new AccessDeniedException("Студент не принадлежит вашей группе");
        }

        // D-15: Upsert with $set (mutable) / $setOnInsert (immutable)
        Query filter = Query.query(
                Criteria.where("lesson_id").is(lessonId)
                        .and("user_id").is(userId)
        );
        Update update = new Update()
                .set("status", request.status())
                .set("source", AttendanceSource.HEADMAN)
                .set("marked_by", requestContext.getUserId())
                .set("updated_at", Instant.now())
                .setOnInsert("lesson_id", lessonId)
                .setOnInsert("user_id", userId)
                .setOnInsert("group_id", lesson.getGroupId())
                .setOnInsert("subject_id", lesson.getSubjectId())
                .setOnInsert("semester_id", semesterCacheService.getActiveSemesterId())
                .setOnInsert("lesson_number", lesson.getLessonNumber())
                .setOnInsert("lesson_date", LocalDate.parse(lesson.getDate()))
                .setOnInsert("created_at", Instant.now());

        mongoTemplate.upsert(filter, update, AttendanceDocument.class);

        // Read back the document for event publishing and response construction
        AttendanceDocument doc = mongoTemplate.findOne(filter, AttendanceDocument.class);

        // INFRA-06: Publish attendance.marked event
        eventPublisher.publishMarked(doc);

        return doc;
    }

    /**
     * M05 D7 / P2-10/4: pseudo-atomic batch mark.
     *
     * <p>Все authorization-checks выполняются <b>до</b> любого Mongo write.
     * Если любой check падает — весь batch отклонён с 400/403, Mongo не тронут.
     *
     * <p>Оптимизации относительно N single-mark вызовов:
     * <ul>
     *   <li>1 gRPC {@code scheduleGrpcClient.getLessonById} на уникальный lessonId
     *       (обычно все items одного урока — 1 вызов вместо N).</li>
     *   <li>1 gRPC {@code academicGrpcClient.getGroupMembers} (один вызов на
     *       всю группу старосты) вместо N.</li>
     *   <li>1 cache-hit {@code semesterCacheService.getActiveSemesterId} на batch.</li>
     * </ul>
     *
     * <p>Ожидаемый выигрыш: для 30-student batch ~10× снижение latency
     * (3 gRPC round-trip вместо ~90, 30 Mongo upsert вместо 30 +
     * round-trip per upsert).
     */
    /**
     * M05 audit fix (security #1): ограничение fan-out'а одним запросом.
     * Headman в реальных сценариях отмечает посещаемость одного урока
     * (bulk-mark на экране журнала одной пары). Кэп защищает
     * {@code grpcTaskExecutor} (core=2 / max=8 / queue=100) от
     * DoS'а через 100-item payload со 100 уникальными lessonId'ами.
     */
    private static final int MAX_UNIQUE_LESSONS_PER_BATCH = 10;

    public List<AttendanceDocument> markBatch(MarkBatchRequest request) {
        List<MarkBatchItem> items = request.items();

        // D-14: validate statuses (CANCELLED forbidden)
        for (MarkBatchItem item : items) {
            if (!ALLOWED_STATUSES.contains(item.status())) {
                throw new BadRequestException(
                        "Недопустимый статус: " + item.status()
                                + ". CANCELLED устанавливается только системой");
            }
        }

        // D-12: headman role (из JWT claim'а)
        if (!requestContext.isHeadman()) {
            throw new AccessDeniedException("Только староста может отмечать посещаемость");
        }

        Long headmanGroupId = requestContext.getGroupId();
        Long callerId = requestContext.getUserId();

        // M05 audit fix (security #2): re-check isHeadman через source-of-truth
        // (academic-service). JWT claim is_headman валиден до истечения токена
        // (~15 мин), revoke в academic не виден здесь без cross-service
        // invalidation. Re-check обслуживается rbac-кэшем → ~O(60s) window,
        // приемлемый компромисс между latency и RBAC-freshness.
        if (!academicGrpcClient.isHeadman(callerId, headmanGroupId).getIsHeadman()) {
            throw new AccessDeniedException("Только староста может отмечать посещаемость");
        }

        // M05 G8: N уникальных getLessonById + 1 getGroupMembers fan-out
        // параллельно через grpcTaskExecutor. Deadline 3s enforced на stub.
        Set<Long> uniqueLessonIds = items.stream()
                .map(MarkBatchItem::lessonId)
                .collect(Collectors.toSet());
        if (uniqueLessonIds.size() > MAX_UNIQUE_LESSONS_PER_BATCH) {
            throw new BadRequestException(
                    "Батч должен содержать items не более чем для "
                            + MAX_UNIQUE_LESSONS_PER_BATCH + " различных уроков");
        }
        Map<Long, CompletableFuture<LessonResponse>> lessonFuts = new HashMap<>(uniqueLessonIds.size());
        for (Long lessonId : uniqueLessonIds) {
            lessonFuts.put(lessonId, CompletableFuture.supplyAsync(
                    () -> scheduleGrpcClient.getLessonById(lessonId), grpcTaskExecutor));
        }
        CompletableFuture<GroupMembersResponse> membersFut = CompletableFuture.supplyAsync(
                () -> academicGrpcClient.getGroupMembers(headmanGroupId), grpcTaskExecutor);

        Map<Long, LessonResponse> lessonsById = new HashMap<>(uniqueLessonIds.size());
        for (Map.Entry<Long, CompletableFuture<LessonResponse>> e : lessonFuts.entrySet()) {
            LessonResponse lesson = AsyncGrpcUtils.joinOrUnwrap(e.getValue());
            if (!headmanGroupId.equals(lesson.getGroupId())) {
                // M05 audit fix (security #4): без id's в message — избегаем
                // enumeration side-channel (тайминг/текст).
                throw new AccessDeniedException("Нельзя отмечать студентов чужой группы");
            }
            lessonsById.put(e.getKey(), lesson);
        }
        GroupMembersResponse members = AsyncGrpcUtils.joinOrUnwrap(membersFut);
        Set<Long> allowedStudentIds = new HashSet<>(members.getStudentsList().size());
        members.getStudentsList().forEach(s -> allowedStudentIds.add(s.getUserId()));

        for (MarkBatchItem item : items) {
            if (!allowedStudentIds.contains(item.userId())) {
                throw new AccessDeniedException("Студент не принадлежит вашей группе");
            }
        }

        // Все pre-check'и прошли — выполняем upsert'ы. Mongo standalone
        // не поддерживает multi-doc transactions (M06 scope — replica set).
        // Partial-failure из-за infra остаётся edge-case (idempotent upsert
        // переживает retry).
        //
        // M05 audit fix (bug-hunter 2.1): события публикуются ПОСЛЕ успешного
        // upsert'а всех items. Если N-й upsert падает — уже накопленные k-1
        // доки не триггерят attendance.marked, client retry'ит весь batch и
        // получает 0 дубликатов событий (upsert идемпотентен по данным).
        // Плюс findAndModify(returnNew=true) вместо upsert+findOne — один
        // round-trip на item вместо двух.
        Long semesterId = semesterCacheService.getActiveSemesterId();
        Instant now = Instant.now();
        Long markedBy = requestContext.getUserId();
        List<AttendanceDocument> result = new ArrayList<>(items.size());
        FindAndModifyOptions opts = FindAndModifyOptions.options().returnNew(true).upsert(true);

        for (MarkBatchItem item : items) {
            LessonResponse lesson = lessonsById.get(item.lessonId());
            Query filter = Query.query(
                    Criteria.where("lesson_id").is(item.lessonId())
                            .and("user_id").is(item.userId()));
            Update update = new Update()
                    .set("status", item.status())
                    .set("source", AttendanceSource.HEADMAN)
                    .set("marked_by", markedBy)
                    .set("updated_at", now)
                    .setOnInsert("lesson_id", item.lessonId())
                    .setOnInsert("user_id", item.userId())
                    .setOnInsert("group_id", lesson.getGroupId())
                    .setOnInsert("subject_id", lesson.getSubjectId())
                    .setOnInsert("semester_id", semesterId)
                    .setOnInsert("lesson_number", lesson.getLessonNumber())
                    .setOnInsert("lesson_date", LocalDate.parse(lesson.getDate()))
                    .setOnInsert("created_at", now);
            AttendanceDocument doc = mongoTemplate.findAndModify(
                    filter, update, opts, AttendanceDocument.class);
            result.add(doc);
        }

        // Публикация событий — после того как весь batch персистирован.
        for (AttendanceDocument doc : result) {
            eventPublisher.publishMarked(doc);
        }

        return result;
    }

}
