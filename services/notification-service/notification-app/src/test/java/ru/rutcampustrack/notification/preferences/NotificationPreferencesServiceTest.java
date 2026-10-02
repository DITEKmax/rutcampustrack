package ru.rutcampustrack.notification.preferences;

import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Clock;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class NotificationPreferencesServiceTest {

    @Test
    void lessonClosedUsesLessonsPreferenceCategory() {
        NotificationPreferencesService service = new NotificationPreferencesService(
                mock(NotificationPreferencesStore.class), Clock.systemUTC());

        assertThat(service.categoryForEvent("lesson.closed")).isEqualTo("lessons");
    }
}
