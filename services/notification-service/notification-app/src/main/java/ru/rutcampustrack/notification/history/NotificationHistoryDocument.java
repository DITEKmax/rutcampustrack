package ru.rutcampustrack.notification.history;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;
import ru.rutcampustrack.notification.contract.enums.NotificationType;

import java.time.Instant;
import java.util.Map;

/**
 * MongoDB document для персистентной истории уведомлений (M10 / NEW-166).
 *
 * <p>Collection {@code notification_history} в DB {@code notification_db}.
 * Индексы создаются в {@link NotificationHistoryMongoConfig} (включая
 * TTL на {@code sent_at}).
 *
 * <p>{@code payload} — snapshot event payload на момент persist
 * (denormalized, immutable после save). Если lesson/user потом
 * обновится/удалится, history покажет исторический snapshot.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "notification_history")
public class NotificationHistoryDocument {

    @Id
    private String id;

    @Field("user_id")
    private Long userId;

    @Field("type")
    private NotificationType type;

    @Field("payload")
    private Map<String, Object> payload;

    @Field("sent_at")
    private Instant sentAt;

    @Field("read_at")
    private Instant readAt;

    @Field("trace_id")
    private String traceId;
}
