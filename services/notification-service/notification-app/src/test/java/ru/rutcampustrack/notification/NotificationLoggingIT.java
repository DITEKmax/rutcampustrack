package ru.rutcampustrack.notification;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.OutputStreamAppender;
import ch.qos.logback.core.encoder.Encoder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * M01 Группа 8 acceptance: logback-spring.xml notification-service
 * (с {@code <include resource="shared/logback-base.xml"/>}) реально
 * собирает pipeline с MaskingJsonProvider и маскирует Bearer в JSON-выводе.
 *
 * <p>Тест пишет в тот же ROOT logger что и сервис. SHARED_JSON_STDOUT
 * appender пишет в stdout — мы не перехватываем stdout, а присоединяем
 * свой OutputStreamAppender к ROOT с тем же encoder и проверяем output.
 *
 * <p>Не требует Spring-контекст — работает с глобальным Logback'ом.
 */
class NotificationLoggingIT {

    private static final org.slf4j.Logger log = LoggerFactory.getLogger(NotificationLoggingIT.class);

    private ByteArrayOutputStream captured;
    private OutputStreamAppender<ILoggingEvent> captureAppender;

    @BeforeEach
    void setup() {
        LoggerContext ctx = (LoggerContext) LoggerFactory.getILoggerFactory();
        // Берём encoder из shared-web SHARED_JSON_STDOUT — это гарантирует
        // что мы используем тот же pipeline (включая MaskingJsonProvider).
        OutputStreamAppender<ILoggingEvent> sharedAppender =
                (OutputStreamAppender<ILoggingEvent>)
                        ctx.getLogger(Logger.ROOT_LOGGER_NAME).getAppender("SHARED_JSON_STDOUT");
        assertThat(sharedAppender).as("SHARED_JSON_STDOUT из shared/logback-base.xml должен быть подключён").isNotNull();

        Encoder<ILoggingEvent> encoder = sharedAppender.getEncoder();
        captured = new ByteArrayOutputStream();
        captureAppender = new OutputStreamAppender<>();
        captureAppender.setContext(ctx);
        captureAppender.setEncoder(encoder);
        captureAppender.setOutputStream(captured);
        captureAppender.setImmediateFlush(true);
        captureAppender.start();

        Logger rootLogger = ctx.getLogger(Logger.ROOT_LOGGER_NAME);
        rootLogger.setLevel(Level.INFO);
        rootLogger.addAppender(captureAppender);
    }

    @AfterEach
    void teardown() throws IOException {
        LoggerContext ctx = (LoggerContext) LoggerFactory.getILoggerFactory();
        ctx.getLogger(Logger.ROOT_LOGGER_NAME).detachAppender(captureAppender);
        captureAppender.stop();
        captured.close();
    }

    private String readOutput() {
        return new String(captured.toByteArray(), StandardCharsets.UTF_8);
    }

    @Test
    @DisplayName("JSON-вывод с полями service/level/msg (service=notification-web из logback-spring.xml)")
    void jsonStructure() {
        log.info("simple notification message");
        String out = readOutput();
        assertThat(out)
                .contains("\"service\":\"notification-web\"")
                .contains("\"level\":\"INFO\"")
                .contains("\"msg\":\"simple notification message\"");
    }

    @Test
    @DisplayName("Bearer JWT в сообщении → маскирован в JSON-выводе (acceptance shared-logback)")
    void masksBearerInActualPipeline() {
        log.info("Request authorization: Bearer eyJhbGciOiJIUzI1NiJ9.payload.signature");
        String out = readOutput();
        assertThat(out)
                .contains("Bearer ***")
                .doesNotContain("eyJhbGciOiJIUzI1NiJ9")
                .doesNotContain("signature");
    }
}
