package ru.rutcampustrack.shared.web.api.exception;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ErrorResponseTest {

    private final ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());

    @Test
    @DisplayName("M11 G0: 6-arg constructor (auth-service legacy) — null'ы для extension fields")
    void sixArgConstructor() {
        ErrorResponse er = new ErrorResponse(
                401, "about:blank", "Unauthorized", "Bad creds",
                "/api/auth/login", Instant.parse("2026-04-24T10:15:30Z"));

        assertThat(er.status()).isEqualTo(401);
        assertThat(er.traceId()).isNull();
        assertThat(er.fieldErrors()).isNull();
        assertThat(er.field()).isNull();
        assertThat(er.extras()).isNull();
    }

    @Test
    @DisplayName("M11 G0: 7-arg constructor (schedule/attendance legacy) — fieldErrors заполнен")
    void sevenArgConstructor() {
        FieldError fe = new FieldError("dayOfWeek", 7, "must be between 0 and 5");
        ErrorResponse er = new ErrorResponse(
                400, "https://api.rutcampustrack.ru/problems/validation",
                "Validation failed", "Bad input",
                "/api/schedule/items", Instant.now(), List.of(fe));

        assertThat(er.fieldErrors()).hasSize(1);
        assertThat(er.fieldErrors().get(0).field()).isEqualTo("dayOfWeek");
        assertThat(er.field()).isNull();
    }

    @Test
    @DisplayName("M11 G0: 9-arg constructor (academic full) — field + extras")
    void nineArgConstructor() {
        ErrorResponse er = new ErrorResponse(
                409, ErrorResponse.PROBLEM_BASE + "conflict",
                "Конфликт", "Группа имеет студентов",
                "/api/academic/groups/1", Instant.now(),
                null, "name", Map.of("studentsCount", 12));

        assertThat(er.field()).isEqualTo("name");
        assertThat(er.extras()).containsEntry("studentsCount", 12);
    }

    @Test
    @DisplayName("M11 G0: factory notFound() возвращает корректный 404")
    void factoryNotFound() {
        ErrorResponse er = ErrorResponse.notFound("Group 99 not found", "/api/groups/99", "trace-1");

        assertThat(er.status()).isEqualTo(404);
        assertThat(er.title()).isEqualTo("Ресурс не найден");
        assertThat(er.type()).isEqualTo(ErrorResponse.PROBLEM_BASE + "resource-not-found");
        assertThat(er.traceId()).isEqualTo("trace-1");
    }

    @Test
    @DisplayName("M11 G0: JSON serialization исключает null поля (JsonInclude.NON_NULL)")
    void jsonSerializationOmitsNulls() throws Exception {
        ErrorResponse er = new ErrorResponse(
                401, "about:blank", "Unauthorized", "Bad creds",
                "/api/auth/login", Instant.parse("2026-04-24T10:15:30Z"));

        String json = mapper.writeValueAsString(er);

        assertThat(json).contains("\"status\":401");
        assertThat(json).contains("\"title\":\"Unauthorized\"");
        assertThat(json).doesNotContain("traceId");
        assertThat(json).doesNotContain("fieldErrors");
        assertThat(json).doesNotContain("field");
        assertThat(json).doesNotContain("extras");
    }

    @Test
    @DisplayName("M11 G0: FieldError сериализуется (для fieldErrors[] body)")
    void fieldErrorSerialization() throws Exception {
        FieldError fe = new FieldError("login", "max", "Логин уже используется");
        String json = mapper.writeValueAsString(fe);
        assertThat(json).contains("\"field\":\"login\"");
        assertThat(json).contains("\"rejectedValue\":\"max\"");
        assertThat(json).contains("\"message\":\"Логин уже используется\"");
    }

    @Test
    @DisplayName("M11 G0: 8-arg constructor (academic с field, без extras)")
    void eightArgConstructor() {
        ErrorResponse er = new ErrorResponse(
                409, ErrorResponse.PROBLEM_BASE + "conflict",
                "Конфликт", "Login занят",
                "/api/users", Instant.now(), null, "login");

        assertThat(er.field()).isEqualTo("login");
        assertThat(er.extras()).isNull();
        assertThat(er.traceId()).isNull();
    }

    @Test
    @DisplayName("M11 G0: factory badRequest() возвращает 400")
    void factoryBadRequest() {
        ErrorResponse er = ErrorResponse.badRequest("Invalid JSON", "/api/x", "t-1");
        assertThat(er.status()).isEqualTo(400);
        assertThat(er.title()).isEqualTo("Неверный запрос");
        assertThat(er.type()).isEqualTo(ErrorResponse.PROBLEM_BASE + "bad-request");
    }

    @Test
    @DisplayName("M11 G0: factory internal() возвращает 500")
    void factoryInternal() {
        ErrorResponse er = ErrorResponse.internal("Oops", "/api/x", "t-1");
        assertThat(er.status()).isEqualTo(500);
        assertThat(er.title()).isEqualTo("Внутренняя ошибка сервера");
        assertThat(er.type()).isEqualTo(ErrorResponse.PROBLEM_BASE + "internal-error");
    }

    @Test
    @DisplayName("M11 G0: InvalidParam record — все поля")
    void invalidParamAllFields() {
        InvalidParam ip = new InvalidParam("groupId", "Параметр обязателен", null);
        assertThat(ip.name()).isEqualTo("groupId");
        assertThat(ip.reason()).isEqualTo("Параметр обязателен");
        assertThat(ip.rejectedValue()).isNull();
    }

    @Test
    @DisplayName("M11 G0: FieldError record — все accessors")
    void fieldErrorAccessors() {
        FieldError fe = new FieldError("email", "not-an-email", "Невалидный email");
        assertThat(fe.field()).isEqualTo("email");
        assertThat(fe.rejectedValue()).isEqualTo("not-an-email");
        assertThat(fe.message()).isEqualTo("Невалидный email");
    }
}
