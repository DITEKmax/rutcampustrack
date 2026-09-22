package ru.rutcampustrack.attendance.marking;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.rutcampustrack.academic.grpc.GroupMembersResponse;
import ru.rutcampustrack.academic.grpc.StudentInfo;
import ru.rutcampustrack.attendance.checkin.AttendanceDocument;
import ru.rutcampustrack.attendance.contract.dto.marking.MarkBatchItem;
import ru.rutcampustrack.attendance.contract.dto.marking.MarkBatchRequest;
import ru.rutcampustrack.attendance.contract.dto.marking.MarkRequest;
import ru.rutcampustrack.attendance.contract.enums.AttendanceSource;
import ru.rutcampustrack.attendance.contract.enums.AttendanceStatus;
import ru.rutcampustrack.attendance.contract.enums.ExcuseType;
import ru.rutcampustrack.attendance.contract.exception.ResourceNotFoundException;
import ru.rutcampustrack.attendance.event.AttendanceEventPublisher;
import ru.rutcampustrack.attendance.exception.AccessDeniedException;
import ru.rutcampustrack.attendance.exception.AcademicServiceUnavailableException;
import ru.rutcampustrack.attendance.exception.BadRequestException;
import ru.rutcampustrack.attendance.grpc.AcademicGrpcClient;
import ru.rutcampustrack.attendance.grpc.ScheduleGrpcClient;
import ru.rutcampustrack.attendance.journal.JournalLessonPolicy;
import ru.rutcampustrack.attendance.security.RequestContext;
import ru.rutcampustrack.attendance.semester.SemesterCacheService;
import ru.rutcampustrack.attendance.student.PairWriteCoordinator;
import ru.rutcampustrack.schedule.grpc.LessonResponse;
import ru.rutcampustrack.shared.observability.AsyncGrpcUtils;
import org.springframework.web.multipart.MultipartFile;

import java.time.Instant;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Objects;
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
public class MarkingService {

    /** Statuses that a headman is allowed to set. CANCELLED is system-only. */
    private static final Set<AttendanceStatus> ALLOWED_STATUSES = Set.of(
            AttendanceStatus.PRESENT,
            AttendanceStatus.ABSENT,
            AttendanceStatus.EXCUSED
    );

    private final ScheduleGrpcClient scheduleGrpcClient;
    private final AcademicGrpcClient academicGrpcClient;
    private final MongoTemplate mongoTemplate;
    private final AttendanceEventPublisher eventPublisher;
    private final SemesterCacheService semesterCacheService;
    private final RequestContext requestContext;
    private final PairWriteCoordinator pairWriteCoordinator;
    private final Clock clock;
    @Qualifier("grpcTaskExecutor")
    private final TaskExecutor grpcTaskExecutor;
    private final AttendanceAttachmentService attachmentService;

    @Autowired
    public MarkingService(ScheduleGrpcClient scheduleGrpcClient,
                          AcademicGrpcClient academicGrpcClient,
                          MongoTemplate mongoTemplate,
                          AttendanceEventPublisher eventPublisher,
                          SemesterCacheService semesterCacheService,
                          RequestContext requestContext,
                          PairWriteCoordinator pairWriteCoordinator,
                          Clock clock,
                          @Qualifier("grpcTaskExecutor") TaskExecutor grpcTaskExecutor,
                          AttendanceAttachmentService attachmentService) {
        this.scheduleGrpcClient = scheduleGrpcClient;
        this.academicGrpcClient = academicGrpcClient;
        this.mongoTemplate = mongoTemplate;
        this.eventPublisher = eventPublisher;
        this.semesterCacheService = semesterCacheService;
        this.requestContext = requestContext;
        this.pairWriteCoordinator = pairWriteCoordinator;
        this.clock = clock;
        this.grpcTaskExecutor = grpcTaskExecutor;
        this.attachmentService = attachmentService;
    }

    /** Source-compatible constructor for focused unit tests that do not exercise attachments. */
    public MarkingService(ScheduleGrpcClient scheduleGrpcClient,
                          AcademicGrpcClient academicGrpcClient,
                          MongoTemplate mongoTemplate,
                          AttendanceEventPublisher eventPublisher,
                          SemesterCacheService semesterCacheService,
                          RequestContext requestContext,
                          PairWriteCoordinator pairWriteCoordinator,
                          Clock clock,
                          TaskExecutor grpcTaskExecutor) {
        this(scheduleGrpcClient, academicGrpcClient, mongoTemplate, eventPublisher,
                semesterCacheService, requestContext, pairWriteCoordinator, clock,
                grpcTaskExecutor, null);
    }

    /**
     * Marks attendance for a student on a lesson.
     *
     * @param lessonId the lesson to mark attendance for
     * @param userId   the student whose attendance is being set
     * @param request  contains the desired AttendanceStatus
     * @return the persisted or updated AttendanceDocument
     */
    @Transactional
    public AttendanceDocument markAttendance(Long lessonId, Long userId, MarkRequest request) {
        return markAttendance(lessonId, userId, request, null);
    }

    @Transactional
    public AttendanceDocument markAttendance(Long lessonId, Long userId,
                                             MarkRequest request, MultipartFile file) {
        validateManualRequest(request, file);
        LessonResponse lesson = requireWritableLesson(lessonId);
        requireStudentInRoster(membersForLesson(lesson), userId);

        Instant now = clock.instant();
        pairWriteCoordinator.lock(userId, lessonId, lesson.getGroupId(), now);
        Query filter = pairFilter(lessonId, userId);
        AttendanceDocument existing = mongoTemplate.findOne(filter, AttendanceDocument.class);
        requireAttendancePermission(lesson.getGroupId(), request.status() == AttendanceStatus.EXCUSED
                || (existing != null && existing.getStatus() == AttendanceStatus.EXCUSED)
                ? "MANAGE_EXCUSES" : "MARK_ATTENDANCE");
        boolean retainExistingAttachment = request.status() == AttendanceStatus.EXCUSED
                && file == null
                && existing != null
                && existing.getStatus() == AttendanceStatus.EXCUSED
                && existing.getAttachmentId() != null
                && attachmentService != null
                && attachmentService.isAvailable(lessonId, userId, existing.getAttachmentId());

        AttendanceAttachmentService.StoredAttachment attachment = null;
        if (file != null) {
            if (attachmentService == null) {
                throw new IllegalStateException("Attendance attachment storage is unavailable");
            }
            attachment = attachmentService.replace(lessonId, userId, lesson.getGroupId(), file);
        } else if (!retainExistingAttachment && attachmentService != null) {
            attachmentService.delete(lessonId, userId);
        }

        Update update = new Update()
                .set("status", request.status())
                .set("source", AttendanceSource.HEADMAN)
                .set("marked_by", requestContext.getUserId())
                .set("updated_at", now)
                .setOnInsert("lesson_id", lessonId)
                .setOnInsert("user_id", userId)
                .setOnInsert("group_id", lesson.getGroupId())
                .setOnInsert("subject_id", lesson.getSubjectId())
                .setOnInsert("semester_id", lesson.getSemesterId())
                .setOnInsert("lesson_number", lesson.getLessonNumber())
                .setOnInsert("lesson_date", LocalDate.parse(lesson.getDate()))
                .setOnInsert("created_at", now);

        if (request.status() == AttendanceStatus.EXCUSED) {
            update.set("excuse_reason", request.excuseType().name())
                    .set("excuse_type", request.excuseType())
                    .set("excuse_comment", normalizeComment(request.comment()));
            if (attachment != null) {
                update.set("attachment_id", attachment.id())
                        .set("attachment_name", attachment.name())
                        .set("attachment_content_type", attachment.contentType())
                        .set("attachment_size", attachment.size());
            } else if (!retainExistingAttachment) {
                update.unset("attachment_id")
                        .unset("attachment_name")
                        .unset("attachment_content_type")
                        .unset("attachment_size");
            }
        } else {
            update.unset("excuse_reason")
                    .unset("excuse_type")
                    .unset("excuse_comment")
                    .unset("attachment_id")
                    .unset("attachment_name")
                    .unset("attachment_content_type")
                    .unset("attachment_size");
        }

        mongoTemplate.upsert(filter, update, AttendanceDocument.class);
        AttendanceDocument doc = mongoTemplate.findOne(filter, AttendanceDocument.class);
        if (doc == null) {
            throw new IllegalStateException("Attendance mark was not persisted");
        }

        String subjectName = resolveSubjectName(lesson.getSubjectId());
        eventPublisher.publishMarked(doc, subjectName);
        return doc;
    }

    @Transactional
    public void clearAttendance(Long lessonId, Long userId) {
        LessonResponse lesson = requireWritableLesson(lessonId);
        requireStudentInRoster(membersForLesson(lesson), userId);
        pairWriteCoordinator.lock(userId, lessonId, lesson.getGroupId(), clock.instant());
        AttendanceDocument existing = mongoTemplate.findOne(pairFilter(lessonId, userId), AttendanceDocument.class);
        requireAttendancePermission(lesson.getGroupId(), existing != null
                && existing.getStatus() == AttendanceStatus.EXCUSED ? "MANAGE_EXCUSES" : "MARK_ATTENDANCE");
        mongoTemplate.remove(pairFilter(lessonId, userId), AttendanceDocument.class);
        if (attachmentService != null) {
            attachmentService.delete(lessonId, userId);
        }
    }

    public AttendanceAttachmentService.AttachmentDownload downloadAttachment(Long lessonId, Long userId) {
        LessonResponse lesson = requireWritableLesson(lessonId);
        requireAttendancePermission(lesson.getGroupId(), "MANAGE_EXCUSES");
        requireStudentInRoster(membersForLesson(lesson), userId);
        AttendanceDocument document = mongoTemplate.findOne(pairFilter(lessonId, userId), AttendanceDocument.class);
        if (document == null || document.getAttachmentId() == null || attachmentService == null) {
            throw new ResourceNotFoundException("AttendanceAttachment", "lessonId", lessonId);
        }
        return attachmentService.download(lessonId, userId, document.getAttachmentId());
    }

    private void validateManualRequest(MarkRequest request, MultipartFile file) {
        if (request == null || request.status() == null || !ALLOWED_STATUSES.contains(request.status())) {
            throw new BadRequestException("Допустимые ручные статусы: PRESENT, ABSENT или EXCUSED");
        }
        String comment = normalizeComment(request.comment());
        if (comment != null && comment.length() > 1000) {
            throw new BadRequestException("Комментарий не должен превышать 1000 символов");
        }
        if (request.status() == AttendanceStatus.EXCUSED) {
            if (request.excuseType() == null || request.excuseType() == ExcuseType.FREE_ATTENDANCE) {
                throw new BadRequestException("Для EXCUSED требуется поддерживаемый тип причины");
            }
            return;
        }
        if (request.excuseType() != null || comment != null || file != null) {
            throw new BadRequestException("Причина, комментарий и файл допустимы только для EXCUSED");
        }
    }

    private LessonResponse requireWritableLesson(Long lessonId) {
        LessonResponse lesson = scheduleGrpcClient.getLessonById(lessonId);
        if (lesson == null || !Objects.equals(requestContext.getGroupId(), lesson.getGroupId())) {
            throw new AccessDeniedException("Нельзя изменять студентов чужой группы");
        }
        JournalLessonPolicy.Timing timing = JournalLessonPolicy.requireTiming(lesson);
        JournalLessonPolicy.requireStarted(timing, clock);
        return lesson;
    }

    private void requireAttendancePermission(Long targetGroupId, String permission) {
        if (targetGroupId == null || !Objects.equals(targetGroupId, requestContext.getGroupId())) {
            throw new AccessDeniedException("Нельзя изменять студентов чужой группы");
        }
        if (requestContext.isHeadman()) {
            if (!academicGrpcClient.isHeadman(requestContext.getUserId(), targetGroupId).getIsHeadman()) {
                throw new AccessDeniedException("Только староста может отмечать посещаемость");
            }
            return;
        }
        if (!academicGrpcClient.hasAssistantPermission(targetGroupId, permission)) {
            throw new AccessDeniedException("Отсутствует право " + permission);
        }
    }

    private GroupMembersResponse membersForLesson(LessonResponse lesson) {
        if (lesson.getSemesterId() <= 0) {
            throw new AcademicServiceUnavailableException(
                    "Schedule returned a lesson without a positive semester");
        }
        LocalDate lessonDate = LocalDate.parse(lesson.getDate());
        GroupMembersResponse response = academicGrpcClient.getGroupMembers(
                lesson.getGroupId(), lessonDate, lesson.getSemesterId());
        validateHistoricalRoster(response, lessonDate, lesson.getSemesterId());
        return response;
    }

    private static void validateHistoricalRoster(GroupMembersResponse members,
                                                 LocalDate lessonDate, long semesterId) {
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

    private static void requireStudentInRoster(GroupMembersResponse members, Long userId) {
        if (members == null || members.getStudentsList().stream()
                .noneMatch(student -> student.getUserId() == userId)) {
            throw new AccessDeniedException("Студент не принадлежит составу группы на дату пары");
        }
    }

    private static Query pairFilter(Long lessonId, Long userId) {
        return Query.query(Criteria.where("lesson_id").is(lessonId).and("user_id").is(userId));
    }

    private static String normalizeComment(String comment) {
        if (comment == null) return null;
        String normalized = comment.strip();
        return normalized.isEmpty() ? null : normalized;
    }

    /** Best-effort gRPC lookup; на ошибку — null, payload без subject_name. */
    private String resolveSubjectName(Long subjectId) {
        if (subjectId == null) return null;
        try {
            return academicGrpcClient.getSubjectsByIds(java.util.List.of(subjectId))
                    .get(subjectId);
        } catch (Exception ex) {
            return null;
        }
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

    @Transactional
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

        Long headmanGroupId = requestContext.getGroupId();
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
        Instant now = clock.instant();
        Long markedBy = requestContext.getUserId();
        List<AttendanceDocument> result = new ArrayList<>(items.size());
        FindAndModifyOptions opts = FindAndModifyOptions.options().returnNew(true).upsert(true);

        items.stream()
                .sorted(java.util.Comparator
                        .comparing(MarkBatchItem::userId)
                        .thenComparing(MarkBatchItem::lessonId))
                .forEach(item -> pairWriteCoordinator.lock(
                        item.userId(), item.lessonId(), lessonsById.get(item.lessonId()).getGroupId(), now));

        boolean requiresExcusePermission = items.stream().anyMatch(item -> item.status() == AttendanceStatus.EXCUSED);
        if (!requiresExcusePermission) {
            for (MarkBatchItem item : items) {
                AttendanceDocument existing = mongoTemplate.findOne(
                        Query.query(Criteria.where("lesson_id").is(item.lessonId())
                                .and("user_id").is(item.userId())), AttendanceDocument.class);
                if (existing != null && existing.getStatus() == AttendanceStatus.EXCUSED) {
                    requiresExcusePermission = true;
                    break;
                }
            }
        }
        requireAttendancePermission(headmanGroupId, "MARK_ATTENDANCE");
        if (requiresExcusePermission) requireAttendancePermission(headmanGroupId, "MANAGE_EXCUSES");

        for (MarkBatchItem item : items) {
            LessonResponse lesson = lessonsById.get(item.lessonId());
            Query filter = Query.query(
                    Criteria.where("lesson_id").is(item.lessonId())
                            .and("user_id").is(item.userId()));
            AttendanceDocument existing = mongoTemplate.findOne(filter, AttendanceDocument.class);
            boolean retainExistingAttachment = item.status() == AttendanceStatus.EXCUSED
                    && existing != null
                    && existing.getStatus() == AttendanceStatus.EXCUSED
                    && existing.getAttachmentId() != null
                    && attachmentService != null
                    && attachmentService.isAvailable(item.lessonId(), item.userId(), existing.getAttachmentId());
            if (!retainExistingAttachment && attachmentService != null) {
                attachmentService.delete(item.lessonId(), item.userId());
            }
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
            if (item.status() != AttendanceStatus.EXCUSED || !retainExistingAttachment) {
                update.unset("attachment_id")
                        .unset("attachment_name")
                        .unset("attachment_content_type")
                        .unset("attachment_size");
            }
            if (item.status() != AttendanceStatus.EXCUSED) {
                update.unset("excuse_reason")
                        .unset("excuse_type")
                        .unset("excuse_comment");
            }
            AttendanceDocument doc = mongoTemplate.findAndModify(
                    filter, update, opts, AttendanceDocument.class);
            result.add(doc);
        }

        // Публикация событий — после того как весь batch персистирован.
        // NOTIF unification: один gRPC getSubjectsByIds на batch (не N).
        Set<Long> subjectIds = new HashSet<>();
        for (AttendanceDocument doc : result) {
            if (doc.getSubjectId() != null) subjectIds.add(doc.getSubjectId());
        }
        Map<Long, String> subjectNames;
        try {
            subjectNames = subjectIds.isEmpty()
                    ? Map.of()
                    : academicGrpcClient.getSubjectsByIds(new ArrayList<>(subjectIds));
        } catch (Exception ex) {
            subjectNames = Map.of();
        }
        for (AttendanceDocument doc : result) {
            eventPublisher.publishMarked(doc, subjectNames.get(doc.getSubjectId()));
        }

        return result;
    }

}
