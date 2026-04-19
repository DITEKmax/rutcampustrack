package ru.rutcampustrack.shared.logback;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class MaskingJsonProviderTest {

    static Stream<Arguments> maskedCases() {
        return Stream.of(
                Arguments.of(
                        "Authorization: Bearer eyJhbGciOiJIUzI1NiJ9.payload.sig",
                        "Authorization: Bearer ***",
                        "Bearer JWT"),
                Arguments.of(
                        "bearer eyAbc.Def.Ghi",
                        "Bearer ***",
                        "case-insensitive match, replacement всегда 'Bearer ***'"),
                Arguments.of(
                        "request={\"telegram_id\":123456789}",
                        "request={\"telegram_id\":\"***\"}",
                        "telegram_id (underscore) numeric"),
                Arguments.of(
                        "payload {\"telegramId\":42}",
                        "payload {\"telegram_id\":\"***\"}",
                        "telegramId (camelCase) numeric"),
                Arguments.of(
                        "quoted {\"telegram_id\":\"-42\"}",
                        "quoted {\"telegram_id\":\"***\"}",
                        "telegram_id quoted negative"),
                Arguments.of(
                        "sent to https://fcm.googleapis.com/fcm/send/abc-TOKEN_xyz:APA91b...",
                        "sent to https://fcm.googleapis.com/***",
                        "FCM endpoint"),
                Arguments.of(
                        "Bearer eyJ1 and telegram_id:42 in one message",
                        "Bearer *** and telegram_id:42 in one message",
                        "Multiple secrets (telegram_id без кавычек не маскируется, это ок — не JSON)")
        );
    }

    @ParameterizedTest(name = "{2}")
    @MethodSource("maskedCases")
    @DisplayName("Маскирование Bearer / telegram_id / FCM endpoint")
    void masks(String input, String expected, String description) {
        assertThat(MaskingJsonProvider.mask(input)).isEqualTo(expected);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "Plain INFO message",
            "User logged in: name=Иван",
            "Timestamp: 2026-04-19T10:00:00Z",
            "Numeric id: 42"
    })
    @DisplayName("Обычные сообщения без секретов — не трогаются")
    void leavesNormalMessagesIntact(String input) {
        assertThat(MaskingJsonProvider.mask(input)).isEqualTo(input);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " "})
    @DisplayName("Пустая строка и пробелы — возвращаются как есть")
    void emptyPassthrough(String input) {
        assertThat(MaskingJsonProvider.mask(input)).isEqualTo(input);
    }

    @org.junit.jupiter.api.Test
    @DisplayName("null → null")
    void nullPassthrough() {
        assertThat(MaskingJsonProvider.mask(null)).isNull();
    }
}
