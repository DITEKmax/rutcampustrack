package ru.rutcampustrack.attendance.integration;

import static ru.rutcampustrack.attendance.integration.AbstractAttendanceIntegrationTest.MONGODB;
import static ru.rutcampustrack.attendance.integration.AbstractAttendanceIntegrationTest.RABBITMQ;

import org.springframework.test.context.DynamicPropertyRegistry;

import java.util.UUID;

/** Own runtime namespaces for audit integration tests; production initialization stays enabled. */
public final class AuditTestIsolation {
    private AuditTestIsolation() {}

    public static void register(DynamicPropertyRegistry registry, String owner, int redisDatabase) {
        String namespace =
                "audit_"
                        + owner.substring(0, Math.min(owner.length(), 20))
                        + "_"
                        + UUID.randomUUID().toString().replace("-", "");
        String uri = MONGODB.getReplicaSetUrl();
        int slash = uri.indexOf('/', "mongodb://".length());
        int query = uri.indexOf('?', slash);
        String databaseUri =
                uri.substring(0, slash) + "/" + namespace + (query < 0 ? "" : uri.substring(query));
        registry.add("spring.data.mongodb.uri", () -> databaseUri);
        registry.add("spring.data.mongodb.database", () -> namespace);
        registry.add("spring.data.redis.database", () -> redisDatabase);
        try {
            var created = RABBITMQ.execInContainer("rabbitmqctl", "add_vhost", namespace);
            if (created.getExitCode() != 0)
                throw new IllegalStateException("Cannot initialize owned broker vhost");
            var grants =
                    RABBITMQ.execInContainer(
                            "rabbitmqctl",
                            "set_permissions",
                            "-p",
                            namespace,
                            RABBITMQ.getAdminUsername(),
                            ".*",
                            ".*",
                            ".*");
            if (grants.getExitCode() != 0)
                throw new IllegalStateException("Cannot grant owned broker vhost");
        } catch (Exception failure) {
            throw new IllegalStateException("Cannot isolate integration broker", failure);
        }
        registry.add("spring.rabbitmq.virtual-host", () -> namespace);
    }
}
