package ru.rutcampustrack.academic.dashboard;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import ru.rutcampustrack.academic.integration.AbstractAcademicIntegrationTest;
import ru.rutcampustrack.academic.repository.UserRoleGrantWriter;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Real SQL proof for readable counters and the BEFORE_COMMIT durable signal. */
class DashboardInvalidationIT extends AbstractAcademicIntegrationTest {
    @Autowired JdbcTemplate jdbc;
    @Autowired UserRoleGrantWriter grants;
    @Autowired DashboardService dashboard;
    @Autowired PlatformTransactionManager transactionManager;
    @Autowired ObjectMapper mapper;

    @Test void committedGrantAndEmptySignalAreAtomicAndRollbackLeavesNeitherChange() throws Exception {
        long before = dashboard.getStats().getContent().getTotalTeachers();
        long user = jdbc.queryForObject("""
                INSERT INTO users(login,password_hash,last_name,first_name,role,status,is_headman,
                                  password_changed,created_at,updated_at)
                VALUES (?,'synthetic-hash','Dashboard','Teacher','teacher','active',FALSE,FALSE,NOW(),NOW()) RETURNING id
                """, Long.class, "dashboard-invalidation-" + UUID.randomUUID());
        var tx = new TransactionTemplate(transactionManager);
        long signalsBefore = signals();
        try {
            tx.executeWithoutResult(status -> {
                grants.upsertRole(user, "teacher", "active", null);
                assertThat(signals()).isEqualTo(signalsBefore); // not publishable inside domain mutation
            });
            assertThat(dashboard.getStats().getContent().getTotalTeachers()).isEqualTo(before + 1);
            assertThat(signals()).isEqualTo(signalsBefore + 1);
            String json = jdbc.queryForObject("SELECT payload::text FROM academic_outbox "
                    + "WHERE event_type='dashboard.invalidated' ORDER BY id DESC LIMIT 1", String.class);
            var envelope = mapper.readTree(json);
            assertThat(envelope.path("payload").isObject()).isTrue();
            assertThat(envelope.path("payload").size()).isZero();
            assertThat(envelope.path("source").asText()).isEqualTo("academic-service");
            tx.executeWithoutResult(status -> {
                grants.upsertRole(user, "teacher", "suspended", null);
                status.setRollbackOnly();
            });
            assertThat(dashboard.getStats().getContent().getTotalTeachers()).isEqualTo(before + 1);
            assertThat(signals()).isEqualTo(signalsBefore + 1);
        } finally {
            jdbc.update("DELETE FROM user_role_grants WHERE user_id=?", user);
            jdbc.update("DELETE FROM users WHERE id=?", user);
        }
    }

    private long signals() {
        return jdbc.queryForObject("SELECT COUNT(*) FROM academic_outbox WHERE event_type='dashboard.invalidated'", Long.class);
    }
}
