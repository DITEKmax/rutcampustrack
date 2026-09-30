package ru.rutcampustrack.auth.controller;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import ru.rutcampustrack.auth.exception.GlobalExceptionHandler;
import ru.rutcampustrack.auth.exception.PasswordResetValidationExceptionHandler;
import ru.rutcampustrack.auth.service.PasswordResetService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class PasswordResetValidationExceptionHandlerTest {

    private static final String VALIDATION_TYPE =
            "https://api.rutcampustrack.ru/problems/validation-failed";

    @Test
    void invalidRequestAndProofValuesAreNotIncludedInRecoveryErrors() throws Exception {
        MockMvc mvc = MockMvcBuilders.standaloneSetup(
                        new PasswordResetController(mock(PasswordResetService.class)))
                .setControllerAdvice(
                        new PasswordResetValidationExceptionHandler(),
                        new GlobalExceptionHandler(),
                        new ru.rutcampustrack.shared.web.exception.GlobalExceptionHandler())
                .build();

        assertValidationDoesNotEcho(mvc, "/auth/password-reset/request",
                "{\"login\":\"LOGIN_SENTINEL_" + "x".repeat(140) + "\"}", "LOGIN_SENTINEL_");
        assertValidationDoesNotEcho(mvc, "/auth/password-reset/verify",
                "{\"challengeId\":\"challenge-1\",\"code\":\"CODE_SENTINEL_123456789\"}",
                "CODE_SENTINEL_");
        assertValidationDoesNotEcho(mvc, "/auth/password-reset/complete",
                "{\"resetTicket\":\"TICKET_SENTINEL_" + "t".repeat(70)
                        + "\",\"newPassword\":\"PASSWORD_SENTINEL_" + "p".repeat(260) + "\"}",
                "TICKET_SENTINEL_", "PASSWORD_SENTINEL_");
    }

    private static void assertValidationDoesNotEcho(
            MockMvc mvc,
            String path,
            String requestBody,
            String... sentinels) throws Exception {
        MvcResult result = mvc.perform(post(path)
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.APPLICATION_PROBLEM_JSON)
                        .content(requestBody))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(header().string("Cache-Control", "no-store"))
                .andReturn();

        String body = result.getResponse().getContentAsString();
        assertThat(body).contains(VALIDATION_TYPE, "fieldErrors", "validation-failed");
        for (String sentinel : sentinels) {
            assertThat(body).doesNotContain(sentinel);
        }
    }
}
