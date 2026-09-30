package ru.rutcampustrack.academic.contract.dto.semester;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import ru.rutcampustrack.academic.contract.enums.SemesterArchiveParticipantCommand;
import ru.rutcampustrack.academic.contract.enums.SemesterArchiveParticipantStatus;

import java.util.UUID;

/** Durable participant proof correlated to one exact command and authority version. */
@Schema(description = "Подтверждение локального barrier по точной версии команды")
public record SemesterArchiveParticipantAcknowledgement(
        @JsonProperty("operation_id") UUID operationId,
        @JsonProperty("semester_id") long semesterId,
        @JsonProperty("state_version") long stateVersion,
        @JsonProperty("command") SemesterArchiveParticipantCommand command,
        @JsonProperty("status") SemesterArchiveParticipantStatus status,
        @JsonProperty("blocking_reason") String blockingReason,
        @JsonProperty("participant_digest") String participantDigest,
        @JsonProperty("counts") SemesterDeletionCounts counts) {

    public SemesterArchiveParticipantAcknowledgement(UUID operationId, long semesterId, long stateVersion,
                                                    SemesterArchiveParticipantCommand command,
                                                    SemesterArchiveParticipantStatus status,
                                                    String blockingReason) {
        this(operationId, semesterId, stateVersion, command, status, blockingReason, null, null);
    }
}
