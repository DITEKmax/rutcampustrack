package ru.rutcampustrack.auth.session.model;

public enum SessionRevokeReason {
    CURRENT_LOGOUT,
    LOGOUT_ALL,
    PASSWORD_CHANGED,
    SECURITY_REVOKED
}
