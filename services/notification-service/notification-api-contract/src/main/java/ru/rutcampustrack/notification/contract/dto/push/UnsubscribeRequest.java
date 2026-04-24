package ru.rutcampustrack.notification.contract.dto.push;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "Запрос на отписку Web Push подписки по endpoint")
public record UnsubscribeRequest(
        @Schema(description = "Endpoint push-подписки, которую нужно удалить",
                example = "https://fcm.googleapis.com/fcm/send/abc123DEF456",
                requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank String endpoint
) {}
