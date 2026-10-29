package ru.rutcampustrack.academic.grpc;

import static org.assertj.core.api.Assertions.assertThat;

import com.github.dockerjava.api.model.ExposedPort;
import com.github.dockerjava.api.model.PortBinding;
import com.github.dockerjava.api.model.Ports;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.testcontainers.containers.GenericContainer;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicBoolean;

class RedisHeadmanRateLimiterIT {
    @Test
    void instancesShareQuotaKeepUsersIndependentAndSubsequentCallsDoNotExtendExpiry() {
        try (var redis =
                new GenericContainer<>("redis:7-alpine")
                        .withExposedPorts(6379)
                        .withCreateContainerCmdModifier(
                                command ->
                                        command.getHostConfig()
                                                .withPortBindings(
                                                        new PortBinding(
                                                                Ports.Binding.bindIpAndPort(
                                                                        "127.0.0.1", 0),
                                                                ExposedPort.tcp(6379)))
                                                .withPublishAllPorts(false))) {
            redis.start();
            var config =
                    new RedisStandaloneConfiguration(redis.getHost(), redis.getMappedPort(6379));
            config.setDatabase(15);
            var connections = new LettuceConnectionFactory(config);
            connections.afterPropertiesSet();
            try {
                var template = new StringRedisTemplate(connections);
                var properties = new HeadmanRateLimitProperties(2, 1);
                var first =
                        new RedisHeadmanRateLimiter(
                                template, properties, new SimpleMeterRegistry());
                var second =
                        new RedisHeadmanRateLimiter(
                                template, properties, new SimpleMeterRegistry());
                // Keep this bounded TTL observation inside one natural minute quota window.
                Awaitility.await()
                        .atMost(Duration.ofSeconds(12))
                        .until(() -> Instant.now().getEpochSecond() % 60 < 50);
                Instant started = Instant.now();
                assertThat(first.tryConsume(11)).isTrue();
                assertThat(second.tryConsume(11)).isTrue();
                assertThat(first.tryConsume(11)).isFalse();
                assertThat(second.tryConsume(22)).isTrue();
                assertThat(first.tryConsume(22)).isTrue();
                assertThat(second.tryConsume(22)).isFalse();
                var renewed = new AtomicBoolean();
                Awaitility.await()
                        .pollInterval(Duration.ofMillis(100))
                        .atMost(Duration.ofSeconds(8))
                        .until(
                                () -> {
                                    boolean allowed = second.tryConsume(11);
                                    if (allowed) renewed.set(true);
                                    return allowed;
                                });
                assertThat(renewed).isTrue();
                assertThat(Duration.between(started, Instant.now()))
                        .isGreaterThanOrEqualTo(Duration.ofSeconds(5));
                assertThat(first.tryConsume(11)).isTrue();
                assertThat(second.tryConsume(11)).isFalse();
            } finally {
                connections.destroy();
            }
        }
    }
}
