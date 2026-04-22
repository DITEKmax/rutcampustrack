package ru.rutcampustrack.schedule.integration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Smoke test verifying the security filter chain works end-to-end:
 * - No X-User-Id header -> UserContextFilter does NOT populate RequestContext
 * - RequestContext.role is null -> RoleCheckAspect throws AccessDeniedException
 * - GlobalExceptionHandler maps AccessDeniedException to HTTP 403 with RFC 7807 body
 */
@AutoConfigureMockMvc
class SecuritySmokeIT extends AbstractScheduleIntegrationTest {

    @Autowired
    MockMvc mockMvc;

    @Test
    void request_withoutRoleHeaders_returns403() throws Exception {
        mockMvc.perform(get("/schedule/health-check"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status", is(403)))
                .andExpect(jsonPath("$.type", is("https://api.rutcampustrack.ru/problems/access-denied")));
    }

    @Test
    void request_withValidHeaders_returns200() throws Exception {
        mockMvc.perform(get("/schedule/health-check")
                        .header("X-User-Id", "1")
                        .header("X-User-Role", "STUDENT")
                        .header("X-Group-Id", "10")
                        .header("X-Is-Headman", "false"))
                .andExpect(status().isOk());
    }
}
