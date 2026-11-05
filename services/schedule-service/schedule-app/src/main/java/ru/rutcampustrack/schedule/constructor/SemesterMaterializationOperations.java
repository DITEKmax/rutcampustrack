package ru.rutcampustrack.schedule.constructor;

import java.util.UUID;
import ru.rutcampustrack.schedule.contract.dto.item.SemesterMaterializationResponse;

/** Narrow API integration boundary owned separately from generation implementation. */
public interface SemesterMaterializationOperations {
    SemesterMaterializationResponse materializeExisting(long itemId, UUID key);
    SemesterMaterializationResponse operation(UUID operationId);
}
