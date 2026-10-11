package ru.rutcampustrack.auth.controller;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import ru.rutcampustrack.auth.exception.PasswordResetValidationExceptionHandler;
import ru.rutcampustrack.auth.exception.QrLoginExceptionHandler;
import ru.rutcampustrack.auth.qr.QrLoginClientIp;
import ru.rutcampustrack.auth.service.QrLoginService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class QrLoginValidationExceptionHandlerTest {

    private final MockMvc mvc = MockMvcBuilders.standaloneSetup(
                    new QrLoginController(mock(QrLoginService.class), mock(QrLoginClientIp.class)))
            .setControllerAdvice(new QrLoginExceptionHandler(), new PasswordResetValidationExceptionHandler(),
                    new ru.rutcampustrack.auth.exception.GlobalExceptionHandler(),
                    new ru.rutcampustrack.shared.web.exception.GlobalExceptionHandler())
            .build();

    @Test
    void invalidBrowserProofAndApprovalValuesAreRedacted() throws Exception {
        String marker = "RCT_QR_PROOF_SENTINEL";
        String proof = "{\"purpose\":\"LOGIN\",\"issuerId\":\"00000000-0000-0000-0000-000000000001\","
                + "\"challengeId\":\"00000000-0000-0000-0000-000000000002\",\"issuerSecret\":\"" + marker + "\"}";
        for (String path : new String[]{"/auth/qr/status", "/auth/qr/exchange"}) {
            assertSafeValidation(mvc, path, proof, marker);
        }
        String approval = "{\"purpose\":\"LOGIN\",\"challengeId\":\"00000000-0000-0000-0000-000000000002\","
                + "\"approvalToken\":\"" + marker + "\"";
        assertSafeValidation(mvc, "/auth/qr/preview", approval + "}", marker);
        assertSafeValidation(mvc, "/auth/qr/decision", approval + ",\"decision\":\"CONFIRM\"}", marker);
    }

    @Test
    void malformedAndUnknownEnumProofsAreNotCacheable() throws Exception {
        for (String body : new String[]{"{", "{\"purpose\":\"UNKNOWN\",\"issuerSecret\":\"RCT_QR_PROOF_SENTINEL\"}"}) {
            MvcResult result = mvc.perform(post("/auth/qr/status")
                            .contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest()).andReturn();
            assertThat(result.getResponse().getHeader("Cache-Control")).isEqualTo("no-store");
            assertThat(result.getResponse().getContentAsString()).doesNotContain("RCT_QR_PROOF_SENTINEL");
        }
    }

    @Test
    void redactionGuardDetectsMissingAdviceInIsolatedMvcControl() {
        // Standalone synthetic fixture only: no server, tokens or production changes.
        MockMvc unprotectedControl = MockMvcBuilders.standaloneSetup(
                        new QrLoginController(mock(QrLoginService.class), mock(QrLoginClientIp.class)))
                .setControllerAdvice(new ru.rutcampustrack.shared.web.exception.GlobalExceptionHandler())
                .build();
        String marker = "RCT_QR_CONTROL_SENTINEL";
        String body = "{\"purpose\":\"LOGIN\",\"issuerId\":\"00000000-0000-0000-0000-000000000001\","
                + "\"challengeId\":\"00000000-0000-0000-0000-000000000002\",\"issuerSecret\":\"" + marker + "\"}";
        assertThatThrownBy(() -> assertSafeValidation(unprotectedControl, "/auth/qr/status", body, marker))
                .isInstanceOf(AssertionError.class).hasMessageContaining(marker);
    }

    private static void assertSafeValidation(MockMvc target, String path, String body, String marker) throws Exception {
        MvcResult result = target.perform(post(path).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andReturn();
        assertThat(result.getResponse().getContentAsString()).doesNotContain(marker).contains("qr-login-failed", "INVALID_REQUEST");
        assertThat(result.getResponse().getHeader("Cache-Control")).isEqualTo("no-store");
    }
}
