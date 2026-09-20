package ru.rutcampustrack.auth.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;
import java.util.Objects;

@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(name = "AccountHistoryPage")
public record AccountHistoryPage(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<AccountHistoryEvent> items,
        @Schema(nullable = true) String nextCursor
) {
    public AccountHistoryPage {
        items = List.copyOf(Objects.requireNonNull(items, "items"));
    }
}
