package ru.rutcampustrack.academic.group;

import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import ru.rutcampustrack.academic.event.GroupUpdatedEvent;

/** Keep committed group snapshots until the transaction that changed them actually commits. */
@Component
public class GroupCacheInvalidationListener {
    private final CacheManager caches;

    public GroupCacheInvalidationListener(CacheManager caches) {
        this.caches = caches;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onGroupUpdated(GroupUpdatedEvent event) {
        Long groupId = ((GroupUpdatedEvent.Payload) event.getPayload()).groupId();
        evict(caches.getCache("groups"), groupId);
        evict(caches.getCache("group_members"), groupId);
    }

    private static void evict(Cache cache, Long groupId) {
        if (cache != null) {
            cache.evict(groupId);
        }
    }
}
