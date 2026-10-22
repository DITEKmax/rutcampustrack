package ru.rutcampustrack.notification.security;

import org.springframework.context.annotation.Scope;
import org.springframework.context.annotation.ScopedProxyMode;
import org.springframework.stereotype.Component;
import ru.rutcampustrack.notification.contract.enums.UserRole;

/**
 * Request-scoped bean that holds the authenticated user context injected by API Gateway via
 * X-User-Id / X-User-Role / X-Group-Id / X-Is-Headman headers.
 *
 * <p>CRITICAL: proxyMode = ScopedProxyMode.TARGET_CLASS is mandatory so that singleton beans (e.g.
 * RoleCheckAspect) receive a properly scoped proxy instead of a stale instance captured at startup.
 */
@Component
@Scope(value = "request", proxyMode = ScopedProxyMode.TARGET_CLASS)
public class RequestContext {

    private Long userId;
    private UserRole role;
    private Long groupId;
    private boolean headman;
    private String selectedRole;
    private String internalToken;
    private java.util.UUID sessionId;
    private long sessionVersion;
    private long rolesVersion;
    private boolean readOnly;

    public String getSelectedRole() {
        return selectedRole;
    }

    public void setSelectedRole(String value) {
        selectedRole = value;
    }

    public String getInternalToken() {
        return internalToken;
    }

    public void setInternalToken(String value) {
        internalToken = value;
    }

    public java.util.UUID getSessionId() {
        return sessionId;
    }

    public void setSessionId(java.util.UUID value) {
        sessionId = value;
    }

    public long getSessionVersion() {
        return sessionVersion;
    }

    public void setSessionVersion(long value) {
        sessionVersion = value;
    }

    public long getRolesVersion() {
        return rolesVersion;
    }

    public void setRolesVersion(long value) {
        rolesVersion = value;
    }

    public boolean isReadOnly() {
        return readOnly;
    }

    public void setReadOnly(boolean value) {
        readOnly = value;
    }

    /** No legacy header or domain-role normalization can create a selected role. */
    public String requireSelectedRole() {
        if (userId == null
                || userId <= 0
                || internalToken == null
                || internalToken.isBlank()
                || sessionId == null
                || sessionVersion <= 0
                || rolesVersion <= 0
                || !java.util.Set.of("STUDENT", "HEADMAN", "TEACHER", "ADMIN")
                        .contains(selectedRole)) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.UNAUTHORIZED,
                    "Signed notification context is required");
        }
        return selectedRole;
    }

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
