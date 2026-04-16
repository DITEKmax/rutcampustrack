package ru.rutcampustrack.attendance.contract.dto.latecheckin;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

/**
 * Headman's decision on a student's late-checkin request submitted from a web client
 * (web-panel, PWA, mini-app). Telegram-bot decisions go through RabbitMQ and do not
 * hit this endpoint.
 */
@Schema(description = "Решение старосты по запросу поздней отметки")
public record LateCheckinDecisionRequest(
        @Schema(description = "true — подтвердить присутствие, false — отклонить", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull
        Boolean approved
) {}
