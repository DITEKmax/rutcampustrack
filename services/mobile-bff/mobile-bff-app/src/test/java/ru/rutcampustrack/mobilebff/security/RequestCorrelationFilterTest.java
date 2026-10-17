package ru.rutcampustrack.mobilebff.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import ru.rutcampustrack.shared.observability.RequestCorrelation;

class RequestCorrelationFilterTest {
    @Test
    void preservesGatewayIdAndReturnsItOnEarlyApplicationError() throws Exception {
        String id = RequestCorrelation.newId();
        var request = new MockHttpServletRequest();
        request.addHeader(RequestCorrelation.BAGGAGE_FIELD, id);
        var response = new MockHttpServletResponse();
        new RequestCorrelationFilter()
                .doFilter(
                        request,
                        response,
                        (incoming, outgoing) -> {
                            assertThat(
                                            ((HttpServletRequest) incoming)
                                                    .getHeader(RequestCorrelation.BAGGAGE_FIELD))
                                    .isEqualTo(id);
                            ((jakarta.servlet.http.HttpServletResponse) outgoing).setStatus(401);
                        });
        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getHeader(RequestCorrelation.HEADER)).isEqualTo(id);
    }

    @Test
    void malformedDiagnosticHeaderCannotLeakThroughStandardBaggageExtraction() {
        var request = new MockHttpServletRequest();
        request.addHeader(RequestCorrelation.BAGGAGE_FIELD, "synthetic-credential-marker");
        request.addHeader("baggage", "requestId=synthetic-credential-marker");
        var response = new MockHttpServletResponse();
        assertThatThrownBy(
                        () ->
                                new RequestCorrelationFilter()
                                        .doFilter(
                                                request,
                                                response,
                                                (incoming, outgoing) -> {
                                                    var wrapped = (HttpServletRequest) incoming;
                                                    assertThat(wrapped.getHeader("baggage"))
                                                            .isNull();
                                                    assertThat(
                                                                    wrapped.getHeaders("baggage")
                                                                            .hasMoreElements())
                                                            .isFalse();
                                                    String id =
                                                            wrapped.getHeader(
                                                                    RequestCorrelation
                                                                            .BAGGAGE_FIELD);
                                                    assertThat(id)
                                                            .isEqualTo(
                                                                    RequestCorrelation
                                                                            .canonicalOrNew(id));
                                                    assertThat(id)
                                                            .isNotEqualTo(
                                                                    "synthetic-credential-marker");
                                                    throw new ServletException("unavailable");
                                                }))
                .isInstanceOf(ServletException.class);
        assertThat(response.getHeader(RequestCorrelation.HEADER)).isNotBlank();
    }
}
