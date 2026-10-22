package ru.rutcampustrack.notification.history;

import static org.mockito.Mockito.*;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;

class NotificationHistoryConsumerTest {
    @Test
    void existingCommittedClaimSkipsTransactionalWriterOtherwiseDelegatesOriginalEnvelope() {
        var mongo = mock(MongoTemplate.class);
        var processor = mock(NotificationHistoryEventProcessor.class);
        var consumer = new NotificationHistoryConsumer(mongo, processor);
        var event =
                NotificationAudienceAdmissionTest.envelope(
                        "semester.activated", Map.of("semester_id", 3L));
        when(mongo.exists(any(Query.class), anyString())).thenReturn(true);
        consumer.onEvent(event);
        verifyNoInteractions(processor);
        when(mongo.exists(any(Query.class), anyString())).thenReturn(false);
        consumer.onEvent(event);
        verify(processor).persist(event);
    }
}
