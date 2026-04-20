package ru.rutcampustrack.notification.alert;

import java.util.Map;

/**
 * M04 Группа 9 — упрощённое представление Alertmanager alert'а.
 * {@link AlertController} парсит Alertmanager webhook payload и мапит
 * в эту структуру (по одному {@code AlertPayload} на alert в пачке).
 *
 * @param name        значение label {@code alertname}
 * @param severity    label {@code severity} (critical | warning)
 * @param status      {@code firing} или {@code resolved}
 * @param summary     annotation {@code summary}
 * @param description annotation {@code description}
 * @param labels      все labels (dedup информация если понадобится в bot'е)
 */
public record AlertPayload(
        String name,
        String severity,
        String status,
        String summary,
        String description,
        Map<String, String> labels
) {
    public Map<String, Object> toMap() {
        return Map.of(
                "name", name == null ? "unknown" : name,
                "severity", severity == null ? "unknown" : severity,
                "status", status == null ? "unknown" : status,
                "summary", summary == null ? "" : summary,
                "description", description == null ? "" : description,
                "labels", labels == null ? Map.of() : labels
        );
    }
}
