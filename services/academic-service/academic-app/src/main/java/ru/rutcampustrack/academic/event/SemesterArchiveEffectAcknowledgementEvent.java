package ru.rutcampustrack.academic.event;

import com.fasterxml.jackson.annotation.JsonProperty;
import ru.rutcampustrack.shared.events.EventVersion;

import java.util.HexFormat;
import java.util.UUID;

/** Durable proof that Academic applied one exact Schedule effect payload. */
@EventVersion(1)
public final class SemesterArchiveEffectAcknowledgementEvent extends DomainEvent {

    public record Payload(
            @JsonProperty("source_event_id") UUID sourceEventId,
            @JsonProperty("target") String target,
            @JsonProperty("source_event_type") String sourceEventType,
            @JsonProperty("semester_id") long semesterId,
            @JsonProperty("payload_hash") String payloadHash,
            @JsonProperty("result") String result,
            @JsonProperty("blocking_reason") String blockingReason) { }

    public SemesterArchiveEffectAcknowledgementEvent(UUID sourceEventId,
                                                      long semesterId,
                                                      byte[] payloadHash,
                                                      UUID acknowledgementEventId) {
        super(SemesterArchiveEffectAcknowledgementEvent.class,
                "semester.archive.effect.ack",
                new Payload(sourceEventId, "ACADEMIC", "homework.binding.archived",
                        semesterId, HexFormat.of().formatHex(payloadHash), "APPLIED", null));
        setEventId(acknowledgementEventId);
    }
}
