package ru.rutcampustrack.academic.assignment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.persistence.PersistenceException;
import org.junit.jupiter.api.Test;
import org.springframework.orm.jpa.JpaSystemException;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.rutcampustrack.academic.exception.GlobalExceptionHandler;
import ru.rutcampustrack.academic.grpc.GrpcExceptionAdvice;

/** Converter failures are nested by JPA; transport boundaries must still refuse them safely. */
class AssignmentCodeBoundaryTest {
    private static JpaSystemException persistedUnknown() {
        try {
            new ru.rutcampustrack.academic.config.AssignmentLifecycleConverter()
                    .convertToEntityAttribute("FUTURE_SYNTHETIC_CODE");
            throw new AssertionError("unknown code was accepted");
        } catch (ru.rutcampustrack.academic.exception.UnknownAssignmentCodeException expected) {
            return new JpaSystemException(new PersistenceException("Converter failure", expected));
        }
    }

    @RestController
    static class ReadFixture {
        @GetMapping("/assignment-code-fixture")
        String read() {
            throw persistedUnknown();
        }
    }

    @Test
    void nestedJpaConverterFailureReturnsConflictWithoutExposingRawCode() throws Exception {
        var response =
                MockMvcBuilders.standaloneSetup(new ReadFixture())
                        .setControllerAdvice(new GlobalExceptionHandler())
                        .build()
                        .perform(get("/assignment-code-fixture"))
                        .andExpect(status().isConflict())
                        .andReturn()
                        .getResponse();
        assertThat(response.getContentAsString())
                .doesNotContain("FUTURE_SYNTHETIC_CODE", "Converter failure");
    }

    @Test
    void nestedJpaConverterFailureReturnsGrpcFailedPrecondition() {
        var status = new GrpcExceptionAdvice().handleInternal(persistedUnknown());
        assertThat(status.getCode()).isEqualTo(io.grpc.Status.Code.FAILED_PRECONDITION);
        assertThat(status.getDescription())
                .doesNotContain("FUTURE_SYNTHETIC_CODE", "Converter failure");
    }
}
