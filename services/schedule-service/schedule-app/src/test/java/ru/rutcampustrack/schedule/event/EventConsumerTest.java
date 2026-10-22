package ru.rutcampustrack.schedule.event;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;

import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import ru.rutcampustrack.shared.events.IdempotencyGuard;

import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

class EventConsumerTest {
    @Test
    void archiveEffectAckFailureKeepsOriginalExceptionAndPrivateCorrelation() {
        var guard = mock(IdempotencyGuard.class);
        var ledger = mock(SemesterArchiveEffectLedger.class);
        var consumer =
                new EventConsumer(
                        mock(ru.rutcampustrack.schedule.subject.SubjectDeletedCascadeService.class),
                        guard,
                        mock(ru.rutcampustrack.schedule.lesson.LessonTransferWriter.class),
                        ledger);
        String eventId = UUID.randomUUID().toString();
        var envelope =
                Map.<String, Object>of(
                        "event_type",
                        "semester.archive.effect.ack",
                        "event_id",
                        eventId,
                        "event_version",
                        1,
                        "source",
                        "attendance-service",
                        "trace_id",
                        "private-ack-correlation",
                        "payload",
                        Map.of(
                                "source_event_id",
                                UUID.randomUUID().toString(),
                                "target",
                                "ATTENDANCE",
                                "source_event_type",
                                "lesson.closed",
                                "semester_id",
                                3,
                                "payload_hash",
                                "a".repeat(64),
                                "result",
                                "APPLIED"));
        when(guard.tryClaim("schedule", envelope)).thenReturn(true);
        var logger = (Logger) LoggerFactory.getLogger(EventConsumer.class);
        var previousLevel = logger.getLevel();
        var previousMdc = MDC.getCopyOfContextMap();
        var appender =
                new ListAppender<ILoggingEvent>() {
                    @Override
                    protected void append(ILoggingEvent entry) {
                        entry.prepareForDeferredProcessing();
                        super.append(entry);
                    }
                };
        appender.setContext(logger.getLoggerContext());
        appender.start();
        logger.addAppender(appender);
        logger.setLevel(Level.WARN);
        try {
            MDC.put("eventId", "previous-event");
            var cases = new LinkedHashMap<String, String>();
            cases.put("42501", "SQL_PERMISSION");
            cases.put("42P01", "SQL_UNDEFINED_TABLE");
            cases.put("42703", "SQL_UNDEFINED_COLUMN");
            cases.put("23505", "SQL_CONSTRAINT");
            cases.put("40001", "SQL_SERIALIZATION");
            cases.put("40P01", "SQL_DEADLOCK");
            cases.put("08006", "SQL_CONNECTION");
            cases.put("PRIVATE_UNKNOWN_STATE", "SQL_OTHER");
            cases.put(null, "SQL_OTHER");
            var failures = new LinkedHashMap<RuntimeException, String>();
            for (var entry : cases.entrySet())
                failures.put(
                        new org.springframework.jdbc.UncategorizedSQLException(
                                "PRIVATE_TASK",
                                "PRIVATE_SQL",
                                new SQLException("PRIVATE_ERROR", entry.getKey())),
                        entry.getValue());
            failures.put(new IllegalArgumentException("PRIVATE_ERROR"), "INVALID_ARGUMENT");
            failures.put(
                    new org.springframework.dao.DataAccessResourceFailureException("PRIVATE_ERROR"),
                    "DATA_ACCESS");
            failures.put(new IllegalStateException("PRIVATE_ERROR"), "OTHER");
            for (var entry : failures.entrySet()) {
                doThrow(entry.getKey())
                        .when(ledger)
                        .acknowledge(
                                any(),
                                anyString(),
                                anyString(),
                                anyLong(),
                                anyString(),
                                anyString(),
                                isNull(),
                                any());
                int before = appender.list.size();
                assertSame(
                        entry.getKey(),
                        assertThrows(RuntimeException.class, () -> consumer.onEvent(envelope)));
                assertEquals(before + 1, appender.list.size());
                var record = appender.list.getLast();
                assertEquals(
                        "archive.effect.ack failure category=" + entry.getValue(),
                        record.getFormattedMessage());
                assertEquals(1, record.getArgumentArray().length);
                assertTrue(record.getArgumentArray()[0] instanceof Enum<?>);
                assertNull(record.getThrowableProxy());
                assertFalse(record.getFormattedMessage().contains("PRIVATE"));
                assertFalse(record.getFormattedMessage().contains(eventId));
                assertEquals(eventId, record.getMDCPropertyMap().get("eventId"));
                assertEquals("previous-event", MDC.get("eventId"));
            }
            doNothing()
                    .when(ledger)
                    .acknowledge(
                            any(),
                            anyString(),
                            anyString(),
                            anyLong(),
                            anyString(),
                            anyString(),
                            isNull(),
                            any());
            int before = appender.list.size();
            assertDoesNotThrow(() -> consumer.onEvent(envelope));
            assertEquals(before, appender.list.size());
        } finally {
            logger.detachAppender(appender);
            appender.stop();
            logger.setLevel(previousLevel);
            MDC.clear();
            if (previousMdc != null) MDC.setContextMap(previousMdc);
        }
    }
}
