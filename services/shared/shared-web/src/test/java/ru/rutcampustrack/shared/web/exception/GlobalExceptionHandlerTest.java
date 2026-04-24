package ru.rutcampustrack.shared.web.exception;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Path;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.NoHandlerFoundException;
import ru.rutcampustrack.shared.web.api.exception.ErrorResponse;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();
    private final MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/test/resource");

    @AfterEach
    void cleanupMdc() {
        MDC.clear();
    }

    @Test
    @DisplayName("MethodArgumentNotValid → 400 + fieldErrors[] с field/rejectedValue/message")
    void handleMethodArgumentNotValid() {
        BindingResult binding = mock(BindingResult.class);
        when(binding.getFieldErrors()).thenReturn(List.of(
                new FieldError("dto", "email", "not-an-email", false, null, null, "must be a valid email"),
                new FieldError("dto", "age", -1, false, null, null, "must be positive")
        ));
        MethodArgumentNotValidException ex = mock(MethodArgumentNotValidException.class);
        when(ex.getBindingResult()).thenReturn(binding);

        ResponseEntity<ErrorResponse> resp = handler.handleMethodArgumentNotValid(ex, request);

        assertProblem(resp, HttpStatus.BAD_REQUEST, "validation-failed");
        ErrorResponse body = resp.getBody();
        assertThat(body).isNotNull();
        assertThat(body.fieldErrors()).hasSize(2);
        assertThat(body.fieldErrors().get(0).field()).isEqualTo("email");
        assertThat(body.fieldErrors().get(0).message()).isEqualTo("must be a valid email");
        assertThat(body.fieldErrors().get(0).rejectedValue()).isEqualTo("not-an-email");
        assertThat(body.fieldErrors().get(1).rejectedValue()).isEqualTo(-1);
    }

    @Test
    @DisplayName("ConstraintViolation → 400 + fieldErrors[] из propertyPath")
    void handleConstraintViolation() {
        ConstraintViolation<Object> v = mockViolation("createUser.login", "size must be >=3", "ab");
        ConstraintViolationException ex = new ConstraintViolationException(Set.of(v));

        ResponseEntity<ErrorResponse> resp = handler.handleConstraintViolation(ex, request);

        assertProblem(resp, HttpStatus.BAD_REQUEST, "validation-failed");
        assertThat(resp.getBody().fieldErrors()).hasSize(1);
        assertThat(resp.getBody().fieldErrors().get(0).field()).isEqualTo("createUser.login");
        assertThat(resp.getBody().fieldErrors().get(0).rejectedValue()).isEqualTo("ab");
    }

    @Test
    @DisplayName("HttpMessageNotReadable → 400 malformed-request")
    void handleNotReadable() {
        HttpMessageNotReadableException ex = new HttpMessageNotReadableException(
                "JSON parse error",
                new ServletServerHttpRequest(request));

        ResponseEntity<ErrorResponse> resp = handler.handleNotReadable(ex, request);

        assertProblem(resp, HttpStatus.BAD_REQUEST, "malformed-request");
    }

    @Test
    @DisplayName("HttpMediaTypeNotSupported → 415")
    void handleMediaType() {
        HttpMediaTypeNotSupportedException ex =
                new HttpMediaTypeNotSupportedException(MediaType.TEXT_XML, List.of(MediaType.APPLICATION_JSON));

        ResponseEntity<ErrorResponse> resp = handler.handleMediaType(ex, request);

        assertProblem(resp, HttpStatus.UNSUPPORTED_MEDIA_TYPE, "media-type-not-supported");
    }

    @Test
    @DisplayName("MissingServletRequestParameter → 400 + fieldErrors[field]")
    void handleMissingParam() {
        MissingServletRequestParameterException ex =
                new MissingServletRequestParameterException("userId", "Long");

        ResponseEntity<ErrorResponse> resp = handler.handleMissingParam(ex, request);

        assertProblem(resp, HttpStatus.BAD_REQUEST, "missing-parameter");
        assertThat(resp.getBody().fieldErrors()).hasSize(1);
        assertThat(resp.getBody().fieldErrors().get(0).field()).isEqualTo("userId");
    }

    @Test
    @DisplayName("MethodArgumentTypeMismatch → 400 + fieldErrors[field, rejectedValue]")
    void handleTypeMismatch() {
        MethodArgumentTypeMismatchException ex = mock(MethodArgumentTypeMismatchException.class);
        when(ex.getName()).thenReturn("groupId");
        when(ex.getValue()).thenReturn("not-a-number");

        ResponseEntity<ErrorResponse> resp = handler.handleTypeMismatch(ex, request);

        assertProblem(resp, HttpStatus.BAD_REQUEST, "type-mismatch");
        assertThat(resp.getBody().fieldErrors().get(0).field()).isEqualTo("groupId");
        assertThat(resp.getBody().fieldErrors().get(0).rejectedValue()).isEqualTo("not-a-number");
    }

    @Test
    @DisplayName("HttpRequestMethodNotSupported → 405 + detail содержит метод")
    void handleMethodNotSupported() {
        HttpRequestMethodNotSupportedException ex =
                new HttpRequestMethodNotSupportedException("DELETE");

        ResponseEntity<ErrorResponse> resp = handler.handleMethodNotSupported(ex, request);

        assertProblem(resp, HttpStatus.METHOD_NOT_ALLOWED, "method-not-allowed");
        assertThat(resp.getBody().detail()).contains("DELETE");
    }

    @Test
    @DisplayName("NoHandlerFound → 404")
    void handleNoHandler() {
        NoHandlerFoundException ex =
                new NoHandlerFoundException("GET", "/no/such", new org.springframework.http.HttpHeaders());

        ResponseEntity<ErrorResponse> resp = handler.handleNoHandler(ex, request);

        assertProblem(resp, HttpStatus.NOT_FOUND, "resource-not-found");
    }

    @Test
    @DisplayName("AccessDenied → 403")
    void handleAccessDenied() {
        AccessDeniedException ex = new AccessDeniedException("Required role: [ADMIN]");

        ResponseEntity<ErrorResponse> resp = handler.handleAccessDenied(ex, request);

        assertProblem(resp, HttpStatus.FORBIDDEN, "access-denied");
        assertThat(resp.getBody().detail()).isEqualTo("Required role: [ADMIN]");
    }

    @Test
    @DisplayName("handleGeneral → 500 + traceId из MDC")
    void handleGeneral_withTraceId() {
        MDC.put(GlobalExceptionHandler.MDC_TRACE_ID, "abc-trace-123");
        RuntimeException ex = new RuntimeException("boom");

        ResponseEntity<ErrorResponse> resp = handler.handleGeneral(ex, request);

        assertProblem(resp, HttpStatus.INTERNAL_SERVER_ERROR, "internal-error");
        assertThat(resp.getBody().traceId()).isEqualTo("abc-trace-123");
        // Не должно утекать message эксепшна в response detail.
        assertThat(resp.getBody().detail()).doesNotContain("boom");
    }

    @Test
    @DisplayName("handleGeneral без MDC → traceId=null, скрыт через @JsonInclude")
    void handleGeneral_withoutTraceId() {
        RuntimeException ex = new RuntimeException("boom");

        ResponseEntity<ErrorResponse> resp = handler.handleGeneral(ex, request);

        assertThat(resp.getBody().traceId()).isNull();
    }

    private static void assertProblem(ResponseEntity<ErrorResponse> resp,
                                       HttpStatus expectedStatus,
                                       String expectedTypeSuffix) {
        assertThat(resp.getStatusCode()).isEqualTo(expectedStatus);
        assertThat(resp.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_PROBLEM_JSON);
        assertThat(resp.getBody()).isNotNull();
        assertThat(resp.getBody().status()).isEqualTo(expectedStatus.value());
        assertThat(resp.getBody().type()).isEqualTo(ErrorResponse.PROBLEM_BASE + expectedTypeSuffix);
        assertThat(resp.getBody().instance()).isEqualTo("/api/test/resource");
        assertThat(resp.getBody().timestamp()).isNotNull();
    }

    @SuppressWarnings("unchecked")
    private static ConstraintViolation<Object> mockViolation(String path, String message, Object invalidValue) {
        ConstraintViolation<Object> v = mock(ConstraintViolation.class);
        Path p = mock(Path.class);
        when(p.toString()).thenReturn(path);
        when(v.getPropertyPath()).thenReturn(p);
        when(v.getMessage()).thenReturn(message);
        when(v.getInvalidValue()).thenReturn(invalidValue);
        return v;
    }
}
