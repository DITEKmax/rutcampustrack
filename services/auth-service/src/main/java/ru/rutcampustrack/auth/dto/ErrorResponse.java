package ru.rutcampustrack.auth.dto;

import java.time.Instant;

public record ErrorResponse(
    String type,
    String title,
    int status,
    String detail,
    String instance,
    Instant timestamp
) {}
