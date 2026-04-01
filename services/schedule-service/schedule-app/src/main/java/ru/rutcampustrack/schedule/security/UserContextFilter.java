package ru.rutcampustrack.schedule.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import ru.rutcampustrack.schedule.contract.enums.UserRole;

import java.io.IOException;

/**
 * Servlet filter that populates RequestContext from HTTP headers injected by API Gateway.
 * Headers: X-User-Id, X-User-Role, X-Group-Id (optional), X-Is-Headman.
 *
 * If X-User-Id is absent, RequestContext fields remain null -> RoleCheckAspect throws 403.
 */
@Component
public class UserContextFilter extends OncePerRequestFilter {

    private final RequestContext requestContext;

    public UserContextFilter(RequestContext requestContext) {
        this.requestContext = requestContext;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String userIdHeader = request.getHeader("X-User-Id");
        if (userIdHeader != null) {
            requestContext.setUserId(Long.parseLong(userIdHeader));
            requestContext.setRole(UserRole.valueOf(request.getHeader("X-User-Role").toUpperCase()));
            String groupIdHeader = request.getHeader("X-Group-Id");
            if (groupIdHeader != null && !groupIdHeader.isEmpty()) {
                requestContext.setGroupId(Long.parseLong(groupIdHeader));
            }
            requestContext.setHeadman(Boolean.parseBoolean(request.getHeader("X-Is-Headman")));
        }
        chain.doFilter(request, response);
    }
}
