package ru.rutcampustrack.attendance.event;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import ru.rutcampustrack.attendance.exception.ConflictException;
import ru.rutcampustrack.attendance.semester.SemesterCacheService;
import ru.rutcampustrack.attendance.studentrequest.StudentRequestService;
import ru.rutcampustrack.shared.events.AbstractEventConsumer;
import ru.rutcampustrack.shared.events.EventIdempotent;
import ru.rutcampustrack.shared.events.IdempotencyGuard;

import java.time.LocalDate;
import java.util.Map;

/**
 * Generic RabbitMQ event consumer for the Attendance Service. Reads event_type from the envelope
 * and routes to domain-specific handlers. Delegates lesson lifecycle logic to LessonEventService.
 * Semester cache refresh on semester.archived handled directly via SemesterCacheService.
 *
 * <p>CRITICAL: ((Number) value).longValue() is used for all numeric ID extractions. Jackson
 * deserializes JSON integers as Integer when target is Object — direct Long cast would throw
 * ClassCastException.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class EventConsumer extends AbstractEventConsumer {

    private ru.rutcampustrack.attendance.audit.ActionEventHandler actionEvents;

    @org.springframework.beans.factory.annotation.Autowired
    public void setActionEvents(ru.rutcampustrack.attendance.audit.ActionEventHandler handler) {
        this.actionEvents = handler;
    }

    private InitialMembershipAttendanceBackfillService initialMembershipBackfills;

    @org.springframework.beans.factory.annotation.Autowired
    public void setInitialMembershipBackfills(InitialMembershipAttendanceBackfillService service) {
        this.initialMembershipBackfills = service;
    }

    public static final String CONSUMER_ID = "attendance";

    private final LessonEventService lessonEventService;
    private final LessonTransferParticipantService lessonTransferParticipantService;
    private final SemesterArchiveParticipantService semesterArchiveParticipantService;
    private final SemesterDeletionParticipantService semesterDeletionParticipantService;
    private final SemesterArchiveEffectService semesterArchiveEffectService;
    private final SemesterCacheService semesterCacheService;
    private final StudentRequestService studentRequestService;
    private final IdempotencyGuard idempotencyGuard;
    private final TransactionTemplate transactionTemplate;

    private static final int SUPPORTED_EVENT_VERSION = 1;
    private static final String TRUSTED_BOT_SOURCE = "notification-bot";

    @RabbitListener(queues = "attendance-service.events")
    @EventIdempotent(consumer = CONSUMER_ID)
    public void onEvent(Map<String, Object> envelope) {
        Object rawEventType = envelope == null ? null : envelope.get("event_type");
        if (!(rawEventType instanceof String eventType) || eventType.isBlank()) {
            throw new IllegalArgumentException("Event envelope requires an event_type");
        }

        if ("schedule.action-recorded".equals(eventType)
                || "homework.action-recorded".equals(eventType)) {
            actionEvents.validate(
                    envelope); // malformed/source-spoofed replays never bypass validation
        }

        if (InitialMembershipAttendanceBackfillService.TYPE.equals(eventType)) {
            withTraceContext(
                    envelope,
                    () -> {
                        try {
                            initialMembershipBackfills.handle(envelope);
                        } catch (RuntimeException failure) {
                            try {
                                // Existing private MDC correlates the durable work; no payload or
                                // exception text.
                                log.warn(
                                        "event consumption failure family=INITIAL_MEMBERSHIP"
                                                + " category={}",
                                        initialMembershipFailure(failure));
                            } finally {
                                throw failure;
                            }
                        }
                    });
            return;
        }

        if ("lesson.closed".equals(eventType)) {
            // Schedule and Academic reads must finish before claim/fence/transaction.
            withTraceContext(envelope, () -> handleLessonClosed(envelope));
            return;
        }

        // The durable domain receipt is the source of truth for tracked event
        // replays. Check it before inserting the global idempotency claim: a
        // duplicate-key insert aborts a Mongo transaction, so receipt reads
        // and the saved acknowledgement must run in their own transaction.
        if (replayTrackedReceiptInTrace(eventType, envelope)) {
            return;
        }

        transactionTemplate.executeWithoutResult(
                status -> {
                    if (!idempotencyGuard.tryClaim(CONSUMER_ID, envelope)) {
                        return;
                    }
                    dispatchClaimed(eventType, envelope);
                });
    }

    private boolean replayReceiptIfPresent(String eventType, Map<String, Object> envelope) {
        switch (eventType) {
            case "lesson.cancelled", "lesson.deleted", "lesson.one_off.cancelled" -> {
                if (semesterArchiveEffectService.hasReceipt(envelope)) {
                    semesterArchiveEffectService.apply(envelope, () -> {});
                    return true;
                }
            }
            case "lesson.transfer.requested" -> {
                if (lessonTransferParticipantService.hasReceipt(envelope)) {
                    lessonTransferParticipantService.apply(envelope);
                    return true;
                }
            }
            case "semester.archive.participant.command" -> {
                if (SemesterDeletionParticipantService.isDeletionCommand(envelope)
                        && semesterDeletionParticipantService.hasReceipt(envelope)) {
                    semesterDeletionParticipantService.apply(envelope);
                    return true;
                }
                if (!SemesterDeletionParticipantService.isDeletionCommand(envelope)
                        && semesterArchiveParticipantService.hasReceipt(envelope)) {
                    semesterArchiveParticipantService.apply(envelope);
                    return true;
                }
            }
            default -> {
                return false;
            }
        }
        return false;
    }

    private boolean replayTrackedReceiptInTrace(String eventType, Map<String, Object> envelope) {
        boolean[] replayed = {false};
        withTraceContext(envelope, () -> replayed[0] = replayReceiptIfPresent(eventType, envelope));
        return replayed[0];
    }

    private void dispatchClaimed(String eventType, Map<String, Object> envelope) {
        // M04 QA3 — extract trace_id из envelope в MDC до handler'а.
        withTraceContext(
                envelope,
                () -> {
                    log.debug("Received event: {}", eventType);
                    switch (eventType) {
                        case "schedule.action-recorded", "homework.action-recorded" ->
                                actionEvents.accept(envelope);
                        case "lesson.started" -> handleLessonStarted(envelope);
                        case "lesson.cancelled" ->
                                semesterArchiveEffectService.apply(
                                        envelope,
                                        () -> preflightLessonCancelled(envelope),
                                        () -> handleLessonCancelled(envelope));
                        case "lesson.deleted" ->
                                semesterArchiveEffectService.apply(
                                        envelope,
                                        () -> preflightLessonDeleted(envelope),
                                        () -> handleLessonDeleted(envelope));
                        case "lesson.one_off.cancelled" ->
                                semesterArchiveEffectService.apply(
                                        envelope,
                                        () -> preflightOneOffLessonCancelled(envelope),
                                        () -> handleOneOffLessonCancelled(envelope));
                        case "lesson.transfer.requested" ->
                                lessonTransferParticipantService.apply(envelope);
                        case "semester.archive.participant.command" -> {
                            if (SemesterDeletionParticipantService.isDeletionCommand(envelope)) {
                                semesterDeletionParticipantService.apply(envelope);
                            } else {
                                semesterArchiveParticipantService.apply(envelope);
                            }
                        }
                        case "semester.archived" -> handleSemesterArchived(envelope);
                        case "late_checkin.decision" -> handleLateCheckinDecision(envelope);
                        case "excuse.decision" -> handleExcuseDecision(envelope);
                        default -> log.debug("Ignoring unknown event type: {}", eventType);
                    }
                });
    }

    private void handleLessonStarted(Map<String, Object> envelope) {
        Map<String, Object> payload = extractPayload(envelope);
        if (payload == null) return;
        Long lessonId = extractLong(payload, "lesson_id");
        log.debug("lesson.started: no-op (lesson_id={})", lessonId);
    }

    private void handleLessonClosed(Map<String, Object> envelope) {
        LessonClosedStage stage = LessonClosedStage.RECEIPT;
        try {
            if (semesterArchiveEffectService.hasReceipt(envelope)) {
                // Do not call Schedule or attempt a duplicate claim after an exact
                // receipt has already committed. apply() re-acks that receipt.
                semesterArchiveEffectService.apply(envelope, () -> {});
                return;
            }

            stage = LessonClosedStage.PAYLOAD;
            Map<String, Object> payload = extractRequiredPayload(envelope);
            Long lessonId = extractPositiveInteger(payload, "lesson_id");
            Long groupId = extractPositiveInteger(payload, "group_id");
            Long semesterId = extractPositiveInteger(payload, "semester_id");
            stage = LessonClosedStage.PREPARE;
            LessonEventService.LessonClosedSnapshot effectSnapshot =
                    lessonEventService.prepareLessonClosed(lessonId, groupId, semesterId);
            stage = LessonClosedStage.TRANSACTION;
            transactionTemplate.executeWithoutResult(
                    status -> {
                        if (!idempotencyGuard.tryClaim(CONSUMER_ID, envelope)) {
                            // A concurrent delivery may have committed after the preflight
                            // receipt read. Leave this delivery unacknowledged; Rabbit
                            // redelivery will observe and replay the committed receipt.
                            return;
                        }
                        semesterArchiveEffectService.apply(
                                envelope,
                                () -> lessonEventService.preflightLessonClosed(effectSnapshot),
                                () -> lessonEventService.applyLessonClosed(effectSnapshot));
                    });
        } catch (RuntimeException failure) {
            try {
                // Existing MDC supplies correlation; never attach the exception or envelope.
                log.warn(
                        "lesson.closed failure stage={} category={} rpc={} status={} cause={}",
                        stage,
                        lessonClosedFailure(failure),
                        lessonClosedRpc(stage, failure),
                        lessonClosedStatus(failure),
                        lessonClosedCause(failure));
            } finally {
                throw failure;
            }
        }
    }

    private enum LessonClosedStage {
        RECEIPT,
        PAYLOAD,
        PREPARE,
        TRANSACTION
    }

    private enum LessonClosedFailure {
        ACADEMIC_UNAVAILABLE,
        SCHEDULE_UNAVAILABLE,
        RESOURCE_NOT_FOUND,
        INVALID_ARGUMENT,
        TRANSACTION_ROLLBACK,
        MONGO_TRANSACTION,
        DUPLICATE_KEY,
        EFFECT_REJECTED,
        OTHER
    }

    private enum LessonClosedRpc {
        NONE,
        ACADEMIC_GROUP_MEMBERS,
        SCHEDULE_LESSON_BY_ID
    }

    private enum LessonClosedCause {
        NONE,
        SEMANTIC_REJECTION,
        MONGO_TRANSIENT_TRANSACTION,
        MONGO_UNKNOWN_COMMIT,
        MONGO_WRITE_CONFLICT,
        MONGO_DUPLICATE_KEY,
        MONGO_OTHER,
        TRANSACTION_ROLLBACK,
        OTHER
    }

    private enum NoRpcStatus {
        NONE
    }

    private static Enum<?> lessonClosedStatus(RuntimeException failure) {
        io.grpc.Status.Code code = null;
        if (failure
                instanceof
                ru.rutcampustrack.attendance.exception.AcademicServiceUnavailableException
                        academic) {
            code = academic.getStatusCode();
        } else if (failure
                instanceof
                ru.rutcampustrack.attendance.exception.ScheduleServiceUnavailableException
                        schedule) {
            code = schedule.getStatusCode();
        }
        return code == null ? NoRpcStatus.NONE : code;
    }

    private static LessonClosedRpc lessonClosedRpc(
            LessonClosedStage stage, RuntimeException failure) {
        if (stage != LessonClosedStage.PREPARE || lessonClosedStatus(failure) == NoRpcStatus.NONE) {
            return LessonClosedRpc.NONE;
        }
        return failure
                        instanceof
                        ru.rutcampustrack.attendance.exception.AcademicServiceUnavailableException
                ? LessonClosedRpc.ACADEMIC_GROUP_MEMBERS
                : LessonClosedRpc.SCHEDULE_LESSON_BY_ID;
    }

    private static Enum<?> lessonClosedCause(RuntimeException failure) {
        if (failure
                        instanceof
                        ru.rutcampustrack.attendance.exception.ScheduleServiceUnavailableException
                                schedule
                && schedule.getStatusCode() == io.grpc.Status.Code.UNAVAILABLE
                && schedule.getUnavailableOrigin() != null) {
            return schedule.getUnavailableOrigin();
        }
        if (lessonClosedStatus(failure) != NoRpcStatus.NONE) return LessonClosedCause.NONE;
        Throwable current = failure;
        for (int depth = 0; current != null && depth < 12; depth++, current = current.getCause()) {
            if (current instanceof com.mongodb.MongoException mongo) {
                if (mongo.hasErrorLabel("UnknownTransactionCommitResult"))
                    return LessonClosedCause.MONGO_UNKNOWN_COMMIT;
                if (mongo.hasErrorLabel("TransientTransactionError"))
                    return LessonClosedCause.MONGO_TRANSIENT_TRANSACTION;
                if (mongo.getCode() == 112) return LessonClosedCause.MONGO_WRITE_CONFLICT;
                if (mongo.getCode() == 11000) return LessonClosedCause.MONGO_DUPLICATE_KEY;
                return LessonClosedCause.MONGO_OTHER;
            }
            if (current instanceof org.springframework.dao.DuplicateKeyException)
                return LessonClosedCause.MONGO_DUPLICATE_KEY;
        }
        if (failure instanceof org.springframework.transaction.UnexpectedRollbackException)
            return LessonClosedCause.TRANSACTION_ROLLBACK;
        if (failure
                        instanceof
                        ru.rutcampustrack.attendance.exception.AcademicServiceUnavailableException
                || failure
                        instanceof
                        ru.rutcampustrack.attendance.exception.ScheduleServiceUnavailableException)
            return LessonClosedCause.SEMANTIC_REJECTION;
        return LessonClosedCause.OTHER;
    }

    private enum InitialMembershipFailure {
        ACADEMIC_RPC_UNAVAILABLE,
        ACADEMIC_RPC_DEADLINE,
        ACADEMIC_RPC_AUTHENTICATION,
        ACADEMIC_RPC_PERMISSION,
        ACADEMIC_RPC_OTHER,
        ACADEMIC_DIRECTED_IDENTITY,
        ACADEMIC_SEMESTER_ECHO,
        ACADEMIC_SEMESTER_ADMISSION,
        ACADEMIC_ROSTER_ECHO,
        ACADEMIC_ROSTER_IDENTITY
    }

    private static Enum<?> initialMembershipFailure(RuntimeException failure) {
        if (!(failure
                instanceof
                ru.rutcampustrack.attendance.exception.AcademicServiceUnavailableException)) {
            return lessonClosedFailure(failure);
        }
        String message = failure.getMessage();
        if (message == null) return LessonClosedFailure.ACADEMIC_UNAVAILABLE;
        // Exact source-owned forms only. Never emit the message or infer from arbitrary text.
        return switch (message) {
            case "Academic Service unavailable: UNAVAILABLE" ->
                    InitialMembershipFailure.ACADEMIC_RPC_UNAVAILABLE;
            case "Academic Service unavailable: DEADLINE_EXCEEDED" ->
                    InitialMembershipFailure.ACADEMIC_RPC_DEADLINE;
            case "Academic Service unavailable: UNAUTHENTICATED" ->
                    InitialMembershipFailure.ACADEMIC_RPC_AUTHENTICATION;
            case "Academic Service unavailable: PERMISSION_DENIED" ->
                    InitialMembershipFailure.ACADEMIC_RPC_PERMISSION;
            case "Academic Service unavailable: OK",
                    "Academic Service unavailable: CANCELLED",
                    "Academic Service unavailable: UNKNOWN",
                    "Academic Service unavailable: INVALID_ARGUMENT",
                    "Academic Service unavailable: NOT_FOUND",
                    "Academic Service unavailable: ALREADY_EXISTS",
                    "Academic Service unavailable: RESOURCE_EXHAUSTED",
                    "Academic Service unavailable: FAILED_PRECONDITION",
                    "Academic Service unavailable: ABORTED",
                    "Academic Service unavailable: OUT_OF_RANGE",
                    "Academic Service unavailable: UNIMPLEMENTED",
                    "Academic Service unavailable: INTERNAL",
                    "Academic Service unavailable: DATA_LOSS" ->
                    InitialMembershipFailure.ACADEMIC_RPC_OTHER;
            case "Attendance semester authority has no directed service identity" ->
                    InitialMembershipFailure.ACADEMIC_DIRECTED_IDENTITY;
            case "Mismatched semester state" -> InitialMembershipFailure.ACADEMIC_SEMESTER_ECHO;
            case "Initial membership semester is unavailable for admitted effects" ->
                    InitialMembershipFailure.ACADEMIC_SEMESTER_ADMISSION;
            case "Academic returned a missing or mismatched historical roster echo" ->
                    InitialMembershipFailure.ACADEMIC_ROSTER_ECHO;
            case "Academic returned duplicate or invalid historical student identity" ->
                    InitialMembershipFailure.ACADEMIC_ROSTER_IDENTITY;
            default -> LessonClosedFailure.ACADEMIC_UNAVAILABLE;
        };
    }

    private static LessonClosedFailure lessonClosedFailure(RuntimeException failure) {
        if (failure
                instanceof
                ru.rutcampustrack.attendance.exception.AcademicServiceUnavailableException)
            return LessonClosedFailure.ACADEMIC_UNAVAILABLE;
        if (failure
                instanceof
                ru.rutcampustrack.attendance.exception.ScheduleServiceUnavailableException)
            return LessonClosedFailure.SCHEDULE_UNAVAILABLE;
        if (failure
                instanceof
                ru.rutcampustrack.attendance.contract.exception.ResourceNotFoundException)
            return LessonClosedFailure.RESOURCE_NOT_FOUND;
        if (failure instanceof IllegalArgumentException)
            return LessonClosedFailure.INVALID_ARGUMENT;
        if (failure instanceof org.springframework.transaction.UnexpectedRollbackException)
            return LessonClosedFailure.TRANSACTION_ROLLBACK;
        if (failure instanceof org.springframework.data.mongodb.MongoTransactionException)
            return LessonClosedFailure.MONGO_TRANSACTION;
        if (failure instanceof org.springframework.dao.DuplicateKeyException)
            return LessonClosedFailure.DUPLICATE_KEY;
        if (failure instanceof SemesterArchiveEffectRejectedException)
            return LessonClosedFailure.EFFECT_REJECTED;
        return LessonClosedFailure.OTHER;
    }

    private void preflightLessonCancelled(Map<String, Object> envelope) {
        Map<String, Object> payload = extractRequiredPayload(envelope);
        Long lessonId = extractPositiveInteger(payload, "lesson_id");
        Long semesterId = extractPositiveInteger(payload, "semester_id");
        lessonEventService.preflightLessonCancelled(lessonId, semesterId);
    }

    private void handleLessonCancelled(Map<String, Object> envelope) {
        Map<String, Object> payload = extractRequiredPayload(envelope);
        Long lessonId = extractPositiveInteger(payload, "lesson_id");
        Long semesterId = extractPositiveInteger(payload, "semester_id");
        lessonEventService.processLessonCancelled(lessonId, semesterId);
    }

    @SuppressWarnings("unchecked")
    private void preflightLessonDeleted(Map<String, Object> envelope) {
        Map<String, Object> payload = extractRequiredPayload(envelope);
        Object raw = payload.get("lesson_ids");
        if (!(raw instanceof java.util.List<?> list) || list.isEmpty()) {
            throw new IllegalArgumentException("lesson.deleted requires non-empty lesson_ids");
        }
        java.util.List<Long> lessonIds = new java.util.ArrayList<>(list.size());
        for (Object id : list) {
            Long parsed = positiveInteger(id);
            if (parsed == null)
                throw new IllegalArgumentException("lesson.deleted contains invalid lesson_ids");
            lessonIds.add(parsed);
        }
        Long semesterId = extractPositiveInteger(payload, "semester_id");
        lessonEventService.preflightLessonsDeleted(lessonIds, semesterId);
    }

    /**
     * Cascade-delete attendance docs when schedule-service physically removes Lesson rows (e.g.
     * ScheduleItem edit that regenerates planned lessons). Drops every attendance doc whose {@code
     * lesson_id} is in the event payload.
     */
    @SuppressWarnings("unchecked")
    private void handleLessonDeleted(Map<String, Object> envelope) {
        Map<String, Object> payload = extractRequiredPayload(envelope);
        Object raw = payload.get("lesson_ids");
        if (!(raw instanceof java.util.List<?> list) || list.isEmpty()) {
            throw new IllegalArgumentException("lesson.deleted requires non-empty lesson_ids");
        }
        java.util.List<Long> lessonIds = new java.util.ArrayList<>(list.size());
        for (Object id : list) {
            Long parsed = positiveInteger(id);
            if (parsed == null)
                throw new IllegalArgumentException("lesson.deleted contains invalid lesson_ids");
            lessonIds.add(parsed);
        }
        if (lessonIds.isEmpty() || lessonIds.size() != list.size()) {
            throw new IllegalArgumentException("lesson.deleted contains invalid lesson_ids");
        }
        Long semesterId = extractPositiveInteger(payload, "semester_id");
        lessonEventService.processLessonsDeleted(lessonIds, semesterId);
    }

    /**
     * D-22 / AC-08: cascade-delete attendance docs for a cancelled one-off lesson. Natural key:
     * {@code (group_id, lesson_date, lesson_number)}. Idempotent — repeated delivery yields 0
     * deletes without exception.
     */
    private void handleOneOffLessonCancelled(Map<String, Object> envelope) {
        Map<String, Object> payload = extractRequiredPayload(envelope);
        Long groupId = extractPositiveInteger(payload, "group_id");
        String dateStr = (String) payload.get("date");
        Object lessonNumberRaw = payload.get("lesson_number");
        Long semesterId = extractPositiveInteger(payload, "semester_id");
        if (groupId == null || dateStr == null || lessonNumberRaw == null || semesterId == null) {
            throw new IllegalArgumentException("lesson.one_off.cancelled has invalid scope");
        }
        LocalDate date = LocalDate.parse(dateStr);
        Long lessonNumberValue = positiveInteger(lessonNumberRaw);
        if (lessonNumberValue == null || lessonNumberValue > Integer.MAX_VALUE) {
            throw new IllegalArgumentException(
                    "lesson.one_off.cancelled has invalid lesson_number");
        }
        Integer lessonNumber = lessonNumberValue.intValue();
        lessonEventService.processOneOffLessonCancelled(semesterId, groupId, date, lessonNumber);
    }

    private void preflightOneOffLessonCancelled(Map<String, Object> envelope) {
        Map<String, Object> payload = extractRequiredPayload(envelope);
        Long groupId = extractPositiveInteger(payload, "group_id");
        String dateStr = (String) payload.get("date");
        Object lessonNumberRaw = payload.get("lesson_number");
        Long semesterId = extractPositiveInteger(payload, "semester_id");
        if (groupId == null || dateStr == null || lessonNumberRaw == null || semesterId == null) {
            throw new IllegalArgumentException("lesson.one_off.cancelled has invalid scope");
        }
        LocalDate date = LocalDate.parse(dateStr);
        Long lessonNumberValue = positiveInteger(lessonNumberRaw);
        if (lessonNumberValue == null || lessonNumberValue > Integer.MAX_VALUE) {
            throw new IllegalArgumentException(
                    "lesson.one_off.cancelled has invalid lesson_number");
        }
        lessonEventService.preflightOneOffLessonCancelled(
                semesterId, groupId, date, lessonNumberValue.intValue());
    }

    private void handleSemesterArchived(Map<String, Object> envelope) {
        semesterCacheService.refresh();
        log.info("semester.archived: refreshed semester cache");
    }

    /**
     * Consumed from notification-bot: headman pressed approve/reject on a late-checkin request.
     * Payload: {@code request_id} (string), {@code decision_by} (positive internal user_id), {@code
     * approved} (bool).
     */
    private void handleLateCheckinDecision(Map<String, Object> envelope) {
        requireTrustedBotDecisionEnvelope(envelope, "late_checkin.decision");
        Map<String, Object> payload = extractPayload(envelope);
        if (payload == null) return;
        String requestId = (String) payload.get("request_id");
        Long decisionBy = extractPositiveLong(payload.get("decision_by"));
        Object approvedRaw = payload.get("approved");
        if (requestId == null
                || requestId.isBlank()
                || decisionBy == null
                || !(approvedRaw instanceof Boolean)) {
            throw new IllegalArgumentException("late_checkin.decision has invalid payload");
        }
        try {
            studentRequestService.decideLateCheckinFromBot(
                    requestId, decisionBy, (Boolean) approvedRaw);
        } catch (ConflictException stale) {
            // A trusted event with a terminal request is an authenticated stale
            // delivery.  The persisted terminal detail remains authoritative;
            // acknowledge without mutating it or publishing a second event.
            log.info(
                    "Ignoring stale late_checkin.decision for request_id={}: {}",
                    requestId,
                    stale.getMessage());
        }
    }

    /**
     * Старос/тa нажал inline-кнопку под excuse-тикетом в Telegram. Payload: {@code ticket_id},
     * {@code decision_by} (telegram user_id), {@code approved}, опционально {@code
     * decision_comment}.
     */
    private void handleExcuseDecision(Map<String, Object> envelope) {
        requireTrustedBotDecisionEnvelope(envelope, "excuse.decision");
        Map<String, Object> payload = extractPayload(envelope);
        if (payload == null) return;
        String ticketId = (String) payload.get("ticket_id");
        Long decisionBy = extractLong(payload, "decision_by");
        Object approvedRaw = payload.get("approved");
        Object commentRaw = payload.get("decision_comment");
        String comment;
        if (commentRaw == null) {
            comment = null;
        } else if (commentRaw instanceof String value) {
            comment = value;
        } else {
            throw new IllegalArgumentException("excuse.decision has invalid decision_comment");
        }
        if (ticketId == null
                || ticketId.isBlank()
                || decisionBy == null
                || decisionBy <= 0
                || !(approvedRaw instanceof Boolean)) {
            throw new IllegalArgumentException("excuse.decision has invalid payload");
        }
        try {
            studentRequestService.decideExcuseFromBot(
                    ticketId, decisionBy, (Boolean) approvedRaw, comment);
        } catch (ConflictException stale) {
            // See the late-checkin path: trusted stale commands are no-op ACKs,
            // while malformed/unauthorized failures still propagate to DLQ.
            log.info(
                    "Ignoring stale excuse.decision for ticket_id={}: {}",
                    ticketId,
                    stale.getMessage());
        }
    }

    private void requireTrustedBotDecisionEnvelope(
            Map<String, Object> envelope, String expectedType) {
        Object version = envelope.get("event_version");
        Object source = envelope.get("source");
        if (!(version instanceof Number number)
                || number.intValue() != SUPPORTED_EVENT_VERSION
                || !TRUSTED_BOT_SOURCE.equals(source)
                || !expectedType.equals(envelope.get("event_type"))) {
            throw new IllegalArgumentException(
                    "Unsupported or untrusted " + expectedType + " envelope");
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> extractPayload(Map<String, Object> envelope) {
        Object raw = envelope.get("payload");
        if (!(raw instanceof Map<?, ?> payload)
                || payload.keySet().stream().anyMatch(key -> !(key instanceof String))) {
            throw new IllegalArgumentException("Event envelope requires an object payload");
        }
        return (Map<String, Object>) payload;
    }

    private Long extractLong(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value == null) return null;
        return ((Number) value).longValue();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> extractRequiredPayload(Map<String, Object> envelope) {
        Object raw = envelope.get("payload");
        if (!(raw instanceof Map<?, ?> payload)
                || payload.keySet().stream().anyMatch(key -> !(key instanceof String))) {
            throw new IllegalArgumentException("Tracked Schedule event requires an object payload");
        }
        return (Map<String, Object>) payload;
    }

    private Long extractPositiveInteger(Map<String, Object> payload, String key) {
        Long value = positiveInteger(payload.get(key));
        if (value == null) throw new IllegalArgumentException(key + " must be a positive integer");
        return value;
    }

    private static Long positiveInteger(Object value) {
        if (!(value instanceof Byte
                || value instanceof Short
                || value instanceof Integer
                || value instanceof Long)) return null;
        long parsed = ((Number) value).longValue();
        return parsed > 0 ? parsed : null;
    }

    private Long extractPositiveLong(Object value) {
        if (!(value instanceof Byte
                || value instanceof Short
                || value instanceof Integer
                || value instanceof Long)) {
            return null;
        }
        long candidate = ((Number) value).longValue();
        return candidate > 0 ? candidate : null;
    }
}
