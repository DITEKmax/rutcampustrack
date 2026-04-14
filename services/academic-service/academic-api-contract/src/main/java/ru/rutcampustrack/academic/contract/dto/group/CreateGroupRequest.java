package ru.rutcampustrack.academic.contract.dto.group;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Request DTO for creating a new student group.
 *
 * <p>BUG-006-5: единое поле {@code name}. CreateRequest разрешает ТОЛЬКО активный формат
 * {@code ^[А-ЯЁ][А-ЯЁа-яё]{1,3}-\d{3}$} — клиент не может создать архивную группу
 * (суффикс {@code (выпуск YYYY)} ставит только сервис архивации, см. план 58-06).
 */
public record CreateGroupRequest(

        @NotBlank(message = "Название группы обязательно")
        @Pattern(
                regexp = "^[А-ЯЁ][А-ЯЁа-яё]{1,3}-\\d{3}$",
                message = "Формат: ХХ(х)-NNN (пример: УИТ-311)"
        )
        @Size(max = 8, message = "Название не должно превышать 8 символов")
        String name
) {}
