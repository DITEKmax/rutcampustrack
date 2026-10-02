package ru.rutcampustrack.auth.qr;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

/** Atomic fixed windows, including coarse direct-Auth limits; unavailable Redis never authorizes a command. */
@Component
public final class QrLoginRateLimiter {
    private static final DefaultRedisScript<Long> SCRIPT = new DefaultRedisScript<>("""
            local count = redis.call('INCR', KEYS[1])
            if count == 1 then redis.call('EXPIRE', KEYS[1], 60) end
            if count > tonumber(ARGV[1]) then return -math.max(1, redis.call('TTL', KEYS[1])) end
            return 1
            """, Long.class);
    private final StringRedisTemplate redis;
    private final QrLoginProperties properties;
    public QrLoginRateLimiter(StringRedisTemplate redis, QrLoginProperties properties) { this.redis=redis; this.properties=properties; }
    public void issue(UUID issuer, String ip) {
        limit("issue:ip:"+ipKey(ip), properties.getIssuesPerIpMinute());
        if (issuer!=null) limit("issue:issuer:"+issuer, properties.getIssuesPerIssuerMinute());
    }
    public void status(UUID issuer, String ip) {
        limit("status:ip:"+ipKey(ip), properties.getStatusesPerIpMinute());
        limit("status:issuer:"+issuer, properties.getStatusesPerIssuerMinute());
    }
    public void approve(long user) { limit("approve:user:"+user,properties.getApprovalsPerUserMinute()); }
    public void exchange(UUID issuer) { limit("exchange:issuer:"+issuer,properties.getExchangesPerIssuerMinute()); }
    private static String ipKey(String ip) { return HexFormat.of().formatHex(QrLoginCrypto.hash("qr:ip:"+ip)); }
    private void limit(String scope, int maximum) {
        Long result;
        try { result=redis.execute(SCRIPT,List.of("qr_login:limit:"+scope),Integer.toString(maximum)); }
        catch (RuntimeException unavailable) { throw new QrLoginException(QrLoginException.Code.UNAVAILABLE); }
        if (result==null) throw new QrLoginException(QrLoginException.Code.UNAVAILABLE);
        if (result<0) throw new QrLoginException(QrLoginException.Code.RATE_LIMITED,-result);
    }
}
