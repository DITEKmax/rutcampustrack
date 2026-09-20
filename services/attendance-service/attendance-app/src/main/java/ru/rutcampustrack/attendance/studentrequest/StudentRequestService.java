package ru.rutcampustrack.attendance.studentrequest;

import com.mongodb.MongoException;
import org.bson.Document;
import org.bson.types.Binary;
import org.bson.types.ObjectId;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.TransactionException;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;
import ru.rutcampustrack.attendance.checkin.AttendanceDocument;
import ru.rutcampustrack.attendance.checkin.AttendanceRepository;
import ru.rutcampustrack.attendance.contract.enums.AttendanceSource;
import ru.rutcampustrack.attendance.contract.enums.AttendanceStatus;
import ru.rutcampustrack.attendance.contract.enums.ExcuseTicketStatus;
import ru.rutcampustrack.attendance.contract.enums.ExcuseType;
import ru.rutcampustrack.attendance.contract.enums.LateCheckinRequestOrigin;
import ru.rutcampustrack.attendance.contract.enums.LateCheckinRequestStatus;
import ru.rutcampustrack.attendance.contract.enums.LateCheckinResolutionReason;
import ru.rutcampustrack.attendance.contract.enums.StudentRequestKind;
import ru.rutcampustrack.attendance.contract.enums.StudentRequestOrigin;
import ru.rutcampustrack.attendance.contract.enums.StudentRequestStatus;
import ru.rutcampustrack.attendance.contract.enums.UserRole;
import ru.rutcampustrack.attendance.contract.exception.ResourceNotFoundException;
import ru.rutcampustrack.attendance.exception.AccessDeniedException;
import ru.rutcampustrack.attendance.exception.BadRequestException;
import ru.rutcampustrack.attendance.exception.ConflictException;
import ru.rutcampustrack.attendance.exception.InvalidIdempotencyKeyException;
import ru.rutcampustrack.attendance.exception.PayloadTooLargeException;
import ru.rutcampustrack.attendance.excuse.ExcuseEventPublisher;
import ru.rutcampustrack.attendance.excuse.ExcuseRepository;
import ru.rutcampustrack.attendance.excuse.entity.ExcuseTicket;
import ru.rutcampustrack.attendance.grpc.AcademicGrpcClient;
import ru.rutcampustrack.attendance.grpc.ScheduleGrpcClient;
import ru.rutcampustrack.attendance.latecheckin.LateCheckinEventPublisher;
import ru.rutcampustrack.attendance.latecheckin.LateCheckinRepository;
import ru.rutcampustrack.attendance.latecheckin.entity.LateCheckinRequest;
import ru.rutcampustrack.attendance.semester.SemesterCacheService;
import ru.rutcampustrack.attendance.student.PairWriteCoordinator;
import ru.rutcampustrack.attendance.studentrequest.StudentRequestModels.AttachmentDescriptor;
import ru.rutcampustrack.attendance.studentrequest.StudentRequestModels.AttachmentDownload;
import ru.rutcampustrack.attendance.studentrequest.StudentRequestModels.AttachmentInput;
import ru.rutcampustrack.attendance.studentrequest.StudentRequestModels.Budget;
import ru.rutcampustrack.attendance.studentrequest.StudentRequestModels.Decision;
import ru.rutcampustrack.attendance.studentrequest.StudentRequestModels.ExcuseSubmission;
import ru.rutcampustrack.attendance.studentrequest.StudentRequestModels.FileLimits;
import ru.rutcampustrack.attendance.studentrequest.StudentRequestModels.Identity;
import ru.rutcampustrack.attendance.studentrequest.StudentRequestModels.LateCheckinSubmission;
import ru.rutcampustrack.attendance.studentrequest.StudentRequestModels.LessonOption;
import ru.rutcampustrack.attendance.studentrequest.StudentRequestModels.LessonSnapshot;
import ru.rutcampustrack.attendance.studentrequest.StudentRequestModels.NotificationResolution;
import ru.rutcampustrack.attendance.studentrequest.StudentRequestModels.PendingRequestRef;
import ru.rutcampustrack.attendance.studentrequest.StudentRequestModels.ReasonOption;
import ru.rutcampustrack.attendance.studentrequest.StudentRequestModels.RequestDetail;
import ru.rutcampustrack.attendance.studentrequest.StudentRequestModels.RequestOptions;
import ru.rutcampustrack.attendance.studentrequest.StudentRequestModels.RequestPage;
import ru.rutcampustrack.attendance.studentrequest.StudentRequestModels.RequestSummary;
import ru.rutcampustrack.attendance.studentrequest.entity.RequestAttachmentDescriptorDocument;
import ru.rutcampustrack.attendance.studentrequest.entity.RequestAttachmentDocument;
import ru.rutcampustrack.attendance.studentrequest.entity.StudentLateCheckinBudgetDocument;
import ru.rutcampustrack.attendance.studentrequest.entity.StudentLessonSnapshotDocument;
import ru.rutcampustrack.attendance.studentrequest.entity.StudentRequestReceiptDocument;
import ru.rutcampustrack.schedule.grpc.LessonInfo;
import ru.rutcampustrack.schedule.grpc.LessonResponse;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Student request domain boundary for EXCUSE and manual LATE_CHECKIN.
 *
 * <p>Transport adapters call this class later.  All command writes use the
 * attendance Mongo transaction, pair fences and a durable receipt.  External
 * schedule/academic reads happen before the transaction; local state is then
 * re-read after the sorted pair locks are acquired.
 */
@Service
public class StudentRequestService {

    public static final int MANUAL_LATE_LIMIT = 5;
    public static final int MAX_ATTACHMENTS = 2;
    public static final long MAX_ATTACHMENT_BYTES = 10L * 1024 * 1024;
    public static final long MAX_TOTAL_ATTACHMENT_BYTES = 20L * 1024 * 1024;
    public static final int DEFAULT_PAGE_SIZE = 20;
    public static final int MAX_PAGE_SIZE = 100;
    public static final List<String> ALLOWED_ATTACHMENT_CONTENT_TYPES =
            List.of("image/jpeg", "image/png", "application/pdf");
    public static final List<String> ALLOWED_ATTACHMENT_EXTENSIONS =
            List.of(".jpg", ".jpeg", ".png", ".pdf");

    private static final int MAX_TRANSACTION_ATTEMPTS = 10;
    private static final long RETRY_BACKOFF_MILLIS = 25L;
    private static final List<ExcuseType> STUDENT_REASONS = List.of(
            ExcuseType.ILLNESS,
            ExcuseType.MEDICAL_EXAMINATION,
            ExcuseType.COMPETITION_PARTICIPATION,
            ExcuseType.FAMILY_CIRCUMSTANCES,
            ExcuseType.OTHER
    );
    private static final Map<ExcuseType, String> REASON_LABELS = Map.of(
            ExcuseType.ILLNESS, "Болезнь",
            ExcuseType.MEDICAL_EXAMINATION, "Медицинское обследование",
            ExcuseType.COMPETITION_PARTICIPATION, "Участие в соревнованиях",
            ExcuseType.FAMILY_CIRCUMSTANCES, "Семейные обстоятельства",
            ExcuseType.OTHER, "Другое"
    );
    private static final Set<String> ALLOWED_MIME_TYPES = Set.copyOf(ALLOWED_ATTACHMENT_CONTENT_TYPES);
    private static final Set<String> ALLOWED_EXTENSIONS = Set.copyOf(ALLOWED_ATTACHMENT_EXTENSIONS);
    private static final Comparator<Long> LESSON_ORDER = Comparator.naturalOrder();

    private final ExcuseRepository excuseRepository;
    private final LateCheckinRepository lateCheckinRepository;
    private final AttendanceRepository attendanceRepository;
    private final StudentRequestReceiptRepository receiptRepository;
    private final StudentLateCheckinBudgetRepository budgetRepository;
    private final RequestAttachmentRepository attachmentRepository;
    private final ScheduleGrpcClient scheduleGrpcClient;
    private final AcademicGrpcClient academicGrpcClient;
    private final SemesterCacheService semesterCacheService;
    private final PairWriteCoordinator pairWriteCoordinator;
    private final ExcuseEventPublisher excuseEventPublisher;
    private final LateCheckinEventPublisher lateCheckinEventPublisher;
    private final MongoTemplate mongoTemplate;
    private final TransactionTemplate transactionTemplate;
    private final Clock clock;

    public StudentRequestService(
            ExcuseRepository excuseRepository,
            LateCheckinRepository lateCheckinRepository,
            AttendanceRepository attendanceRepository,
            StudentRequestReceiptRepository receiptRepository,
            StudentLateCheckinBudgetRepository budgetRepository,
            RequestAttachmentRepository attachmentRepository,
            ScheduleGrpcClient scheduleGrpcClient,
            AcademicGrpcClient academicGrpcClient,
            SemesterCacheService semesterCacheService,
            PairWriteCoordinator pairWriteCoordinator,
            ExcuseEventPublisher excuseEventPublisher,
            LateCheckinEventPublisher lateCheckinEventPublisher,
            MongoTemplate mongoTemplate,
            TransactionTemplate transactionTemplate,
            Clock clock
    ) {
        this.excuseRepository = excuseRepository;
        this.lateCheckinRepository = lateCheckinRepository;
        this.attendanceRepository = attendanceRepository;
        this.receiptRepository = receiptRepository;
        this.budgetRepository = budgetRepository;
        this.attachmentRepository = attachmentRepository;
        this.scheduleGrpcClient = scheduleGrpcClient;
        this.academicGrpcClient = academicGrpcClient;
        this.semesterCacheService = semesterCacheService;
        this.pairWriteCoordinator = pairWriteCoordinator;
        this.excuseEventPublisher = excuseEventPublisher;
        this.lateCheckinEventPublisher = lateCheckinEventPublisher;
        this.mongoTemplate = mongoTemplate;
        this.transactionTemplate = transactionTemplate;
        this.clock = clock;
    }

    /** Submit an EXCUSE package and return the public detail projection. */
    public RequestDetail submitExcuse(Identity identity, ExcuseSubmission command) {
        ValidatedAttachments files = validateExcuseCommand(command);
        long studentId = requireStudentIdentity(identity);
        String key = command.idempotencyKey();
        String payloadHash = excusePayloadHash(command, files);

        RequestDetail replay = replay(identity, studentId, StudentRequestKind.EXCUSE, key, payloadHash);
        if (replay != null) {
            return replay;
        }

        Long semesterId = requireCurrentSemester();
        List<ResolvedLesson> lessons = resolveLessons(identity, command.lessonIds(), semesterId);
        String studentName = academicGrpcClient.getUserDisplayName(studentId);
        return executeWithRetry(() -> transactionTemplate.execute(status ->
                submitExcuseInTransaction(identity, command, files, payloadHash, semesterId, lessons, studentName)));
    }

    /** Submit an EXCUSE package and expose the persisted legacy ticket to a bridge adapter. */
    public ExcuseTicket submitExcuseTicket(Identity identity, ExcuseSubmission command) {
        RequestDetail detail = submitExcuse(identity, command);
        return excuseRepository.findById(detail.summary().id()).orElseThrow(
                () -> new ResourceNotFoundException("ExcuseTicket", "id", detail.summary().id()));
    }

    /** Submit one manual LATE_CHECKIN request and charge the current semester budget. */
    public RequestDetail submitLateCheckin(Identity identity, LateCheckinSubmission command) {
        validateLateCommand(command);
        long studentId = requireStudentIdentity(identity);
        String key = command.idempotencyKey();
        String payloadHash = latePayloadHash(command.lessonId());
        RequestDetail replay = replay(identity, studentId, StudentRequestKind.LATE_CHECKIN, key, payloadHash);
        if (replay != null) {
            return replay;
        }

        Long semesterId = requireCurrentSemester();
        ResolvedLesson lesson = resolveLessons(identity, List.of(command.lessonId()), semesterId).get(0);
        String studentName = academicGrpcClient.getUserDisplayName(studentId);
        return executeWithRetry(() -> transactionTemplate.execute(status ->
                submitLateInTransaction(identity, command, payloadHash, semesterId, lesson, studentName)));
    }

    /** Submit one manual LATE_CHECKIN and expose its persisted legacy request. */
    public LateCheckinRequest submitLateCheckinRequest(Identity identity, LateCheckinSubmission command) {
        RequestDetail detail = submitLateCheckin(identity, command);
        return lateCheckinRepository.findById(detail.summary().id()).orElseThrow(
                () -> new ResourceNotFoundException("LateCheckinRequest", "id", detail.summary().id()));
    }

    /**
     * Owner-scoped union view over excuse_tickets and late_checkin_requests.
     * Sorting is stable: updatedAt DESC, kind ASC, id ASC.
     */
    public RequestPage list(Identity identity, RequestBucket bucket, Integer page, Integer size) {
        long studentId = requireStudentIdentity(identity);
        int effectivePage = page == null ? 0 : page;
        int effectiveSize = size == null ? DEFAULT_PAGE_SIZE : size;
        validatePage(effectivePage, effectiveSize);

        List<Document> countPipeline = unionPipeline(studentId, bucket, false);
        countPipeline.add(new Document("$count", "total"));
        Document count = firstDocument(mongoTemplate.getCollection("excuse_tickets")
                .aggregate(countPipeline));
        long total = count == null ? 0L : numberAsLong(count.get("total"));

        List<Document> dataPipeline = unionPipeline(studentId, bucket, true);
        dataPipeline.add(new Document("$skip", (long) effectivePage * effectiveSize));
        dataPipeline.add(new Document("$limit", effectiveSize));
        List<RequestSummary> content = new ArrayList<>();
        for (Document document : mongoTemplate.getCollection("excuse_tickets").aggregate(dataPipeline)) {
            content.add(summaryFromUnionDocument(document));
        }
        int totalPages = total == 0 ? 0 : (int) ((total + effectiveSize - 1) / effectiveSize);
        return new RequestPage(content, effectivePage, effectiveSize, total, totalPages);
    }

    public RequestPage list(Identity identity, RequestBucket bucket) {
        return list(identity, bucket, 0, DEFAULT_PAGE_SIZE);
    }

    /** Owner-only detail lookup across both request collections. */
    public RequestDetail get(Identity identity, String requestId) {
        requireStudentIdentity(identity);
        ExcuseTicket excuse = excuseRepository.findById(requestId).orElse(null);
        if (excuse != null) {
            requireOwner(identity, excuse.getStudentId());
            return toDetail(excuse);
        }
        LateCheckinRequest late = lateCheckinRepository.findById(requestId).orElse(null);
        if (late != null) {
            requireOwner(identity, late.getStudentId());
            return toDetail(late);
        }
        throw new ResourceNotFoundException("StudentRequest", "id", requestId);
    }

    /**
     * Build options from the current group schedule.  Schedule/academic errors
     * are intentionally allowed to fail closed through their client exceptions.
     */
    public RequestOptions options(Identity identity) {
        long studentId = requireStudentIdentity(identity);
        Long semesterId = requireCurrentSemester();
        Instant now = clock.instant();
        var response = scheduleGrpcClient.getLessonsByGroup(
                identity.groupId(), semesterId,
                now.atZone(ZoneOffset.UTC).toLocalDate().minusMonths(12).toString(),
                now.atZone(ZoneOffset.UTC).toLocalDate().plusMonths(12).toString());
        List<LessonSnapshot> lessons = response.getLessonsList().stream()
                .map(item -> toSnapshot(item, semesterId))
                .filter(Objects::nonNull)
                .toList();
        validateOptionSnapshots(identity, lessons, semesterId);
        return optionsFor(studentId, semesterId, enrichSnapshots(lessons));
    }

    /** Purely supplied snapshot variant used by transport adapters and focused tests. */
    public RequestOptions options(Identity identity, List<LessonSnapshot> lessons) {
        long studentId = requireStudentIdentity(identity);
        Long semesterId = requireCurrentSemester();
        validateOptionSnapshots(identity, lessons, semesterId);
        return optionsFor(studentId, semesterId,
                enrichSnapshots(lessons == null ? List.of() : lessons));
    }

    /** Owner-only cancellation.  It is idempotent for an already-cancelled request. */
    public RequestDetail cancel(Identity identity, String requestId) {
        long studentId = requireStudentIdentity(identity);
        return executeWithRetry(() -> transactionTemplate.execute(status -> {
            ExcuseTicket excuse = excuseRepository.findById(requestId).orElse(null);
            if (excuse != null) {
                requireOwner(identity, excuse.getStudentId());
                List<Long> lessonIds = sortedLessonIds(excuse.getLessonIds());
                lockPairs(studentId, excuse.getGroupId(), lessonIds);
                ExcuseTicket current = excuseRepository.findById(requestId).orElseThrow(
                        () -> new ResourceNotFoundException("ExcuseTicket", "id", requestId));
                if (current.getStatus() == ExcuseTicketStatus.CANCELLED) {
                    return toDetail(current);
                }
                if (current.getStatus() != ExcuseTicketStatus.SUBMITTED) {
                    throw new ConflictException("Решение по тикету уже принято");
                }
                Instant now = clock.instant();
                current.setStatus(ExcuseTicketStatus.CANCELLED);
                current.setDecisionAt(now);
                current.setUpdatedAt(now);
                ExcuseTicket saved = excuseRepository.save(current);
                excuseEventPublisher.publishDecided(saved);
                return toDetail(saved);
            }

            LateCheckinRequest late = lateCheckinRepository.findById(requestId).orElseThrow(
                    () -> new ResourceNotFoundException("StudentRequest", "id", requestId));
            requireOwner(identity, late.getStudentId());
            lockPairs(studentId, late.getGroupId(), List.of(late.getLessonId()));
            LateCheckinRequest current = lateCheckinRepository.findById(requestId).orElseThrow(
                    () -> new ResourceNotFoundException("LateCheckinRequest", "id", requestId));
            if (current.getStatus() == LateCheckinRequestStatus.CANCELLED) {
                return toDetail(current);
            }
            if (current.getStatus() != LateCheckinRequestStatus.PENDING) {
                throw new ConflictException("Решение по запросу уже принято");
            }
            Instant now = clock.instant();
            current.setStatus(LateCheckinRequestStatus.CANCELLED);
            current.setResolutionReason(LateCheckinResolutionReason.CANCELLED_BY_STUDENT);
            current.setDecisionAt(now);
            current.setUpdatedAt(now);
            LateCheckinRequest saved = lateCheckinRepository.save(current);
            lateCheckinEventPublisher.publishDecided(saved, saved.getLessonDate(), saved.getLessonNumber(),
                    saved.getSubjectId(), null);
            return toDetail(saved);
        }));
    }

    /** Download after owner/request binding and logical expiry checks. */
    public AttachmentDownload download(Identity identity, String requestId, String attachmentId) {
        long studentId = requireStudentIdentity(identity);
        RequestDetail request = get(identity, requestId);
        if (request.summary().kind() != StudentRequestKind.EXCUSE) {
            throw new ResourceNotFoundException("Attachment", "id", attachmentId);
        }
        RequestAttachmentDocument document = attachmentRepository.findById(attachmentId)
                .orElseThrow(() -> new ResourceNotFoundException("Attachment", "id", attachmentId));
        if (!Objects.equals(document.getRequestId(), requestId)
                || !Objects.equals(document.getOwnerStudentId(), studentId)) {
            throw new AccessDeniedException("Доступ к вложению запрещён");
        }
        Instant now = clock.instant();
        if (document.getState() == AttachmentState.EXPIRED
                || document.getExpiresAt() == null
                || !now.isBefore(document.getExpiresAt())) {
            expireOne(document, now);
            throw new ResponseStatusException(HttpStatus.GONE, "Срок хранения вложения истёк");
        }
        if (document.getData() == null) {
            throw new ResponseStatusException(HttpStatus.GONE, "Вложение больше недоступно");
        }
        return new AttachmentDownload(document.getData().getData(), document.getContentType(), document.getName());
    }

    /**
     * Read one retained excuse attachment for the trusted notification bot.
     * The actor is an internal Academic user id, never a Telegram id or an
     * event supplied group.  The persisted request group is loaded first and
     * a current Academic headman check is performed for every non-owner read.
     */
    public AttachmentDownload fetchExcuseAttachmentForBot(long actorUserId,
                                                           String requestId,
                                                           String attachmentId) {
        if (actorUserId <= 0) {
            throw new AccessDeniedException("Внутренний actor_user_id должен быть положительным");
        }
        validateObjectId(requestId, "request_id");
        validateObjectId(attachmentId, "attachment_id");
        ExcuseTicket ticket = excuseRepository.findById(requestId).orElseThrow(
                () -> new ResourceNotFoundException("StudentRequest", "id", requestId));
        authorizeBotAttachmentActor(actorUserId, ticket);
        RequestAttachmentDocument document = attachmentRepository.findById(attachmentId)
                .orElseThrow(() -> new ResourceNotFoundException("Attachment", "id", attachmentId));
        if (!Objects.equals(document.getRequestId(), requestId)
                || !Objects.equals(document.getOwnerStudentId(), ticket.getStudentId())) {
            throw new AccessDeniedException("Доступ к вложению запрещён");
        }
        Instant now = clock.instant();
        if (document.getState() == AttachmentState.EXPIRED
                || document.getExpiresAt() == null
                || !now.isBefore(document.getExpiresAt())) {
            expireOne(document, now);
            throw new ResponseStatusException(HttpStatus.GONE, "Срок хранения вложения истёк");
        }
        if (document.getData() == null) {
            throw new ResponseStatusException(HttpStatus.GONE, "Вложение больше недоступно");
        }
        return new AttachmentDownload(document.getData().getData(), document.getContentType(), document.getName());
    }

    /**
     * Resolve all private notification data from the persisted request.
     *
     * <p>The event envelope is deliberately absent from this method: callers
     * receive the request's stored group, student and full detail projection.
     * Status is checked before {@link #toDetail(ExcuseTicket)} or
     * {@link #toDetail(LateCheckinRequest)} so a corrupt/null persisted value
     * cannot be normalized to PENDING by the public projection mapper.
     */
    public NotificationResolution resolveRequestNotification(StudentRequestKind kind, String requestId) {
        if (kind == null) {
            throw new BadRequestException("Вид заявки обязателен");
        }
        validateObjectId(requestId, "request_id");
        return switch (kind) {
            case EXCUSE -> {
                ExcuseTicket ticket = excuseRepository.findById(requestId).orElseThrow(
                        () -> new ResourceNotFoundException("StudentRequest", "id", requestId));
                validateNotificationStatus(ticket.getStatus());
                RequestDetail detail = toNotificationDetail(ticket);
                yield canonicalNotification(ticket.getGroupId(), ticket.getStudentId(),
                        ticket.getStudentName(), detail);
            }
            case LATE_CHECKIN -> {
                LateCheckinRequest request = lateCheckinRepository.findById(requestId).orElseThrow(
                        () -> new ResourceNotFoundException("StudentRequest", "id", requestId));
                validateNotificationStatus(request.getStatus());
                RequestDetail detail = toDetail(request);
                yield canonicalNotification(request.getGroupId(), request.getStudentId(),
                        request.getStudentName(), detail);
            }
        };
    }

    private static void validateNotificationStatus(ExcuseTicketStatus status) {
        if (status == null) {
            throw new BadRequestException("Состояние заявки недоступно");
        }
        switch (status) {
            case DRAFT, SUBMITTED, APPROVED, REJECTED, CANCELLED -> {
                // Known persisted values only.  Keep this guard before mapStatus.
            }
        }
    }

    private static void validateNotificationStatus(LateCheckinRequestStatus status) {
        if (status == null) {
            throw new BadRequestException("Состояние заявки недоступно");
        }
        switch (status) {
            case PENDING, APPROVED, REJECTED, CANCELLED -> {
                // Known persisted values only.  Keep this guard before mapStatus.
            }
        }
    }

    private NotificationResolution canonicalNotification(Long groupId, Long studentId,
                                                          String storedStudentName,
                                                          RequestDetail detail) {
        if (groupId == null || groupId <= 0 || studentId == null || studentId <= 0
                || detail == null || detail.summary() == null) {
            throw new BadRequestException("Канонический контекст заявки неполон");
        }
        String studentName = storedStudentName;
        if (studentName == null || studentName.isBlank()) {
            studentName = academicGrpcClient.getUserDisplayName(studentId);
        }
        if (studentName == null || studentName.isBlank()) {
            throw new BadRequestException("Имя студента недоступно");
        }
        return new NotificationResolution(groupId, studentId, studentName.strip(), detail);
    }

    private RequestDetail toNotificationDetail(ExcuseTicket ticket) {
        List<RequestAttachmentDocument> storedAttachments =
                attachmentRepository.findByRequestIdAndOwnerStudentIdOrderByPositionAsc(
                        ticket.getId(), ticket.getStudentId());
        List<AttachmentDescriptor> attachments = reconcileNotificationAttachments(ticket, storedAttachments);
        return toDetail(ticket, attachments);
    }

    private List<AttachmentDescriptor> reconcileNotificationAttachments(
            ExcuseTicket ticket, List<RequestAttachmentDocument> storedAttachments) {
        if (ticket.getId() == null || ticket.getId().isBlank()
                || ticket.getStudentId() == null || ticket.getStudentId() <= 0
                || storedAttachments == null) {
            throw new BadRequestException("Канонический инвентарь вложений недоступен");
        }

        Set<String> embeddedIds = new HashSet<>();
        List<RequestAttachmentDescriptorDocument> embeddedDescriptors = ticket.getAttachmentDescriptors();
        if (embeddedDescriptors != null) {
            for (RequestAttachmentDescriptorDocument descriptor : embeddedDescriptors) {
                if (descriptor == null || descriptor.getId() == null || descriptor.getId().isBlank()
                        || !embeddedIds.add(descriptor.getId())) {
                    throw new BadRequestException("Канонический инвентарь вложений некорректен");
                }
            }
        }

        Set<String> storedIds = new HashSet<>();
        for (RequestAttachmentDocument document : storedAttachments) {
            if (document == null || document.getId() == null || document.getId().isBlank()
                    || !Objects.equals(document.getRequestId(), ticket.getId())
                    || !Objects.equals(document.getOwnerStudentId(), ticket.getStudentId())
                    || !storedIds.add(document.getId())) {
                throw new BadRequestException("Сохранённый инвентарь вложений некорректен");
            }
        }
        if (!embeddedIds.equals(storedIds)) {
            throw new BadRequestException("Канонический и сохранённый инвентари вложений не совпадают");
        }
        return storedAttachments.stream().map(this::toDescriptor).toList();
    }

    /**
     * Apply an authenticated bot excuse decision.  A matching terminal
     * command is a semantic duplicate and returns the persisted detail without
     * taking locks or publishing a second event.  A conflicting terminal
     * command remains a conflict so the broker can route it for inspection.
     */
    public RequestDetail decideExcuseFromBot(String ticketId, long actorUserId,
                                              boolean approved, String decisionComment) {
        validateObjectId(ticketId, "ticket_id");
        String normalizedComment = validateDecisionComment(decisionComment);
        ExcuseTicket ticket = excuseRepository.findById(ticketId).orElseThrow(
                () -> new ResourceNotFoundException("ExcuseTicket", "id", ticketId));
        authorizeBotDecisionActor(actorUserId, ticket);
        ExcuseTicketStatus expected = approved ? ExcuseTicketStatus.APPROVED : ExcuseTicketStatus.REJECTED;
        if (ticket.getStatus() == expected
                && Objects.equals(ticket.getDecisionBy(), actorUserId)
                && Objects.equals(ticket.getDecisionComment(), normalizedComment)) {
            return toDetail(ticket);
        }
        if (ticket.getStatus() != ExcuseTicketStatus.SUBMITTED
                && ticket.getStatus() != ExcuseTicketStatus.DRAFT) {
            throw new ConflictException("Решение по тикету уже принято");
        }
        return decideExcuse(new Identity(actorUserId, UserRole.STUDENT, ticket.getGroupId(), true),
                ticketId, approved, normalizedComment);
    }

    /** Same semantic-duplicate/stale handling for bot late-checkin decisions. */
    public RequestDetail decideLateCheckinFromBot(String requestId, long actorUserId, boolean approved) {
        validateObjectId(requestId, "request_id");
        LateCheckinRequest request = lateCheckinRepository.findById(requestId).orElseThrow(
                () -> new ResourceNotFoundException("LateCheckinRequest", "id", requestId));
        authorizeBotDecisionActor(actorUserId, request.getGroupId());
        LateCheckinRequestStatus expected = approved
                ? LateCheckinRequestStatus.APPROVED : LateCheckinRequestStatus.REJECTED;
        if (request.getStatus() == expected && Objects.equals(request.getDecisionBy(), actorUserId)) {
            return toDetail(request);
        }
        if (request.getStatus() != LateCheckinRequestStatus.PENDING) {
            throw new ConflictException("Решение по запросу уже принято");
        }
        return decideLateCheckin(new Identity(actorUserId, UserRole.STUDENT, request.getGroupId(), true),
                requestId, approved);
    }

    private void authorizeBotAttachmentActor(long actorUserId, ExcuseTicket ticket) {
        if (Objects.equals(ticket.getStudentId(), actorUserId)) {
            throw new AccessDeniedException("Студент не может получать вложение через bot actor");
        }
        authorizeBotDecisionActor(actorUserId, ticket.getGroupId());
    }

    private void authorizeBotDecisionActor(long actorUserId, ExcuseTicket ticket) {
        if (Objects.equals(ticket.getStudentId(), actorUserId)) {
            throw new AccessDeniedException("Нельзя принимать решение по собственной заявке");
        }
        authorizeBotDecisionActor(actorUserId, ticket.getGroupId());
    }

    private void authorizeBotDecisionActor(long actorUserId, Long groupId) {
        if (actorUserId <= 0 || groupId == null || groupId <= 0) {
            throw new AccessDeniedException("Решение может принимать только староста своей группы");
        }
        var authority = academicGrpcClient.isHeadman(actorUserId, groupId);
        if (authority == null || !authority.getIsHeadman()) {
            throw new AccessDeniedException("Решение может принимать только староста своей группы");
        }
    }

    /** Logical expiry plus byte clearing for the task-owned collection only. */
    public long expireAttachments() {
        Instant now = clock.instant();
        Query query = Query.query(Criteria.where("state").is(AttachmentState.ACTIVE.name())
                .and("expires_at").lte(now));
        Update update = new Update()
                .set("state", AttachmentState.EXPIRED.name())
                .set("expired_at", now)
                .unset("data");
        return mongoTemplate.updateMulti(query, update, "request_attachments").getModifiedCount();
    }

    private RequestDetail submitExcuseInTransaction(
            Identity identity,
            ExcuseSubmission command,
            ValidatedAttachments files,
            String payloadHash,
            Long semesterId,
            List<ResolvedLesson> lessons,
            String studentName
    ) {
        long studentId = identity.userId();
        OptionalReceipt previous = findReceipt(studentId, StudentRequestKind.EXCUSE, command.idempotencyKey());
        if (previous.present()) {
            return replayOrMismatch(identity, previous.document(), payloadHash);
        }

        lockPairs(studentId, identity.groupId(), lessons.stream()
                .map(ResolvedLesson::lessonId).sorted(LESSON_ORDER).toList());
        for (ResolvedLesson lesson : lessons) {
            AttendanceDocument attendance = attendanceRepository
                    .findByLessonIdAndUserId(lesson.lessonId(), studentId).orElse(null);
            validateExcuseEligibility(lesson, attendance, studentId);
        }

            Instant now = clock.instant();
            ExcuseTicket ticket = ExcuseTicket.builder()
                .id(new ObjectId().toHexString())
                .studentId(studentId)
                .groupId(identity.groupId())
                .studentName(studentName)
                .lessonIds(lessons.stream().map(ResolvedLesson::lessonId).sorted().toList())
                .semesterId(semesterId)
                .lessonSnapshots(lessons.stream().map(ResolvedLesson::document).toList())
                .excuseType(command.reason())
                .comment(normalizeComment(command.comment()))
                .attachmentDescriptors(new ArrayList<>())
                .status(ExcuseTicketStatus.SUBMITTED)
                .createdAt(now)
                .updatedAt(now)
                .build();
        ExcuseTicket saved = excuseRepository.save(ticket);

        List<RequestAttachmentDescriptorDocument> descriptors = new ArrayList<>();
        for (int position = 0; position < files.items().size(); position++) {
            ValidatedAttachment file = files.items().get(position);
                Instant expiresAt = now.atZone(ZoneOffset.UTC).plusYears(1).toInstant();
            RequestAttachmentDocument attachment = RequestAttachmentDocument.builder()
                    .id(new ObjectId().toHexString())
                    .requestId(saved.getId())
                    .ownerStudentId(studentId)
                    .groupId(identity.groupId())
                    .position(position)
                    .name(file.safeName())
                    .contentType(file.detectedContentType())
                    .size((long) file.bytes().length)
                    .sha256(file.sha256())
                    .state(AttachmentState.ACTIVE)
                    .data(new Binary(file.bytes()))
                    .uploadedAt(now)
                    .expiresAt(expiresAt)
                    .build();
            RequestAttachmentDocument persisted = attachmentRepository.save(attachment);
            descriptors.add(RequestAttachmentDescriptorDocument.builder()
                    .id(persisted.getId())
                    .name(persisted.getName())
                    .contentType(persisted.getContentType())
                    .size(persisted.getSize())
                    .sha256(persisted.getSha256())
                    .state(persisted.getState())
                    .uploadedAt(persisted.getUploadedAt())
                    .expiresAt(persisted.getExpiresAt())
                    .build());
        }
        saved.setAttachmentDescriptors(descriptors);
        saved = excuseRepository.save(saved);

        excuseEventPublisher.publishRequested(saved, lessonDetails(lessons), descriptors);
        receiptRepository.save(StudentRequestReceiptDocument.builder()
                .studentId(studentId)
                .commandKind(StudentRequestKind.EXCUSE.name())
                .idempotencyKey(command.idempotencyKey())
                .payloadHash(payloadHash)
                .requestId(saved.getId())
                .createdAt(now)
                .build());
        return toDetail(saved);
    }

    private RequestDetail submitLateInTransaction(
            Identity identity,
            LateCheckinSubmission command,
            String payloadHash,
            Long semesterId,
            ResolvedLesson lesson,
            String studentName
    ) {
        long studentId = identity.userId();
        OptionalReceipt previous = findReceipt(studentId, StudentRequestKind.LATE_CHECKIN,
                command.idempotencyKey());
        if (previous.present()) {
            return replayOrMismatch(identity, previous.document(), payloadHash);
        }

        lockPairs(studentId, identity.groupId(), List.of(lesson.lessonId()));
        AttendanceDocument attendance = attendanceRepository
                .findByLessonIdAndUserId(lesson.lessonId(), studentId).orElse(null);
        validateLateEligibility(lesson, attendance, studentId);
        if (lateCheckinRepository.findFirstByStudentIdAndLessonIdAndStatus(
                studentId, lesson.lessonId(), LateCheckinRequestStatus.PENDING).isPresent()) {
            throw new ConflictException("Запрос на эту пару уже отправлен");
        }
        consumeBudget(studentId, semesterId, clock.instant());

        Instant now = clock.instant();
        LateCheckinRequest request = LateCheckinRequest.builder()
                .id(new ObjectId().toHexString())
                .studentId(studentId)
                .groupId(identity.groupId())
                .lessonId(lesson.lessonId())
                .subjectId(lesson.subjectId())
                .subjectName(lesson.subjectName())
                .subjectType(lesson.subjectType())
                .semesterId(semesterId)
                .lessonNumber(lesson.lessonNumber())
                .lessonDate(lesson.date())
                .studentName(studentName)
                .status(LateCheckinRequestStatus.PENDING)
                .origin(LateCheckinRequestOrigin.MANUAL)
                .createdAt(now)
                .updatedAt(now)
                .build();
        LateCheckinRequest saved = lateCheckinRepository.save(request);
        lateCheckinEventPublisher.publishRequested(saved, saved.getLessonDate(), saved.getLessonNumber(),
                saved.getSubjectId(), null);
        receiptRepository.save(StudentRequestReceiptDocument.builder()
                .studentId(studentId)
                .commandKind(StudentRequestKind.LATE_CHECKIN.name())
                .idempotencyKey(command.idempotencyKey())
                .payloadHash(payloadHash)
                .requestId(saved.getId())
                .createdAt(now)
                .build());
        return toDetail(saved);
    }

    private void validateExcuseEligibility(ResolvedLesson lesson, AttendanceDocument attendance,
                                           long studentId) {
        String status = lesson.status();
        if ("cancelled".equals(status)) {
            throw new BadRequestException("Пара отменена");
        }
        if (attendance != null && (attendance.getStatus() == AttendanceStatus.PRESENT
                || attendance.getStatus() == AttendanceStatus.EXCUSED
                || attendance.getStatus() == AttendanceStatus.FREE_ATTENDANCE
                || attendance.getStatus() == AttendanceStatus.CANCELLED)) {
            throw new ConflictException("На выбранной паре уже есть итоговая отметка");
        }
        if ("closed".equals(status)
                && (attendance == null || attendance.getStatus() != AttendanceStatus.ABSENT)) {
            throw new ConflictException("Для закрытой пары требуется собственная отметка «н»");
        }
        if (!Set.of("planned", "active", "closed").contains(status)) {
            throw new BadRequestException("Неизвестное состояние пары");
        }
        if (excuseRepository.existsByStudentIdAndLessonIdsInAndStatusIn(
                studentId, List.of(lesson.lessonId()),
                List.of(ExcuseTicketStatus.SUBMITTED, ExcuseTicketStatus.APPROVED))) {
            throw new ConflictException("На выбранной паре уже есть активная уважительная заявка");
        }
    }

    private void validateLateEligibility(ResolvedLesson lesson, AttendanceDocument attendance,
                                          long studentId) {
        if (!"closed".equals(lesson.status())) {
            throw new BadRequestException("Запрос можно создать только по закрытой паре");
        }
        if (lesson.blocked()) {
            throw new ConflictException("Пара заблокирована");
        }
        if (attendance == null || attendance.getStatus() != AttendanceStatus.ABSENT) {
            throw new ConflictException("Поздняя отметка доступна только для собственной отметки «н»");
        }
        if (attendance.getStatus() == AttendanceStatus.PRESENT) {
            throw new ConflictException("Вы уже отмечены на этой паре");
        }
        if (lateCheckinRepository.findFirstByStudentIdAndLessonIdAndStatus(
                studentId, lesson.lessonId(), LateCheckinRequestStatus.PENDING).isPresent()) {
            throw new ConflictException("На выбранной паре уже есть активный запрос");
        }
    }

    private void consumeBudget(long studentId, long semesterId, Instant now) {
        Criteria identity = Criteria.where("student_id").is(studentId)
                .and("semester_id").is(semesterId);
        StudentLateCheckinBudgetDocument current = mongoTemplate.findOne(
                Query.query(identity), StudentLateCheckinBudgetDocument.class);
        int effectiveLimit = effectiveBudgetLimit(current);
        if (current != null && current.getUsed() != null
                && current.getUsed() >= effectiveLimit) {
            throw new ConflictException("Лимит ручных запросов за семестр исчерпан");
        }
        Criteria underPersistedLimit = current == null
                ? Criteria.where("used").lt(MANUAL_LATE_LIMIT)
                : Criteria.where("used").lt(effectiveLimit);
        Query query = Query.query(new Criteria().andOperator(identity, underPersistedLimit));
        Update update = new Update()
                .inc("used", 1)
                .setOnInsert("student_id", studentId)
                .setOnInsert("semester_id", semesterId)
                .setOnInsert("limit", MANUAL_LATE_LIMIT)
                .set("updated_at", now);
        StudentLateCheckinBudgetDocument result = mongoTemplate.findAndModify(
                query, update, FindAndModifyOptions.options().upsert(true).returnNew(true),
                StudentLateCheckinBudgetDocument.class);
        if (result == null) {
            throw new ConflictException("Лимит ручных запросов за семестр исчерпан");
        }
    }

    private boolean hasRemainingBudget(long studentId, long semesterId) {
        StudentLateCheckinBudgetDocument budget = budgetRepository
                .findByStudentIdAndSemesterId(studentId, semesterId).orElse(null);
        int used = budget == null || budget.getUsed() == null ? 0 : budget.getUsed();
        return used < effectiveBudgetLimit(budget);
    }

    private static int effectiveBudgetLimit(StudentLateCheckinBudgetDocument budget) {
        return budget == null || budget.getLimit() == null ? MANUAL_LATE_LIMIT : budget.getLimit();
    }

    private List<ResolvedLesson> resolveLessons(Identity identity, List<Long> requestedIds, long semesterId) {
        List<Long> ids = normalizeLessonIds(requestedIds);
        List<LessonInfo> compact = scheduleGrpcClient.getLessonsByIds(ids);
        Map<Long, LessonInfo> compactById = new HashMap<>();
        for (LessonInfo lesson : compact) {
            compactById.put(lesson.getLessonId(), lesson);
        }
        if (compactById.size() != ids.size() || !compactById.keySet().containsAll(ids)) {
            throw new BadRequestException("Одна или несколько пар не найдены");
        }

        // GetLessonsByIds has no semester field.  The semester-scoped schedule
        // query is the authoritative guard against submitting an old lesson ID.
        Instant now = clock.instant();
        Set<Long> currentSemesterIds = scheduleGrpcClient.getLessonsByGroup(
                        identity.groupId(), semesterId,
                        now.atZone(ZoneOffset.UTC).toLocalDate().minusYears(2).toString(),
                        now.atZone(ZoneOffset.UTC).toLocalDate().plusYears(2).toString())
                .getLessonsList().stream()
                .map(LessonResponse::getId)
                .collect(java.util.stream.Collectors.toSet());
        if (!currentSemesterIds.containsAll(ids)) {
            throw new BadRequestException("Все пары должны относиться к текущему семестру");
        }

        Set<Long> subjectIds = new HashSet<>();
        for (Long id : ids) {
            subjectIds.add(compactById.get(id).getSubjectId());
        }
        Map<Long, AcademicGrpcClient.SubjectDetails> subjectDetails =
                academicGrpcClient.getSubjectDetailsByIds(new ArrayList<>(subjectIds));

        List<ResolvedLesson> result = new ArrayList<>(ids.size());
        for (Long id : ids) {
            LessonInfo info = compactById.get(id);
            LessonResponse full = scheduleGrpcClient.getLessonById(id);
            long groupId = full.getGroupId() > 0 ? full.getGroupId() : info.getGroupId();
            if (!Objects.equals(groupId, identity.groupId())) {
                throw new AccessDeniedException("Пара принадлежит другой группе");
            }
            String status = full.getStatus() == null ? "" : full.getStatus().toLowerCase(Locale.ROOT);
            if (status.isBlank()) {
                throw new BadRequestException("Состояние пары недоступно");
            }
            LocalDate date = parseDate(full.getDate(), info.getDate());
            if (date == null) {
                throw new BadRequestException("Дата пары недоступна");
            }
            LocalTime startsAt = parseTime(full.getStartTime(), info.getStartsAt(), false);
            LocalTime endsAt = parseTime(full.getEndTime(), null, true);
            long subjectId = full.getSubjectId() > 0 ? full.getSubjectId() : info.getSubjectId();
            AcademicGrpcClient.SubjectDetails subject = subjectDetails.get(subjectId);
            if (subject == null) {
                throw new ResourceNotFoundException("Subject", "id", subjectId);
            }
            int lessonNumber = full.getLessonNumber() > 0 ? full.getLessonNumber() : info.getLessonNumber();
            StudentLessonSnapshotDocument snapshot = StudentLessonSnapshotDocument.builder()
                    .lessonId(id)
                    .groupId(groupId)
                    .subjectId(subjectId)
                    .subjectName(subject.name())
                    .subjectType(subject.type())
                    .semesterId(semesterId)
                    .lessonNumber(lessonNumber)
                    .date(date)
                    .startsAt(startsAt)
                    .endsAt(endsAt)
                    .status(status)
                    .blocked(full.getIsBlockedByHeadman())
                    .build();
            result.add(new ResolvedLesson(id, groupId, subjectId, lessonNumber, date, status,
                    full.getIsBlockedByHeadman(), subject.name(), subject.type(), snapshot));
        }
        return result;
    }

    private static LocalDate parseDate(String full, String compact) {
        String value = full == null || full.isBlank() ? compact : full;
        return value == null || value.isBlank() ? null : LocalDate.parse(value);
    }

    private static LocalTime parseTime(String full, String compact, boolean end) {
        String value = full == null || full.isBlank() ? compact : full;
        if (value == null || value.isBlank()) {
            return null;
        }
        if (value.contains("T")) {
            value = value.substring(value.indexOf('T') + 1);
        }
        return LocalTime.parse(value);
    }

    private RequestOptions optionsFor(long studentId, long semesterId, List<LessonSnapshot> lessons) {
        List<LessonOption> options = new ArrayList<>();
        for (LessonSnapshot lesson : lessons) {
            AttendanceDocument attendance = attendanceRepository
                    .findByLessonIdAndUserId(lesson.lessonId(), studentId).orElse(null);
            boolean excuseEligible;
            try {
                validateExcuseEligibility(new ResolvedLesson(lesson.lessonId(), lesson.groupId(),
                        lesson.subjectId(), lesson.lessonNumber(), lesson.date(),
                        normalizeStatus(lesson.status()), lesson.blocked(), toDocument(lesson)),
                        attendance, studentId);
                excuseEligible = true;
            } catch (RuntimeException rejected) {
                excuseEligible = false;
            }
            boolean lateEligible;
            try {
                validateLateEligibility(new ResolvedLesson(lesson.lessonId(), lesson.groupId(),
                        lesson.subjectId(), lesson.lessonNumber(), lesson.date(),
                        normalizeStatus(lesson.status()), lesson.blocked(), toDocument(lesson)),
                        attendance, studentId);
                lateEligible = hasRemainingBudget(studentId, semesterId);
            } catch (RuntimeException rejected) {
                lateEligible = false;
            }
            options.add(new LessonOption(lesson, excuseEligible, lateEligible,
                    pendingForLesson(studentId, lesson.lessonId())));
        }
        StudentLateCheckinBudgetDocument budget = budgetRepository
                .findByStudentIdAndSemesterId(studentId, semesterId).orElse(null);
        int used = budget == null || budget.getUsed() == null ? 0 : budget.getUsed();
        int limit = effectiveBudgetLimit(budget);
        return new RequestOptions(
                STUDENT_REASONS.stream()
                        .map(reason -> new ReasonOption(reason, REASON_LABELS.get(reason), commentRequired(reason))).toList(),
                new FileLimits(MAX_ATTACHMENTS, MAX_ATTACHMENT_BYTES, MAX_TOTAL_ATTACHMENT_BYTES,
                        ALLOWED_MIME_TYPES.stream().sorted().toList(), ALLOWED_EXTENSIONS.stream().sorted().toList()),
                new Budget(semesterId, limit, used, Math.max(0, limit - used)),
                options);
    }

    /** Resolve display fields before a snapshot is exposed to a transport adapter. */
    private List<LessonSnapshot> enrichSnapshots(List<LessonSnapshot> lessons) {
        if (lessons == null || lessons.isEmpty()) {
            return List.of();
        }
        List<Long> subjectIds = lessons.stream()
                .map(LessonSnapshot::subjectId)
                .filter(id -> id > 0)
                .distinct()
                .toList();
        Map<Long, AcademicGrpcClient.SubjectDetails> details =
                academicGrpcClient.getSubjectDetailsByIds(subjectIds);
        return lessons.stream().map(lesson -> {
            AcademicGrpcClient.SubjectDetails subject = details.get(lesson.subjectId());
            String name = lesson.subjectName() != null
                    ? lesson.subjectName() : subject == null ? null : subject.name();
            String type = lesson.subjectType() != null
                    ? lesson.subjectType() : subject == null ? null : subject.type();
            return new LessonSnapshot(lesson.lessonId(), lesson.groupId(), lesson.subjectId(),
                    name, type, lesson.semesterId(), lesson.lessonNumber(), lesson.date(),
                    lesson.startsAt(), lesson.endsAt(), lesson.status(), lesson.blocked());
        }).toList();
    }

    private void validateOptionSnapshots(Identity identity, List<LessonSnapshot> lessons, long semesterId) {
        if (lessons == null) {
            return;
        }
        for (LessonSnapshot lesson : lessons) {
            if (lesson == null || lesson.lessonId() <= 0 || lesson.groupId() <= 0
                    || lesson.subjectId() <= 0) {
                throw new BadRequestException("Некорректный snapshot пары");
            }
            if (lesson.groupId() != identity.groupId()) {
                throw new AccessDeniedException("Пара принадлежит другой группе");
            }
            if (lesson.semesterId() != semesterId) {
                throw new BadRequestException("Пара относится к другому семестру");
            }
        }
    }

    private List<PendingRequestRef> pendingForLesson(long studentId, long lessonId) {
        List<PendingRequestRef> refs = new ArrayList<>();
        Query excuseQuery = Query.query(Criteria.where("student_id").is(studentId)
                .and("lesson_ids").is(lessonId)
                .and("status").is(ExcuseTicketStatus.SUBMITTED.name()));
        for (ExcuseTicket ticket : mongoTemplate.find(excuseQuery, ExcuseTicket.class)) {
            refs.add(new PendingRequestRef(ticket.getId(), StudentRequestKind.EXCUSE,
                    StudentRequestOrigin.MANUAL));
        }
        Query lateQuery = Query.query(Criteria.where("student_id").is(studentId)
                .and("lesson_id").is(lessonId)
                .and("status").is(LateCheckinRequestStatus.PENDING.name()));
        for (LateCheckinRequest request : mongoTemplate.find(lateQuery, LateCheckinRequest.class)) {
            refs.add(new PendingRequestRef(request.getId(), StudentRequestKind.LATE_CHECKIN,
                    request.getOrigin() == null ? StudentRequestOrigin.MANUAL
                            : StudentRequestOrigin.valueOf(request.getOrigin().name())));
        }
        return refs.stream().sorted(Comparator.comparing(PendingRequestRef::kind)
                .thenComparing(PendingRequestRef::id)).toList();
    }

    private List<Document> unionPipeline(long studentId, RequestBucket bucket, boolean sort) {
        String bucketStatus = bucket == null || bucket == RequestBucket.OPEN ? "PENDING" : null;
        List<Document> pipeline = new ArrayList<>();
        pipeline.add(new Document("$match", new Document("student_id", studentId)));
        pipeline.add(new Document("$project", new Document("_id", 1)
                .append("request_kind", "EXCUSE")
                .append("origin", "MANUAL")
                .append("status", new Document("$switch", new Document("branches", List.of(
                        new Document("case", new Document("$eq", List.of("$status", "SUBMITTED")))
                                .append("then", "PENDING"),
                        new Document("case", new Document("$eq", List.of("$status", "DRAFT")))
                                .append("then", "PENDING")
                )).append("default", new Document("$toUpper", "$status"))))
                .append("lesson_snapshots", 1)
                .append("lesson_ids", 1)
                .append("semester_id", 1)
                .append("created_at", 1)
                .append("updated_at", 1)));
        if (bucketStatus != null) {
            pipeline.add(new Document("$match", new Document("status", bucketStatus)));
        } else {
            pipeline.add(new Document("$match", new Document("status",
                    new Document("$in", List.of("APPROVED", "REJECTED", "CANCELLED")))));
        }
        pipeline.add(new Document("$unionWith", new Document("coll", "late_checkin_requests")
                .append("pipeline", lateUnionPipeline(studentId, bucketStatus))));
        if (sort) {
            pipeline.add(new Document("$sort", new Document("updated_at", -1)
                    .append("request_kind", 1).append("_id", 1)));
        }
        return pipeline;
    }

    private List<Document> lateUnionPipeline(long studentId, String bucketStatus) {
        List<Document> pipeline = new ArrayList<>();
        pipeline.add(new Document("$match", new Document("student_id", studentId)));
        pipeline.add(new Document("$project", new Document("_id", 1)
                .append("request_kind", "LATE_CHECKIN")
                .append("origin", new Document("$ifNull", List.of(
                        new Document("$toUpper", "$origin"), "MANUAL")))
                .append("status", new Document("$toUpper", "$status"))
                .append("late_lesson_id", "$lesson_id")
                .append("late_group_id", "$group_id")
                .append("late_subject_id", "$subject_id")
                .append("late_subject_name", "$subject_name")
                .append("late_subject_type", "$subject_type")
                .append("late_semester_id", "$semester_id")
                .append("late_lesson_number", "$lesson_number")
                .append("late_lesson_date", "$lesson_date")
                .append("created_at", 1)
                .append("updated_at", 1)));
        if (bucketStatus != null) {
            pipeline.add(new Document("$match", new Document("status", bucketStatus.toUpperCase(Locale.ROOT))));
        } else {
            pipeline.add(new Document("$match", new Document("status",
                    new Document("$in", List.of("APPROVED", "REJECTED", "CANCELLED")))));
        }
        return pipeline;
    }

    private RequestSummary summaryFromUnionDocument(Document document) {
        String id = valueAsString(document.get("_id"));
        StudentRequestKind kind = StudentRequestKind.valueOf(valueAsString(document.get("request_kind")));
        StudentRequestStatus status = StudentRequestStatus.valueOf(valueAsString(document.get("status")));
        StudentRequestOrigin origin = StudentRequestOrigin.valueOf(valueAsString(document.get("origin")));
        List<LessonSnapshot> lessons = new ArrayList<>();
        if (kind == StudentRequestKind.EXCUSE) {
            Object raw = document.get("lesson_snapshots");
            if (raw instanceof Collection<?> collection) {
                for (Object item : collection) {
                    if (item instanceof Document nested) {
                        lessons.add(snapshotFromDocument(nested));
                    }
                }
            }
            if (lessons.isEmpty() && document.get("lesson_ids") instanceof Collection<?> ids) {
                for (Object idValue : ids) {
                    lessons.add(new LessonSnapshot(numberAsLong(idValue), 0, 0,
                            numberAsLong(document.get("semester_id")), 0, null, null, null,
                            "unknown", false));
                }
            }
        } else {
            lessons.add(new LessonSnapshot(
                    numberAsLong(document.get("late_lesson_id")),
                    numberAsLong(document.get("late_group_id")),
                    numberAsLong(document.get("late_subject_id")),
                    valueAsNullableString(document.get("late_subject_name")),
                    valueAsNullableString(document.get("late_subject_type")),
                    numberAsLong(document.get("late_semester_id")),
                    numberAsInt(document.get("late_lesson_number")),
                    localDate(document.get("late_lesson_date")), null, null,
                    "closed", false));
        }
        return new RequestSummary(id, kind, status, origin, lessons,
                instant(document.get("created_at")), instant(document.get("updated_at")));
    }

    private LessonSnapshot snapshotFromDocument(Document document) {
        return new LessonSnapshot(
                numberAsLong(document.get("lesson_id")),
                numberAsLong(document.get("group_id")),
                numberAsLong(document.get("subject_id")),
                valueAsNullableString(document.get("subject_name")),
                valueAsNullableString(document.get("subject_type")),
                numberAsLong(document.get("semester_id")),
                numberAsInt(document.get("lesson_number")),
                localDate(document.get("date")),
                localTime(document.get("starts_at")),
                localTime(document.get("ends_at")),
                valueAsString(document.get("status")),
                Boolean.TRUE.equals(document.get("blocked")));
    }

    private static Document firstDocument(Iterable<Document> documents) {
        for (Document document : documents) {
            return document;
        }
        return null;
    }

    private RequestDetail toDetail(ExcuseTicket ticket) {
        List<RequestAttachmentDocument> storedAttachments =
                attachmentRepository.findByRequestIdAndOwnerStudentIdOrderByPositionAsc(
                        ticket.getId(), ticket.getStudentId());
        List<AttachmentDescriptor> attachments = !storedAttachments.isEmpty()
                ? storedAttachments.stream().map(this::toDescriptor).toList()
                : ticket.getAttachmentDescriptors() == null
                ? List.of()
                : ticket.getAttachmentDescriptors().stream().map(StudentRequestService::toDescriptor).toList();
        return toDetail(ticket, attachments);
    }

    private RequestDetail toDetail(ExcuseTicket ticket, List<AttachmentDescriptor> attachments) {
        RequestSummary summary = new RequestSummary(
                ticket.getId(), StudentRequestKind.EXCUSE,
                mapStatus(ticket.getStatus()), StudentRequestOrigin.MANUAL,
                snapshots(ticket.getLessonSnapshots(), ticket.getLessonIds(), ticket.getSemesterId()),
                ticket.getCreatedAt(), ticket.getUpdatedAt());
        Decision decision = ticket.getDecisionBy() == null && ticket.getDecisionComment() == null
                && ticket.getDecisionAt() == null
                ? null
                : new Decision(ticket.getDecisionBy(), ticket.getDecisionComment(), ticket.getDecisionAt());
        return new RequestDetail(summary, ticket.getExcuseType(), ticket.getComment(), attachments, decision);
    }

    private RequestDetail toDetail(LateCheckinRequest request) {
        LessonSnapshot snapshot = new LessonSnapshot(
                request.getLessonId() == null ? 0 : request.getLessonId(),
                request.getGroupId() == null ? 0 : request.getGroupId(),
                request.getSubjectId() == null ? 0 : request.getSubjectId(),
                request.getSubjectName(), request.getSubjectType(),
                request.getSemesterId() == null ? 0 : request.getSemesterId(),
                request.getLessonNumber() == null ? 0 : request.getLessonNumber(),
                request.getLessonDate(), null, null, "closed", false);
        RequestSummary summary = new RequestSummary(
                request.getId(), StudentRequestKind.LATE_CHECKIN,
                mapStatus(request.getStatus()), request.getOrigin() == null
                        ? StudentRequestOrigin.MANUAL
                        : StudentRequestOrigin.valueOf(request.getOrigin().name()),
                List.of(snapshot), request.getCreatedAt(), request.getUpdatedAt());
        Decision decision = request.getDecisionBy() == null && request.getDecisionAt() == null
                ? null : new Decision(request.getDecisionBy(), null, request.getDecisionAt());
        return new RequestDetail(summary, null, null, List.of(), decision);
    }

    private static List<LessonSnapshot> snapshots(List<StudentLessonSnapshotDocument> documents,
                                                   List<Long> ids, Long semesterId) {
        if (documents != null && !documents.isEmpty()) {
            return documents.stream().map(StudentRequestService::toSnapshot).toList();
        }
        if (ids == null) {
            return List.of();
        }
        return ids.stream().map(id -> new LessonSnapshot(id, 0, 0,
                semesterId == null ? 0 : semesterId, 0, null, null, null,
                "unknown", false)).toList();
    }

    private static LessonSnapshot toSnapshot(StudentLessonSnapshotDocument document) {
        return new LessonSnapshot(
                document.getLessonId() == null ? 0 : document.getLessonId(),
                document.getGroupId() == null ? 0 : document.getGroupId(),
                document.getSubjectId() == null ? 0 : document.getSubjectId(),
                document.getSubjectName(), document.getSubjectType(),
                document.getSemesterId() == null ? 0 : document.getSemesterId(),
                document.getLessonNumber() == null ? 0 : document.getLessonNumber(),
                document.getDate(), document.getStartsAt(), document.getEndsAt(),
                document.getStatus(), document.isBlocked());
    }

    private static AttachmentDescriptor toDescriptor(RequestAttachmentDescriptorDocument descriptor) {
        return new AttachmentDescriptor(descriptor.getId(), descriptor.getName(), descriptor.getContentType(),
                descriptor.getSize() == null ? 0 : descriptor.getSize(), descriptor.getSha256(),
                descriptor.getState(), descriptor.getUploadedAt(), descriptor.getExpiresAt(),
                descriptor.getExpiredAt());
    }

    private AttachmentDescriptor toDescriptor(RequestAttachmentDocument document) {
        AttachmentState state = document.getState();
        Instant expiredAt = document.getExpiredAt();
        Instant now = clock.instant();
        if (document.getExpiresAt() == null || !now.isBefore(document.getExpiresAt())) {
            state = AttachmentState.EXPIRED;
            if (expiredAt == null) {
                expiredAt = now;
            }
        }
        return new AttachmentDescriptor(document.getId(), document.getName(), document.getContentType(),
                document.getSize() == null ? 0 : document.getSize(), document.getSha256(),
                state, document.getUploadedAt(), document.getExpiresAt(), expiredAt);
    }

    private static StudentRequestStatus mapStatus(ExcuseTicketStatus status) {
        if (status == null || status == ExcuseTicketStatus.SUBMITTED || status == ExcuseTicketStatus.DRAFT) {
            return StudentRequestStatus.PENDING;
        }
        return StudentRequestStatus.valueOf(status.name());
    }

    private static StudentRequestStatus mapStatus(LateCheckinRequestStatus status) {
        return status == null ? StudentRequestStatus.PENDING : StudentRequestStatus.valueOf(status.name());
    }

    /**
     * Atomic approval/rejection path for a student excuse.  Headman adapters
     * can call it later; PRESENT always wins the per-pair race.
     */
    public RequestDetail decideExcuse(Identity identity, String ticketId, boolean approved,
                                      String decisionComment) {
        return executeDecisionWithRetry(() -> transactionTemplate.execute(status -> {
            ExcuseTicket ticket = excuseRepository.findById(ticketId).orElseThrow(
                    () -> new ResourceNotFoundException("ExcuseTicket", "id", ticketId));
            long actor = requireDecisionAuthority(identity, ticket.getGroupId());
            if (Objects.equals(ticket.getStudentId(), actor)) {
                throw new AccessDeniedException("Нельзя принимать решение по собственной заявке");
            }
            lockPairs(ticket.getStudentId(), ticket.getGroupId(), sortedLessonIds(ticket.getLessonIds()));
            ExcuseTicket current = excuseRepository.findById(ticketId).orElseThrow(
                    () -> new ResourceNotFoundException("ExcuseTicket", "id", ticketId));
            if (current.getStatus() == ExcuseTicketStatus.CANCELLED
                    || current.getStatus() == ExcuseTicketStatus.APPROVED
                    || current.getStatus() == ExcuseTicketStatus.REJECTED) {
                throw new ConflictException("Решение по тикету уже принято");
            }
            Instant now = clock.instant();
            if (approved) {
                List<Long> ids = sortedLessonIds(current.getLessonIds());
                Map<Long, StudentLessonSnapshotDocument> snapshots = snapshotMap(current.getLessonSnapshots());
                for (Long lessonId : ids) {
                    AttendanceDocument attendance = attendanceRepository
                            .findByLessonIdAndUserId(lessonId, current.getStudentId()).orElse(null);
                    if (attendance != null && attendance.getStatus() == AttendanceStatus.PRESENT) {
                        continue;
                    }
                    if (attendance == null || attendance.getStatus() != AttendanceStatus.CANCELLED) {
                        StudentLessonSnapshotDocument snapshot = snapshots.get(lessonId);
                        saveExcusedAttendance(current, snapshot, attendance, now);
                    }
                }
            }
            current.setStatus(approved ? ExcuseTicketStatus.APPROVED : ExcuseTicketStatus.REJECTED);
            current.setDecisionBy(actor);
            current.setDecisionComment(normalizeComment(decisionComment));
            current.setDecisionAt(now);
            current.setUpdatedAt(now);
            ExcuseTicket saved = excuseRepository.save(current);
            excuseEventPublisher.publishDecided(saved);
            return toDetail(saved);
        }), () -> recoverExcuseDecision(identity, ticketId, approved, decisionComment));
    }

    /** Atomic approval/rejection path for either manual or automatic late check-in. */
    public RequestDetail decideLateCheckin(Identity identity, String requestId, boolean approved) {
        return executeDecisionWithRetry(() -> transactionTemplate.execute(status -> {
            LateCheckinRequest request = lateCheckinRepository.findById(requestId).orElseThrow(
                    () -> new ResourceNotFoundException("LateCheckinRequest", "id", requestId));
            long actor = requireDecisionAuthority(identity, request.getGroupId());
            if (Objects.equals(request.getStudentId(), actor)) {
                throw new AccessDeniedException("Нельзя принимать решение по собственной заявке");
            }
            lockPairs(request.getStudentId(), request.getGroupId(), List.of(request.getLessonId()));
            LateCheckinRequest current = lateCheckinRepository.findById(requestId).orElseThrow(
                    () -> new ResourceNotFoundException("LateCheckinRequest", "id", requestId));
            if (current.getStatus() != LateCheckinRequestStatus.PENDING) {
                throw new ConflictException("Решение по запросу уже принято");
            }
            Instant now = clock.instant();
            current.setStatus(approved ? LateCheckinRequestStatus.APPROVED : LateCheckinRequestStatus.REJECTED);
            current.setResolutionReason(approved
                    ? LateCheckinResolutionReason.HEADMAN_APPROVED
                    : LateCheckinResolutionReason.HEADMAN_REJECTED);
            current.setDecisionBy(actor);
            current.setDecisionAt(now);
            current.setUpdatedAt(now);
            if (approved) {
                AttendanceDocument attendance = attendanceRepository
                        .findByLessonIdAndUserId(current.getLessonId(), current.getStudentId()).orElse(null);
                if (attendance == null || attendance.getStatus() != AttendanceStatus.PRESENT) {
                    StudentLessonSnapshotDocument snapshot = StudentLessonSnapshotDocument.builder()
                            .lessonId(current.getLessonId()).groupId(current.getGroupId())
                            .subjectId(current.getSubjectId()).semesterId(current.getSemesterId())
                            .lessonNumber(current.getLessonNumber()).date(current.getLessonDate())
                            .status("closed").build();
                    savePresentAttendance(current, snapshot, attendance, now, actor);
                } else {
                    current.setResolutionReason(LateCheckinResolutionReason.PRESENT_PRIORITY);
                }
            }
            LateCheckinRequest saved = lateCheckinRepository.save(current);
            lateCheckinEventPublisher.publishDecided(saved, saved.getLessonDate(), saved.getLessonNumber(),
                    saved.getSubjectId(), null);
            return toDetail(saved);
        }), () -> recoverLateCheckinDecision(identity, requestId, approved));
    }

    /**
     * Recover a decision whose transaction committed but whose acknowledgement
     * was lost.  The read is deliberately outside a second transaction: a
     * matching terminal document already contains the durable event, so
     * re-running the decision body could publish a duplicate event.
     */
    private RequestDetail recoverExcuseDecision(Identity identity, String ticketId,
                                                boolean approved, String decisionComment) {
        ExcuseTicket ticket = excuseRepository.findById(ticketId).orElse(null);
        if (ticket == null) {
            return null;
        }
        long actor = requireDecisionAuthority(identity, ticket.getGroupId());
        if (Objects.equals(ticket.getStudentId(), actor)) {
            throw new AccessDeniedException("Нельзя принимать решение по собственной заявке");
        }
        ExcuseTicketStatus expected = approved ? ExcuseTicketStatus.APPROVED : ExcuseTicketStatus.REJECTED;
        if (ticket.getStatus() != expected
                || !Objects.equals(ticket.getDecisionBy(), actor)
                || !Objects.equals(ticket.getDecisionComment(), normalizeComment(decisionComment))) {
            return null;
        }
        return toDetail(ticket);
    }

    private RequestDetail recoverLateCheckinDecision(Identity identity, String requestId, boolean approved) {
        LateCheckinRequest request = lateCheckinRepository.findById(requestId).orElse(null);
        if (request == null) {
            return null;
        }
        long actor = requireDecisionAuthority(identity, request.getGroupId());
        if (Objects.equals(request.getStudentId(), actor)) {
            throw new AccessDeniedException("Нельзя принимать решение по собственной заявке");
        }
        LateCheckinRequestStatus expected = approved
                ? LateCheckinRequestStatus.APPROVED : LateCheckinRequestStatus.REJECTED;
        if (request.getStatus() != expected
                || !Objects.equals(request.getDecisionBy(), actor)) {
            return null;
        }
        return toDetail(request);
    }

    private void saveExcusedAttendance(ExcuseTicket ticket,
                                       StudentLessonSnapshotDocument snapshot,
                                       AttendanceDocument current,
                                       Instant now) {
        AttendanceDocument document = current == null ? new AttendanceDocument() : current;
        if (document.getCreatedAt() == null) {
            document.setCreatedAt(now);
        }
        document.setLessonId(ticket.getLessonIds().contains(document.getLessonId())
                ? document.getLessonId() : snapshot == null ? null : snapshot.getLessonId());
        if (document.getLessonId() == null && snapshot != null) {
            document.setLessonId(snapshot.getLessonId());
        }
        document.setUserId(ticket.getStudentId());
        document.setGroupId(ticket.getGroupId());
        if (snapshot != null) {
            document.setSubjectId(snapshot.getSubjectId());
            document.setSemesterId(snapshot.getSemesterId());
            document.setLessonNumber(snapshot.getLessonNumber());
            document.setLessonDate(snapshot.getDate());
        }
        document.setStatus(AttendanceStatus.EXCUSED);
        document.setSource(AttendanceSource.HEADMAN_EXCUSE);
        document.setExcuseReason(reasonText(ticket));
        document.setUpdatedAt(now);
        attendanceRepository.save(document);
    }

    private void savePresentAttendance(LateCheckinRequest request,
                                       StudentLessonSnapshotDocument snapshot,
                                       AttendanceDocument current,
                                       Instant now,
                                       Long markedBy) {
        AttendanceDocument document = current == null ? new AttendanceDocument() : current;
        if (document.getCreatedAt() == null) {
            document.setCreatedAt(now);
        }
        document.setLessonId(request.getLessonId());
        document.setUserId(request.getStudentId());
        document.setGroupId(request.getGroupId());
        document.setSubjectId(request.getSubjectId());
        document.setSemesterId(request.getSemesterId());
        document.setLessonNumber(request.getLessonNumber());
        document.setLessonDate(request.getLessonDate());
        document.setStatus(AttendanceStatus.PRESENT);
        document.setSource(AttendanceSource.LATE_CHECKIN);
        document.setMarkedBy(markedBy);
        document.setExcuseReason(null);
        document.setUpdatedAt(now);
        attendanceRepository.save(document);
    }

    private static String reasonText(ExcuseTicket ticket) {
        if (ticket.getExcuseType() == ExcuseType.OTHER && ticket.getComment() != null
                && !ticket.getComment().isBlank()) {
            String value = ticket.getComment().strip();
            return value.length() > 80 ? value.substring(0, 80) + "…" : value;
        }
        return REASON_LABELS.getOrDefault(ticket.getExcuseType(), "Уважительная");
    }

    private long requireDecisionAuthority(Identity identity, Long resourceGroupId) {
        long caller = requireAuthenticatedScope(identity);
        if (!identity.headman()) {
            throw new AccessDeniedException("Решение доступно только старосте группы");
        }
        if (resourceGroupId == null || !Objects.equals(resourceGroupId, identity.groupId())) {
            throw new AccessDeniedException("Нельзя принимать решение по чужой группе");
        }
        var authority = academicGrpcClient.isHeadman(caller, resourceGroupId);
        if (authority == null || !authority.getIsHeadman()) {
            throw new AccessDeniedException("Староста не подтверждён для группы заявки");
        }
        return caller;
    }

    private long requireAuthenticatedScope(Identity identity) {
        if (identity == null || identity.userId() <= 0 || identity.groupId() == null || identity.groupId() <= 0) {
            throw new AccessDeniedException("Не хватает authenticated user/group scope");
        }
        if (identity.role() != UserRole.STUDENT) {
            throw new AccessDeniedException("Решение доступно только старосте-студенту");
        }
        return identity.userId();
    }

    private void lockPairs(long studentId, long groupId, List<Long> lessonIds) {
        for (Long lessonId : sortedLessonIds(lessonIds)) {
            pairWriteCoordinator.lock(studentId, lessonId, groupId, clock.instant());
        }
    }

    private static List<Long> sortedLessonIds(List<Long> lessonIds) {
        if (lessonIds == null) {
            return List.of();
        }
        return lessonIds.stream().filter(Objects::nonNull).distinct().sorted().toList();
    }

    private static Map<Long, StudentLessonSnapshotDocument> snapshotMap(
            List<StudentLessonSnapshotDocument> snapshots) {
        Map<Long, StudentLessonSnapshotDocument> map = new HashMap<>();
        if (snapshots != null) {
            for (StudentLessonSnapshotDocument snapshot : snapshots) {
                if (snapshot.getLessonId() != null) {
                    map.put(snapshot.getLessonId(), snapshot);
                }
            }
        }
        return map;
    }

    private long requireStudentIdentity(Identity identity) {
        if (identity == null || identity.userId() <= 0 || identity.groupId() == null || identity.groupId() <= 0) {
            throw new AccessDeniedException("Не хватает student/group scope");
        }
        if (identity.role() != UserRole.STUDENT) {
            throw new AccessDeniedException("Заявки доступны только студенту");
        }
        if (identity.headman()) {
            throw new ConflictException("Староста использует журнал посещаемости");
        }
        return identity.userId();
    }

    private void requireOwner(Identity identity, Long ownerStudentId) {
        if (!Objects.equals(ownerStudentId, identity.userId())) {
            throw new AccessDeniedException("Доступ к заявке запрещён");
        }
    }

    private Long requireCurrentSemester() {
        Long semesterId = semesterCacheService.getActiveSemesterId();
        if (semesterId == null || semesterId <= 0) {
            throw new BadRequestException("Текущий семестр недоступен");
        }
        return semesterId;
    }

    private ValidatedAttachments validateExcuseCommand(ExcuseSubmission command) {
        if (command == null) {
            throw new BadRequestException("Команда обязательна");
        }
        validateKey(command.idempotencyKey());
        if (command.reason() == null || !STUDENT_REASONS.contains(command.reason())) {
            throw new BadRequestException("Причина не поддерживается student contract");
        }
        List<Long> ids = normalizeLessonIds(command.lessonIds());
        if (ids.isEmpty()) {
            throw new BadRequestException("Нужно выбрать хотя бы одну пару");
        }
        String comment = normalizeComment(command.comment());
        if (comment != null && comment.length() > 1000) {
            throw new BadRequestException("Комментарий не должен превышать 1000 символов");
        }
        if (commentRequired(command.reason()) && (comment == null || comment.isBlank())) {
            throw new BadRequestException("Для причины OTHER нужен комментарий");
        }
        List<AttachmentInput> inputs = command.attachments() == null ? List.of() : command.attachments();
        if (inputs.size() > MAX_ATTACHMENTS) {
            throw new BadRequestException("Можно приложить не более двух файлов");
        }
        List<ValidatedAttachment> validated = new ArrayList<>();
        long total = 0;
        for (AttachmentInput input : inputs) {
            ValidatedAttachment file = validateAttachment(input);
            total = Math.addExact(total, file.bytes().length);
            if (total > MAX_TOTAL_ATTACHMENT_BYTES) {
                throw new PayloadTooLargeException("Общий размер файлов не должен превышать 20 МБ");
            }
            validated.add(file);
        }
        return new ValidatedAttachments(validated);
    }

    private void validateLateCommand(LateCheckinSubmission command) {
        if (command == null || command.lessonId() <= 0) {
            throw new BadRequestException("Пара обязательна");
        }
        validateKey(command.idempotencyKey());
    }

    private static void validateKey(String key) {
        if (key == null || key.length() < 16 || key.length() > 128
                || key.chars().anyMatch(c -> c < 0x21 || c > 0x7e)) {
            throw new InvalidIdempotencyKeyException();
        }
    }

    private static List<Long> normalizeLessonIds(List<Long> lessonIds) {
        if (lessonIds == null) {
            throw new BadRequestException("lessonIds обязательны");
        }
        if (lessonIds.stream().anyMatch(id -> id == null || id <= 0)) {
            throw new BadRequestException("Некорректный lessonId");
        }
        List<Long> sorted = lessonIds.stream().sorted().toList();
        if (new HashSet<>(sorted).size() != sorted.size()) {
            throw new ConflictException("Пары в пакете должны быть уникальны");
        }
        return sorted;
    }

    private static String normalizeComment(String comment) {
        if (comment == null) {
            return null;
        }
        String normalized = comment.strip();
        return normalized.isEmpty() ? null : normalized;
    }

    private static boolean commentRequired(ExcuseType reason) {
        return reason == ExcuseType.OTHER;
    }

    private static String validateDecisionComment(String comment) {
        String normalized = normalizeComment(comment);
        if (normalized != null && normalized.length() > 1000) {
            throw new BadRequestException("Комментарий не должен превышать 1000 символов");
        }
        return normalized;
    }

    private static void validateObjectId(String value, String field) {
        if (value == null || !value.matches("[0-9a-fA-F]{24}")) {
            throw new BadRequestException(field + " должен быть 24-символьным hex id");
        }
    }

    private ValidatedAttachment validateAttachment(AttachmentInput input) {
        if (input == null || input.bytes() == null || input.bytes().length == 0) {
            throw new BadRequestException("Пустое вложение недопустимо");
        }
        byte[] bytes = input.bytes();
        if (bytes.length > MAX_ATTACHMENT_BYTES) {
            throw new PayloadTooLargeException("Размер одного файла не должен превышать 10 МБ");
        }
        String contentType = input.declaredContentType() == null
                ? "" : input.declaredContentType().strip().toLowerCase(Locale.ROOT);
        String safeName = safeFilename(input.originalFilename());
        String extension = extension(safeName);
        if (!ALLOWED_MIME_TYPES.contains(contentType) || !ALLOWED_EXTENSIONS.contains(extension)) {
            throw new BadRequestException("Поддерживаются только JPEG, PNG и PDF");
        }
        String detectedType = detectType(bytes);
        if (!contentType.equals(detectedType) || !mimeExtensionMatches(contentType, extension)) {
            throw new BadRequestException("MIME, расширение и сигнатура файла не совпадают");
        }
        return new ValidatedAttachment(safeName, detectedType, bytes, sha256(bytes));
    }

    private static String safeFilename(String original) {
        String value = original == null ? "attachment" : original.strip();
        value = value.replace('\\', '_').replace('/', '_');
        value = value.replace("..", "_");
        StringBuilder safe = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c >= 0x20 && c != 0x7f) {
                safe.append(c);
            }
        }
        String result = safe.toString().strip();
        if (result.isEmpty() || result.equals(".") || result.equals("..")) {
            result = "attachment";
        }
        if (result.length() > 255) {
            result = result.substring(0, 255);
        }
        return result;
    }

    private static String extension(String filename) {
        int dot = filename.lastIndexOf('.');
        return dot < 0 ? "" : filename.substring(dot).toLowerCase(Locale.ROOT);
    }

    private static boolean mimeExtensionMatches(String mime, String extension) {
        return (mime.equals("image/jpeg") && (extension.equals(".jpg") || extension.equals(".jpeg")))
                || (mime.equals("image/png") && extension.equals(".png"))
                || (mime.equals("application/pdf") && extension.equals(".pdf"));
    }

    private static String detectType(byte[] bytes) {
        if (bytes.length >= 3 && (bytes[0] & 0xff) == 0xff
                && (bytes[1] & 0xff) == 0xd8 && (bytes[2] & 0xff) == 0xff) {
            return "image/jpeg";
        }
        byte[] png = new byte[]{
                (byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a};
        if (bytes.length >= png.length) {
            boolean matches = true;
            for (int i = 0; i < png.length; i++) {
                if (bytes[i] != png[i]) {
                    matches = false;
                    break;
                }
            }
            if (matches) {
                return "image/png";
            }
        }
        byte[] pdf = "%PDF-".getBytes(StandardCharsets.US_ASCII);
        if (bytes.length >= pdf.length) {
            boolean matches = true;
            for (int i = 0; i < pdf.length; i++) {
                if (bytes[i] != pdf[i]) {
                    matches = false;
                    break;
                }
            }
            if (matches) {
                return "application/pdf";
            }
        }
        throw new BadRequestException("Не удалось определить поддерживаемый тип файла");
    }

    private String excusePayloadHash(ExcuseSubmission command, ValidatedAttachments files) {
        StringBuilder canonical = new StringBuilder();
        appendCanonicalTag(canonical, "EXCUSE");
        normalizeLessonIds(command.lessonIds()).forEach(id ->
                appendCanonicalField(canonical, "lesson_id", Long.toString(id)));
        appendCanonicalField(canonical, "reason", command.reason().name());
        appendCanonicalField(canonical, "comment", normalizeComment(command.comment()));
        for (ValidatedAttachment file : files.items()) {
            appendCanonicalTag(canonical, "attachment");
            appendCanonicalField(canonical, "safe_name", file.safeName());
            appendCanonicalField(canonical, "content_type", file.detectedContentType());
            appendCanonicalField(canonical, "size", Long.toString(file.bytes().length));
            appendCanonicalField(canonical, "sha256", file.sha256());
        }
        return sha256(canonical.toString());
    }

    private static void appendCanonicalTag(StringBuilder canonical, String tag) {
        byte[] encodedTag = tag.getBytes(StandardCharsets.UTF_8);
        canonical.append('T').append(encodedTag.length).append(':').append(tag).append(';');
    }

    private static void appendCanonicalField(StringBuilder canonical, String tag, String value) {
        appendCanonicalTag(canonical, tag);
        if (value == null) {
            canonical.append("N;");
            return;
        }
        byte[] encodedValue = value.getBytes(StandardCharsets.UTF_8);
        canonical.append('V').append(encodedValue.length).append(':').append(value).append(';');
    }

    private static String latePayloadHash(long lessonId) {
        return sha256("LATE_CHECKIN\n" + lessonId);
    }

    private RequestDetail replay(Identity identity, long studentId, StudentRequestKind kind, String key,
                                 String payloadHash) {
        OptionalReceipt receipt = findReceipt(studentId, kind, key);
        return receipt.present() ? replayOrMismatch(identity, receipt.document(), payloadHash) : null;
    }

    private OptionalReceipt findReceipt(long studentId, StudentRequestKind kind, String key) {
        return new OptionalReceipt(receiptRepository
                .findByStudentIdAndCommandKindAndIdempotencyKey(studentId, kind.name(), key)
                .orElse(null));
    }

    private RequestDetail replayOrMismatch(Identity identity, StudentRequestReceiptDocument receipt,
                                           String payloadHash) {
        if (!Objects.equals(receipt.getPayloadHash(), payloadHash)) {
            throw new ConflictException("Idempotency-Key уже использован с другим запросом");
        }
        return get(identity, receipt.getRequestId());
    }

    private List<Map<String, Object>> lessonDetails(List<ResolvedLesson> lessons) {
        List<Map<String, Object>> details = new ArrayList<>();
        for (ResolvedLesson lesson : lessons) {
            Map<String, Object> detail = new LinkedHashMap<>();
            detail.put("lesson_id", lesson.lessonId());
            detail.put("lesson_number", lesson.lessonNumber());
            detail.put("date", lesson.date() == null ? null : lesson.date().toString());
            detail.put("subject_id", lesson.subjectId());
            detail.put("subject_name", lesson.subjectName());
            detail.put("subject_type", lesson.subjectType());
            details.add(detail);
        }
        return details;
    }

    private static List<Long> sortedLessonIds(Collection<Long> lessonIds) {
        return lessonIds == null ? List.of() : lessonIds.stream()
                .filter(Objects::nonNull).distinct().sorted().toList();
    }

    private static LessonSnapshot toSnapshot(LessonResponse response, long semesterId) {
        if (response == null || response.getId() <= 0 || response.getDate() == null
                || response.getDate().isBlank()) {
            return null;
        }
        LocalDate date = LocalDate.parse(response.getDate());
        return new LessonSnapshot(response.getId(), response.getGroupId(), response.getSubjectId(), null, null, semesterId,
                response.getLessonNumber(), date, parseTime(response.getStartTime(), null, false),
                parseTime(response.getEndTime(), null, true), normalizeStatus(response.getStatus()),
                response.getIsBlockedByHeadman());
    }

    private static String normalizeStatus(String status) {
        return status == null ? "" : status.toLowerCase(Locale.ROOT);
    }

    private static StudentLessonSnapshotDocument toDocument(LessonSnapshot lesson) {
        return StudentLessonSnapshotDocument.builder()
                .lessonId(lesson.lessonId()).groupId(lesson.groupId()).subjectId(lesson.subjectId())
                .subjectName(lesson.subjectName()).subjectType(lesson.subjectType())
                .semesterId(lesson.semesterId()).lessonNumber(lesson.lessonNumber()).date(lesson.date())
                .startsAt(lesson.startsAt()).endsAt(lesson.endsAt()).status(lesson.status())
                .blocked(lesson.blocked()).build();
    }

    private void expireOne(RequestAttachmentDocument document, Instant now) {
        if (document.getState() == AttachmentState.EXPIRED && document.getData() == null) {
            return;
        }
        document.setState(AttachmentState.EXPIRED);
        document.setExpiredAt(now);
        document.setData(null);
        attachmentRepository.save(document);
    }

    private void validatePage(int page, int size) {
        if (page < 0 || size < 1 || size > MAX_PAGE_SIZE) {
            throw new BadRequestException("Размер страницы должен быть 1.." + MAX_PAGE_SIZE);
        }
    }

    private static String valueAsString(Object value) {
        return value == null ? "" : value.toString();
    }

    private static String valueAsNullableString(Object value) {
        return value == null ? null : value.toString();
    }

    private static long numberAsLong(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value == null || value.toString().isBlank()) {
            return 0;
        }
        return Long.parseLong(value.toString());
    }

    private static int numberAsInt(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value == null || value.toString().isBlank()) {
            return 0;
        }
        return Integer.parseInt(value.toString());
    }

    private static Instant instant(Object value) {
        if (value instanceof java.util.Date date) {
            return date.toInstant();
        }
        if (value instanceof Instant result) {
            return result;
        }
        return value == null || value.toString().isBlank() ? null : Instant.parse(value.toString());
    }

    private static LocalDate localDate(Object value) {
        if (value instanceof LocalDate date) {
            return date;
        }
        if (value instanceof java.util.Date date) {
            return date.toInstant().atZone(ZoneOffset.UTC).toLocalDate();
        }
        if (value instanceof Instant instant) {
            return instant.atZone(ZoneOffset.UTC).toLocalDate();
        }
        return value == null || value.toString().isBlank() ? null : LocalDate.parse(value.toString());
    }

    private static LocalTime localTime(Object value) {
        if (value instanceof LocalTime time) {
            return time;
        }
        if (value == null || value.toString().isBlank()) {
            return null;
        }
        if (value instanceof java.util.Date date) {
            return date.toInstant().atZone(ZoneOffset.UTC).toLocalTime();
        }
        if (value instanceof Instant instant) {
            return instant.atZone(ZoneOffset.UTC).toLocalTime();
        }
        String string = value.toString();
        if (string.contains("T")) {
            string = string.substring(string.indexOf('T') + 1);
        }
        return LocalTime.parse(string);
    }

    private static String sha256(byte[] value) {
        return HexFormat.of().formatHex(digest(value));
    }

    private static String sha256(String value) {
        return sha256(value.getBytes(StandardCharsets.UTF_8));
    }

    private static byte[] digest(byte[] value) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(value);
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }

    private <T> T executeWithRetry(Supplier<T> action) {
        RuntimeException last = null;
        for (int attempt = 1; attempt <= MAX_TRANSACTION_ATTEMPTS; attempt++) {
            try {
                T value = action.get();
                if (value == null) {
                    throw new IllegalStateException("Student request transaction returned no result");
                }
                return value;
            } catch (RuntimeException error) {
                last = error;
                if (!isRetryable(error) || attempt == MAX_TRANSACTION_ATTEMPTS) {
                    throw error;
                }
                try {
                    Thread.sleep(RETRY_BACKOFF_MILLIS * attempt
                            + java.util.concurrent.ThreadLocalRandom.current()
                            .nextLong(RETRY_BACKOFF_MILLIS));
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    throw error;
                }
            }
        }
        throw last == null ? new IllegalStateException("Student request transaction did not execute") : last;
    }

    /**
     * Decision-only retry path.  An unknown commit result is first resolved by
     * the caller-specific exact-match recovery read; only an unresolved result
     * is retried as an ordinary transaction error.
     */
    private <T> T executeDecisionWithRetry(Supplier<T> action, Supplier<T> recovery) {
        RuntimeException last = null;
        for (int attempt = 1; attempt <= MAX_TRANSACTION_ATTEMPTS; attempt++) {
            try {
                T value = action.get();
                if (value == null) {
                    throw new IllegalStateException("Student request transaction returned no result");
                }
                return value;
            } catch (RuntimeException error) {
                last = error;
                if (isUnknownCommitResult(error)) {
                    T recovered = recovery.get();
                    if (recovered != null) {
                        return recovered;
                    }
                }
                if (!isRetryable(error) || attempt == MAX_TRANSACTION_ATTEMPTS) {
                    throw error;
                }
                try {
                    Thread.sleep(RETRY_BACKOFF_MILLIS * attempt
                            + java.util.concurrent.ThreadLocalRandom.current()
                            .nextLong(RETRY_BACKOFF_MILLIS));
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    throw error;
                }
            }
        }
        throw last == null ? new IllegalStateException("Student request transaction did not execute") : last;
    }

    private static boolean isUnknownCommitResult(Throwable error) {
        for (Throwable current = error; current != null; current = current.getCause()) {
            if (current instanceof MongoException mongo
                    && mongo.hasErrorLabel(MongoException.UNKNOWN_TRANSACTION_COMMIT_RESULT_LABEL)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isRetryable(Throwable error) {
        for (Throwable current = error; current != null; current = current.getCause()) {
            if (current instanceof DuplicateKeyException) {
                return true;
            }
            if (current instanceof MongoException mongo
                    && (mongo.hasErrorLabel(MongoException.TRANSIENT_TRANSACTION_ERROR_LABEL)
                    || mongo.hasErrorLabel(MongoException.UNKNOWN_TRANSACTION_COMMIT_RESULT_LABEL))) {
                return true;
            }
            if (current instanceof TransactionException
                    && current.getMessage() != null
                    && current.getMessage().contains("TransientTransactionError")) {
                return true;
            }
        }
        return false;
    }

    private record ResolvedLesson(
            long lessonId,
            long groupId,
            long subjectId,
            int lessonNumber,
            LocalDate date,
            String status,
            boolean blocked,
            String subjectName,
            String subjectType,
            StudentLessonSnapshotDocument document
    ) {
        private ResolvedLesson(long lessonId, long groupId, long subjectId, int lessonNumber,
                               LocalDate date, String status, boolean blocked,
                               StudentLessonSnapshotDocument document) {
            this(lessonId, groupId, subjectId, lessonNumber, date, status, blocked,
                    document == null ? null : document.getSubjectName(),
                    document == null ? null : document.getSubjectType(), document);
        }
    }

    private record ValidatedAttachment(
            String safeName,
            String detectedContentType,
            byte[] bytes,
            String sha256
    ) {
    }

    private record ValidatedAttachments(List<ValidatedAttachment> items) {
    }

    private record OptionalReceipt(StudentRequestReceiptDocument document) {
        boolean present() {
            return document != null;
        }
    }
}
