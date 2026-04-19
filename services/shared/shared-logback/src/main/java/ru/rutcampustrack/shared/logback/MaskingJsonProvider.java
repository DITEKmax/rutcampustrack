package ru.rutcampustrack.shared.logback;

import ch.qos.logback.classic.spi.ILoggingEvent;
import com.fasterxml.jackson.core.JsonGenerator;
import net.logstash.logback.composite.loggingevent.MessageJsonProvider;

import java.io.IOException;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Расширение {@link MessageJsonProvider} с regex-маскированием (P2-6/1):
 * <ul>
 *   <li>{@code Bearer eyJ...} → {@code Bearer ***}</li>
 *   <li>{@code "telegram_id": 123456789} → {@code "telegram_id":"***"}</li>
 *   <li>{@code https://fcm.googleapis.com/fcm/send/<token>} → {@code https://fcm.googleapis.com/***}</li>
 * </ul>
 *
 * <p>Применяется только к полю {@code message} JSON-лога. Остальные поля
 * (MDC, stack traces, key-value pairs) не трогаются — для них regex может
 * дать false-positive на структурированные данные.
 *
 * <p>Подключается в {@code logback-base.xml} в составе {@code LoggingEventCompositeJsonEncoder}.
 */
public class MaskingJsonProvider extends MessageJsonProvider {

    static final Pattern BEARER_TOKEN =
            Pattern.compile("Bearer\\s+ey[A-Za-z0-9_\\-.]+", Pattern.CASE_INSENSITIVE);

    static final Pattern TELEGRAM_ID =
            Pattern.compile("\"telegram_?id\"\\s*:\\s*(?:\"-?\\d+\"|-?\\d+)", Pattern.CASE_INSENSITIVE);

    static final Pattern FCM_ENDPOINT =
            Pattern.compile("https://fcm\\.googleapis\\.com/[^\\s\"']+", Pattern.CASE_INSENSITIVE);

    private static final List<Replacement> REPLACEMENTS = List.of(
            new Replacement(BEARER_TOKEN, "Bearer ***"),
            new Replacement(TELEGRAM_ID, "\"telegram_id\":\"***\""),
            new Replacement(FCM_ENDPOINT, "https://fcm.googleapis.com/***")
    );

    @Override
    public void writeTo(JsonGenerator generator, ILoggingEvent event) throws IOException {
        String masked = mask(event.getFormattedMessage());
        generator.writeStringField(getFieldName(), masked);
    }

    static String mask(String input) {
        if (input == null || input.isEmpty()) {
            return input;
        }
        String out = input;
        for (Replacement r : REPLACEMENTS) {
            out = r.pattern().matcher(out).replaceAll(r.replacement());
        }
        return out;
    }

    private record Replacement(Pattern pattern, String replacement) {
    }
}
