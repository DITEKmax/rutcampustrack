package ru.rutcampustrack.auth.integration;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import ru.rutcampustrack.auth.dto.LoginRequest;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * M03a Группа 11: composite (ip, login) rate-limit integration test против реального Redis.
 *
 * <p>Проверяет контракт 01 P0-6: атакующий с одного IP не может DoS-лочить аккаунт
 * жертвы — жертва с другого IP продолжает логиниться.</p>
 */
class LoginRateLimiterIT extends AbstractIntegrationTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private StringRedisTemplate redis;

    @AfterEach
    void cleanup() {
        Set<String> keys = redis.keys("login_attempts:*");
        if (keys != null && !keys.isEmpty()) redis.delete(keys);
        Set<String> blocks = redis.keys("login_blocked:*");
        if (blocks != null && !blocks.isEmpty()) redis.delete(blocks);
    }

    @Test
    @DisplayName("5 wrong-password attempts с одного IP → 6-й даёт 429, Redis key (ip, login)")
    void fiveFailuresFromOneIp_triggersBlock() {
        HttpHeaders h = new HttpHeaders();
        h.set("X-Forwarded-For", "10.0.0.5");
        HttpEntity<LoginRequest> body =
                new HttpEntity<>(new LoginRequest("student", "wrong"), h);

        for (int i = 0; i < 5; i++) {
            ResponseEntity<String> r = restTemplate.exchange("/auth/login",
                    HttpMethod.POST, body, String.class);
            assertThat(r.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }

        // 6-й запрос должен быть уже заблокирован (OtpRateLimitException → 429)
        ResponseEntity<String> blocked = restTemplate.exchange("/auth/login",
                HttpMethod.POST, body, String.class);
        assertThat(blocked.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);

        // Redis ключ построен на composite (ip, login)
        assertThat(redis.hasKey("login_blocked:10.0.0.5:student")).isTrue();
    }

    @Test
    @DisplayName("Атакующий с IP A лочит login, жертва с IP B логинится успешно")
    void attackerIpDoesNotBlockVictimIp() {
        // Атакующий делает 5 попыток → блок (ip=attacker)
        HttpHeaders attackerHeaders = new HttpHeaders();
        attackerHeaders.set("X-Forwarded-For", "9.9.9.9");
        for (int i = 0; i < 5; i++) {
            restTemplate.exchange("/auth/login", HttpMethod.POST,
                    new HttpEntity<>(new LoginRequest("student", "wrong"), attackerHeaders),
                    String.class);
        }
        assertThat(redis.hasKey("login_blocked:9.9.9.9:student")).isTrue();

        // Жертва с корректным паролем через ДРУГОЙ IP должна залогиниться
        HttpHeaders victimHeaders = new HttpHeaders();
        victimHeaders.set("X-Forwarded-For", "1.1.1.1");
        ResponseEntity<String> victimLogin = restTemplate.exchange("/auth/login",
                HttpMethod.POST,
                new HttpEntity<>(new LoginRequest("student", "password"), victimHeaders),
                String.class);

        assertThat(victimLogin.getStatusCode()).isEqualTo(HttpStatus.OK);
        // Жертвин ключ пуст — она не попадала в корзину атакующего
        assertThat(redis.hasKey("login_blocked:1.1.1.1:student")).isFalse();
    }

    @Test
    @DisplayName("Успешный login очищает counter именно для (ip, login), не глобально")
    void successfulLoginClearsOnlyOwnIpLoginBucket() {
        // IP-A: 2 failed — counter=2
        HttpHeaders ipA = new HttpHeaders();
        ipA.set("X-Forwarded-For", "1.1.1.1");
        for (int i = 0; i < 2; i++) {
            restTemplate.exchange("/auth/login", HttpMethod.POST,
                    new HttpEntity<>(new LoginRequest("student", "wrong"), ipA),
                    String.class);
        }
        assertThat(redis.opsForValue().get("login_attempts:1.1.1.1:student")).isEqualTo("2");

        // IP-B: successful login → должен очистить только свой ключ
        HttpHeaders ipB = new HttpHeaders();
        ipB.set("X-Forwarded-For", "2.2.2.2");
        ResponseEntity<String> ok = restTemplate.exchange("/auth/login", HttpMethod.POST,
                new HttpEntity<>(new LoginRequest("student", "password"), ipB),
                String.class);
        assertThat(ok.getStatusCode()).isEqualTo(HttpStatus.OK);

        // IP-A counter НЕ затронут (clearFailures per-ip)
        assertThat(redis.opsForValue().get("login_attempts:1.1.1.1:student")).isEqualTo("2");
        // IP-B counter — не существовал (successful first), ключа и не должно быть
        assertThat(redis.hasKey("login_attempts:2.2.2.2:student")).isFalse();
    }
}
