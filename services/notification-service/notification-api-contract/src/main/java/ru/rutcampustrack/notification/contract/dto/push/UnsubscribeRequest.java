package ru.rutcampustrack.notification.contract.dto.push;

import jakarta.validation.constraints.NotBlank;

public record UnsubscribeRequest(@NotBlank String endpoint) {}
