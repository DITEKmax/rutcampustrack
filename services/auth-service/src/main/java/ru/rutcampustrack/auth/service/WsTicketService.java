package ru.rutcampustrack.auth.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

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
 *     <li>{@code ws_ticket:<uuid>} → JSON payload {@code {userId, role, expiresAt}}, TTL 30s</li>
 *     <li>{@code ws_ticket_user:<userId>} → SET&lt;uuid&gt; для batch-invalidate
 *     при logout (Группа 8). TTL 60s, refresh на каждый issue.</li>
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

    /**
     * Lua: atomic GET + DEL + SREM. Возвращает payload или nil.
     * KEYS[1] = ws_ticket:<uuid>, KEYS[2] = ws_ticket_user:<userId-from-payload>
     * ARGV[1] = uuid (для SREM)
     *
     * Мы сначала читаем payload; если есть, парсим userId; но Lua не парсит JSON.
     * Поэтому структура ключа: value = "<userId>|<role>|<expiresEpoch>".
     * Это проще Lua-парсинга JSON и избавляет от зависимости cjson.
     */
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

    private final StringRedisTemplate redisTemplate;
    private final DefaultRedisScript<String> consumeScript;

    public WsTicketService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
        this.consumeScript = new DefaultRedisScript<>(CONSUME_SCRIPT, String.class);
    }

    /**
     * Issue ticket для userId/role. TTL = 30s single-use.
     * @return (ticket, expiresAt) пара
     */
    public Issued issue(long userId, String role) {
        String ticket = UUID.randomUUID().toString();
        Instant expiresAt = Instant.now().plus(TICKET_TTL);
        String payload = userId + "|" + role + "|" + expiresAt.getEpochSecond();

        redisTemplate.opsForValue().set(KEY_PREFIX + ticket, payload, TICKET_TTL);
        String userSetKey = USER_SET_PREFIX + userId;
        redisTemplate.opsForSet().add(userSetKey, ticket);
        redisTemplate.expire(userSetKey, USER_SET_TTL);
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
        return parsePayload(payload);
    }

    /**
     * Для Группы 8 (logout): invalidate все tickets пользователя. Используется
     * SMEMBERS + DEL pipeline.
     */
    public void invalidateAllFor(long userId) {
        String userSetKey = USER_SET_PREFIX + userId;
        var tickets = redisTemplate.opsForSet().members(userSetKey);
        if (tickets != null && !tickets.isEmpty()) {
            var keys = tickets.stream().map(t -> KEY_PREFIX + t).toList();
            redisTemplate.delete(keys);
        }
        redisTemplate.delete(userSetKey);
    }

    private Optional<TicketClaims> parsePayload(String payload) {
        int first = payload.indexOf('|');
        int second = payload.indexOf('|', first + 1);
        if (first < 0 || second < 0) {
            log.warn("Invalid ws-ticket payload format");
            return Optional.empty();
        }
        try {
            long userId = Long.parseLong(payload.substring(0, first));
            String role = payload.substring(first + 1, second);
            long expiresEpoch = Long.parseLong(payload.substring(second + 1));
            return Optional.of(new TicketClaims(userId, role, Instant.ofEpochSecond(expiresEpoch)));
        } catch (NumberFormatException e) {
            log.warn("Failed to parse ws-ticket payload", e);
            return Optional.empty();
        }
    }

    public record Issued(String ticket, Instant expiresAt) {}

    public record TicketClaims(long userId, String role, Instant expiresAt) {}
}
