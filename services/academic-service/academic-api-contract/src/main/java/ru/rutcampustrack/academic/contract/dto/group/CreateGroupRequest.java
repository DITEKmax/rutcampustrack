package ru.rutcampustrack.academic.contract.dto.group;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request DTO for creating a new student group.
 */
public record CreateGroupRequest(

        @NotBlank(message = "Название группы обязательно")
        @Size(max = 50, message = "Название не должно превышать 50 символов")
        String name,

        @NotBlank(message = "Код группы обязателен")
        @Size(max = 20, message = "Код не должен превышать 20 символов")
        String code
) {}
