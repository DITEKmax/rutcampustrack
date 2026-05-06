package ru.rutcampustrack.notification.push;

import org.springframework.data.mongodb.repository.MongoRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Spring Data MongoDB repository for push subscriptions.
 *
 * Per D-06: findAllByGroupId used by Plan 03 PushService to retrieve all subscribers
 * in a group for fanout delivery.
 * deleteByUserIdAndEndpoint scopes deletion to current user — T-27-07 mitigation.
 *
 * M05 G7 (NEW-148): touchLastSeen + deleteByLastSeenBefore для retention
 * cleanup stale push-подписок (см. PushSubscriptionCleanupJob).
 */
public interface PushSubscriptionRepository extends MongoRepository<PushSubscriptionDocument, String> {

    List<PushSubscriptionDocument> findAllByGroupId(Long groupId);

    Optional<PushSubscriptionDocument> findByUserIdAndEndpoint(Long userId, String endpoint);

    void deleteByUserIdAndEndpoint(Long userId, String endpoint);

    void deleteByEndpoint(String endpoint);

    long deleteByLastSeenBefore(Instant cutoff);
}
