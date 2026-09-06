package ru.rutcampustrack.attendance.student;

import com.mongodb.MongoException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.TransactionException;
import org.springframework.transaction.support.TransactionTemplate;
import ru.rutcampustrack.attendance.checkin.AttendanceDocument;
import ru.rutcampustrack.attendance.checkin.AttendanceRepository;
import ru.rutcampustrack.attendance.contract.enums.AttendanceSource;
import ru.rutcampustrack.attendance.contract.enums.AttendanceStatus;
import ru.rutcampustrack.attendance.contract.enums.LateCheckinRequestOrigin;
import ru.rutcampustrack.attendance.contract.enums.LateCheckinRequestStatus;
import ru.rutcampustrack.attendance.contract.enums.LateCheckinResolutionReason;
import ru.rutcampustrack.attendance.event.AttendanceEventPublisher;
import ru.rutcampustrack.attendance.geofence.GeofenceService;
import ru.rutcampustrack.attendance.latecheckin.LateCheckinEventPublisher;
import ru.rutcampustrack.attendance.latecheckin.LateCheckinRepository;
import ru.rutcampustrack.attendance.latecheckin.entity.LateCheckinRequest;
import ru.rutcampustrack.attendance.student.StudentCheckinException.Code;
import ru.rutcampustrack.attendance.student.StudentCheckinModels.Ack;
import ru.rutcampustrack.attendance.student.StudentCheckinModels.Attendance;
import ru.rutcampustrack.attendance.student.StudentCheckinModels.Coordinates;
import ru.rutcampustrack.attendance.student.StudentCheckinModels.Geo;
import ru.rutcampustrack.attendance.student.StudentCheckinModels.Identity;
import ru.rutcampustrack.attendance.student.StudentCheckinModels.Lesson;
import ru.rutcampustrack.attendance.student.StudentCheckinModels.Outcome;
import ru.rutcampustrack.attendance.student.StudentCheckinModels.Request;
import ru.rutcampustrack.attendance.student.StudentCheckinModels.Resolution;
import ru.rutcampustrack.shared.observability.BusinessMetrics;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;

@Service
public class StudentCheckinService {

    static final Duration CHECKIN_BUFFER = Duration.ofMinutes(5);
    static final Duration COOLDOWN = Duration.ofMinutes(5);
    private static final int MAX_TRANSACTION_ATTEMPTS = 4;

    private final AttendanceRepository attendanceRepository;
    private final CheckinPairStateRepository pairRepository;
    private final StudentCheckinReceiptRepository receiptRepository;
    private final LateCheckinRepository lateCheckinRepository;
    private final PairWriteCoordinator pairCoordinator;
    private final GeofenceService geofenceService;
    private final AttendanceEventPublisher attendanceEventPublisher;
    private final LateCheckinEventPublisher lateCheckinEventPublisher;
    private final BusinessMetrics businessMetrics;
    private final TransactionTemplate transactionTemplate;
    private final Clock clock;

    public StudentCheckinService(
            AttendanceRepository attendanceRepository,
            CheckinPairStateRepository pairRepository,
            StudentCheckinReceiptRepository receiptRepository,
            LateCheckinRepository lateCheckinRepository,
            PairWriteCoordinator pairCoordinator,
            GeofenceService geofenceService,
            AttendanceEventPublisher attendanceEventPublisher,
            LateCheckinEventPublisher lateCheckinEventPublisher,
            BusinessMetrics businessMetrics,
            TransactionTemplate transactionTemplate,
            Clock clock
    ) {
        this.attendanceRepository = attendanceRepository;
        this.pairRepository = pairRepository;
        this.receiptRepository = receiptRepository;
        this.lateCheckinRepository = lateCheckinRepository;
        this.pairCoordinator = pairCoordinator;
        this.geofenceService = geofenceService;
        this.attendanceEventPublisher = attendanceEventPublisher;
        this.lateCheckinEventPublisher = lateCheckinEventPublisher;
        this.businessMetrics = businessMetrics;
        this.transactionTemplate = transactionTemplate;
        this.clock = clock;
    }

    public Ack checkin(Identity identity, Lesson lesson, String idempotencyKey, Geo geo) {
        validateIdentityAndPayload(identity, lesson == null ? 0 : lesson.id(), idempotencyKey, geo);
        Ack replay = replay(identity, lesson.id(), idempotencyKey, geo);
        if (replay != null) return replay;
        validateDynamicEligibility(identity, lesson);
        Instant eligibilityAt = clock.instant();
        validateWindow(lesson, eligibilityAt, clock);
        Instant acceptedAt = eligibilityAt.truncatedTo(ChronoUnit.MILLIS);
        String payloadHash = sha256(lesson.id() + "\n" + geo.canonicalValue());

        RuntimeException last = null;
        for (int attempt = 1; attempt <= MAX_TRANSACTION_ATTEMPTS; attempt++) {
            try {
                Ack result = transactionTemplate.execute(status ->
                        executeTransaction(identity, lesson, idempotencyKey, payloadHash, geo, acceptedAt));
                if (result == null) {
                    throw new IllegalStateException("Mongo transaction returned no check-in result");
                }
                return result;
            } catch (RuntimeException error) {
                last = error;
                if (error instanceof StudentCheckinException || !isRetryable(error)
                        || attempt == MAX_TRANSACTION_ATTEMPTS) {
                    throw error;
                }
            }
        }
        throw last == null ? new IllegalStateException("Check-in transaction did not execute") : last;
    }

    /** Immutable receipt lookup deliberately precedes schedule/status/window reads. */
    public Ack replay(Identity identity, long lessonId, String idempotencyKey, Geo geo) {
        validateIdentityAndPayload(identity, lessonId, idempotencyKey, geo);
        String payloadHash = sha256(lessonId + "\n" + geo.canonicalValue());
        return receiptRepository.findByStudentIdAndLessonIdAndIdempotencyKey(
                        identity.userId(), lessonId, idempotencyKey)
                .map(receipt -> {
                    if (!receipt.getPayloadHash().equals(payloadHash)) {
                        throw new StudentCheckinException(Code.IDEMPOTENCY_PAYLOAD_MISMATCH,
                                "Idempotency-Key уже использован с другим запросом");
                    }
                    return fromReceipt(receipt);
                })
                .orElse(null);
    }

    private Ack executeTransaction(
            Identity identity,
            Lesson lesson,
            String idempotencyKey,
            String payloadHash,
            Geo geo,
            Instant acceptedAt
    ) {
        var previousReceipt = receiptRepository.findByStudentIdAndLessonIdAndIdempotencyKey(
                identity.userId(), lesson.id(), idempotencyKey);
        if (previousReceipt.isPresent()) {
            if (!previousReceipt.get().getPayloadHash().equals(payloadHash)) {
                throw new StudentCheckinException(Code.IDEMPOTENCY_PAYLOAD_MISMATCH,
                        "Idempotency-Key уже использован с другим запросом");
            }
            return fromReceipt(previousReceipt.get());
        }

        AttendanceDocument current = attendanceRepository
                .findByLessonIdAndUserId(lesson.id(), identity.userId())
                .orElse(null);
        if (isManualHeadmanAbsence(current)) {
            throw new StudentCheckinException(Code.MANUAL_ABSENCE_REQUIRES_APPEAL,
                    "Ручная отметка «н» оспаривается отдельной заявкой");
        }
        if (current != null && current.getStatus() == AttendanceStatus.PRESENT) {
            return presentAck(current, lesson.id(), acceptedAt);
        }

        pairCoordinator.lock(identity.userId(), lesson.id(), identity.groupId(), acceptedAt);
        current = attendanceRepository.findByLessonIdAndUserId(lesson.id(), identity.userId())
                .orElse(null);
        if (isManualHeadmanAbsence(current)) {
            throw new StudentCheckinException(Code.MANUAL_ABSENCE_REQUIRES_APPEAL,
                    "Ручная отметка «н» оспаривается отдельной заявкой");
        }
        if (current != null && current.getStatus() == AttendanceStatus.PRESENT) {
            return presentAck(current, lesson.id(), acceptedAt);
        }

        CheckinPairStateDocument pair = pairRepository.findById(
                PairWriteCoordinator.pairId(identity.userId(), lesson.id())).orElseThrow();
        if (pair.getRetryAt() != null && acceptedAt.isBefore(pair.getRetryAt())) {
            throw new StudentCheckinException(Code.CHECKIN_COOLDOWN,
                    "Повторная геопроверка пока недоступна", pair.getRetryAt());
        }

        boolean geoAccepted = geo instanceof Coordinates coordinates
                && geofenceService.isWithinCampus(coordinates.latitude(), coordinates.longitude());
        Instant retryAt = acceptedAt.plus(COOLDOWN);
        pair.setLastAttemptAt(acceptedAt);
        pair.setRetryAt(retryAt);
        pair.setUpdatedAt(acceptedAt);
        pairRepository.save(pair);

        Ack ack = geoAccepted
                ? markPresent(identity, lesson, current, acceptedAt)
                : createOrReuseRequest(identity, lesson, acceptedAt, retryAt);
        receiptRepository.save(toReceipt(identity, lesson, idempotencyKey, payloadHash, ack));
        return ack;
    }

    private Ack markPresent(
            Identity identity,
            Lesson lesson,
            AttendanceDocument current,
            Instant now
    ) {
        AttendanceDocument attendance = current == null ? new AttendanceDocument() : current;
        if (attendance.getCreatedAt() == null) {
            attendance.setCreatedAt(now);
        }
        attendance.setLessonId(lesson.id());
        attendance.setUserId(identity.userId());
        attendance.setGroupId(identity.groupId());
        attendance.setSubjectId(lesson.subjectId());
        attendance.setSemesterId(lesson.semesterId());
        attendance.setLessonNumber(lesson.lessonNumber());
        attendance.setLessonDate(lesson.date());
        attendance.setStatus(AttendanceStatus.PRESENT);
        attendance.setSource(AttendanceSource.STUDENT_GEO);
        attendance.setMarkedBy(null);
        attendance.setUpdatedAt(now);
        AttendanceDocument saved = attendanceRepository.save(attendance);

        lateCheckinRepository.findFirstByStudentIdAndLessonIdAndStatus(
                        identity.userId(), lesson.id(), LateCheckinRequestStatus.PENDING)
                .ifPresent(request -> {
                    request.setStatus(LateCheckinRequestStatus.CANCELLED);
                    request.setResolutionReason(LateCheckinResolutionReason.GEO_CONFIRMED);
                    request.setDecisionAt(now);
                    request.setDecisionBy(null);
                    request.setUpdatedAt(now);
                    LateCheckinRequest cancelled = lateCheckinRepository.save(request);
                    lateCheckinEventPublisher.publishDecided(
                            cancelled,
                            lesson.date(),
                            lesson.lessonNumber(),
                            lesson.subjectId(),
                            null
                    );
                });

        attendanceEventPublisher.publishMarked(saved);
        businessMetrics.checkinCounter("present").increment();
        return presentAck(saved, lesson.id(), now);
    }

    private Ack createOrReuseRequest(Identity identity, Lesson lesson, Instant now, Instant retryAt) {
        LateCheckinRequest request = lateCheckinRepository
                .findFirstByStudentIdAndLessonIdAndStatus(
                        identity.userId(), lesson.id(), LateCheckinRequestStatus.PENDING)
                .orElse(null);
        boolean created = request == null;
        if (created) {
            request = LateCheckinRequest.builder()
                    .studentId(identity.userId())
                    .groupId(identity.groupId())
                    .lessonId(lesson.id())
                    .subjectId(lesson.subjectId())
                    .semesterId(lesson.semesterId())
                    .lessonNumber(lesson.lessonNumber())
                    .lessonDate(lesson.date())
                    .studentName(identity.displayName())
                    .status(LateCheckinRequestStatus.PENDING)
                    .origin(LateCheckinRequestOrigin.AUTO_GEO_FAILURE)
                    .createdAt(now)
                    .updatedAt(now)
                    .build();
            request = lateCheckinRepository.save(request);
            lateCheckinEventPublisher.publishRequested(
                    request,
                    lesson.date(),
                    lesson.lessonNumber(),
                    lesson.subjectId(),
                    null
            );
            businessMetrics.lateCheckinCreatedCounter().increment();
        }
        Request projection = new Request(request.getId(), request.getStatus(), null);
        return new Ack(Outcome.PENDING_CONFIRMATION, lesson.id(), null, projection, retryAt, now);
    }

    private static boolean isManualHeadmanAbsence(AttendanceDocument current) {
        return current != null
                && current.getStatus() == AttendanceStatus.ABSENT
                && current.getSource() == AttendanceSource.HEADMAN;
    }

    private static Ack presentAck(AttendanceDocument document, long lessonId, Instant serverNow) {
        Attendance attendance = new Attendance(document.getStatus(), document.getSource(), document.getUpdatedAt());
        return new Ack(Outcome.PRESENT, lessonId, attendance, null, null, serverNow);
    }

    private static void validateIdentityAndPayload(
            Identity identity, long lessonId, String idempotencyKey, Geo geo) {
        if (identity == null || identity.userId() <= 0 || identity.groupId() == null || identity.groupId() <= 0) {
            throw new StudentCheckinException(Code.INVALID_SESSION, "Не хватает student/group scope");
        }
        if (!"STUDENT".equalsIgnoreCase(identity.role())) {
            throw new StudentCheckinException(Code.WRONG_ROLE, "Геоотметка доступна роли STUDENT");
        }
        if (lessonId <= 0) {
            throw new StudentCheckinException(Code.LESSON_NOT_FOUND, "Пара не найдена");
        }
        if (idempotencyKey == null || idempotencyKey.length() < 16 || idempotencyKey.length() > 128
                || idempotencyKey.chars().anyMatch(c -> c < 0x21 || c > 0x7e)) {
            throw new StudentCheckinException(Code.INVALID_IDEMPOTENCY_KEY, "Некорректный Idempotency-Key");
        }
        if (geo == null) {
            throw new StudentCheckinException(Code.INVALID_REQUEST, "geo обязателен");
        }
        if (geo instanceof Coordinates coordinates
                && (!Double.isFinite(coordinates.latitude()) || !Double.isFinite(coordinates.longitude())
                || coordinates.latitude() < -90 || coordinates.latitude() > 90
                || coordinates.longitude() < -180 || coordinates.longitude() > 180)) {
            throw new StudentCheckinException(Code.INVALID_REQUEST, "Некорректные координаты");
        }
        if (geo instanceof StudentCheckinModels.Unavailable unavailable
                && (unavailable.reason() == null || unavailable.reason().isBlank())) {
            throw new StudentCheckinException(Code.INVALID_REQUEST, "Причина недоступной геолокации обязательна");
        }
    }

    private static void validateDynamicEligibility(Identity identity, Lesson lesson) {
        if (identity.headman()) {
            throw new StudentCheckinException(Code.CHECKIN_NOT_ELIGIBLE,
                    "Староста отмечает себя через журнал посещаемости");
        }
        if (lesson == null || lesson.id() <= 0) {
            throw new StudentCheckinException(Code.LESSON_NOT_FOUND, "Пара не найдена");
        }
        if (identity.groupId() != lesson.groupId()) {
            throw new StudentCheckinException(Code.OUT_OF_SCOPE, "Пара принадлежит другой группе");
        }
        if ("cancelled".equalsIgnoreCase(lesson.status())) {
            throw new StudentCheckinException(Code.CHECKIN_NOT_ELIGIBLE, "Пара отменена");
        }
        if (lesson.geoBlocked()) {
            throw new StudentCheckinException(Code.CHECKIN_NOT_ELIGIBLE, "Геоотметка заблокирована");
        }
    }

    static void validateWindow(Lesson lesson, Instant now, Clock clock) {
        Instant opensAt = lesson.date().atTime(lesson.startsAt()).atZone(clock.getZone()).toInstant()
                .minus(CHECKIN_BUFFER);
        Instant closesAt = lesson.date().atTime(lesson.endsAt()).atZone(clock.getZone()).toInstant()
                .plus(CHECKIN_BUFFER);
        if (now.isBefore(opensAt) || now.isAfter(closesAt)) {
            throw new StudentCheckinException(Code.CHECKIN_NOT_ELIGIBLE, "Вне временного окна отметки");
        }
    }

    private StudentCheckinReceiptDocument toReceipt(
            Identity identity, Lesson lesson, String key, String payloadHash, Ack ack) {
        return StudentCheckinReceiptDocument.builder()
                .studentId(identity.userId())
                .lessonId(lesson.id())
                .idempotencyKey(key)
                .payloadHash(payloadHash)
                .outcome(ack.outcome().name())
                .attendanceStatus(ack.attendance() == null ? null : ack.attendance().status().name())
                .attendanceSource(ack.attendance() == null ? null : ack.attendance().source().name())
                .markedAt(ack.attendance() == null ? null : ack.attendance().markedAt())
                .requestId(ack.request() == null ? null : ack.request().id())
                .requestStatus(ack.request() == null ? null : ack.request().status().name())
                .requestResolution(ack.request() == null || ack.request().resolution() == null
                        ? null : ack.request().resolution().name())
                .retryAt(ack.retryAt())
                .serverNow(ack.serverNow())
                .createdAt(ack.serverNow())
                .build();
    }

    private static Ack fromReceipt(StudentCheckinReceiptDocument receipt) {
        Attendance attendance = receipt.getAttendanceStatus() == null ? null : new Attendance(
                AttendanceStatus.valueOf(receipt.getAttendanceStatus()),
                AttendanceSource.valueOf(receipt.getAttendanceSource()),
                receipt.getMarkedAt());
        Request request = receipt.getRequestId() == null ? null : new Request(
                receipt.getRequestId(),
                LateCheckinRequestStatus.valueOf(receipt.getRequestStatus()),
                receipt.getRequestResolution() == null ? null : Resolution.valueOf(receipt.getRequestResolution()));
        return new Ack(Outcome.valueOf(receipt.getOutcome()), receipt.getLessonId(), attendance, request,
                receipt.getRetryAt(), receipt.getServerNow());
    }

    private static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
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
}
