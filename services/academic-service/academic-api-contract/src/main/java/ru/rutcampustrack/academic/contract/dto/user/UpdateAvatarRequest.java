package ru.rutcampustrack.academic.contract.dto.user;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * BUG-004: смена аватара текущим пользователем.
 * NULL avatarId — сбросить в дефолт (рисуем инициалы).
 * Допустимый формат: avatar_NN, где NN — две цифры (фронт хранит каталог пресетов).
 */
public record UpdateAvatarRequest(
        @Size(max = 32)
        @Pattern(regexp = "^(avatar_[0-9]{2})?$",
                message = "avatarId должен соответствовать формату avatar_NN или быть пустым")
        String avatarId
) {}
