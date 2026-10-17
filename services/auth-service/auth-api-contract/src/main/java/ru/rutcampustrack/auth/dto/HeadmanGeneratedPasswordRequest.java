package ru.rutcampustrack.auth.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

public record HeadmanGeneratedPasswordRequest(
        @NotNull @Pattern(regexp = "[1-9][0-9]{0,18}") String expectedCredentialsVersion) {}
