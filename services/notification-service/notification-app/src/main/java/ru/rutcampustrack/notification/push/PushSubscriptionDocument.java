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

    @Field("created_at")
    private Instant createdAt;
}
