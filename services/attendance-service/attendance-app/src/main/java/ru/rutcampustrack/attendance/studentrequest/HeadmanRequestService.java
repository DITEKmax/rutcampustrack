package ru.rutcampustrack.attendance.studentrequest;

import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Service;
import ru.rutcampustrack.attendance.checkin.AttendanceDocument;
import ru.rutcampustrack.attendance.checkin.AttendanceRepository;
import ru.rutcampustrack.attendance.contract.dto.headman.HeadmanRequestAttachmentResponse;
import ru.rutcampustrack.attendance.contract.dto.headman.HeadmanRequestDecision;
import ru.rutcampustrack.attendance.contract.dto.headman.HeadmanRequestDecisionRequest;
import ru.rutcampustrack.attendance.contract.dto.headman.HeadmanRequestDetailResponse;
import ru.rutcampustrack.attendance.contract.dto.headman.HeadmanRequestLessonResponse;
import ru.rutcampustrack.attendance.contract.dto.headman.HeadmanRequestPageResponse;
import ru.rutcampustrack.attendance.contract.dto.headman.HeadmanRequestSummaryResponse;
import ru.rutcampustrack.attendance.contract.enums.AttendanceStatus;
import ru.rutcampustrack.attendance.contract.enums.ExcuseTicketStatus;
import ru.rutcampustrack.attendance.contract.enums.LateCheckinRequestStatus;
import ru.rutcampustrack.attendance.contract.enums.StudentRequestKind;
import ru.rutcampustrack.attendance.exception.BadRequestException;
import ru.rutcampustrack.attendance.excuse.ExcuseRepository;
import ru.rutcampustrack.attendance.excuse.entity.ExcuseTicket;
import ru.rutcampustrack.attendance.latecheckin.LateCheckinRepository;
import ru.rutcampustrack.attendance.latecheckin.entity.LateCheckinRequest;
import ru.rutcampustrack.attendance.security.RequestContext;
import ru.rutcampustrack.attendance.studentrequest.StudentRequestModels.AttachmentDownload;
import ru.rutcampustrack.attendance.studentrequest.StudentRequestModels.Identity;
import ru.rutcampustrack.attendance.studentrequest.entity.RequestAttachmentDescriptorDocument;
import ru.rutcampustrack.attendance.studentrequest.entity.RequestAttachmentDocument;
import ru.rutcampustrack.attendance.studentrequest.entity.StudentLessonSnapshotDocument;
import ru.rutcampustrack.attendance.contract.exception.ResourceNotFoundException;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Headman read/transport adapter for the two persisted request collections.
 * Decisions stay in {@link StudentRequestService}; this class only builds the
 * unified read projection and converts the authenticated command.
 */
@Service
public class HeadmanRequestService {

    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 100;

    private final ExcuseRepository excuseRepository;
    private final LateCheckinRepository lateCheckinRepository;
    private final AttendanceRepository attendanceRepository;
    private final RequestAttachmentRepository attachmentRepository;
    private final StudentRequestService studentRequestService;
    private final MongoTemplate mongoTemplate;
    private final Clock clock;

    public HeadmanRequestService(
            ExcuseRepository excuseRepository,
            LateCheckinRepository lateCheckinRepository,
            AttendanceRepository attendanceRepository,
            RequestAttachmentRepository attachmentRepository,
            StudentRequestService studentRequestService,
            MongoTemplate mongoTemplate,
            Clock clock
    ) {
        this.excuseRepository = excuseRepository;
        this.lateCheckinRepository = lateCheckinRepository;
        this.attendanceRepository = attendanceRepository;
        this.attachmentRepository = attachmentRepository;
        this.studentRequestService = studentRequestService;
        this.mongoTemplate = mongoTemplate;
        this.clock = clock;
    }

    public HeadmanRequestPageResponse list(Identity identity, String bucket, int page, int size,
                                           String type, String studentName,
                                           String coverageDateFrom, String coverageDateTo) {
        long groupId = requireListAuthority(identity);
        QueryOptions options = QueryOptions.parse(bucket, page, size, type, studentName,
                coverageDateFrom, coverageDateTo, groupId, mongoTemplate);

        List<Document> countPipeline = unionPipeline(options, false);
        countPipeline.add(new Document("$count", "total"));
        Document count = firstDocument(mongoTemplate.getCollection("excuse_tickets").aggregate(countPipeline));
        long total = count == null ? 0L : numberAsLong(count.get("total"));

        List<Document> dataPipeline = unionPipeline(options, true);
        dataPipeline.add(new Document("$skip", (long) options.page() * options.size()));
        dataPipeline.add(new Document("$limit", options.size()));
        List<HeadmanRequestSummaryResponse> content = new ArrayList<>();
        for (Document document : mongoTemplate.getCollection("excuse_tickets").aggregate(dataPipeline)) {
            content.add(summaryFromDocument(document));
        }
        int totalPages = total == 0 ? 0 : (int) ((total + options.size() - 1) / options.size());
        return new HeadmanRequestPageResponse(content, options.page(), options.size(), total, totalPages);
    }

    public HeadmanRequestDetailResponse get(Identity identity, String requestId) {
        Loaded loaded = load(requestId);
        requireHeadmanAuthority(identity, loaded.groupId());
        return detail(loaded);
    }

    public HeadmanRequestDetailResponse decide(Identity identity, String requestId,
                                               HeadmanRequestDecisionRequest command) {
        if (command == null || command.decision() == null) {
            throw new BadRequestException("Решение обязательно");
        }
        String reason = normalize(command.reason());
        if (command.decision() == HeadmanRequestDecision.REJECTED && reason == null) {
            throw new BadRequestException("Для отклонения нужно указать причину");
        }

        Loaded loaded = load(requestId);
        requireHeadmanAuthority(identity, loaded.groupId());
        boolean approved = command.decision() == HeadmanRequestDecision.APPROVED;
        if (loaded.kind() == StudentRequestKind.EXCUSE) {
            studentRequestService.decideExcuse(identity, requestId, approved, reason);
        } else {
            studentRequestService.decideLateCheckin(identity, requestId, approved, reason);
        }
        // Re-read the durable projection.  The list preview is never used as
        // a mutation filter, and the canonical service re-checks every pair.
        return get(identity, requestId);
    }

    public AttachmentDownload downloadAttachment(Identity identity, String requestId, String attachmentId) {
        Loaded loaded = load(requestId);
        requireHeadmanAuthority(identity, loaded.groupId());
        if (loaded.kind() != StudentRequestKind.EXCUSE) {
            throw new ResourceNotFoundException("Attachment", "id", attachmentId);
        }
        return studentRequestService.fetchExcuseAttachmentForBot(identity.userId(), requestId, attachmentId);
    }

    private long requireListAuthority(Identity identity) {
        if (identity == null || identity.groupId() == null || identity.groupId() <= 0) {
            throw new BadRequestException("Группа authenticated headman недоступна");
        }
        requireHeadmanAuthority(identity, identity.groupId());
        return identity.groupId();
    }

    private void requireHeadmanAuthority(Identity identity, Long groupId) {
        studentRequestService.requireHeadmanAuthority(identity, groupId);
    }

    private Loaded load(String requestId) {
        if (requestId == null || requestId.isBlank()) {
            throw new BadRequestException("id заявки обязателен");
        }
        ExcuseTicket excuse = excuseRepository.findById(requestId).orElse(null);
        if (excuse != null) {
            return new Loaded(StudentRequestKind.EXCUSE, excuse.getGroupId(), excuse, null);
        }
        LateCheckinRequest late = lateCheckinRepository.findById(requestId).orElse(null);
        if (late != null) {
            return new Loaded(StudentRequestKind.LATE_CHECKIN, late.getGroupId(), null, late);
        }
        throw new ResourceNotFoundException("StudentRequest", "id", requestId);
    }

    private HeadmanRequestDetailResponse detail(Loaded loaded) {
        if (loaded.kind() == StudentRequestKind.EXCUSE) {
            List<HeadmanRequestLessonResponse> lessons = excuseLessons(loaded.excuse());
            return new HeadmanRequestDetailResponse(summary(loaded.excuse(), lessons), lessons,
                    excuseAttachments(loaded.excuse()));
        }
        List<HeadmanRequestLessonResponse> lessons = lateLessons(loaded.late());
        return new HeadmanRequestDetailResponse(summary(loaded.late(), lessons), lessons, List.of());
    }

    private HeadmanRequestSummaryResponse summaryFromDocument(Document document) {
        StudentRequestKind kind = StudentRequestKind.valueOf(valueAsString(document.get("request_kind")));
        List<HeadmanRequestLessonResponse> lessons = documentLessons(document, kind);
        Long studentId = nullableLong(document.get("student_id"));
        return summary(
                valueAsString(document.get("_id")),
                kind,
                valueAsString(document.get("status")),
                studentId,
                valueAsString(document.get("student_name")),
                kind == StudentRequestKind.EXCUSE ? valueAsString(document.get("excuse_type")) : "LATE_CHECKIN",
                kind == StudentRequestKind.EXCUSE ? valueAsString(document.get("comment")) : null,
                lessons,
                booleanCollection(document.get("attachment_descriptors")),
                instant(document.get("created_at")),
                instant(document.get("updated_at")),
                nullableLong(document.get("decision_by")),
                instant(document.get("decision_at")),
                valueAsString(document.get("decision_comment")));
    }

    private HeadmanRequestSummaryResponse summary(ExcuseTicket ticket,
                                                  List<HeadmanRequestLessonResponse> lessons) {
        return summary(ticket.getId(), StudentRequestKind.EXCUSE, mapStatus(ticket.getStatus()),
                ticket.getStudentId(), ticket.getStudentName(),
                ticket.getExcuseType() == null ? null : ticket.getExcuseType().name(), ticket.getComment(), lessons,
                ticket.getAttachmentDescriptors() != null && !ticket.getAttachmentDescriptors().isEmpty(),
                ticket.getCreatedAt(), ticket.getUpdatedAt(), ticket.getDecisionBy(), ticket.getDecisionAt(),
                ticket.getDecisionComment());
    }

    private HeadmanRequestSummaryResponse summary(LateCheckinRequest request,
                                                  List<HeadmanRequestLessonResponse> lessons) {
        return summary(request.getId(), StudentRequestKind.LATE_CHECKIN, mapStatus(request.getStatus()),
                request.getStudentId(), request.getStudentName(), "LATE_CHECKIN", null, lessons, false,
                request.getCreatedAt(), request.getUpdatedAt(), request.getDecisionBy(), request.getDecisionAt(),
                request.getDecisionComment());
    }

    private HeadmanRequestSummaryResponse summary(String id, StudentRequestKind kind, String status,
                                                  Long studentId, String studentName, String reason, String comment,
                                                  List<HeadmanRequestLessonResponse> lessons, boolean hasAttachments,
                                                  Instant createdAt, Instant updatedAt, Long decisionBy,
                                                  Instant decisionAt, String decisionComment) {
        List<LocalDate> dates = lessons.stream().map(HeadmanRequestLessonResponse::date)
                .filter(Objects::nonNull).toList();
        int alreadyMarked = 0;
        if (studentId != null) {
            alreadyMarked = (int) lessons.stream()
                    .map(HeadmanRequestLessonResponse::lessonId)
                    .filter(Objects::nonNull)
                    .map(idValue -> attendanceRepository.findByLessonIdAndUserId(idValue, studentId).orElse(null))
                    .filter(document -> document != null && document.getStatus() == AttendanceStatus.PRESENT)
                    .count();
        }
        return new HeadmanRequestSummaryResponse(id, kind.name(), status, studentId, studentName, reason, comment,
                dates.stream().min(Comparator.naturalOrder()).orElse(null),
                dates.stream().max(Comparator.naturalOrder()).orElse(null), lessons.size(), alreadyMarked,
                hasAttachments, createdAt, updatedAt, decisionBy, decisionAt, decisionComment);
    }

    private List<HeadmanRequestLessonResponse> excuseLessons(ExcuseTicket ticket) {
        List<HeadmanRequestLessonResponse> lessons = new ArrayList<>();
        if (ticket.getLessonSnapshots() != null) {
            for (StudentLessonSnapshotDocument snapshot : ticket.getLessonSnapshots()) {
                lessons.add(lesson(snapshot, ticket.getStudentId()));
            }
        }
        if (lessons.isEmpty() && ticket.getLessonIds() != null) {
            for (Long lessonId : ticket.getLessonIds()) {
                lessons.add(lesson(lessonId, ticket.getGroupId(), ticket.getSemesterId(), ticket.getStudentId()));
            }
        }
        return List.copyOf(lessons);
    }

    private List<HeadmanRequestLessonResponse> lateLessons(LateCheckinRequest request) {
        return List.of(lesson(request.getLessonId(), request.getGroupId(), request.getSubjectId(),
                request.getSubjectName(), request.getSubjectType(), request.getSemesterId(),
                request.getLessonNumber(), request.getLessonDate(), "closed", request.getStudentId()));
    }

    private HeadmanRequestLessonResponse lesson(StudentLessonSnapshotDocument snapshot, Long studentId) {
        return lesson(snapshot.getLessonId(), snapshot.getGroupId(), snapshot.getSubjectId(), snapshot.getSubjectName(),
                snapshot.getSubjectType(), snapshot.getSemesterId(), snapshot.getLessonNumber(), snapshot.getDate(),
                snapshot.getStatus(), studentId, snapshot.getStartsAt(), snapshot.getEndsAt());
    }

    private HeadmanRequestLessonResponse lesson(Long lessonId, Long groupId, Long semesterId, Long studentId) {
        return lesson(lessonId, groupId, null, null, null, semesterId, null, null, "unknown", studentId, null, null);
    }

    private HeadmanRequestLessonResponse lesson(Long lessonId, Long groupId, Long subjectId, String subjectName,
                                                String subjectType, Long semesterId, Integer lessonNumber,
                                                LocalDate date, String requestStatus, Long studentId) {
        return lesson(lessonId, groupId, subjectId, subjectName, subjectType, semesterId, lessonNumber, date,
                requestStatus, studentId, null, null);
    }

    private HeadmanRequestLessonResponse lesson(Long lessonId, Long groupId, Long subjectId, String subjectName,
                                                String subjectType, Long semesterId, Integer lessonNumber,
                                                LocalDate date, String requestStatus, Long studentId,
                                                LocalTime startsAt, LocalTime endsAt) {
        AttendanceDocument attendance = lessonId == null || studentId == null
                ? null : attendanceRepository.findByLessonIdAndUserId(lessonId, studentId).orElse(null);
        return new HeadmanRequestLessonResponse(lessonId, groupId, subjectId, subjectName, subjectType, semesterId,
                lessonNumber, date, startsAt, endsAt, requestStatus,
                attendance == null || attendance.getStatus() == null ? null : attendance.getStatus().name(),
                attendance == null || attendance.getSource() == null ? null : attendance.getSource().name());
    }

    private List<HeadmanRequestLessonResponse> documentLessons(Document document, StudentRequestKind kind) {
        List<HeadmanRequestLessonResponse> lessons = new ArrayList<>();
        Long studentId = nullableLong(document.get("student_id"));
        if (kind == StudentRequestKind.EXCUSE) {
            Object raw = document.get("lesson_snapshots");
            if (raw instanceof Collection<?> collection) {
                for (Object item : collection) {
                    Document snapshot = asDocument(item);
                    if (snapshot != null) {
                        lessons.add(lesson(nullableLong(snapshot.get("lesson_id")), nullableLong(snapshot.get("group_id")),
                                nullableLong(snapshot.get("subject_id")), valueAsString(snapshot.get("subject_name")),
                                valueAsString(snapshot.get("subject_type")), nullableLong(snapshot.get("semester_id")),
                                nullableInt(snapshot.get("lesson_number")), localDate(snapshot.get("date")),
                                valueAsString(snapshot.get("status")), studentId, localTime(snapshot.get("starts_at")),
                                localTime(snapshot.get("ends_at"))));
                    }
                }
            }
            if (lessons.isEmpty() && document.get("lesson_ids") instanceof Collection<?> ids) {
                for (Object id : ids) {
                    lessons.add(lesson(nullableLong(id), nullableLong(document.get("group_id")),
                            nullableLong(document.get("semester_id")), studentId));
                }
            }
        } else {
            lessons.add(lesson(nullableLong(document.get("late_lesson_id")), nullableLong(document.get("late_group_id")),
                    nullableLong(document.get("late_subject_id")), valueAsString(document.get("late_subject_name")),
                    valueAsString(document.get("late_subject_type")), nullableLong(document.get("late_semester_id")),
                    nullableInt(document.get("late_lesson_number")), localDate(document.get("late_lesson_date")),
                    "closed", studentId));
        }
        return List.copyOf(lessons);
    }

    private List<HeadmanRequestAttachmentResponse> excuseAttachments(ExcuseTicket ticket) {
        List<RequestAttachmentDocument> stored = attachmentRepository
                .findByRequestIdAndOwnerStudentIdOrderByPositionAsc(ticket.getId(), ticket.getStudentId());
        if (!stored.isEmpty()) {
            return stored.stream().map(document -> attachment(document, ticket.getId())).toList();
        }
        if (ticket.getAttachmentDescriptors() == null) {
            return List.of();
        }
        return ticket.getAttachmentDescriptors().stream()
                .map(descriptor -> attachment(descriptor, ticket.getId())).toList();
    }

    private HeadmanRequestAttachmentResponse attachment(RequestAttachmentDocument document, String requestId) {
        String state = document.getState() == null ? null : document.getState().name();
        if (document.getExpiresAt() != null && !clock.instant().isBefore(document.getExpiresAt())) {
            state = "EXPIRED";
        }
        return new HeadmanRequestAttachmentResponse(document.getId(), document.getName(), document.getContentType(),
                document.getSize() == null ? 0L : document.getSize(), document.getSha256(), state,
                document.getUploadedAt(), document.getExpiresAt(), attachmentUrl(requestId, document.getId()));
    }

    private HeadmanRequestAttachmentResponse attachment(RequestAttachmentDescriptorDocument descriptor,
                                                        String requestId) {
        return new HeadmanRequestAttachmentResponse(descriptor.getId(), descriptor.getName(),
                descriptor.getContentType(), descriptor.getSize() == null ? 0L : descriptor.getSize(),
                descriptor.getSha256(), descriptor.getState() == null ? null : descriptor.getState().name(),
                descriptor.getUploadedAt(), descriptor.getExpiresAt(), attachmentUrl(requestId, descriptor.getId()));
    }

    private static String attachmentUrl(String requestId, String attachmentId) {
        return "/api/attendance/requests/" + requestId + "/attachments/" + attachmentId;
    }

    private List<Document> unionPipeline(QueryOptions options, boolean sort) {
        boolean includeExcuse = options.type() == null || options.type() == StudentRequestKind.EXCUSE;
        boolean includeLate = options.type() == null || options.type() == StudentRequestKind.LATE_CHECKIN;
        List<Document> pipeline = new ArrayList<>();
        Document excuseMatch = new Document("group_id", options.groupId());
        if (!includeExcuse) {
            excuseMatch.append("_id", new Document("$exists", false));
        }
        appendStatus(excuseMatch, options.bucket(), "status");
        appendSnapshotDateFilter(excuseMatch, options);
        pipeline.add(new Document("$match", excuseMatch));
        pipeline.add(new Document("$project", new Document("_id", 1)
                .append("request_kind", "EXCUSE")
                .append("status", normalizedStatus("status"))
                .append("student_id", 1).append("student_name", 1)
                .append("excuse_type", 1).append("comment", 1)
                .append("lesson_snapshots", 1).append("lesson_ids", 1).append("group_id", 1)
                .append("semester_id", 1).append("attachment_descriptors", 1)
                .append("decision_by", 1).append("decision_at", 1).append("decision_comment", 1)
                .append("created_at", 1).append("updated_at", 1)));
        if (includeLate) {
            pipeline.add(new Document("$unionWith", new Document("coll", "late_checkin_requests")
                    .append("pipeline", latePipeline(options))));
        }
        if (options.studentName() != null) {
            pipeline.add(new Document("$match", new Document("student_name",
                    new Document("$regex", Pattern.quote(options.studentName()))
                            .append("$options", "i"))));
        }
        if (sort) {
            String primaryTime = options.bucket() == QueryBucket.ARCHIVE ? "decision_at" : "created_at";
            pipeline.add(new Document("$sort", new Document(primaryTime, -1)
                    .append("request_kind", 1).append("_id", 1)));
        }
        return pipeline;
    }

    private List<Document> latePipeline(QueryOptions options) {
        Document match = new Document("group_id", options.groupId());
        appendStatus(match, options.bucket(), "status");
        appendDateFilter(match, "lesson_date", options);
        List<Document> pipeline = new ArrayList<>();
        pipeline.add(new Document("$match", match));
        pipeline.add(new Document("$project", new Document("_id", 1)
                .append("request_kind", "LATE_CHECKIN")
                .append("status", normalizedStatus("status"))
                .append("student_id", 1).append("student_name", 1)
                .append("decision_by", 1).append("decision_at", 1).append("decision_comment", 1)
                .append("late_lesson_id", "$lesson_id").append("late_group_id", "$group_id")
                .append("late_subject_id", "$subject_id").append("late_subject_name", "$subject_name")
                .append("late_subject_type", "$subject_type").append("late_semester_id", "$semester_id")
                .append("late_lesson_number", "$lesson_number").append("late_lesson_date", "$lesson_date")
                .append("created_at", 1).append("updated_at", 1)));
        return pipeline;
    }

    private static void appendStatus(Document match, QueryBucket bucket, String field) {
        List<String> statuses = bucket == QueryBucket.OPEN
                ? List.of("SUBMITTED", "PENDING", "submitted", "pending")
                : List.of("APPROVED", "REJECTED", "CANCELLED", "approved", "rejected", "cancelled");
        match.append(field, new Document("$in", statuses));
    }

    private void appendSnapshotDateFilter(Document match, QueryOptions options) {
        if (options.coverageDateFrom() == null && options.coverageDateTo() == null) {
            return;
        }
        List<Document> overlap = new ArrayList<>(2);
        if (options.coverageDateFrom() != null) {
            Document maximum = coverageExtreme(false);
            Document knownMaximum = new Document("$ne", Arrays.asList(maximum, null));
            Document fromBound = new Document("$gte",
                    Arrays.asList(maximum, mongoDate(options.coverageDateFrom())));
            overlap.add(new Document("$and", Arrays.asList(knownMaximum, fromBound)));
        }
        if (options.coverageDateTo() != null) {
            Document minimum = coverageExtreme(true);
            Document knownMinimum = new Document("$ne", Arrays.asList(minimum, null));
            Document toBound = new Document("$lte",
                    Arrays.asList(minimum, mongoDate(options.coverageDateTo())));
            overlap.add(new Document("$and", Arrays.asList(knownMinimum, toBound)));
        }
        match.append("$expr", overlap.size() == 1
                ? overlap.getFirst()
                : new Document("$and", overlap));
    }

    /**
     * Reduce embedded snapshot dates instead of relying on Mongo's single
     * expression $min/$max array traversal.  The accumulator remains a BSON
     * date, and null/missing snapshot dates are ignored.
     */
    private static Document coverageExtreme(boolean minimum) {
        String comparator = minimum ? "$lt" : "$gt";
        String currentDate = "$$this.date";
        Document replace = new Document("$and", List.of(
                new Document("$ne", Arrays.asList(currentDate, null)),
                new Document("$or", List.of(
                        new Document("$eq", Arrays.asList("$$value", null)),
                        new Document(comparator, List.of(currentDate, "$$value"))))));
        return new Document("$reduce", new Document("input",
                new Document("$ifNull", List.of("$lesson_snapshots", List.of())))
                .append("initialValue", null)
                .append("in", new Document("$cond", List.of(replace, currentDate, "$$value"))));
    }

    private static Document normalizedStatus(String field) {
        String reference = "$" + field;
        return new Document("$switch", new Document("branches", List.of(
                new Document("case", new Document("$in", List.of(reference,
                        List.of("SUBMITTED", "DRAFT", "submitted", "draft"))))
                        .append("then", "PENDING")
        )).append("default", new Document("$toUpper", reference)));
    }

    private void appendDateFilter(Document match, String field, QueryOptions options) {
        if (options.coverageDateFrom() != null || options.coverageDateTo() != null) {
            match.append(field, dateRange(options));
        }
    }

    private Document dateRange(QueryOptions options) {
        Document range = new Document();
        if (options.coverageDateFrom() != null) {
            range.append("$gte", mongoDate(options.coverageDateFrom()));
        }
        if (options.coverageDateTo() != null) {
            range.append("$lte", mongoDate(options.coverageDateTo()));
        }
        return range;
    }

    private Object mongoDate(LocalDate date) {
        Object converted = mongoTemplate.getConverter().convertToMongoType(date);
        return converted == null ? date.toString() : converted;
    }

    private static String mapStatus(ExcuseTicketStatus status) {
        if (status == null || status == ExcuseTicketStatus.SUBMITTED || status == ExcuseTicketStatus.DRAFT) {
            return "PENDING";
        }
        return status.name();
    }

    private static String mapStatus(LateCheckinRequestStatus status) {
        return status == null ? "PENDING" : status.name();
    }

    private static String normalize(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.strip();
        return normalized.isEmpty() ? null : normalized;
    }

    private static Document firstDocument(Iterable<Document> documents) {
        for (Document document : documents) {
            return document;
        }
        return null;
    }

    private static String valueAsString(Object value) {
        return value == null ? null : value.toString();
    }

    private static long numberAsLong(Object value) {
        Long number = nullableLong(value);
        return number == null ? 0L : number;
    }

    private static Long nullableLong(Object value) {
        if (value == null) return null;
        if (value instanceof Number number) return number.longValue();
        try { return Long.parseLong(value.toString()); } catch (NumberFormatException ignored) { return null; }
    }

    private static Integer nullableInt(Object value) {
        Long number = nullableLong(value);
        return number == null ? null : number.intValue();
    }

    private static Instant instant(Object value) {
        if (value instanceof Instant instant) return instant;
        if (value instanceof Date date) return date.toInstant();
        if (value instanceof java.time.LocalDateTime localDateTime) return localDateTime.toInstant(ZoneOffset.UTC);
        if (value == null) return null;
        try { return Instant.parse(value.toString()); } catch (RuntimeException ignored) { return null; }
    }

    private static LocalDate localDate(Object value) {
        if (value instanceof LocalDate date) return date;
        Instant instant = instant(value);
        if (instant != null) return instant.atZone(ZoneOffset.UTC).toLocalDate();
        if (value == null) return null;
        try { return LocalDate.parse(value.toString()); } catch (RuntimeException ignored) { return null; }
    }

    private static LocalTime localTime(Object value) {
        if (value instanceof LocalTime time) return time;
        if (value == null) return null;
        try { return LocalTime.parse(value.toString()); } catch (RuntimeException ignored) { return null; }
    }

    private static Document asDocument(Object value) {
        if (value instanceof Document document) return document;
        if (value instanceof Map<?, ?> map) {
            Document document = new Document();
            map.forEach((key, item) -> document.append(String.valueOf(key), item));
            return document;
        }
        return null;
    }

    private static boolean booleanCollection(Object value) {
        return value instanceof Collection<?> collection && !collection.isEmpty();
    }

    private record Loaded(StudentRequestKind kind, Long groupId, ExcuseTicket excuse, LateCheckinRequest late) {
    }

    private enum QueryBucket { OPEN, ARCHIVE }

    private record QueryOptions(QueryBucket bucket, int page, int size, StudentRequestKind type,
                                String studentName, LocalDate coverageDateFrom, LocalDate coverageDateTo,
                                long groupId) {
        private static QueryOptions parse(String rawBucket, int page, int size, String rawType,
                                          String rawStudentName, String rawFrom, String rawTo, long groupId,
                                          MongoTemplate mongoTemplate) {
            QueryBucket bucket;
            try { bucket = QueryBucket.valueOf(rawBucket == null ? "OPEN" : rawBucket.toUpperCase(Locale.ROOT)); }
            catch (RuntimeException exception) { throw new BadRequestException("bucket должен быть OPEN или ARCHIVE"); }
            if (page < 0 || size < 1 || size > MAX_PAGE_SIZE) {
                throw new BadRequestException("Некорректные page/size");
            }
            StudentRequestKind type = null;
            if (rawType != null && !rawType.isBlank()) {
                try { type = StudentRequestKind.valueOf(rawType.toUpperCase(Locale.ROOT)); }
                catch (RuntimeException exception) { throw new BadRequestException("type должен быть EXCUSE или LATE_CHECKIN"); }
            }
            LocalDate from = parseDate(rawFrom, "coverageDateFrom");
            LocalDate to = parseDate(rawTo, "coverageDateTo");
            if (from != null && to != null && from.isAfter(to)) {
                throw new BadRequestException("coverageDateFrom не может быть позже coverageDateTo");
            }
            String studentName = normalize(rawStudentName);
            return new QueryOptions(bucket, page, size, type, studentName, from, to, groupId);
        }

        private static LocalDate parseDate(String value, String field) {
            if (value == null || value.isBlank()) return null;
            try { return LocalDate.parse(value); }
            catch (RuntimeException exception) { throw new BadRequestException(field + " должен быть YYYY-MM-DD"); }
        }
    }
}
