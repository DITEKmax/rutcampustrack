package ru.rutcampustrack.academic.contract.dto.group;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Request DTO for full replacement update of a group (PUT semantics).
 *
 * <p>BUG-006-5: единое поле {@code name}. Ручное переименование оставляет группу активной,
 * поэтому разрешён только активный формат. План 58-06 блокирует PUT для архивных групп
 * на уровне сервиса.
 */
public record UpdateGroupRequest(

        @NotBlank(message = "Название группы обязательно")
        @Pattern(
                regexp = "^[А-ЯЁ][А-ЯЁа-яё]{1,3}-\\d{3}$",
                message = "Формат: ХХ(х)-NNN (пример: УИТ-311)"
        )
        @Size(max = 8, message = "Название не должно превышать 8 символов")
        String name,

        boolean active
) {}
