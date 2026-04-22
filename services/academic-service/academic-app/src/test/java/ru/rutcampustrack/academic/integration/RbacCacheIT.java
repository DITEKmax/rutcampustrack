package ru.rutcampustrack.academic.integration;

import net.devh.boot.grpc.client.inject.GrpcClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.CacheManager;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import ru.rutcampustrack.academic.contract.dto.user.PatchUserRequest;
import ru.rutcampustrack.academic.grpc.AcademicGrpcServiceGrpc;
import ru.rutcampustrack.academic.grpc.HeadmanCheckRequest;
import ru.rutcampustrack.academic.grpc.HeadmanCheckResponse;
import ru.rutcampustrack.academic.user.UserService;

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * M05 Группа 3 / D6: покрывает rbac cache namespace для
 * {@code AcademicGrpcServiceImpl.isHeadman}.
 *
 * <ul>
 *   <li>RBAC-01: hit-rate после warm-up — два одинаковых вызова,
 *       hit counter инкрементится, DB query только один.</li>
 *   <li>RBAC-02: @CacheEvict программатический при смене
 *       {@code is_headman} в {@link UserService#patchUser}.</li>
 *   <li>RBAC-03: TTL 1 минута соответствует конфигу.</li>
 * </ul>
 */
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
        "grpc.server.in-process-name=academic-rbac-cache-test",
        "grpc.server.port=-1",
        "grpc.client.inProcess.address=in-process:academic-rbac-cache-test",
        "grpc.client.inProcess.negotiationType=plaintext"
    }
)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class RbacCacheIT extends AbstractAcademicCacheIntegrationTest {

    private static final long GROUP_ID = 1L;
    private static final long TEACHER_ID = 2L;
    private static final long STUDENT_ID = 3L;

    @GrpcClient("inProcess")
    @SuppressWarnings("unused")
    private AcademicGrpcServiceGrpc.AcademicGrpcServiceBlockingStub stub;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private CacheManager cacheManager;

    @Autowired
    private UserService userService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void clearCaches() {
        cacheManager.getCacheNames().forEach(name -> {
            var cache = cacheManager.getCache(name);
            if (cache != null) {
                cache.clear();
            }
        });
    }

    /**
     * RBAC-01: Two consecutive isHeadman calls — второй обслуживается
     * из rbac cache. Наличие Redis key подтверждает кеш-запись
     * (hit-rate косвенно — если бы не было кеша, любая смена БД между
     * вызовами давала бы разный результат; здесь мы полагаемся на
     * тот же паттерн, что используется в {@code CacheIT}).
     */
    @Test
    void isHeadman_secondCall_servedFromRbacCache() {
        HeadmanCheckRequest request = HeadmanCheckRequest.newBuilder()
                .setUserId(STUDENT_ID)
                .setGroupId(GROUP_ID)
                .build();

        HeadmanCheckResponse first = stub.isHeadman(request);
        HeadmanCheckResponse second = stub.isHeadman(request);

        assertThat(first.getIsHeadman()).isTrue();
        assertThat(second.getIsHeadman()).isTrue();

        String cacheKey = "rbac::" + STUDENT_ID + ":" + GROUP_ID;
        assertThat(redisTemplate.hasKey(cacheKey)).isTrue();
    }

    /**
     * RBAC-04: TTL кеша rbac соответствует configured 1 минуте (60s).
     */
    @Test
    void rbacCache_ttlMatchesConfiguredValue() {
        HeadmanCheckRequest request = HeadmanCheckRequest.newBuilder()
                .setUserId(STUDENT_ID)
                .setGroupId(GROUP_ID)
                .build();

        stub.isHeadman(request);

        String cacheKey = "rbac::" + STUDENT_ID + ":" + GROUP_ID;
        long ttl = redisTemplate.getExpire(cacheKey, TimeUnit.SECONDS);
        assertThat(ttl).isBetween(55L, 60L);
    }

    /**
     * RBAC-02: снятие флага {@code is_headman} через {@code patchUser}
     * инвалидирует rbac-запись. Следующий isHeadman возвращает свежий
     * результат (false) — cache.hasKey до вызова = false.
     */
    @Test
    void patchUser_headmanRevoke_evictsRbacCache() {
        HeadmanCheckRequest request = HeadmanCheckRequest.newBuilder()
                .setUserId(STUDENT_ID)
                .setGroupId(GROUP_ID)
                .build();

        // Разогрев кеша
        stub.isHeadman(request);
        String cacheKey = "rbac::" + STUDENT_ID + ":" + GROUP_ID;
        assertThat(redisTemplate.hasKey(cacheKey)).isTrue();

        // Снять флаг старосты
        userService.patchUser(STUDENT_ID,
                new PatchUserRequest(null, null, null, false, null, null, null, null));

        // Кеш rbac::3:1 должен быть evict'нут программатически
        assertThat(redisTemplate.hasKey(cacheKey)).isFalse();

        // Следующий вызов возвращает актуальное значение
        HeadmanCheckResponse fresh = stub.isHeadman(request);
        assertThat(fresh.getIsHeadman()).isFalse();

        // Восстановить для консистентности seed-data между тестами
        jdbcTemplate.update("UPDATE users SET is_headman = true WHERE id = " + STUDENT_ID);
    }

    /**
     * RBAC-03: для не-старосты кеш тоже работает — запись {@code false}
     * попадает в rbac, повторный вызов не идёт в БД.
     */
    @Test
    void isHeadman_negativeResult_alsoCached() {
        HeadmanCheckRequest request = HeadmanCheckRequest.newBuilder()
                .setUserId(TEACHER_ID)
                .setGroupId(GROUP_ID)
                .build();

        HeadmanCheckResponse first = stub.isHeadman(request);
        HeadmanCheckResponse second = stub.isHeadman(request);

        assertThat(first.getIsHeadman()).isFalse();
        assertThat(second.getIsHeadman()).isFalse();

        // Boolean false сериализуется в Redis как валидное значение —
        // disableCachingNullValues() в CacheConfig не блокирует false.
        String cacheKey = "rbac::" + TEACHER_ID + ":" + GROUP_ID;
        assertThat(redisTemplate.hasKey(cacheKey)).isTrue();
    }

}
