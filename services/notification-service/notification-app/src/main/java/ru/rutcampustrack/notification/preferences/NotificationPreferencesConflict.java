package ru.rutcampustrack.notification.preferences;

import ru.rutcampustrack.notification.contract.dto.preferences.NotificationPreferencesV2Dto;

public class NotificationPreferencesConflict extends RuntimeException {
    private final NotificationPreferencesV2Dto current;

    public NotificationPreferencesConflict(NotificationPreferencesV2Dto current) {
        super("Notification preferences revision conflicts");
        this.current = current;
    }

    public NotificationPreferencesV2Dto current() {
        return current;
    }
}
