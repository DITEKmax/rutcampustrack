package ru.rutcampustrack.academic.group;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.cache.CacheManager;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import ru.rutcampustrack.academic.contract.dto.group.UpdateGroupRequest;
import ru.rutcampustrack.academic.grpc.AcademicReadService;
import ru.rutcampustrack.academic.integration.AbstractAcademicIntegrationTest;
import ru.rutcampustrack.academic.integration.AcademicIdentityIntegrationFixture.RequestScope;
import ru.rutcampustrack.auth.session.model.AuthRole;

/** A real outer transaction must not let a reader cache the pre-commit group indefinitely. */
@Import(GroupCacheCommitIT.OwnedCaches.class)
class GroupCacheCommitIT extends AbstractAcademicIntegrationTest {
    @TestConfiguration
    static class OwnedCaches {
        @Bean
        @Primary
        CacheManager ownedCommitTestCaches() {
            return new ConcurrentMapCacheManager("groups", "group_members");
        }
    }

    @Autowired GroupService groups;
    @Autowired AcademicReadService reads;
    @Autowired CacheManager caches;
    @Autowired PlatformTransactionManager transactions;
    @Autowired JdbcTemplate jdbc;
    private RequestScope requestScope;
    private long groupId;
    private String original;

    @BeforeEach
    void prime() {
        var admin = identityFixture.select(identityFixture.canonical().adminId(), AuthRole.ADMIN);
        // Admission precedes the real outer transaction used by both commit/rollback cases.
        requestScope = identityFixture.request(admin);
        groupId = identityFixture.canonical().groupId();
        caches.getCache("groups").clear();
        original = reads.fetchGroup(groupId).getName();
        caches.getCache("group_members").put(groupId, "roster-snapshot");
    }

    @AfterEach
    void clearRequest() {
        if (requestScope != null) requestScope.close();
    }

    @Test
    void concurrentReadBeforeOuterCommitCannotLeaveAnOldCachedGroupAfterCommit() {
        var outer = new TransactionTemplate(transactions);
        outer.executeWithoutResult(
                status -> {
                    groups.updateGroup(groupId, new UpdateGroupRequest("УИТ-311", true, 4));
                    var reader = new TransactionTemplate(transactions);
                    reader.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
                    reader.executeWithoutResult(
                            ignored -> {
                                assertThat(
                                                jdbc.queryForObject(
                                                        "SELECT name FROM groups WHERE id=?",
                                                        String.class,
                                                        groupId))
                                        .isEqualTo(original);
                                assertThat(reads.fetchGroup(groupId).getName()).isEqualTo(original);
                            });
                });
        assertThat(jdbc.queryForObject("SELECT name FROM groups WHERE id=?", String.class, groupId))
                .isEqualTo("УИТ-311");
        assertThat(reads.fetchGroup(groupId).getName())
                .as("group read after outer commit returns the committed name")
                .isEqualTo("УИТ-311");
        assertThat(caches.getCache("group_members").get(groupId)).isNull();
    }

    @Test
    void outerRollbackRetainsValidCachedGroupAndRoster() {
        new TransactionTemplate(transactions)
                .executeWithoutResult(
                        status -> {
                            groups.updateGroup(groupId, new UpdateGroupRequest("УИТ-311", true, 4));
                            status.setRollbackOnly();
                        });
        assertThat(caches.getCache("groups").get(groupId)).isNotNull();
        assertThat(caches.getCache("group_members").get(groupId)).isNotNull();
        assertThat(reads.fetchGroup(groupId).getName()).isEqualTo(original);
        assertThat(jdbc.queryForObject("SELECT name FROM groups WHERE id=?", String.class, groupId))
                .isEqualTo(original);
    }
}
