package ru.rutcampustrack.notification.push;

import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

/**
 * Spring Data MongoDB repository for push subscriptions.
 *
 * Per D-06: findAllByGroupId used by Plan 03 PushService to retrieve all subscribers
 * in a group for fanout delivery.
 * deleteByUserIdAndEndpoint scopes deletion to current user — T-27-07 mitigation.
 */
public interface PushSubscriptionRepository extends MongoRepository<PushSubscriptionDocument, String> {

    List<PushSubscriptionDocument> findAllByGroupId(Long groupId);

    void deleteByUserIdAndEndpoint(Long userId, String endpoint);

    void deleteByEndpoint(String endpoint);
}
