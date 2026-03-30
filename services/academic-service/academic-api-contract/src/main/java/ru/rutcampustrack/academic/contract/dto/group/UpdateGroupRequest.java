package ru.rutcampustrack.academic.contract.dto.group;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateGroupRequest(
        @NotBlank(message = "Название группы не может быть пустым")
        @Size(max = 50, message = "Название группы не более 50 символов")
        String name,

        @NotBlank(message = "Код группы не может быть пустым")
        @Size(max = 20, message = "Код группы не более 20 символов")
        String code,

        boolean active
) {}
