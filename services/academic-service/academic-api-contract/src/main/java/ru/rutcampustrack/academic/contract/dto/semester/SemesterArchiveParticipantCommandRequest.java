package ru.rutcampustrack.academic.contract.dto.semester;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import ru.rutcampustrack.academic.contract.enums.SemesterArchiveParticipantCommand;

import java.util.UUID;

/** Versioned internal command identity shared by the Academic and Attendance event handlers. */
@Schema(description = "Команда локальному write barrier для одной версии операции архивации")
public record SemesterArchiveParticipantCommandRequest(
        @JsonProperty("operation_id") UUID operationId,
        @JsonProperty("semester_id") long semesterId,
        @JsonProperty("state_version") long stateVersion,
        @JsonProperty("command") SemesterArchiveParticipantCommand command,
        @JsonProperty("expected_participant_digest") String expectedParticipantDigest) {

    public SemesterArchiveParticipantCommandRequest(UUID operationId, long semesterId, long stateVersion,
                                                    SemesterArchiveParticipantCommand command) {
        this(operationId, semesterId, stateVersion, command, null);
    }
}
