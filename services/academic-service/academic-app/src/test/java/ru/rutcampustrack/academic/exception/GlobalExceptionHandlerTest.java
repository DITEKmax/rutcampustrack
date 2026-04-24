package ru.rutcampustrack.academic.exception;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import ru.rutcampustrack.shared.web.api.exception.ErrorResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * Unit tests for GlobalExceptionHandler — conflict handling (BUG-006-2).
 *
 * Verifies:
 * <ul>
 *   <li>ConflictException → 409 + body.field populated per constructor</li>
 *   <li>DataIntegrityViolationException with known constraint → 409 + field mapped</li>
 *   <li>Unknown constraint → 500 fallback</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class GlobalExceptionHandlerTest {

    @Mock
    private HttpServletRequest request;

    private GlobalExceptionHandler handler;

    @BeforeEach
    void setUp() {
        handler = new GlobalExceptionHandler();
        when(request.getRequestURI()).thenReturn("/api/academic/users");
    }

    @Test
    void conflictExceptionWithLoginFieldReturns409AndFieldInBody() {
        ConflictException ex = new ConflictException("login", "student00001",
                "Логин уже занят");

        ResponseEntity<ErrorResponse> response = handler.handleConflict(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().status()).isEqualTo(409);
        assertThat(response.getBody().field()).isEqualTo("login");
        assertThat(response.getBody().detail()).contains("Логин");
    }

    @Test
    void conflictExceptionWithEmailFieldReturns409AndFieldEmail() {
        ConflictException ex = new ConflictException("email", "a@b.ru",
                "Email уже зарегистрирован");

        ResponseEntity<ErrorResponse> response = handler.handleConflict(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody().field()).isEqualTo("email");
    }

    @Test
    void conflictExceptionWithTelegramIdFieldReturns409() {
        ConflictException ex = new ConflictException("telegramId", 12345L,
                "Telegram ID уже используется другой учётной записью");

        ResponseEntity<ErrorResponse> response = handler.handleConflict(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody().field()).isEqualTo("telegramId");
    }

    @Test
    void conflictExceptionWithEmployeeNumberFieldReturns409() {
        ConflictException ex = new ConflictException("employeeNumber", "EMP-001",
                "Табельный номер занят");

        ResponseEntity<ErrorResponse> response = handler.handleConflict(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody().field()).isEqualTo("employeeNumber");
    }

    @Test
    void dataIntegrityViolationWithKnownLoginConstraintReturns409AndFieldLogin() {
        // Simulate Hibernate/PostgreSQL exception carrying a constraint-name message.
        DataIntegrityViolationException ex = new DataIntegrityViolationException(
                "could not execute statement",
                new RuntimeException("duplicate key value violates unique constraint \"users_login_key\"")
        );

        ResponseEntity<ErrorResponse> response = handler.handleDataIntegrityViolation(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().field()).isEqualTo("login");
    }

    @Test
    void dataIntegrityViolationWithKnownEmailConstraintReturns409AndFieldEmail() {
        DataIntegrityViolationException ex = new DataIntegrityViolationException(
                "could not execute statement",
                new RuntimeException("duplicate key value violates unique constraint \"users_email_key\"")
        );

        ResponseEntity<ErrorResponse> response = handler.handleDataIntegrityViolation(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody().field()).isEqualTo("email");
    }

    @Test
    void dataIntegrityViolationWithKnownTelegramConstraintReturns409AndFieldTelegramId() {
        DataIntegrityViolationException ex = new DataIntegrityViolationException(
                "could not execute statement",
                new RuntimeException("duplicate key value violates unique constraint \"users_telegram_id_key\"")
        );

        ResponseEntity<ErrorResponse> response = handler.handleDataIntegrityViolation(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody().field()).isEqualTo("telegramId");
    }

    @Test
    void dataIntegrityViolationWithKnownEmployeeNumberConstraintReturns409AndFieldEmployeeNumber() {
        DataIntegrityViolationException ex = new DataIntegrityViolationException(
                "could not execute statement",
                new RuntimeException("duplicate key value violates unique constraint \"users_employee_number_key\"")
        );

        ResponseEntity<ErrorResponse> response = handler.handleDataIntegrityViolation(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody().field()).isEqualTo("employeeNumber");
    }

    @Test
    void dataIntegrityViolationWithUnknownConstraintReturns500() {
        DataIntegrityViolationException ex = new DataIntegrityViolationException(
                "foo",
                new RuntimeException("duplicate key value violates unique constraint \"unknown_constraint\"")
        );

        ResponseEntity<ErrorResponse> response = handler.handleDataIntegrityViolation(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().field()).isNull();
    }

    @Test
    void dataIntegrityViolationWithoutConstraintMessageReturns500() {
        DataIntegrityViolationException ex = new DataIntegrityViolationException(
                "something bad happened without a constraint name"
        );

        ResponseEntity<ErrorResponse> response = handler.handleDataIntegrityViolation(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
    }
}
