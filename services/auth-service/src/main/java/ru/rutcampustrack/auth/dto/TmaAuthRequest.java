package ru.rutcampustrack.auth.dto;

import jakarta.validation.constraints.NotBlank;

public record TmaAuthRequest(
    @NotBlank(message = "initData must not be blank") String initData
) {}
