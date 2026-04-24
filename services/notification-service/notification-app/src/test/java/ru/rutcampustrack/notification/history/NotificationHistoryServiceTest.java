package ru.rutcampustrack.notification.history;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import ru.rutcampustrack.notification.contract.dto.history.UnreadCountDto;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationHistoryServiceTest {

    @Mock
    private NotificationHistoryRepository repository;

    @Mock
    private CacheManager cacheManager;

    @Mock
    private Cache cache;

    private NotificationHistoryService service;

    @BeforeEach
    void setUp() {
        service = new NotificationHistoryService(repository, cacheManager);
    }

    @Test
    void getUnreadCountDelegatesToRepository() {
        when(repository.countByUserIdAndReadAtIsNull(42L)).thenReturn(7L);

        UnreadCountDto result = service.getUnreadCount(42L);

        assertThat(result.count()).isEqualTo(7L);
    }

    @Test
    void markAsReadReturnsFalseWhenNothingUpdated() {
        when(repository.markRead(eq("abc"), eq(1L), any(Instant.class))).thenReturn(0L);

        boolean ok = service.markAsRead("abc", 1L);

        assertThat(ok).isFalse();
    }

    @Test
    void markAsReadReturnsTrueOnUpdate() {
        when(repository.markRead(eq("abc"), eq(1L), any(Instant.class))).thenReturn(1L);

        boolean ok = service.markAsRead("abc", 1L);

        assertThat(ok).isTrue();
    }

    @Test
    void markAllReadReturnsCount() {
        when(repository.markAllRead(eq(1L), any(Instant.class))).thenReturn(12L);

        long count = service.markAllRead(1L);

        assertThat(count).isEqualTo(12L);
    }

    @Test
    void invalidateUnreadCountEvictsCache() {
        when(cacheManager.getCache(CaffeineConfig.UNREAD_COUNT_CACHE)).thenReturn(cache);

        service.invalidateUnreadCount(99L);

        verify(cache).evict(99L);
    }

    @Test
    void invalidateUnreadCountTolerantToMissingCache() {
        when(cacheManager.getCache(CaffeineConfig.UNREAD_COUNT_CACHE)).thenReturn(null);

        // Не должно бросить.
        service.invalidateUnreadCount(99L);

        verify(cache, never()).evict(anyLong());
        verify(cache, never()).evict(anyString());
    }
}
