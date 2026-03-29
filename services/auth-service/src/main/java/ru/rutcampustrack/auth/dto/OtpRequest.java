package ru.rutcampustrack.auth.dto;

import jakarta.validation.constraints.NotNull;

public record OtpRequest(
    @NotNull(message = "telegram_id is required")
    Long telegramId
) {}
