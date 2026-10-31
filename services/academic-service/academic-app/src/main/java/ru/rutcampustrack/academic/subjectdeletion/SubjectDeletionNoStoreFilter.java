package ru.rutcampustrack.academic.subjectdeletion;

import jakarta.servlet.*;
import jakarta.servlet.http.*;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/** Includes validation/authentication failures before controller admission. */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class SubjectDeletionNoStoreFilter extends OncePerRequestFilter {
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getServletPath();
        return !(path.matches("/academic/subjects/[^/]+/deletion(?:-preview|-challenge)?")
                || path.startsWith("/academic/subject-deletion-operations/")
                || path.matches("/academic/groups/[^/]+/(?:deletion-preview|deletions)")
                || path.matches("/academic/groups/[^/]+/deletions/[^/]+/resolve")
                || path.startsWith("/academic/group-deletions/"));
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        response.setHeader("Cache-Control", "no-store");
        chain.doFilter(request, response);
    }
}
