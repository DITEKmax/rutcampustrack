package ru.rutcampustrack.shared.logback;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.core.OutputStreamAppender;
import net.logstash.logback.composite.loggingevent.LogLevelJsonProvider;
import net.logstash.logback.composite.loggingevent.LoggingEventFormattedTimestampJsonProvider;
import net.logstash.logback.composite.loggingevent.LoggingEventJsonProviders;
import net.logstash.logback.composite.loggingevent.LoggingEventPatternJsonProvider;
import net.logstash.logback.encoder.LoggingEventCompositeJsonEncoder;
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
 * Интеграционный тест: программно строит Logback pipeline с
 * {@link MaskingJsonProvider} и проверяет что JSON-вывод содержит
 * замаскированные значения.
 *
 * <p>Использует программную конфигурацию (не XML), чтобы не зависеть от
 * классpath-discovery logback-test.xml и параллельного запуска тестов.
 */
class LogbackBaseIntegrationTest {

    private LoggerContext ctx;
    private ByteArrayOutputStream captured;
    private Logger logger;

    @BeforeEach
    void setup() {
        ctx = (LoggerContext) LoggerFactory.getILoggerFactory();
        captured = new ByteArrayOutputStream();

        MaskingJsonProvider masking = new MaskingJsonProvider();
        masking.setFieldName("msg");

        LogLevelJsonProvider levelProvider = new LogLevelJsonProvider();
        levelProvider.setFieldName("level");

        LoggingEventFormattedTimestampJsonProvider tsProvider =
                new LoggingEventFormattedTimestampJsonProvider();
        tsProvider.setFieldName("ts");

        LoggingEventPatternJsonProvider servicePattern = new LoggingEventPatternJsonProvider();
        servicePattern.setPattern("{\"service\":\"test-service\"}");
        // start() вызовет encoder — ему нужен JsonFactory который энкодер инжектирует.

        LoggingEventJsonProviders providers = new LoggingEventJsonProviders();
        providers.addProvider(tsProvider);
        providers.addProvider(levelProvider);
        providers.addProvider(masking);
        providers.addProvider(servicePattern);

        LoggingEventCompositeJsonEncoder encoder = new LoggingEventCompositeJsonEncoder();
        encoder.setProviders(providers);
        encoder.setContext(ctx);
        encoder.start();

        OutputStreamAppender<ch.qos.logback.classic.spi.ILoggingEvent> appender =
                new OutputStreamAppender<>();
        appender.setContext(ctx);
        appender.setEncoder(encoder);
        appender.setOutputStream(captured);
        appender.setImmediateFlush(true);
        appender.start();

        logger = ctx.getLogger("ru.rutcampustrack.test.LogbackCaptureLogger");
        logger.setLevel(Level.INFO);
        logger.setAdditive(false);
        logger.detachAndStopAllAppenders();
        logger.addAppender(appender);
    }

    @AfterEach
    void tearDown() throws IOException {
        if (logger != null) {
            logger.detachAndStopAllAppenders();
        }
        captured.close();
    }

    private String readOutput() {
        return new String(captured.toByteArray(), StandardCharsets.UTF_8);
    }

    @Test
    @DisplayName("JSON-вывод содержит поля service/level/msg")
    void jsonStructure() {
        logger.info("simple message");
        String out = readOutput();
        assertThat(out)
                .contains("\"service\":\"test-service\"")
                .contains("\"level\":\"INFO\"")
                .contains("\"msg\":\"simple message\"");
    }

    @Test
    @DisplayName("Bearer JWT в сообщении → маскирован в JSON")
    void masksBearerInJsonOutput() {
        logger.info("Auth header: Bearer eyJhbGciOiJIUzI1NiJ9.payload.signature");
        String out = readOutput();
        assertThat(out)
                .contains("Bearer ***")
                .doesNotContain("eyJhbGciOiJIUzI1NiJ9")
                .doesNotContain("signature");
    }

    @Test
    @DisplayName("telegram_id в JSON-сообщении → маскирован")
    void masksTelegramIdInJsonOutput() {
        logger.info("incoming {\"telegram_id\":987654321}");
        String out = readOutput();
        assertThat(out)
                .contains("telegram_id")
                .contains("***")
                .doesNotContain("987654321");
    }

    @Test
    @DisplayName("FCM endpoint → маскирован")
    void masksFcmEndpointInJsonOutput() {
        logger.info("push to https://fcm.googleapis.com/fcm/send/abc-TOKEN_xyz:APA91b");
        String out = readOutput();
        assertThat(out)
                .contains("https://fcm.googleapis.com/***")
                .doesNotContain("APA91b")
                .doesNotContain("TOKEN_xyz");
    }
}
