package ru.rutcampustrack.academic.event;

import ru.rutcampustrack.academic.contract.dto.semester.SemesterArchiveParticipantCommandRequest;
import ru.rutcampustrack.academic.contract.enums.SemesterArchiveParticipantCommand;
import ru.rutcampustrack.shared.events.EventVersion;

import java.util.UUID;

@EventVersion(1)
public final class SemesterArchiveParticipantCommandEvent extends DomainEvent {

    public SemesterArchiveParticipantCommandEvent(UUID operationId,
                                                  long semesterId,
                                                  long stateVersion,
                                                  SemesterArchiveParticipantCommand command) {
        this(operationId, semesterId, stateVersion, command, null);
    }

    public SemesterArchiveParticipantCommandEvent(UUID operationId,
                                                  long semesterId,
                                                  long stateVersion,
                                                  SemesterArchiveParticipantCommand command,
                                                  String expectedParticipantDigest) {
        super(SemesterArchiveParticipantCommandEvent.class,
                "semester.archive.participant.command",
                new SemesterArchiveParticipantCommandRequest(operationId, semesterId, stateVersion, command,
                        expectedParticipantDigest));
    }
}
