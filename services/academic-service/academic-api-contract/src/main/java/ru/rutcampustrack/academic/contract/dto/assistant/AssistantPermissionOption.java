package ru.rutcampustrack.academic.contract.dto.assistant;

import ru.rutcampustrack.academic.contract.enums.AssistantPermission;

/**
 * One server-owned assistant permission and its human-readable label.
 *
 * <p>The mobile clients must render the label returned by Academic instead of
 * maintaining a second permission vocabulary.</p>
 */
public record AssistantPermissionOption(
        AssistantPermission code,
        String label
) {
}
