package ru.rutcampustrack.schedule.security;

import org.springframework.context.annotation.Scope;
import org.springframework.context.annotation.ScopedProxyMode;
import org.springframework.stereotype.Component;
import ru.rutcampustrack.schedule.contract.enums.UserRole;

/**
 * Request-scoped bean holding authenticated user context from API Gateway headers.
 *
 * CRITICAL: proxyMode = ScopedProxyMode.TARGET_CLASS is mandatory so that
 * singleton beans (e.g. RoleCheckAspect) receive a properly scoped proxy
 * instead of a stale instance captured at startup.
 */
@Component
@Scope(value = "request", proxyMode = ScopedProxyMode.TARGET_CLASS)
public class RequestContext {

    private Long userId;
    private UserRole role;
    private Long groupId;
    private boolean headman;

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public UserRole getRole() {
        return role;
    }

    public void setRole(UserRole role) {
        this.role = role;
    }

    public Long getGroupId() {
        return groupId;
    }

    public void setGroupId(Long groupId) {
        this.groupId = groupId;
    }

    public boolean isHeadman() {
        return headman;
    }

    public void setHeadman(boolean headman) {
        this.headman = headman;
    }
}
