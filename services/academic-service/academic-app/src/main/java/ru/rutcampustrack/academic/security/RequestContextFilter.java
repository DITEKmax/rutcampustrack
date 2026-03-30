package ru.rutcampustrack.academic.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import ru.rutcampustrack.academic.contract.enums.UserRole;

import java.io.IOException;

/**
 * Extracts user context from Gateway-injected headers and populates RequestContext.
 * Headers set by API Gateway: X-User-Id, X-User-Role, X-Group-Id, X-Is-Headman.
 */
@Component
public class RequestContextFilter extends OncePerRequestFilter {

    private final RequestContext requestContext;

    public RequestContextFilter(RequestContext requestContext) {
        this.requestContext = requestContext;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String userIdHeader = request.getHeader("X-User-Id");
        if (userIdHeader != null && !userIdHeader.isBlank()) {
            requestContext.setUserId(Long.parseLong(userIdHeader));
            String roleHeader = request.getHeader("X-User-Role");
            if (roleHeader != null) {
                requestContext.setRole(UserRole.valueOf(roleHeader.toUpperCase()));
            }
            String groupIdHeader = request.getHeader("X-Group-Id");
            if (groupIdHeader != null && !groupIdHeader.isBlank()) {
                requestContext.setGroupId(Long.parseLong(groupIdHeader));
            }
            String headmanHeader = request.getHeader("X-Is-Headman");
            if (headmanHeader != null) {
                requestContext.setHeadman(Boolean.parseBoolean(headmanHeader));
            }
        }
        chain.doFilter(request, response);
    }
}
