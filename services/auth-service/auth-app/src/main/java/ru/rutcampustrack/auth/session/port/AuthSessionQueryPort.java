package ru.rutcampustrack.auth.session.port;

import ru.rutcampustrack.auth.session.model.RoleGrant;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** Read-only PostgreSQL queries needed by public authentication/session APIs. */
public interface AuthSessionQueryPort {

    Optional<LoginAuthority> findLoginAuthority(long userId);

    Optional<UserIdentity> findUserIdentity(long userId);

    Optional<SessionMetadata> findSessionMetadata(long userId, UUID sessionId);

    SessionPage findLiveSessions(long userId, UUID currentSessionId, String cursor, int limit);

    HistoryPage findHistory(long userId, String cursor, int limit);

    /** Alias kept for callers that describe the operation as a load. */
    default Optional<LoginAuthority> loadLoginAuthority(long userId) {
        return findLoginAuthority(userId);
    }

    /** Authoritative roles/version and server-owned display metadata at login. */
    record LoginAuthority(
            long userId,
            long rolesVersion,
            List<RoleGrant> grants,
            String displayName,
            Map<Long, String> grantContextLabels,
            Map<Long, String> groupLabels
    ) {
        public LoginAuthority {
            if (userId <= 0 || rolesVersion <= 0) {
                throw new IllegalArgumentException("user and version must be positive");
            }
            grants = List.copyOf(Objects.requireNonNull(grants, "grants"));
            Objects.requireNonNull(displayName, "displayName");
            grantContextLabels = Map.copyOf(Objects.requireNonNull(grantContextLabels, "grantContextLabels"));
            groupLabels = Map.copyOf(Objects.requireNonNull(groupLabels, "groupLabels"));
        }

        public LoginAuthority(long userId, long rolesVersion, List<RoleGrant> grants) {
            this(userId, rolesVersion, grants, "", Map.of(), Map.of());
        }
    }

    /** Real user/group labels used by current-session responses. */
    record UserIdentity(
            long userId,
            String displayName,
            Map<Long, String> groupLabels
    ) {
        public UserIdentity {
            if (userId <= 0) {
                throw new IllegalArgumentException("userId must be positive");
            }
            Objects.requireNonNull(displayName, "displayName");
            groupLabels = Map.copyOf(Objects.requireNonNull(groupLabels, "groupLabels"));
        }

        public UserIdentity(long userId, String displayName) {
            this(userId, displayName, Map.of());
        }
    }

    record SessionPage(List<SessionView> items, String nextCursor) {
        public SessionPage {
            items = List.copyOf(Objects.requireNonNull(items, "items"));
        }
    }

    /** Own immutable metadata used to attribute cookie-only logout events. */
    record SessionMetadata(
            UUID sessionId,
            String authMethod,
            String clientLabel,
            String locationLabel,
            Instant createdAt,
            Instant lastSeenAt,
            Instant refreshExpiresAt,
            Instant revokedAt
    ) {
        public SessionMetadata {
            sessionId = Objects.requireNonNull(sessionId, "sessionId");
            Objects.requireNonNull(authMethod, "authMethod");
            createdAt = Objects.requireNonNull(createdAt, "createdAt");
            lastSeenAt = Objects.requireNonNull(lastSeenAt, "lastSeenAt");
            refreshExpiresAt = Objects.requireNonNull(refreshExpiresAt, "refreshExpiresAt");
            if (lastSeenAt.isBefore(createdAt) || !refreshExpiresAt.isAfter(createdAt)) {
                throw new IllegalArgumentException("session timestamps are inconsistent");
            }
        }

        public boolean isLiveAt(Instant now) {
            return revokedAt == null && now.isBefore(refreshExpiresAt);
        }
    }

    record HistoryPage(List<HistoryView> items, String nextCursor) {
        public HistoryPage {
            items = List.copyOf(Objects.requireNonNull(items, "items"));
        }
    }

    record SessionView(
            UUID sessionId,
            String authMethod,
            String clientLabel,
            String locationLabel,
            Instant createdAt,
            Instant lastSeenAt
    ) {
        public SessionView {
            sessionId = Objects.requireNonNull(sessionId, "sessionId");
            Objects.requireNonNull(authMethod, "authMethod");
            createdAt = Objects.requireNonNull(createdAt, "createdAt");
            lastSeenAt = Objects.requireNonNull(lastSeenAt, "lastSeenAt");
            if (lastSeenAt.isBefore(createdAt)) {
                throw new IllegalArgumentException("lastSeenAt must not precede createdAt");
            }
        }
    }

    record HistoryView(
            long id,
            String type,
            Instant occurredAt,
            String authMethod,
            String clientLabel,
            String locationLabel
    ) {
        public HistoryView {
            if (id <= 0) {
                throw new IllegalArgumentException("id must be positive");
            }
            Objects.requireNonNull(type, "type");
            Objects.requireNonNull(occurredAt, "occurredAt");
        }
    }
}
