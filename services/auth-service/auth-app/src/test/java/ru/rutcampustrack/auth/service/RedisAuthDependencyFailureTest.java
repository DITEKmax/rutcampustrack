package ru.rutcampustrack.auth.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import ru.rutcampustrack.auth.exception.GlobalExceptionHandler;
import ru.rutcampustrack.auth.security.SessionPrincipal;
import ru.rutcampustrack.auth.session.model.AuthRole;
import ru.rutcampustrack.auth.session.model.RoleStatus;

import java.util.UUID;

/** Controlled dependency errors at the real service/HTTP error boundary; no Redis runtime claim. */
class RedisAuthDependencyFailureTest {
    private final StringRedisTemplate redis = mock(StringRedisTemplate.class);
    private final LoginRateLimiter limiter = new LoginRateLimiter(redis);
    private final WsTicketService tickets = new WsTicketService(redis);

    @RestController
    class Boundary {
        @GetMapping("/limit")
        void limit() {
            limiter.checkBlocked("synthetic-ip", "synthetic-user");
        }

        @GetMapping("/ticket")
        boolean ticket() {
            return tickets.consume("synthetic-ticket").isPresent();
        }

        @PostMapping("/ticket")
        WsTicketService.Issued issue() {
            return tickets.issue(
                    new SessionPrincipal(
                            1L,
                            UUID.randomUUID(),
                            1L,
                            1L,
                            AuthRole.STUDENT,
                            RoleStatus.ACTIVE,
                            1L,
                            false,
                            false));
        }
    }

    @Test
    void unavailableLimiterReturns503ThenRecoveryPreserves429() throws Exception {
        var http =
                MockMvcBuilders.standaloneSetup(new Boundary())
                        .setControllerAdvice(new GlobalExceptionHandler())
                        .build();
        when(redis.hasKey(anyString()))
                .thenThrow(new DataAccessResourceFailureException("synthetic unavailable"))
                .thenReturn(false)
                .thenReturn(true);
        when(redis.getExpire(anyString())).thenReturn(60L);
        http.perform(get("/limit")).andExpect(status().isServiceUnavailable());
        http.perform(get("/limit")).andExpect(status().isOk());
        http.perform(get("/limit")).andExpect(status().isTooManyRequests());
    }

    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    void unavailableTicketConsumeIs503WhileRecoveredMissRemainsAnOrdinaryMiss() throws Exception {
        var http =
                MockMvcBuilders.standaloneSetup(new Boundary())
                        .setControllerAdvice(new GlobalExceptionHandler())
                        .build();
        when(redis.execute(any(RedisScript.class), anyList(), anyString()))
                .thenThrow(new DataAccessResourceFailureException("synthetic unavailable"))
                .thenReturn(null);
        http.perform(get("/ticket")).andExpect(status().isServiceUnavailable());
        http.perform(get("/ticket"))
                .andExpect(status().isOk())
                .andExpect(content().string("false"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void failedTicketWriteDoesNotReturnAnIssuedCapability() throws Exception {
        ValueOperations<String, String> values = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(values);
        doThrow(new DataAccessResourceFailureException("synthetic unavailable"))
                .when(values)
                .set(anyString(), anyString(), any(java.time.Duration.class));
        var http =
                MockMvcBuilders.standaloneSetup(new Boundary())
                        .setControllerAdvice(new GlobalExceptionHandler())
                        .build();
        http.perform(post("/ticket")).andExpect(status().isServiceUnavailable());
    }
}
