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
        super(SemesterArchiveParticipantCommandEvent.class,
                "semester.archive.participant.command",
                new SemesterArchiveParticipantCommandRequest(operationId, semesterId, stateVersion, command));
    }
}
