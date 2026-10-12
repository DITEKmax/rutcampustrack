package ru.rutcampustrack.academic.contract.dto.subject;

import java.time.LocalDate;
import java.util.UUID;
import ru.rutcampustrack.academic.contract.enums.SubjectType;

/** Recovery handle disclosed only to the currently authorized headman of the subject's group. */
public record PendingAdoptionResponse(
        Long targetAssignmentId,
        UUID adoptionOperationId,
        String state,
        Long teacherId,
        Long subjectId,
        Long groupId,
        Long semesterId,
        SubjectType lessonType,
        LocalDate validFrom,
        LocalDate validUntilExclusive) {}
