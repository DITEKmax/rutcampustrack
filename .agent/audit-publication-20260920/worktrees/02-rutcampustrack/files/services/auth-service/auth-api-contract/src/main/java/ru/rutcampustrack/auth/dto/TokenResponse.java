package ru.rutcampustrack.auth.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record TokenResponse(
    String accessToken,
    String refreshToken,
    long expiresIn
) {
    @Override
    public String toString() {
        return "TokenResponse[accessToken=<redacted>, refreshToken=<redacted>, expiresIn="
                + expiresIn + ']';
    }
}
