package ru.rutcampustrack.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record OtpVerifyByCodeRequest(
    @NotBlank(message = "code is required")
    @Size(min = 6, max = 6, message = "code must be 6 digits")
    String code
) {}
