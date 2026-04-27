package ru.rutcampustrack.academic.contract.dto.user;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Минимальный публичный профиль пользователя для display purposes
 * (имя рядом с audit-записями: «отменено таким-то», «отметку поставил такой-то» и т.п.).
 *
 * <p>Намеренно содержит только {@code id} и {@code fullName} — без login, email,
 * telegramId, role, group и прочих PII. Используется в endpoint'ах доступных
 * не-ADMIN ролям (STUDENT/TEACHER) для batch-резолва имён по ID.
 *
 * <p>Sibling {@link UserResponse} оставлен ADMIN-only.
 */
@Schema(description = "Минимальный display-профиль (id + fullName), безопасный для STUDENT/TEACHER batch-резолва")
public class UserSummaryResponse {

    @Schema(description = "ID пользователя")
    private Long id;

    @Schema(description = "Display name: «Иванов Иван Иванович» или «Иванов Иван» если отчества нет")
    private String fullName;

    public UserSummaryResponse() {}

    public UserSummaryResponse(Long id, String fullName) {
        this.id = id;
        this.fullName = fullName;
    }

    public Long getId() {
        return id;
    }

    public String getFullName() {
        return fullName;
    }
}
