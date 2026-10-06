package ru.rutcampustrack.academic.contract.dto.subject;

import java.time.LocalDate;
import java.util.UUID;
import ru.rutcampustrack.academic.contract.enums.SubjectType;

/** Original receipt disclosed only to the current headman of the exact subject group. */
public record PendingClosureResponse(UUID operationId,Long assignmentId,UUID requestKey,
        LocalDate validUntilExclusive,Long teacherId,Long subjectId,Long groupId,Long semesterId,SubjectType lessonType) { }
