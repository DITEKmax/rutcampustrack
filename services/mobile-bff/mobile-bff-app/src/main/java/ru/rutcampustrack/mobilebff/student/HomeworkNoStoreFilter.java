package ru.rutcampustrack.mobilebff.student;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.http.server.PathContainer;
import org.springframework.web.util.pattern.PathPattern;
import org.springframework.web.util.pattern.PathPatternParser;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
final class HomeworkNoStoreFilter extends OncePerRequestFilter {
    private static final PathPattern HOMEWORK_FEED_PATTERN =
            PathPatternParser.defaultInstance.parse("/api/v1/student/homework");
    private static final PathPattern HOMEWORK_COMPLETION_PATTERN =
            PathPatternParser.defaultInstance.parse("/api/v1/student/homework/{homeworkId}/completion");

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !isHomeworkOperation(request);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        response.setHeader(HttpHeaders.CACHE_CONTROL, CacheControl.noStore().getHeaderValue());
        filterChain.doFilter(request, response);
    }

    private static boolean isHomeworkOperation(HttpServletRequest request) {
        PathContainer path = PathContainer.parsePath(pathWithoutContext(request));
        if ("GET".equalsIgnoreCase(request.getMethod())) {
            return HOMEWORK_FEED_PATTERN.matches(path);
        }
        return "PUT".equalsIgnoreCase(request.getMethod())
                && HOMEWORK_COMPLETION_PATTERN.matches(path);
    }

    private static String pathWithoutContext(HttpServletRequest request) {
        String path = request.getRequestURI();
        String contextPath = request.getContextPath();
        if (!contextPath.isEmpty() && path.startsWith(contextPath)) {
            return path.substring(contextPath.length());
        }
        return path;
    }
}
