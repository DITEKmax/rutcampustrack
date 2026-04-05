package ru.rutcampustrack.auth.dto;

/**
 * Response DTO for OTP request — returns generated code.
 * Used by notification-bot to deliver OTP via Telegram message.
 */
public record OtpCodeResponse(String code) {}
