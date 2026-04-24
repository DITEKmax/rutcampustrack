package ru.rutcampustrack.academic.contract.dto.user;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * BUG-004: смена аватара текущим пользователем.
 * NULL avatarId — сбросить в дефолт (рисуем инициалы).
 * Допустимый формат: avatar_NN, где NN — две цифры (фронт хранит каталог пресетов).
 */
@Schema(description = "Смена аватара пользователя (BUG-004)")
public record UpdateAvatarRequest(
        @Schema(description = "ID пресет-аватара формата avatar_NN (NN — 2 цифры). " +
                "NULL или пустая строка — сбросить в дефолт (инициалы)",
                example = "avatar_03", maxLength = 32,
                pattern = "^(avatar_[0-9]{2})?$")
        @Size(max = 32)
        @Pattern(regexp = "^(avatar_[0-9]{2})?$",
                message = "avatarId должен соответствовать формату avatar_NN или быть пустым")
        String avatarId
) {}
