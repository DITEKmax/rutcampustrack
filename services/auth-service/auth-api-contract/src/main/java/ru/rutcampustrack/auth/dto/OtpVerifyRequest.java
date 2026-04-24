package ru.rutcampustrack.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record OtpVerifyRequest(
    @NotNull(message = "telegram_id is required")
    Long telegramId,
    @NotBlank(message = "code is required")
    @Size(min = 6, max = 6, message = "code must be 6 digits")
    String code
) {}
