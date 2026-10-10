package ru.rutcampustrack.notification.history;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.mock.http.MockHttpOutputMessage;
import ru.rutcampustrack.notification.contract.dto.history.NotificationInboxDto;
import ru.rutcampustrack.shared.web.config.JacksonConfig;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class NotificationV2SerializationTest {
    @Test void absentSummaryIsOmittedButRequiredNullsAndIsoDatesRemainOnTheWire() throws Exception {
        var builder = Jackson2ObjectMapperBuilder.json();
        new JacksonConfig().sharedJacksonCustomizer().customize(builder);
        ObjectMapper mapper = builder.build();
        var converter = new MappingJackson2HttpMessageConverter(mapper);
        Instant sentAt = Instant.parse("2026-10-10T08:00:00Z");
        Instant readAt = Instant.parse("2026-10-10T08:05:00Z");
        var page = new NotificationInboxDto.Page("HEADMAN", List.of(
                new NotificationInboxDto.Item("synthetic_unread", "LESSON_CLOSED", "Занятие завершено",
                        null, sentAt, sentAt, null, "1",
                        List.of(new NotificationInboxDto.Detail("Описание", "Синтетическое значение")), null),
                new NotificationInboxDto.Item("synthetic_read", "HOMEWORK_PUBLISHED", "Добавлено задание",
                        "Синтетический предмет", sentAt, readAt, readAt, "2",
                        List.of(new NotificationInboxDto.Detail("Дата", "2026-10-10")),
                        new NotificationInboxDto.Action("homework", "HEADMAN", null, "2026-10-10",
                                null, null, "11", null, null))), null);

        JsonNode wire = write(mapper, converter, page);
        JsonNode unread = wire.path("items").get(0);
        JsonNode read = wire.path("items").get(1);
        assertFalse(unread.has("summary"));
        assertEquals("Описание", unread.path("details").get(0).path("label").textValue());
        assertEquals("Синтетическое значение", unread.path("details").get(0).path("value").textValue());
        assertTrue(unread.has("readAt") && unread.get("readAt").isNull());
        assertTrue(unread.has("action") && unread.get("action").isNull());
        assertTrue(wire.has("nextCursor") && wire.get("nextCursor").isNull());
        assertEquals(sentAt.toString(), unread.path("sentAt").textValue());
        assertEquals(sentAt.toString(), unread.path("orderAt").textValue());
        assertEquals(readAt.toString(), read.path("readAt").textValue());
        assertEquals(readAt.toString(), read.path("orderAt").textValue());
        assertEquals("Синтетический предмет", read.path("summary").textValue());
        assertEquals("Дата", read.path("details").get(0).path("label").textValue());
        assertEquals("2", read.path("revision").textValue());
        assertEquals("11", read.path("action").path("homeworkId").textValue());
        assertFalse(read.path("action").has("lessonId"));
    }

    private static JsonNode write(ObjectMapper mapper, MappingJackson2HttpMessageConverter converter,
                                  Object value) throws Exception {
        var output = new MockHttpOutputMessage();
        converter.write(value, MediaType.APPLICATION_JSON, output);
        return mapper.readTree(output.getBodyAsBytes());
    }
}
