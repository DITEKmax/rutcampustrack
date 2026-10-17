package ru.rutcampustrack.gateway.config;

import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.HttpHandler;
import org.springframework.http.server.reactive.HttpHandlerDecoratorFactory;
import org.springframework.stereotype.Component;

import reactor.core.publisher.Mono;

import ru.rutcampustrack.shared.observability.RequestCorrelation;

/** Runs outside HttpWebHandlerAdapter, before its server observation extracts remote baggage. */
@Component
public class RequestCorrelationHttpHandlerDecorator
        implements HttpHandlerDecoratorFactory, Ordered {
    @Override
    public HttpHandler apply(HttpHandler delegate) {
        return (request, response) -> {
            String requestId = RequestCorrelation.newId();
            var canonical =
                    request.mutate()
                            .headers(
                                    headers -> {
                                        // Public diagnostic headers never supply credentials or
                                        // arbitrary baggage.
                                        headers.remove("baggage");
                                        headers.set(RequestCorrelation.HEADER, requestId);
                                        headers.set(RequestCorrelation.BAGGAGE_FIELD, requestId);
                                    })
                            .build();
            response.getHeaders().set(RequestCorrelation.HEADER, requestId);
            response.beforeCommit(
                    () -> {
                        response.getHeaders().set(RequestCorrelation.HEADER, requestId);
                        return Mono.empty();
                    });
            return delegate.handle(canonical, response);
        };
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }
}
