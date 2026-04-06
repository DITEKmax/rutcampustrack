package ru.rutcampustrack.auth.dto;

public record AccessTokenResponse(
    String accessToken,
    long expiresIn
) {}
