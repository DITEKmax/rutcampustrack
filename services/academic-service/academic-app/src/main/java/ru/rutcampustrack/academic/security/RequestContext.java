package ru.rutcampustrack.academic.security;

import org.springframework.context.annotation.Scope;
import org.springframework.context.annotation.ScopedProxyMode;
import org.springframework.stereotype.Component;
import ru.rutcampustrack.academic.contract.enums.UserRole;

/**
 * Request-scoped bean that holds user context extracted from Gateway headers.
 * Uses TARGET_CLASS proxy so singleton beans (RoleCheckAspect) get a fresh instance per request.
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
