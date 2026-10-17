package ru.rutcampustrack.mobilebff.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import ru.rutcampustrack.shared.observability.RequestCorrelation;

import java.io.IOException;
import java.util.Collections;
import java.util.Enumeration;
import java.util.LinkedHashSet;

/** Normalizes diagnostic input before the standard servlet observation filter extracts baggage. */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestCorrelationFilter extends OncePerRequestFilter {
    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String requestId =
                RequestCorrelation.canonicalOrNew(
                        request.getHeader(RequestCorrelation.BAGGAGE_FIELD));
        response.setHeader(RequestCorrelation.HEADER, requestId);
        chain.doFilter(
                new HttpServletRequestWrapper(request) {
                    @Override
                    public String getHeader(String name) {
                        if (isCorrelationHeader(name)) {
                            return requestId;
                        }
                        return "baggage".equalsIgnoreCase(name) ? null : super.getHeader(name);
                    }

                    @Override
                    public Enumeration<String> getHeaders(String name) {
                        if (isCorrelationHeader(name)) {
                            return Collections.enumeration(Collections.singleton(requestId));
                        }
                        return "baggage".equalsIgnoreCase(name)
                                ? Collections.emptyEnumeration()
                                : super.getHeaders(name);
                    }

                    @Override
                    public Enumeration<String> getHeaderNames() {
                        var names = new LinkedHashSet<>(Collections.list(super.getHeaderNames()));
                        names.removeIf(
                                name ->
                                        "baggage".equalsIgnoreCase(name)
                                                || isCorrelationHeader(name));
                        names.add(RequestCorrelation.HEADER);
                        names.add(RequestCorrelation.BAGGAGE_FIELD);
                        return Collections.enumeration(names);
                    }
                },
                response);
    }

    private static boolean isCorrelationHeader(String name) {
        return RequestCorrelation.HEADER.equalsIgnoreCase(name)
                || RequestCorrelation.BAGGAGE_FIELD.equalsIgnoreCase(name);
    }
}
