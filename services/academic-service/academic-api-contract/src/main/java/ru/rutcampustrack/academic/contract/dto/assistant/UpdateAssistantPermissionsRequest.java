package ru.rutcampustrack.academic.contract.dto.assistant;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import ru.rutcampustrack.academic.contract.enums.AssistantPermission;

/** A legacy replacement or an atomic conditional replacement/revocation of assistant rights. */
@Schema(description = "Запрос на обновление прав помощника старосты")
public record UpdateAssistantPermissionsRequest(
        @Schema(
                        description =
                                "Новый набор прав. Пустой набор отзывает помощника только при переданном expectedPermissions.",
                        requiredMode = Schema.RequiredMode.REQUIRED)
                @NotNull(message = "Список прав обязателен")
                List<@NotNull AssistantPermission> permissions,
        @Schema(
                        description =
                                "Ранее прочитанный полный набор прав. Сравнение без учёта порядка атомарно; несовпадение возвращает 409 ASSISTANT_PERMISSIONS_CONFLICT. Без поля сохраняется прежнее безусловное обновление.")
                @JsonInclude(JsonInclude.Include.NON_NULL)
                List<@NotNull AssistantPermission> expectedPermissions) {
    public UpdateAssistantPermissionsRequest(List<AssistantPermission> permissions) {
        this(permissions, null);
    }

    @JsonIgnore
    @AssertTrue(message = "Для отзыва последнего права нужен снимок expectedPermissions")
    public boolean isEmptyReplacementConditional() {
        return permissions == null || !permissions.isEmpty() || expectedPermissions != null;
    }
}
