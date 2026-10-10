package ru.rutcampustrack.notification.history;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import ru.rutcampustrack.notification.contract.dto.history.NotificationHistoryDto;
import ru.rutcampustrack.notification.contract.dto.history.UnreadCountDto;

import java.time.Instant;

/**
 * Business logic для notification history (M10 G4 / G5).
 *
 * <p>Unread-count кешируется в Caffeine (30s TTL); cache evict'ится при
 * {@link #markAsRead} / {@link #markAllRead} и при persist нового
 * события через {@link #invalidateUnreadCount}.
 */
@Service
@Slf4j
public class NotificationHistoryService {

    private final NotificationHistoryRepository repository;
    private final CacheManager cacheManager;
    private NotificationInboxService inbox;
    private ru.rutcampustrack.notification.security.RequestContext context;

    public NotificationHistoryService(NotificationHistoryRepository repository,
                                      CacheManager cacheManager) {
        this.repository = repository;
        this.cacheManager = cacheManager;
    }

    @org.springframework.beans.factory.annotation.Autowired
    public NotificationHistoryService(NotificationHistoryRepository repository, CacheManager cacheManager,
            NotificationInboxService inbox, ru.rutcampustrack.notification.security.RequestContext context) {
        this(repository, cacheManager); this.inbox = inbox; this.context = context;
    }

    private void admit(Long userId) {
        if (!java.util.Objects.equals(context.getUserId(), userId)) throw new IllegalArgumentException("Notification recipient mismatch");
        context.requireSelectedRole();
    }

    public Page<NotificationHistoryDto> list(Long userId, boolean unreadOnly, Pageable pageable) {
        if (inbox != null) { admit(userId); return inbox.legacyList(context, unreadOnly, pageable); }
        Page<NotificationHistoryDocument> docs = unreadOnly
                ? repository.findByUserIdAndReadAtIsNullOrderBySentAtDesc(userId, pageable)
                : repository.findByUserIdOrderBySentAtDesc(userId, pageable);
        return docs.map(this::toDto);
    }

    public UnreadCountDto getUnreadCount(Long userId) {
        if (inbox != null) { admit(userId); return new UnreadCountDto(inbox.count(context).count()); }
        long count = repository.countByUserIdAndReadAtIsNull(userId);
        return new UnreadCountDto(count);
    }

    @CacheEvict(cacheNames = CaffeineConfig.UNREAD_COUNT_CACHE, key = "#userId")
    public boolean markAsRead(String id, Long userId) {
        if (inbox != null) { admit(userId); return inbox.legacyRead(context, id); }
        long modified = repository.markRead(id, userId, Instant.now());
        return modified > 0;
    }

    @CacheEvict(cacheNames = CaffeineConfig.UNREAD_COUNT_CACHE, key = "#userId")
    public long markAllRead(Long userId) {
        if (inbox != null) { admit(userId); return inbox.markAllRead(context); }
        return repository.markAllRead(userId, Instant.now());
    }

    /**
     * Вызывается {@link NotificationHistoryConsumer} после persist нового
     * события — cached unread-count стал устаревшим, evict моментально
     * чтобы next badge refresh в UI показал +1 без 30s задержки.
     */
    public void invalidateUnreadCount(Long userId) {
        Cache cache = cacheManager.getCache(CaffeineConfig.UNREAD_COUNT_CACHE);
        if (cache != null) {
            cache.evict(userId);
        }
    }

    private NotificationHistoryDto toDto(NotificationHistoryDocument doc) {
        return new NotificationHistoryDto(
                doc.getId(),
                doc.getUserId(),
                doc.getType(),
                doc.getPayload(),
                doc.getSentAt(),
                doc.getReadAt());
    }
}
