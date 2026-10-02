package ru.rutcampustrack.academic.contract.dto.homework;

import java.time.OffsetDateTime;

public record HomeworkHistoryResponse(Long id, long revision, Long actorId,
                                      OffsetDateTime occurredAt, String action,
                                      HomeworkSnapshot before, HomeworkSnapshot after) {}
