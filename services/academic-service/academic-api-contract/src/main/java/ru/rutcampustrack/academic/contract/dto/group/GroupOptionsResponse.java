package ru.rutcampustrack.academic.contract.dto.group;

import java.util.List;

public record GroupOptionsResponse(
        List<Item> items, int number, int size, long totalElements, int totalPages) {
    public record Item(long id, String name) {}
}
