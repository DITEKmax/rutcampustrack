package ru.rutcampustrack.attendance.latecheckin;

import org.springframework.stereotype.Service;
import ru.rutcampustrack.attendance.contract.enums.AttendanceSource;
import ru.rutcampustrack.attendance.contract.enums.AttendanceStatus;
import ru.rutcampustrack.attendance.contract.enums.LateCheckinRequestStatus;
import ru.rutcampustrack.attendance.contract.exception.ResourceNotFoundException;
import ru.rutcampustrack.attendance.checkin.AttendanceRepository;
import ru.rutcampustrack.attendance.exception.AccessDeniedException;
import ru.rutcampustrack.attendance.exception.BadRequestException;
import ru.rutcampustrack.attendance.exception.ConflictException;
import ru.rutcampustrack.attendance.grpc.AcademicGrpcClient;
import ru.rutcampustrack.attendance.grpc.ScheduleGrpcClient;
import ru.rutcampustrack.attendance.latecheckin.entity.LateCheckinRequest;
import ru.rutcampustrack.attendance.security.RequestContext;
import ru.rutcampustrack.attendance.shared.port.AttendanceWritePort;
import ru.rutcampustrack.schedule.grpc.LessonResponse;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

/**
 * Business logic for late-checkin requests.
 *
 * Flow:
 *  1. Student's geo-checkin failed (out of zone / time / rate limited).
 *  2. Student calls {@link #createRequest(Long)} — we validate the lesson is ACTIVE
 *     and belongs to their group, then publish {@code late_checkin.requested}.
 *  3. notification-bot sends an inline-keyboard message to the group's headman.
 *  4. Headman presses approve/reject — the bot publishes {@code late_checkin.decision}
 *     which is consumed by {@link LateCheckinDecisionConsumer} and applied via
 *     {@link #applyDecision(String, Long, boolean)}.
 *  5. On approval: attendance document is upserted (PRESENT, source=LATE_CHECKIN).
 *     On rejection: no attendance change.
 *  6. {@code late_checkin.decided} is published so the student receives a Telegram notice.
 */
@Service
public class LateCheckinService {

    private static final String LESSON_STATUS_ACTIVE = "active";
    private static final String LESSON_STATUS_CLOSED = "closed";

    private final LateCheckinRepository repository;
    private final RequestContext requestContext;
    private final ScheduleGrpcClient scheduleGrpcClient;
    private final AcademicGrpcClient academicGrpcClient;
    private final AttendanceRepository attendanceRepository;
    private final AttendanceWritePort attendanceWritePort;
    private final LateCheckinEventPublisher eventPublisher;

    public LateCheckinService(LateCheckinRepository repository,
                              RequestContext requestContext,
                              ScheduleGrpcClient scheduleGrpcClient,
                              AcademicGrpcClient academicGrpcClient,
                              AttendanceRepository attendanceRepository,
                              AttendanceWritePort attendanceWritePort,
                              LateCheckinEventPublisher eventPublisher) {
        this.repository = repository;
        this.requestContext = requestContext;
        this.scheduleGrpcClient = scheduleGrpcClient;
        this.academicGrpcClient = academicGrpcClient;
        this.attendanceRepository = attendanceRepository;
        this.attendanceWritePort = attendanceWritePort;
        this.eventPublisher = eventPublisher;
    }

    public LateCheckinRequest createRequest(Long lessonId) {
        if (requestContext.isHeadman()) {
            throw new ConflictException(
                    "Староста отмечает себя через журнал посещаемости, а не через запрос к старосте");
        }

        LessonResponse lesson = scheduleGrpcClient.getLessonById(lessonId);
        if (!Objects.equals(lesson.getGroupId(), requestContext.getGroupId())) {
            throw new BadRequestException("Занятие не принадлежит вашей группе");
        }
        String lessonStatus = lesson.getStatus() == null ? "" : lesson.getStatus();
        boolean statusAllowed = LESSON_STATUS_ACTIVE.equalsIgnoreCase(lessonStatus)
                || LESSON_STATUS_CLOSED.equalsIgnoreCase(lessonStatus);
        if (!statusAllowed) {
            throw new BadRequestException("Запрос можно создать только для идущей или уже прошедшей пары");
        }

        // Already marked present — don't waste headman's time
        attendanceRepository.findByLessonIdAndUserId(lessonId, requestContext.getUserId())
                .filter(doc -> doc.getStatus() == AttendanceStatus.PRESENT)
                .ifPresent(doc -> {
                    throw new ConflictException("Вы уже отмечены на этом занятии");
                });

        // Dedup: no second PENDING for same (student, lesson)
        boolean duplicate = repository.existsByStudentIdAndLessonIdAndStatus(
                requestContext.getUserId(), lessonId, LateCheckinRequestStatus.PENDING);
        if (duplicate) {
            throw new ConflictException("Запрос на эту пару уже отправлен");
        }

        String studentName = academicGrpcClient.getUserDisplayName(requestContext.getUserId());

        Instant now = Instant.now();
        LateCheckinRequest request = LateCheckinRequest.builder()
                .studentId(requestContext.getUserId())
                .groupId(requestContext.getGroupId())
                .lessonId(lessonId)
                .studentName(studentName)
                .status(LateCheckinRequestStatus.PENDING)
                .createdAt(now)
                .updatedAt(now)
                .build();

        LateCheckinRequest saved = repository.save(request);

        LocalDate lessonDate = lesson.getDate() == null ? null : LocalDate.parse(lesson.getDate());
        String subjectName = academicGrpcClient
                .getSubjectsByIds(java.util.List.of(lesson.getSubjectId()))
                .get(lesson.getSubjectId());
        eventPublisher.publishRequested(
                saved,
                lessonDate,
                lesson.getLessonNumber(),
                lesson.getSubjectId(),
                subjectName
        );

        return saved;
    }

    /**
     * List PENDING requests for the headman's own group. Caller must be STUDENT+is_headman;
     * the controller enforces the role via {@code @RequireRole}, this method enforces the
     * is_headman flag defensively so the query can never leak requests from another group.
     */
    public List<LateCheckinRequest> listPendingForHeadman() {
        if (!requestContext.isHeadman()) {
            throw new AccessDeniedException("Список запросов доступен только старосте группы");
        }
        Long groupId = requestContext.getGroupId();
        if (groupId == null) {
            throw new AccessDeniedException("Староста не привязан к группе");
        }
        return repository.findByGroupIdAndStatusOrderByCreatedAtAsc(
                groupId, LateCheckinRequestStatus.PENDING);
    }

    /**
     * Apply a headman's decision submitted from a web client (web-panel / PWA / mini-app).
     * Guard: only a headman of the request's own group may decide. Delegates to the same
     * idempotent {@link #applyDecision} used by the RabbitMQ consumer, using the authenticated
     * headman's user_id as {@code decisionBy}.
     *
     * @return the decided request (APPROVED or REJECTED), or the already-decided request
     *         if called twice (idempotent).
     */
    public LateCheckinRequest applyDecisionFromWeb(String requestId, boolean approved) {
        if (!requestContext.isHeadman()) {
            throw new AccessDeniedException("Решение может принимать только староста");
        }
        LateCheckinRequest request = repository.findById(requestId)
                .orElseThrow(() -> new ResourceNotFoundException("LateCheckinRequest", "id", requestId));
        if (!Objects.equals(request.getGroupId(), requestContext.getGroupId())) {
            throw new AccessDeniedException("Запрос принадлежит другой группе");
        }
        applyDecision(requestId, requestContext.getUserId(), approved);
        return repository.findById(requestId).orElse(request);
    }

    /**
     * Apply a headman's decision received via RabbitMQ from the notification-bot.
     * Idempotent — duplicate deliveries to an already-decided request are ignored.
     *
     * @param requestId  the Mongo id of the LateCheckinRequest
     * @param decisionBy telegram user_id (bot path) or internal user_id (web path)
     * @param approved   true → APPROVED + attendance upsert; false → REJECTED
     */
    public void applyDecision(String requestId, Long decisionBy, boolean approved) {
        LateCheckinRequest request = repository.findById(requestId)
                .orElseThrow(() -> new ResourceNotFoundException("LateCheckinRequest", "id", requestId));

        // Idempotency: already decided → drop silently (duplicate bot delivery)
        if (request.getStatus() != LateCheckinRequestStatus.PENDING) {
            return;
        }

        Instant now = Instant.now();
        request.setStatus(approved ? LateCheckinRequestStatus.APPROVED : LateCheckinRequestStatus.REJECTED);
        request.setDecisionBy(decisionBy);
        request.setDecisionAt(now);
        request.setUpdatedAt(now);

        LateCheckinRequest saved = repository.save(request);

        if (approved) {
            attendanceWritePort.mark(
                    saved.getStudentId(),
                    saved.getLessonId(),
                    saved.getGroupId(),
                    AttendanceStatus.PRESENT,
                    AttendanceSource.LATE_CHECKIN);
        }

        LessonResponse lesson = scheduleGrpcClient.getLessonById(saved.getLessonId());
        LocalDate lessonDate = lesson.getDate() == null ? null : LocalDate.parse(lesson.getDate());
        String subjectName = academicGrpcClient
                .getSubjectsByIds(java.util.List.of(lesson.getSubjectId()))
                .get(lesson.getSubjectId());
        eventPublisher.publishDecided(
                saved,
                lessonDate,
                lesson.getLessonNumber(),
                lesson.getSubjectId(),
                subjectName
        );
    }
}
