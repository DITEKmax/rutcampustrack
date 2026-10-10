package ru.rutcampustrack.academic.dashboard;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import ru.rutcampustrack.academic.integration.AbstractAcademicIntegrationTest;
import ru.rutcampustrack.academic.repository.UserRoleGrantWriter;
import ru.rutcampustrack.academic.repository.GroupRepository;
import ru.rutcampustrack.academic.group.GroupArchivalService;
import ru.rutcampustrack.academic.entity.Group;

import java.util.UUID;
import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Real SQL proof for readable counters and the BEFORE_COMMIT durable signal. */
class DashboardInvalidationIT extends AbstractAcademicIntegrationTest {
    @Autowired JdbcTemplate jdbc;
    @Autowired UserRoleGrantWriter grants;
    @Autowired DashboardService dashboard;
    @Autowired PlatformTransactionManager transactionManager;
    @Autowired ObjectMapper mapper;
    @Autowired GroupRepository groups;
    @Autowired GroupArchivalService archival;

    @Test void committedGrantAndEmptySignalAreAtomicAndRollbackLeavesNeitherChange() throws Exception {
        long before = dashboard.getStats().getContent().getTotalTeachers();
        long user = jdbc.queryForObject("""
                INSERT INTO users(login,password_hash,last_name,first_name,role,status,is_headman,
                                  password_changed,created_at,updated_at)
                VALUES (?,'synthetic-hash','Dashboard','Teacher','teacher','active',FALSE,FALSE,NOW(),NOW()) RETURNING id
                """, Long.class, UUID.randomUUID().toString().replace("-", ""));
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

    @Test void canonicalGraduationArchiveCommitsReadableGroupCounterAndSignalButRollbackAndRepeatDoNot() {
        String random = UUID.randomUUID().toString();
        String alphabet = "АБВГДЕЖЗИКЛМНОПРС";
        String prefix = "ДР" + alphabet.charAt(Character.digit(random.charAt(0), 16))
                + alphabet.charAt(Character.digit(random.charAt(1), 16));
        Group group = new Group();
        group.setName(prefix + "-411"); group.setAlphabeticCode(prefix); group.setNumericCode("411");
        group.setCurrentCourse(4); group.setTrainingDurationYears(4); group.setDurationStatus("KNOWN");
        group.setActive(true); group.setCreatedAt(OffsetDateTime.now());
        long groupId = groups.saveAndFlush(group).getId();
        long headman = jdbc.queryForObject("""
                INSERT INTO users(login,password_hash,last_name,first_name,role,status,is_headman,
                                  password_changed,created_at,updated_at)
                VALUES (?,'synthetic-hash','Dashboard','Headman','student','active',FALSE,FALSE,NOW(),NOW()) RETURNING id
                """, Long.class, UUID.randomUUID().toString().replace("-", ""));
        jdbc.update("INSERT INTO user_role_grants(user_id,role,status,group_id,created_at,updated_at) "
                + "VALUES (?,'headman','active',?,NOW(),NOW())", headman, groupId);
        var tx = new TransactionTemplate(transactionManager);
        long activeBefore = dashboard.getStats().getContent().getActiveGroups();
        long signalsBefore = signals();
        long archivedBefore = archivedSignals();
        try {
            tx.executeWithoutResult(status -> {
                archival.archive(groups.findByIdForUpdate(groupId).orElseThrow(), 2026);
                groups.flush();
                status.setRollbackOnly();
            });
            assertThat(groups.findById(groupId).orElseThrow().isActive()).isTrue();
            assertThat(dashboard.getStats().getContent().getActiveGroups()).isEqualTo(activeBefore);
            assertThat(signals()).isEqualTo(signalsBefore);
            assertThat(archivedSignals()).isEqualTo(archivedBefore);
            tx.executeWithoutResult(status -> archival.archive(groups.findByIdForUpdate(groupId).orElseThrow(), 2026));
            assertThat(groups.findById(groupId).orElseThrow().isActive()).isFalse();
            assertThat(dashboard.getStats().getContent().getActiveGroups()).isEqualTo(activeBefore - 1);
            assertThat(signals()).isEqualTo(signalsBefore + 1);
            assertThat(archivedSignals()).isEqualTo(archivedBefore + 1);
            assertThatThrownBy(() -> tx.executeWithoutResult(status ->
                    archival.archive(groups.findByIdForUpdate(groupId).orElseThrow(), 2026)))
                    .isInstanceOf(IllegalStateException.class);
            assertThat(signals()).isEqualTo(signalsBefore + 1);
            assertThat(archivedSignals()).isEqualTo(archivedBefore + 1);
        } finally {
            jdbc.update("DELETE FROM user_role_grants WHERE user_id=?", headman);
            jdbc.update("DELETE FROM users WHERE id=?", headman);
            jdbc.update("DELETE FROM groups WHERE id=?", groupId);
        }
    }

    private long archivedSignals() {
        return jdbc.queryForObject("SELECT COUNT(*) FROM academic_outbox WHERE event_type='group.archived'", Long.class);
    }
}
