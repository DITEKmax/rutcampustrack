package ru.rutcampustrack.academic.contract.dto.user;

import java.util.List;

public record AdminAccessInput(String mode, List<String> permissions) {}
