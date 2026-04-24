package ru.rutcampustrack.notification.history;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.data.mongodb.repository.Update;

/**
 * Spring Data MongoDB repository для notification_history (M10).
 */
public interface NotificationHistoryRepository extends MongoRepository<NotificationHistoryDocument, String> {

    Page<NotificationHistoryDocument> findByUserIdOrderBySentAtDesc(Long userId, Pageable pageable);

    Page<NotificationHistoryDocument> findByUserIdAndReadAtIsNullOrderBySentAtDesc(Long userId, Pageable pageable);

    long countByUserIdAndReadAtIsNull(Long userId);

    @Query("{ '_id': ?0, 'user_id': ?1 }")
    @Update("{ '$set': { 'read_at': ?2 } }")
    long markRead(String id, Long userId, java.time.Instant readAt);

    @Query("{ 'user_id': ?0, 'read_at': null }")
    @Update("{ '$set': { 'read_at': ?1 } }")
    long markAllRead(Long userId, java.time.Instant readAt);
}
