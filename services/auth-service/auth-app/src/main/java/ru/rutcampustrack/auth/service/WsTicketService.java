package ru.rutcampustrack.auth.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import ru.rutcampustrack.auth.security.SessionPrincipal;
import ru.rutcampustrack.auth.session.model.AuthRole;
import ru.rutcampustrack.auth.session.model.RoleStatus;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * M03b Группа 3: issues + consume short-lived WebSocket tickets.
 *
 * <p>Storage (DECISIONS 2026-04-20 WS-ticket storage):</p>
 * <ul>
 *     <li>{@code ws_ticket:<uuid>} → pipe-joined payload containing the complete
 *         selected {@link SessionPrincipal}, TTL 30s.
 *         Pipe-separated (не JSON) — упрощает Lua-script SREM без cjson.</li>
 *     <li>{@code ws_ticket_user:<userId>} → SET&lt;uuid&gt; для batch-invalidate
 *         при logout (Группа 8). TTL 60s, refresh на каждый issue.</li>
 * </ul>
 *
 * <p>Consume — atomic via Lua-script: GET ws_ticket:&lt;uuid&gt; → DEL →
 * SREM из user-set. Гарантирует single-use при concurrent access.</p>
 */
@Service
public class WsTicketService {

    private static final Logger log = LoggerFactory.getLogger(WsTicketService.class);

    public static final Duration TICKET_TTL = Duration.ofSeconds(30);
    private static final Duration USER_SET_TTL = Duration.ofSeconds(60);

    private static final String KEY_PREFIX = "ws_ticket:";
    private static final String USER_SET_PREFIX = "ws_ticket_user:";

    private static final String CONSUME_SCRIPT = """
            local payload = redis.call('GET', KEYS[1])
            if not payload then
                return nil
            end
            redis.call('DEL', KEYS[1])
            local sep1 = string.find(payload, '|', 1, true)
            if sep1 then
                local userId = string.sub(payload, 1, sep1 - 1)
                redis.call('SREM', 'ws_ticket_user:' .. userId, ARGV[1])
            end
            return payload
            """;

    /**
     * M03b Группа 11 (bug-hunt HIGH-2): atomic SADD+EXPIRE чтобы избежать
     * orphan user-set без TTL при network blip. Аналог pattern'а из
     * LoginRateLimiter (KI-6).
     */
    private static final String ADD_TO_SET_SCRIPT = """
            redis.call('SADD', KEYS[1], ARGV[1])
            redis.call('EXPIRE', KEYS[1], ARGV[2])
            return 1
            """;

    private final StringRedisTemplate redisTemplate;
    private final DefaultRedisScript<String> consumeScript;
    private final DefaultRedisScript<Long> addToSetScript;

    public WsTicketService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
        this.consumeScript = new DefaultRedisScript<>(CONSUME_SCRIPT, String.class);
        this.addToSetScript = new DefaultRedisScript<>(ADD_TO_SET_SCRIPT, Long.class);
    }

    /**
     * Issue a single-use socket capability bound to the complete admitted session identity.
     */
    public Issued issue(SessionPrincipal principal) {
        if (principal == null || principal.isBootstrap()) {
            throw new IllegalArgumentException("a selected session identity is required");
        }
        String ticket = UUID.randomUUID().toString();
        Instant expiresAt = Instant.now().plus(TICKET_TTL);
        String payload = String.join("|",
                Long.toString(principal.userId()),
                principal.sessionId().toString(),
                Long.toString(principal.sessionVersion()),
                Long.toString(principal.rolesVersion()),
                principal.selectedRole().name(),
                principal.selectedStatus().name(),
                principal.groupId() == null ? "" : Long.toString(principal.groupId()),
                Boolean.toString(principal.headman()),
                Boolean.toString(principal.readOnly()),
                Long.toString(expiresAt.getEpochSecond()));

        redisTemplate.opsForValue().set(KEY_PREFIX + ticket, payload, TICKET_TTL);
        String userSetKey = USER_SET_PREFIX + principal.userId();
        redisTemplate.execute(addToSetScript,
                List.of(userSetKey),
                ticket, String.valueOf(USER_SET_TTL.toSeconds()));
        return new Issued(ticket, expiresAt);
    }

    /**
     * Atomic consume: GET + DEL + SREM. Возвращает claims если ticket валиден
     * и не был consume'нут, иначе empty.
     */
    public Optional<TicketClaims> consume(String ticket) {
        if (ticket == null || ticket.isBlank()) {
            return Optional.empty();
        }
        List<String> keys = List.of(KEY_PREFIX + ticket);
        String payload = redisTemplate.execute(consumeScript, keys, ticket);
        if (payload == null) {
            return Optional.empty();
        }
        return parsePayload(payload).filter(claims -> Instant.now().isBefore(claims.expiresAt()));
    }

    /**
     * Для Группы 8 (logout): invalidate все tickets пользователя.
     * SMEMBERS + batch DEL + DEL user-set. Возвращает число удалённых
     * ticket'ов для audit-log.
     */
    public int invalidateAllFor(long userId) {
        String userSetKey = USER_SET_PREFIX + userId;
        var tickets = redisTemplate.opsForSet().members(userSetKey);
        int count = 0;
        if (tickets != null && !tickets.isEmpty()) {
            count = tickets.size();
            var keys = tickets.stream().map(t -> KEY_PREFIX + t).toList();
            redisTemplate.delete(keys);
        }
        redisTemplate.delete(userSetKey);
        return count;
    }

    private Optional<TicketClaims> parsePayload(String payload) {
        String[] parts = payload.split("\\|", -1);
        if (parts.length != 10) {
            log.warn("Invalid ws-ticket payload format: expected 10 fields, got {}", parts.length);
            return Optional.empty();
        }
        try {
            long userId = Long.parseLong(parts[0]);
            UUID sessionId = UUID.fromString(parts[1]);
            if (!sessionId.toString().equals(parts[1])) {
                throw new IllegalArgumentException("non-canonical session id");
            }
            long sessionVersion = Long.parseLong(parts[2]);
            long rolesVersion = Long.parseLong(parts[3]);
            AuthRole role = AuthRole.valueOf(parts[4]);
            RoleStatus status = RoleStatus.valueOf(parts[5]);
            Long groupId = parts[6].isEmpty() ? null : Long.valueOf(parts[6]);
            boolean headman = parseBoolean(parts[7]);
            boolean readOnly = parseBoolean(parts[8]);
            long expiresEpoch = Long.parseLong(parts[9]);
            SessionPrincipal principal = new SessionPrincipal(userId, sessionId, sessionVersion,
                    rolesVersion, role, status, groupId, headman, readOnly);
            return Optional.of(new TicketClaims(principal, Instant.ofEpochSecond(expiresEpoch)));
        } catch (RuntimeException e) {
            log.warn("Failed to parse ws-ticket identity payload: {}", e.getClass().getSimpleName());
            return Optional.empty();
        }
    }

    private static boolean parseBoolean(String value) {
        if ("true".equals(value)) return true;
        if ("false".equals(value)) return false;
        throw new IllegalArgumentException("invalid boolean");
    }

    public record Issued(String ticket, Instant expiresAt) {}

    public record TicketClaims(SessionPrincipal principal, Instant expiresAt) {}
}
