package ru.rutcampustrack.auth.dto;

public record TokenPair(
    String accessToken,
    String refreshToken,
    long expiresIn
) {}
