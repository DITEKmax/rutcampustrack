package ru.rutcampustrack.notification.preferences;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import java.time.Clock;
import org.junit.jupiter.api.Test;

class NotificationPreferencesServiceTest {

    @Test
    void lessonClosedUsesLessonsPreferenceCategory() {
        NotificationPreferencesService service =
                new NotificationPreferencesService(
                        mock(NotificationPreferencesStore.class), Clock.systemUTC());

        assertThat(service.categoryForEvent("lesson.closed")).isEqualTo("lessons");
    }
}
