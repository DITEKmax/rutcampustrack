package ru.rutcampustrack.notification.contract.dto.history;

import io.swagger.v3.oas.annotations.media.Schema;
import org.springframework.hateoas.RepresentationModel;
import ru.rutcampustrack.notification.contract.enums.NotificationType;

import java.time.Instant;
import java.util.Map;

/**
 * Response DTO для одной записи в notification_history (M10).
 *
 * <p>CLAUDE.md: Response DTO = class (для HATEOAS RepresentationModel).
 * payload — snapshot события на момент persist, immutable после save.
 */
@Schema(description = "Запись истории уведомлений пользователя (TTL 30 дней): тип, payload-snapshot, времена отправки/прочтения")
public class NotificationHistoryDto extends RepresentationModel<NotificationHistoryDto> {

    private String id;
    private Long userId;
    private NotificationType type;
    private Map<String, Object> payload;
    private Instant sentAt;
    private Instant readAt;

    public NotificationHistoryDto() {}

    public NotificationHistoryDto(String id,
                                  Long userId,
                                  NotificationType type,
                                  Map<String, Object> payload,
                                  Instant sentAt,
                                  Instant readAt) {
        this.id = id;
        this.userId = userId;
        this.type = type;
        this.payload = payload;
        this.sentAt = sentAt;
        this.readAt = readAt;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }

    public NotificationType getType() { return type; }
    public void setType(NotificationType type) { this.type = type; }

    public Map<String, Object> getPayload() { return payload; }
    public void setPayload(Map<String, Object> payload) { this.payload = payload; }

    public Instant getSentAt() { return sentAt; }
    public void setSentAt(Instant sentAt) { this.sentAt = sentAt; }

    public Instant getReadAt() { return readAt; }
    public void setReadAt(Instant readAt) { this.readAt = readAt; }
}
