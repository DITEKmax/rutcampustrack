package ru.rutcampustrack.academic.contract.dto.group;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

public record GroupOptionsResponse(
        List<Item> items, int number, int size, long totalElements, int totalPages) {
    public record Item(
            long id,
            String name,
            @Schema(
                            description = "Группа архивирована",
                            requiredMode = Schema.RequiredMode.REQUIRED)
                    boolean archived) {
        public Item(long id, String name) {
            this(id, name, false);
        }
    }
}
