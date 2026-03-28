package ru.rutcampustrack.auth.dto;

public record PublicKeyResponse(
    String publicKey,
    String algorithm
) {}
