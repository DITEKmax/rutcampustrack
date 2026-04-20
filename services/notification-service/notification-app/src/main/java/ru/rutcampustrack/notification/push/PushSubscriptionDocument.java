package ru.rutcampustrack.notification.push;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.time.Instant;

/**
 * MongoDB document representing a Web Push subscription for a student.
 *
 * Per D-05: Fields use snake_case names to align with MongoDB naming conventions.
 * Stored in the push_subscriptions collection.
 * Unique compound index on (user_id, endpoint) enforced by PushMongoConfig.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "push_subscriptions")
public class PushSubscriptionDocument {

    @Id
    private String id;

    @Field("user_id")
    private Long userId;

    @Field("group_id")
    private Long groupId;

    @Field("endpoint")
    private String endpoint;

    @Field("p256dh")
    private String p256dh;

    @Field("auth")
    private String auth;

    /**
     * Староста группы на момент оформления подписки. Используется Web Push
     * маршрутизацией для событий, адресованных только старостам
     * (excuse.requested, late_checkin.requested).
     *
     * Примечание: снимок значения на момент subscribe. Если староста
     * перевыбирается, PWA должна переподписаться для обновления флага.
     */
    @Field("is_headman")
    private boolean headman;

    @Field("created_at")
    private Instant createdAt;

    /**
     * M05 G7 (NEW-148): последняя успешная доставка Web Push.
     *
     * Обновляется в {@link WebPushDeliveryService} на каждый successful
     * {@code webPushService.send(...)}. Cleanup job удаляет подписки с
     * {@code last_seen < now() - 90d} (subscription либо отозвана без
     * 410 Gone, либо устройство давно не подключается к сети).
     *
     * Для docs, созданных до M05 G7, backfill: {@code last_seen =
     * createdAt} (см. PushSubscriptionCleanupJob.backfillMissingLastSeen).
     */
    @Field("last_seen")
    private Instant lastSeen;
}
