package ru.rutcampustrack.notification.contract.dto.push;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

@Schema(description = "Запрос подписки клиента на Web Push (endpoint + ключи шифрования)")
public record SubscribeRequest(
        @Schema(description = "Endpoint push-подписки (FCM, Mozilla autopush и т.д.)",
                example = "https://fcm.googleapis.com/fcm/send/abc123DEF456",
                requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank String endpoint,

        @Schema(description = "Публичные ключи подписки (p256dh, auth) из PushSubscription",
                requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull @Valid Keys keys
) {
    @Schema(description = "Ключи шифрования Web Push подписки")
    public record Keys(
            @Schema(description = "Публичный ключ P-256 ECDH в base64url",
                    example = "BKR2jT5k1Iu93_V8AHx3cpqE...",
                    requiredMode = Schema.RequiredMode.REQUIRED)
            @NotBlank String p256dh,

            @Schema(description = "Auth secret в base64url",
                    example = "tBHItJI5svbpez7KI4CCXg",
                    requiredMode = Schema.RequiredMode.REQUIRED)
            @NotBlank String auth
    ) {}
}
