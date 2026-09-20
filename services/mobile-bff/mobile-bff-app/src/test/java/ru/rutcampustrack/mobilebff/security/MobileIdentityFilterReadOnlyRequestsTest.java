package ru.rutcampustrack.mobilebff.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import ru.rutcampustrack.shared.security.InternalJwtClaims;
import ru.rutcampustrack.shared.security.InternalJwtProperties;
import ru.rutcampustrack.shared.security.InternalJwtValidator;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MobileIdentityFilterReadOnlyRequestsTest {

    private static final InternalJwtClaims READ_ONLY = new InternalJwtClaims(
            42L,
            UUID.fromString("22222222-2222-4222-8222-222222222222"),
            3L,
            4L,
            "STUDENT",
            "EXPELLED",
            7L,
            false,
            true);

    @ParameterizedTest(name = "readOnly rejects POST {0}")
    @MethodSource("requestMutations")
    void readOnlyRejectsEveryRequestMutationBeforeController(String path) throws Exception {
        InternalJwtValidator validator = mock(InternalJwtValidator.class);
        when(validator.validate(anyString())).thenReturn(READ_ONLY);
        FilterChain chain = mock(FilterChain.class);
        MobileIdentityFilter filter = new MobileIdentityFilter(
                validator,
                new InternalJwtProperties(null, 0, 0, false, null, null, "X-Internal-Token"),
                mock(MobileRequestContext.class),
                new ObjectMapper().findAndRegisterModules(),
                Clock.fixed(Instant.parse("2026-09-13T12:00:00Z"), ZoneOffset.UTC));
        MockHttpServletRequest request = new MockHttpServletRequest("POST", path);
        request.addHeader("X-Internal-Token", "signed-token");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getContentAsString()).contains("ROLE_READ_ONLY");
        verify(chain, never()).doFilter(request, response);
    }

    @ParameterizedTest(name = "readOnly permits GET {0}")
    @MethodSource("readRoutes")
    void readOnlyPermitsRequestReads(String path) throws Exception {
        InternalJwtValidator validator = mock(InternalJwtValidator.class);
        when(validator.validate(anyString())).thenReturn(READ_ONLY);
        FilterChain chain = mock(FilterChain.class);
        MobileIdentityFilter filter = new MobileIdentityFilter(
                validator,
                new InternalJwtProperties(null, 0, 0, false, null, null, "X-Internal-Token"),
                mock(MobileRequestContext.class),
                new ObjectMapper().findAndRegisterModules(),
                Clock.fixed(Instant.parse("2026-09-13T12:00:00Z"), ZoneOffset.UTC));
        MockHttpServletRequest request = new MockHttpServletRequest("GET", path);
        request.addHeader("X-Internal-Token", "signed-token");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
        assertThat(response.getStatus()).isEqualTo(200);
    }

    private static Stream<Arguments> requestMutations() {
        return Stream.of(
                Arguments.of("/api/v1/student/requests/excuse"),
                Arguments.of("/api/v1/student/requests/late-checkin"),
                Arguments.of("/api/v1/student/requests/0123456789abcdef01234567/cancel"));
    }

    private static Stream<Arguments> readRoutes() {
        return Stream.of(
                Arguments.of("/api/v1/student/requests"),
                Arguments.of("/api/v1/student/requests/options"),
                Arguments.of("/api/v1/student/requests/0123456789abcdef01234567"));
    }
}
