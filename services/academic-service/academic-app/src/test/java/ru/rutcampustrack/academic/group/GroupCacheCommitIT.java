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
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import ru.rutcampustrack.academic.contract.dto.group.UpdateGroupRequest;
import ru.rutcampustrack.academic.grpc.AcademicReadService;
import ru.rutcampustrack.academic.integration.AbstractAcademicIntegrationTest;

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
    @Autowired ru.rutcampustrack.academic.security.RequestContext actor;
    private long groupId;
    private String original;

    @BeforeEach
    void prime() {
        RequestContextHolder.setRequestAttributes(
                new ServletRequestAttributes(new MockHttpServletRequest()));
        actor.setUserId(
                jdbc.queryForObject("SELECT id FROM users WHERE login='admin'", Long.class));
        groupId = jdbc.queryForObject("SELECT id FROM groups WHERE name='ИВТ-211'", Long.class);
        caches.getCache("groups").clear();
        original = reads.fetchGroup(groupId).getName();
        caches.getCache("group_members").put(groupId, "roster-snapshot");
    }

    @AfterEach
    void clearRequest() {
        RequestContextHolder.resetRequestAttributes();
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
