package ru.rutcampustrack.auth.service;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Repository;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

/** Redis implementation using fixed-window Lua counters and absolute ticket TTLs. */
@Repository
public class RedisReportDownloadTicketStore implements ReportDownloadTicketStore {

    private static final String TICKET_PREFIX = "report_download_ticket:";
    private static final String ISSUE_PREFIX = "report_download_ticket_issue:";
    private static final String REDEMPTION_PREFIX = "report_download_ticket_redemption:";

    private static final String INCREMENT_WITH_LIMIT_SCRIPT = """
            local current = tonumber(redis.call('GET', KEYS[1]) or '0')
            local maximum = tonumber(ARGV[1])
            if current >= maximum then
                return 0
            end
            local count = redis.call('INCR', KEYS[1])
            if count == 1 then
                redis.call('EXPIRE', KEYS[1], ARGV[2])
            end
            return count
            """;

    private final StringRedisTemplate redisTemplate;
    private final DefaultRedisScript<Long> incrementWithLimitScript =
            new DefaultRedisScript<>(INCREMENT_WITH_LIMIT_SCRIPT, Long.class);

    public RedisReportDownloadTicketStore(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public boolean putIfAbsent(String ticketDigest, String serializedTicket, Duration ttl) {
        Boolean stored = redisTemplate.opsForValue().setIfAbsent(
                TICKET_PREFIX + ticketDigest, serializedTicket, ttl);
        return Boolean.TRUE.equals(stored);
    }

    @Override
    public Optional<String> find(String ticketDigest) {
        return Optional.ofNullable(redisTemplate.opsForValue().get(TICKET_PREFIX + ticketDigest));
    }

    @Override
    public boolean allowIssue(String sessionDigest, int maximum, Duration window) {
        return incrementWithinLimit(ISSUE_PREFIX + sessionDigest, maximum, window);
    }

    @Override
    public boolean allowRedemption(String ticketDigest, int maximum, Duration window) {
        return incrementWithinLimit(REDEMPTION_PREFIX + ticketDigest, maximum, window);
    }

    private boolean incrementWithinLimit(String key, int maximum, Duration window) {
        Long count = redisTemplate.execute(incrementWithLimitScript, List.of(key),
                Integer.toString(maximum), Long.toString(window.toSeconds()));
        if (count == null) {
            throw new IllegalStateException("Redis rate counter returned no result");
        }
        return count > 0;
    }
}
