package ru.rutcampustrack.shared.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.concurrent.atomic.AtomicReference;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

class DualModeUserContextFilterTest {

    private static final UUID SESSION_ID = UUID.fromString("22222222-2222-4222-8222-222222222222");

    private InternalJwtTestFactory factory;
    private FilterChain chain;

    @BeforeEach
    void setUp() {
        factory = new InternalJwtTestFactory();
        chain = mock(FilterChain.class);
    }

    private TestFilter filter(boolean legacyEnabled) {
        InternalJwtProperties props = new InternalJwtProperties(
                null, 0, 0, legacyEnabled, null, null, null);
        PublicKeyProvider keyProvider = new PublicKeyProvider(props) {
            @Override
            public java.security.PublicKey getPublicKey() {
                return factory.publicKey();
            }
        };
        InternalJwtValidator validator = new InternalJwtValidator(keyProvider, props);
        return new TestFilter(validator, props);
    }

    @Test
    void internalToken_applied_chainContinues() throws Exception {
        TestFilter filter = filter(true);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Internal-Token", factory.validToken(
                42L, SESSION_ID, 1L, 1L, "HEADMAN", "ACTIVE", 7L, true, false));
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, chain);

        assertThat(filter.appliedClaims.get())
                .isEqualTo(new InternalJwtClaims(
                        42L, SESSION_ID, 1L, 1L, "HEADMAN", "ACTIVE", 7L, true, false));
        assertThat(filter.legacyApplied).isFalse();
        assertThat(response.getStatus()).isEqualTo(200);
        verify(chain).doFilter(request, response);
    }

    @Test
    void invalidInternalToken_returns401() throws Exception {
        TestFilter filter = filter(true);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Internal-Token", new InternalJwtTestFactory().buildToken(
                42L, SESSION_ID, 1L, 1L, "ADMIN", "ACTIVE", null, false, false,
                java.time.Instant.now().truncatedTo(java.time.temporal.ChronoUnit.SECONDS),
                java.time.Instant.now().plusSeconds(60).truncatedTo(java.time.temporal.ChronoUnit.SECONDS),
                InternalJwtTestFactory.ISSUER, InternalJwtTestFactory.AUDIENCE, "internal",
                new InternalJwtTestFactory().keyPair()));
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(401);
        verifyNoInteractions(chain);
    }

    @Test
    void noInternalToken_legacyEnabled_fallsBackToLegacy() throws Exception {
        TestFilter filter = filter(true);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-User-Id", "42");
        request.addHeader("X-User-Role", "ADMIN");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, chain);

        assertThat(filter.legacyApplied).isTrue();
        assertThat(filter.appliedClaims.get()).isNull();
        verify(chain).doFilter(request, response);
    }

    @Test
    void noInternalToken_legacyDisabled_returns401() throws Exception {
        TestFilter filter = filter(false);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-User-Id", "42");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(401);
        verifyNoInteractions(chain);
    }

    @Test
    void noHeadersAtAll_legacyEnabled_continuesChain() throws Exception {
        TestFilter filter = filter(true);
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, chain);

        assertThat(filter.legacyApplied).isFalse();
        assertThat(filter.appliedClaims.get()).isNull();
        verify(chain).doFilter(request, response);
    }

    @Test
    void noHeadersAtAll_legacyDisabled_returns401() throws Exception {
        TestFilter filter = filter(false);
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(401);
        verifyNoInteractions(chain);
    }

    @Test
    void infrastructurePath_skipsFilter_evenInStrictMode() throws Exception {
        TestFilter filter = filter(false);  // strict mode (legacyHeadersEnabled=false)
        for (String path : new String[]{
                "/actuator/health", "/actuator/prometheus", "/actuator/info",
                "/api-docs", "/v3/api-docs/swagger-config",
                "/swagger-ui/index.html"
        }) {
            MockHttpServletRequest request = new MockHttpServletRequest();
            request.setRequestURI(path);
            // no X-Internal-Token, no X-User-* — in strict mode normally → 401,
            // but infrastructure paths must passthrough для Docker HEALTHCHECK / Prometheus.
            MockHttpServletResponse response = new MockHttpServletResponse();
            FilterChain localChain = mock(FilterChain.class);

            filter.doFilter(request, response, localChain);

            assertThat(response.getStatus())
                    .as("path=%s must not 401 in strict mode", path)
                    .isEqualTo(200);
            verify(localChain).doFilter(request, response);
        }
    }

    @Test
    void internalTokenTakesPrecedence_overLegacyHeaders() throws Exception {
        TestFilter filter = filter(true);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Internal-Token", factory.validToken(
                99L, SESSION_ID, 1L, 1L, "STUDENT", "ACTIVE", null, false, false));
        request.addHeader("X-User-Id", "1"); // should be ignored
        request.addHeader("X-User-Role", "ADMIN"); // should be ignored
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, chain);

        assertThat(filter.appliedClaims.get().userId()).isEqualTo(99L);
        assertThat(filter.appliedClaims.get().role()).isEqualTo("STUDENT");
        assertThat(filter.legacyApplied).isFalse();
    }

    private static class TestFilter extends DualModeUserContextFilter {
        final AtomicReference<InternalJwtClaims> appliedClaims = new AtomicReference<>();
        boolean legacyApplied = false;

        TestFilter(InternalJwtValidator v, InternalJwtProperties p) {
            super(v, p);
        }

        @Override
        protected void applyInternalJwt(InternalJwtClaims claims) {
            appliedClaims.set(claims);
        }

        @Override
        protected void applyLegacyHeaders(HttpServletRequest request) {
            legacyApplied = true;
        }
    }
}
