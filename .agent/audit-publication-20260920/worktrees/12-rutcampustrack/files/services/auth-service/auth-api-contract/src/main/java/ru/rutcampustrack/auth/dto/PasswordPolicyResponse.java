package ru.rutcampustrack.auth.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.Locale;
import java.util.Objects;

@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(name = "PasswordPolicyResponse")
public record PasswordPolicyResponse(
        @Min(1) int minCodePoints,
        @Min(1) int maxUtf8Bytes,
        boolean requiresDecimalDigit,
        @NotNull List<String> specialCategories,
        @NotNull String normalization
) {
    public PasswordPolicyResponse {
        if (minCodePoints <= 0) {
            throw new IllegalArgumentException("minCodePoints must be positive");
        }
        if (maxUtf8Bytes <= 0 || maxUtf8Bytes < minCodePoints) {
            throw new IllegalArgumentException("maxUtf8Bytes must be positive and cover minCodePoints");
        }
        specialCategories = List.copyOf(Objects.requireNonNull(specialCategories, "specialCategories"));
        if (specialCategories.stream().anyMatch(category ->
                category == null || category.length() != 1 || !"PS".contains(category))) {
            throw new IllegalArgumentException("specialCategories must contain Unicode category P or S");
        }
        Objects.requireNonNull(normalization, "normalization");
        if (!normalization.equals(normalization.toUpperCase(Locale.ROOT))) {
            throw new IllegalArgumentException("normalization must be an uppercase wire value");
        }
    }
}
