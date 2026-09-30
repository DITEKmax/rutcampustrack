package ru.rutcampustrack.academic.event;

import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import ru.rutcampustrack.academic.contract.enums.SemesterArchiveParticipantCommand;
import ru.rutcampustrack.academic.contract.enums.SemesterArchiveParticipantStatus;
import ru.rutcampustrack.academic.semester.SemesterArchiveCommandTransaction;
import ru.rutcampustrack.academic.semester.SemesterArchiveCoordinator;
import ru.rutcampustrack.shared.events.AbstractEventConsumer;
import ru.rutcampustrack.shared.events.EventIdempotent;
import ru.rutcampustrack.shared.events.IdempotencyGuard;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

/** Applies Attendance's typed receipt against the exact central operation epoch. */
@Component
public class SemesterArchiveParticipantAcknowledgementConsumer extends AbstractEventConsumer {

    public static final String CONSUMER_ID = "academic-semester-archive-participant-ack";
    private static final String EVENT_TYPE = "semester.archive.participant.ack";
    private static final String EVENT_SOURCE = "attendance-service";

    private final IdempotencyGuard idempotencyGuard;
    private final SemesterArchiveCommandTransaction commands;
    private final SemesterArchiveCoordinator coordinator;

    public SemesterArchiveParticipantAcknowledgementConsumer(
            IdempotencyGuard idempotencyGuard,
            SemesterArchiveCommandTransaction commands,
            SemesterArchiveCoordinator coordinator) {
        this.idempotencyGuard = idempotencyGuard;
        this.commands = commands;
        this.coordinator = coordinator;
    }

    @RabbitListener(queues = RabbitConfig.SEMESTER_ARCHIVE_PARTICIPANT_ACK_QUEUE)
    @EventIdempotent(consumer = CONSUMER_ID)
    @Transactional
    public void onEvent(Map<String, Object> envelope) {
        Object rawType = envelope == null ? null : envelope.get("event_type");
        if (!EVENT_TYPE.equals(rawType)) return;
        if (!EVENT_SOURCE.equals(envelope.get("source"))) {
            throw new IllegalArgumentException("semester archive participant acknowledgement has an untrusted source");
        }
        if (exactLong(envelope.get("event_version"), "event_version") != 1) {
            throw new IllegalArgumentException("semester archive participant acknowledgement has unsupported version");
        }
        Object rawPayload = envelope.get("payload");
        if (!(rawPayload instanceof Map<?, ?> payload)) {
            throw new IllegalArgumentException("semester archive participant acknowledgement has no payload object");
        }
        if (!idempotencyGuard.tryClaim(CONSUMER_ID, envelope)) return;

        UUID operationId = uuid(payload.get("operation_id"), "operation_id");
        long semesterId = positiveLong(payload.get("semester_id"), "semester_id");
        long stateVersion = nonnegativeLong(payload.get("state_version"), "state_version");
        SemesterArchiveParticipantCommand command = enumValue(
                payload.get("command"), SemesterArchiveParticipantCommand.class, "command");
        SemesterArchiveParticipantStatus status = enumValue(
                payload.get("status"), SemesterArchiveParticipantStatus.class, "status");
        Object rawReason = payload.get("blocking_reason");
        String reason = rawReason == null ? null : requiredString(rawReason, "blocking_reason");

        withTraceContext(envelope, () -> commands.recordAttendanceAcknowledgement(
                operationId, semesterId, stateVersion, command, status, reason));
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                coordinator.advance(operationId);
            }
        });
    }

    private static String requiredString(Object raw, String field) {
        if (!(raw instanceof String value) || value.isBlank()) {
            throw new IllegalArgumentException("semester archive participant acknowledgement has invalid " + field);
        }
        return value;
    }

    private static long exactLong(Object raw, String field) {
        if (!(raw instanceof Number number)) {
            throw new IllegalArgumentException("semester archive participant acknowledgement has invalid " + field);
        }
        try {
            return new BigDecimal(number.toString()).longValueExact();
        } catch (NumberFormatException | ArithmeticException invalid) {
            throw new IllegalArgumentException(
                    "semester archive participant acknowledgement has invalid " + field, invalid);
        }
    }

    private static long positiveLong(Object raw, String field) {
        long value = exactLong(raw, field);
        if (value <= 0) {
            throw new IllegalArgumentException("semester archive participant acknowledgement has invalid " + field);
        }
        return value;
    }

    private static long nonnegativeLong(Object raw, String field) {
        long value = exactLong(raw, field);
        if (value < 0) {
            throw new IllegalArgumentException("semester archive participant acknowledgement has invalid " + field);
        }
        return value;
    }

    private static UUID uuid(Object raw, String field) {
        if (!(raw instanceof String value)) {
            throw new IllegalArgumentException("semester archive participant acknowledgement has invalid " + field);
        }
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException malformed) {
            throw new IllegalArgumentException(
                    "semester archive participant acknowledgement has invalid " + field, malformed);
        }
    }

    private static <E extends Enum<E>> E enumValue(Object raw, Class<E> type, String field) {
        if (!(raw instanceof String value)) {
            throw new IllegalArgumentException("semester archive participant acknowledgement has invalid " + field);
        }
        try {
            return Enum.valueOf(type, value);
        } catch (IllegalArgumentException malformed) {
            throw new IllegalArgumentException(
                    "semester archive participant acknowledgement has invalid " + field, malformed);
        }
    }
}
