package ru.rutcampustrack.notification.contract.dto.history;

import java.util.List;

public record NotificationCatalogDto(int schemaVersion, String revision, List<Group> groups,
                                     List<Type> types, List<Color> palette) {
    public record Group(String id, String label) {}
    public record Type(String id, String groupId, String label, List<String> roles) {}
    public record Color(String id, String label) {}
}
