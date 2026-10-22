package ru.rutcampustrack.academic.exportjobs;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpServletResponseWrapper;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/** Export metadata, internal authority and files remain private even on filter-level denials. */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public final class ExportNoStoreFilter extends OncePerRequestFilter {
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getServletPath();
        return !matches(path, "/academic/export-jobs")
                && !matches(path, "/academic/report-download")
                && !matches(path, "/academic/report-download-tickets")
                && !matches(path, "/academic/internal/export-jobs");
    }

    private static boolean matches(String path, String prefix) {
        return path.equals(prefix) || path.startsWith(prefix + "/");
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        response.setHeader("Cache-Control", "no-store");
        chain.doFilter(
                request,
                new HttpServletResponseWrapper(response) {
                    @Override
                    public void addHeader(String name, String value) {
                        if (name.equalsIgnoreCase("Cache-Control"))
                            super.setHeader(name, "no-store");
                        else super.addHeader(name, value);
                    }

                    @Override
                    public void setHeader(String name, String value) {
                        super.setHeader(
                                name, name.equalsIgnoreCase("Cache-Control") ? "no-store" : value);
                    }
                });
    }
}
