package ru.rutcampustrack.attendance.excuse;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import ru.rutcampustrack.attendance.contract.dto.excuse.CreateExcuseRequest;
import ru.rutcampustrack.attendance.contract.dto.excuse.UpdateExcuseStatusRequest;
import ru.rutcampustrack.attendance.contract.enums.AttendanceSource;
import ru.rutcampustrack.attendance.contract.enums.AttendanceStatus;
import ru.rutcampustrack.attendance.contract.enums.ExcuseTicketStatus;
import ru.rutcampustrack.attendance.contract.enums.ExcuseType;
import ru.rutcampustrack.attendance.contract.exception.ResourceNotFoundException;
import ru.rutcampustrack.attendance.exception.AccessDeniedException;
import ru.rutcampustrack.attendance.exception.BadRequestException;
import ru.rutcampustrack.attendance.exception.ConflictException;
import ru.rutcampustrack.attendance.excuse.entity.ExcuseTicket;
import ru.rutcampustrack.attendance.grpc.AcademicGrpcClient;
import ru.rutcampustrack.attendance.grpc.ScheduleGrpcClient;
import ru.rutcampustrack.attendance.security.RequestContext;
import ru.rutcampustrack.attendance.shared.port.AttendanceReadPort;
import ru.rutcampustrack.attendance.shared.port.AttendanceRecord;
import ru.rutcampustrack.attendance.shared.port.AttendanceWritePort;
import ru.rutcampustrack.schedule.grpc.LessonInfo;
import ru.rutcampustrack.shared.observability.BusinessMetrics;

import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Business logic for excuse tickets (Phase 59, Wave 2).
 *
 * Implements decisions D-04..D-18 from 59-CONTEXT.md:
 * - D-10..D-11: duplicate detection (active ticket per lesson per student)
 * - D-12: headman does NOT create tickets — goes through normal markAttendance flow
 * - D-13: headman cannot approve/reject their own ticket
 * - D-14: read access restricted to owner or headman of same group
 * - D-18: status decision is final — no re-approval/re-rejection
 *
 * Cascade on approval (D-16) via AttendanceWritePort (plan 59-04).
 * Event publishing (D-19, D-20) via ExcuseEventPublisher (plan 59-05):
 *   - excuse.requested after createExcuse save
 *   - excuse.decided  after updateStatus save + cascade (order matters — journal
 *     consistency requires the cascade to complete before downstream observers).
 */
@Service
public class ExcuseService {

    private static final List<ExcuseTicketStatus> ACTIVE_STATUSES =
            List.of(ExcuseTicketStatus.SUBMITTED, ExcuseTicketStatus.APPROVED);

    /** Russian labels for excuse types — shown next to "у" in journal cells. */
    private static final Map<ExcuseType, String> EXCUSE_TYPE_LABELS = Map.of(
            ExcuseType.ILLNESS, "Болезнь",
            ExcuseType.SUMMONS, "Повестка",
            ExcuseType.UNIVERSITY_ORDER, "Приказ",
            ExcuseType.EXEMPTION, "Освобождение",
            ExcuseType.FREE_ATTENDANCE, "Свободное посещение",
            ExcuseType.OTHER, "Другое"
    );

    private final ExcuseRepository excuseRepository;
    private final RequestContext requestContext;
    private final AcademicGrpcClient academicGrpcClient;
    private final AttendanceReadPort attendanceReadPort;
    private final AttendanceWritePort attendanceWritePort;
    private final ExcuseEventPublisher excuseEventPublisher;
    private final ScheduleGrpcClient scheduleGrpcClient;
    private final BusinessMetrics businessMetrics;
    private final Clock clock;

    public ExcuseService(ExcuseRepository excuseRepository,
                         RequestContext requestContext,
                         AcademicGrpcClient academicGrpcClient,
                         AttendanceReadPort attendanceReadPort,
                         AttendanceWritePort attendanceWritePort,
                         ExcuseEventPublisher excuseEventPublisher,
                         ScheduleGrpcClient scheduleGrpcClient,
                         BusinessMetrics businessMetrics,
                         Clock clock) {
        this.excuseRepository = excuseRepository;
        this.requestContext = requestContext;
        this.academicGrpcClient = academicGrpcClient;
        this.attendanceReadPort = attendanceReadPort;
        this.attendanceWritePort = attendanceWritePort;
        this.excuseEventPublisher = excuseEventPublisher;
        this.scheduleGrpcClient = scheduleGrpcClient;
        this.businessMetrics = businessMetrics;
        this.clock = clock;
    }

    /**
     * D-04 + D-10..D-12, D-15: STUDENT creates an excuse ticket for lessons in their own group.
     *
     * @throws ConflictException   headman attempted to create (D-12) or a lesson already has an active ticket (D-11)
     * @throws BadRequestException defensive validation guard (comment length, empty list) — primary validation lives in DTO
     */
    public ExcuseTicket createExcuse(CreateExcuseRequest request) {
        ExcuseTicket saved = createTicketInternal(request);
        // D-19 / D-27: publish excuse.requested after save — notification-bot listens.
        // Обогащаем payload деталями пар (номер, дата, предмет): без этого староста
        // в TG/веб видел только ID пар без контекста.
        excuseEventPublisher.publishRequested(saved, resolveLessonDetails(saved.getLessonIds()));
        // M04 Группа 8 — excuse.created{kind}. kind = enum ExcuseType (illness, ...).
        businessMetrics.excuseCreatedCounter(saved.getExcuseType().name().toLowerCase()).increment();
        return saved;
    }

    /**
     * Same lifecycle as {@link #createExcuse(CreateExcuseRequest)} plus a supporting
     * document that is forwarded to the headman by notification-bot and never
     * persisted server-side (per CLAUDE.md — excuse attachments do not live in
     * our storage). The file is read into memory, base64-encoded, and shipped
     * inside the {@code excuse.requested} event payload. Bot picks it up via
     * the existing RabbitMQ consumer.
     */
    public ExcuseTicket createExcuseWithFile(CreateExcuseRequest request, MultipartFile file) {
        final long MAX_BYTES = 10L * 1024 * 1024;
        ExcuseTicket saved = createTicketInternal(request);
        List<Map<String, Object>> lessonDetails = resolveLessonDetails(saved.getLessonIds());
        // M04 Группа 8 — excuse.created counter бампим один раз независимо
        // от того, есть файл или нет (сам ticket уже сохранён).
        businessMetrics.excuseCreatedCounter(saved.getExcuseType().name().toLowerCase()).increment();
        if (file == null || file.isEmpty()) {
            excuseEventPublisher.publishRequested(saved, lessonDetails);
            return saved;
        }
        if (file.getSize() > MAX_BYTES) {
            throw new BadRequestException("Файл должен быть не больше 10 МБ");
        }
        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException e) {
            throw new BadRequestException("Не удалось прочитать файл: " + e.getMessage());
        }
        String fileName = file.getOriginalFilename() != null && !file.getOriginalFilename().isBlank()
                ? file.getOriginalFilename()
                : "attachment";
        excuseEventPublisher.publishRequestedWithFile(
                saved, lessonDetails, fileName, file.getContentType(), bytes);
        return saved;
    }

    private ExcuseTicket createTicketInternal(CreateExcuseRequest request) {
        // D-12: headman does not submit tickets — they self-mark via the journal
        if (requestContext.isHeadman()) {
            throw new ConflictException(
                    "Старосты проставляют пропуски через журнал посещаемости, а не через тикеты");
        }

        // D-15 defensive guard (jakarta validation should already have caught these)
        if (request.lessonIds() == null || request.lessonIds().isEmpty()) {
            throw new BadRequestException("lessonIds не может быть пустым");
        }
        if (request.comment() != null && request.comment().length() > 1000) {
            throw new BadRequestException("Комментарий не должен превышать 1000 символов");
        }

        // D-11: duplicate lesson in an active ticket for this student
        boolean duplicate = excuseRepository.existsByStudentIdAndLessonIdsInAndStatusIn(
                requestContext.getUserId(), request.lessonIds(), ACTIVE_STATUSES);
        if (duplicate) {
            throw new ConflictException(
                    "На один из выбранных уроков уже существует активный тикет");
        }

        // D-25: validate that every lessonId exists and belongs to the student's group
        validateLessonIds(request.lessonIds());

        // Excuse tickets only make sense for lessons where the student is marked absent.
        Long studentId = requestContext.getUserId();
        for (Long lessonId : request.lessonIds()) {
            attendanceReadPort.findByLessonIdAndUserId(lessonId, studentId)
                    .ifPresent(record -> {
                        if (record.status() != AttendanceStatus.ABSENT) {
                            throw new BadRequestException(
                                    "Уважительную можно подать только на пару с «н». "
                                            + "Урок id=" + lessonId + " имеет статус «" + record.status() + "»");
                        }
                    });
        }

        // D-26: snapshot studentName via gRPC
        String studentName = academicGrpcClient.getUserDisplayName(requestContext.getUserId());

        Instant now = clock.instant();
        ExcuseTicket ticket = ExcuseTicket.builder()
                .studentId(requestContext.getUserId())
                .groupId(requestContext.getGroupId())
                .studentName(studentName)
                .lessonIds(List.copyOf(request.lessonIds()))
                .excuseType(request.excuseType())
                .comment(request.comment())
                .status(ExcuseTicketStatus.SUBMITTED)
                .createdAt(now)
                .updatedAt(now)
                .build();

        return excuseRepository.save(ticket);
    }

    /**
     * D-05: STUDENT views their own tickets.
     * Optional status filter narrows to a single lifecycle bucket.
     */
    public Page<ExcuseTicket> getMyTickets(Pageable pageable, ExcuseTicketStatus status) {
        Long userId = requestContext.getUserId();
        if (status != null) {
            return excuseRepository.findByStudentIdAndStatus(userId, status, pageable);
        }
        return excuseRepository.findByStudentId(userId, pageable);
    }

    /**
     * D-06 + D-14: headman views tickets of THEIR OWN group. Non-headman or wrong group → 403.
     */
    public Page<ExcuseTicket> getGroupTickets(Long groupId, Pageable pageable, ExcuseTicketStatus status) {
        if (!requestContext.isHeadman()) {
            throw new AccessDeniedException("Только староста может просматривать тикеты группы");
        }
        if (!groupId.equals(requestContext.getGroupId())) {
            throw new AccessDeniedException("Нельзя просматривать тикеты чужой группы");
        }
        if (status != null) {
            return excuseRepository.findByGroupIdAndStatus(groupId, status, pageable);
        }
        return excuseRepository.findByGroupId(groupId, pageable);
    }

    /**
     * D-08 + D-14: detail view. Owner may always read. Headman may read tickets of their own group.
     * Everyone else → 403.
     */
    public ExcuseTicket getTicketById(String id) {
        ExcuseTicket ticket = excuseRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("ExcuseTicket", "id", id));

        boolean isOwner = ticket.getStudentId().equals(requestContext.getUserId());
        boolean isGroupHeadman = requestContext.isHeadman()
                && ticket.getGroupId().equals(requestContext.getGroupId());

        if (!isOwner && !isGroupHeadman) {
            throw new AccessDeniedException("Доступ к тикету запрещён");
        }
        return ticket;
    }

    /**
     * D-07 + D-13 + D-14 + D-18: headman approves/rejects a ticket.
     * <ul>
     *   <li>Caller must be headman of the ticket's group (D-07/D-14)</li>
     *   <li>Caller must not be the ticket author (D-13)</li>
     *   <li>Ticket must currently be SUBMITTED — otherwise decision is already final (D-18)</li>
     *   <li>Requested status must be APPROVED or REJECTED (D-07)</li>
     * </ul>
     *
     * Cascade on APPROVED (D-16) will be performed by plan 59-04 once AttendanceWritePort exists.
     */
    public ExcuseTicket updateStatus(String id, UpdateExcuseStatusRequest request) {
        ExcuseTicket ticket = excuseRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("ExcuseTicket", "id", id));

        // D-07: only APPROVED or REJECTED are meaningful here
        ExcuseTicketStatus newStatus = request.status();
        if (newStatus != ExcuseTicketStatus.APPROVED && newStatus != ExcuseTicketStatus.REJECTED) {
            throw new BadRequestException("Допустимые статусы: APPROVED или REJECTED");
        }

        // D-07/D-14: must be headman of the ticket's group
        if (!requestContext.isHeadman()) {
            throw new AccessDeniedException("Только староста может принимать решение по тикету");
        }
        if (!ticket.getGroupId().equals(requestContext.getGroupId())) {
            throw new AccessDeniedException("Нельзя принимать решение по тикету чужой группы");
        }

        // D-13: cannot self-approve/reject
        if (ticket.getStudentId().equals(requestContext.getUserId())) {
            throw new ConflictException("Нельзя одобрить собственный тикет");
        }

        // D-18: decision is final
        if (ticket.getStatus() != ExcuseTicketStatus.SUBMITTED) {
            throw new ConflictException("Решение по тикету уже принято");
        }

        Instant now = clock.instant();
        ticket.setStatus(newStatus);
        ticket.setDecisionBy(requestContext.getUserId());
        ticket.setDecisionComment(request.decisionComment());
        ticket.setDecisionAt(now);
        ticket.setUpdatedAt(now);

        ExcuseTicket saved = excuseRepository.save(ticket);

        // D-16: on APPROVED, cascade to attendance documents (EXCUSED or FREE_ATTENDANCE)
        if (newStatus == ExcuseTicketStatus.APPROVED) {
            AttendanceStatus attendanceStatus = mapExcuseTypeToAttendanceStatus(saved.getExcuseType());
            String reason = buildReason(saved);
            for (Long lessonId : saved.getLessonIds()) {
                attendanceWritePort.mark(
                        saved.getStudentId(),
                        lessonId,
                        saved.getGroupId(),
                        attendanceStatus,
                        AttendanceSource.HEADMAN_EXCUSE,
                        reason);
            }
        }

        // D-20 / D-28: publish excuse.decided AFTER cascade, BEFORE return — journal
        // consistency requires the cascade to land before any consumer observes the decision.
        excuseEventPublisher.publishDecided(saved);

        return saved;
    }

    /**
     * Применить решение старосты, пришедшее через inline-кнопку в Telegram.
     *
     * <p>В отличие от {@link #updateStatus}, эта ветка НЕ имеет
     * {@link RequestContext} — вызывающий это consumer RabbitMQ без JWT. Доверие
     * к источнику обеспечивается тем, что inline-клавиатура отправляется только
     * старостам группы (фильтр в {@code headman_alerts.py}), а event
     * {@code excuse.decision} публикуется ботом только на нажатии кнопки.
     *
     * <p>Идемпотентность: повторная доставка для уже решённого тикета игнорируется
     * — симметрия с {@link ru.rutcampustrack.attendance.latecheckin.LateCheckinService#applyDecision}.
     *
     * @param ticketId         Mongo id тикета
     * @param decisionBy       telegram user_id старосты (сохраняется «как есть», как делает late_checkin)
     * @param approved         true = APPROVED, false = REJECTED
     * @param decisionComment  опциональный комментарий
     */
    public void applyDecisionFromBot(String ticketId, Long decisionBy, boolean approved, String decisionComment) {
        ExcuseTicket ticket = excuseRepository.findById(ticketId)
                .orElseThrow(() -> new ResourceNotFoundException("ExcuseTicket", "id", ticketId));

        // D-18: решение уже принято — тихо выходим (дубликат доставки RabbitMQ).
        if (ticket.getStatus() != ExcuseTicketStatus.SUBMITTED) {
            return;
        }

        Instant now = clock.instant();
        ExcuseTicketStatus newStatus = approved ? ExcuseTicketStatus.APPROVED : ExcuseTicketStatus.REJECTED;
        ticket.setStatus(newStatus);
        ticket.setDecisionBy(decisionBy);
        ticket.setDecisionComment(decisionComment);
        ticket.setDecisionAt(now);
        ticket.setUpdatedAt(now);

        ExcuseTicket saved = excuseRepository.save(ticket);

        if (newStatus == ExcuseTicketStatus.APPROVED) {
            AttendanceStatus attendanceStatus = mapExcuseTypeToAttendanceStatus(saved.getExcuseType());
            String reason = buildReason(saved);
            for (Long lessonId : saved.getLessonIds()) {
                attendanceWritePort.mark(
                        saved.getStudentId(),
                        lessonId,
                        saved.getGroupId(),
                        attendanceStatus,
                        AttendanceSource.HEADMAN_EXCUSE,
                        reason);
            }
        }

        excuseEventPublisher.publishDecided(saved);
    }

    /**
     * D-16: map an {@link ExcuseType} to the corresponding {@link AttendanceStatus}
     * used when cascading an approved ticket onto attendance documents.
     *
     * <ul>
     *   <li>{@code FREE_ATTENDANCE} → {@code FREE_ATTENDANCE}</li>
     *   <li>everything else (ILLNESS, SUMMONS, UNIVERSITY_ORDER, EXEMPTION, OTHER) → {@code EXCUSED}</li>
     * </ul>
     */
    private AttendanceStatus mapExcuseTypeToAttendanceStatus(ExcuseType excuseType) {
        if (excuseType == ExcuseType.FREE_ATTENDANCE) {
            return AttendanceStatus.FREE_ATTENDANCE;
        }
        return AttendanceStatus.EXCUSED;
    }

    /**
     * Human-readable reason shown next to "у" in journal cells. For OTHER we prefer
     * the student's own comment (truncated) — it carries the actual explanation.
     */
    private String buildReason(ExcuseTicket ticket) {
        ExcuseType type = ticket.getExcuseType();
        if (type == ExcuseType.OTHER) {
            String comment = ticket.getComment();
            if (comment != null && !comment.isBlank()) {
                String trimmed = comment.strip();
                return trimmed.length() > 80 ? trimmed.substring(0, 80) + "…" : trimmed;
            }
            return EXCUSE_TYPE_LABELS.get(ExcuseType.OTHER);
        }
        return EXCUSE_TYPE_LABELS.getOrDefault(type, "Уважительная");
    }

    /**
     * D-25: validate that every requested lessonId exists in schedule-service and
     * belongs to the caller's group. Uses the batch gRPC {@code GetLessonsByIds}
     * method shipped in plan 59-03.
     *
     * @throws BadRequestException if any id is unknown or belongs to another group
     */
    private void validateLessonIds(List<Long> lessonIds) {
        List<LessonInfo> lessons = scheduleGrpcClient.getLessonsByIds(lessonIds);
        Set<Long> returnedIds = new HashSet<>();
        for (LessonInfo lesson : lessons) {
            returnedIds.add(lesson.getLessonId());
        }
        for (Long requestedId : lessonIds) {
            if (!returnedIds.contains(requestedId)) {
                throw new BadRequestException("Урок с id=" + requestedId + " не найден");
            }
        }
        Long studentGroupId = requestContext.getGroupId();
        for (LessonInfo lesson : lessons) {
            if (!Objects.equals(lesson.getGroupId(), studentGroupId)) {
                throw new BadRequestException(
                        "Урок с id=" + lesson.getLessonId() + " не принадлежит вашей группе");
            }
        }
    }

    /**
     * Собирает детали пар для обогащения payload события и UI.
     *
     * <p>Возвращает список {@code [{lesson_id, lesson_number, date, subject_id,
     * subject_name}]}. Если gRPC-вызов упадёт, возвращаем пустой список — обогащение
     * опционально, и падение schedule/academic не должно ломать публикацию события.
     */
    List<Map<String, Object>> resolveLessonDetails(List<Long> lessonIds) {
        if (lessonIds == null || lessonIds.isEmpty()) return List.of();
        try {
            List<LessonInfo> lessons = scheduleGrpcClient.getLessonsByIds(lessonIds);
            if (lessons.isEmpty()) return List.of();
            List<Long> subjectIds = lessons.stream()
                    .map(LessonInfo::getSubjectId)
                    .distinct()
                    .collect(Collectors.toList());
            Map<Long, String> subjectNames = academicGrpcClient.getSubjectsByIds(subjectIds);
            List<Map<String, Object>> out = new ArrayList<>(lessons.size());
            for (LessonInfo lesson : lessons) {
                Map<String, Object> entry = new LinkedHashMap<>();
                entry.put("lesson_id", lesson.getLessonId());
                entry.put("lesson_number", lesson.getLessonNumber());
                entry.put("date", lesson.getDate());
                entry.put("subject_id", lesson.getSubjectId());
                entry.put("subject_name", subjectNames.getOrDefault(lesson.getSubjectId(), null));
                out.add(entry);
            }
            return out;
        } catch (RuntimeException e) {
            // Обогащение не критично для публикации события. Логируем на debug и едем дальше.
            return List.of();
        }
    }
}
