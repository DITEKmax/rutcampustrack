package ru.rutcampustrack.gateway.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.http.server.reactive.MockServerHttpResponse;

import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import ru.rutcampustrack.shared.observability.RequestCorrelation;

import java.util.ArrayList;
import java.util.List;

class RequestCorrelationHttpHandlerDecoratorTest {
    @Test
    void canonicalIdPrecedesEarlyFailureAndSeparateRequestsNeverReusePublicDiagnosticValues() {
        List<String> seen = new ArrayList<>();
        var decorated =
                new RequestCorrelationHttpHandlerDecorator()
                        .apply(
                                (request, response) -> {
                                    String id =
                                            request.getHeaders()
                                                    .getFirst(RequestCorrelation.BAGGAGE_FIELD);
                                    seen.add(id);
                                    assertThat(
                                                    request.getHeaders()
                                                            .getFirst(RequestCorrelation.HEADER))
                                            .isEqualTo(id);
                                    assertThat(request.getHeaders()).doesNotContainKey("baggage");
                                    assertThat(id).isEqualTo(RequestCorrelation.canonicalOrNew(id));
                                    response.setStatusCode(HttpStatus.UNAUTHORIZED);
                                    return response.setComplete();
                                });
        var request =
                MockServerHttpRequest.get("/api/v1/student/homework")
                        .header(RequestCorrelation.HEADER, "synthetic-credential-marker")
                        .header(RequestCorrelation.BAGGAGE_FIELD, "synthetic-credential-marker")
                        .header("baggage", "requestId=synthetic-credential-marker")
                        .build();
        var first = new MockServerHttpResponse();
        var second = new MockServerHttpResponse();

        StepVerifier.create(decorated.handle(request, first)).verifyComplete();
        StepVerifier.create(decorated.handle(request, second)).verifyComplete();

        assertThat(seen).hasSize(2).doesNotHaveDuplicates();
        assertThat(first.getHeaders().getFirst(RequestCorrelation.HEADER)).isEqualTo(seen.get(0));
        assertThat(second.getHeaders().getFirst(RequestCorrelation.HEADER)).isEqualTo(seen.get(1));
        assertThat(first.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void errorAndCancellationDoNotLoseCanonicalResponseHeader() {
        var decorator = new RequestCorrelationHttpHandlerDecorator();
        var request = MockServerHttpRequest.get("/api/v1/student/homework").build();
        var failure = new MockServerHttpResponse();
        StepVerifier.create(
                        decorator
                                .apply(
                                        (req, res) ->
                                                Mono.error(
                                                        new IllegalStateException("unavailable")))
                                .handle(request, failure))
                .expectError(IllegalStateException.class)
                .verify();
        var cancelled = new MockServerHttpResponse();
        StepVerifier.create(decorator.apply((req, res) -> Mono.never()).handle(request, cancelled))
                .thenCancel()
                .verify();
        assertThat(failure.getHeaders().getFirst(RequestCorrelation.HEADER)).isNotBlank();
        assertThat(cancelled.getHeaders().getFirst(RequestCorrelation.HEADER))
                .isNotBlank()
                .isNotEqualTo(failure.getHeaders().getFirst(RequestCorrelation.HEADER));
    }
}
