package ru.rutcampustrack.notification.event;

import static org.mockito.Mockito.*;

import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import ru.rutcampustrack.notification.history.*;
import ru.rutcampustrack.notification.preferences.NotificationPreferencesService;
import ru.rutcampustrack.notification.push.*;
import ru.rutcampustrack.shared.events.IdempotencyGuard;

class GroupEventTest {
    @Test
    void groupFactsUseSealedRoleAudienceAndNeverUnprotectedGroupBroadcast() {
        var messaging = mock(SimpMessagingTemplate.class);
        var push = mock(WebPushDeliveryService.class);
        var guard = mock(IdempotencyGuard.class);
        var audience = mock(NotificationAudienceService.class);
        var history = mock(NotificationHistoryEventProcessor.class);
        var intents = mock(NotificationPushIntentStore.class);
        var prefs = mock(NotificationPreferencesService.class);
        when(guard.tryClaim(anyString(), anyMap())).thenReturn(true);
        var consumer =
                new EventConsumer(messaging, push, guard, null, audience, history, intents, prefs);
        for (String type : List.of("group.renamed", "group.archived")) {
            var event =
                    EventConsumerTest.event(
                            type,
                            Map.of("group_id", 7L, "notification_cause", "AUTOMATIC_GROUP_CYCLE"));
            var admitted = Map.of(11L, Set.of("STUDENT", "HEADMAN"));
            when(audience.snapshot(event)).thenReturn(admitted);
            consumer.onEvent(event);
            verify(history).persistResolved(event, admitted);
        }
        verifyNoInteractions(messaging, intents);
    }
}
