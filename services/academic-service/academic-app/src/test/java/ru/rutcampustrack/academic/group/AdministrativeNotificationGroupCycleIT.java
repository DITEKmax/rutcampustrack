package ru.rutcampustrack.academic.group;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.isA;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doCallRealMethod;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import ru.rutcampustrack.academic.event.AdministrativeNotificationGroupPromotionCompletedEvent;
import ru.rutcampustrack.academic.event.DomainEventListener;
import ru.rutcampustrack.academic.groupavailability.GroupAvailabilityCoordinator;
import ru.rutcampustrack.academic.groupavailability.GroupAvailabilityStore;
import ru.rutcampustrack.academic.integration.AbstractAcademicIntegrationTest;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

/** Actual committed outbox/marker proof under the common owned split fixture's reset lease. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(GroupPromotionCycleIT.PromotionTestClockConfig.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class AdministrativeNotificationGroupCycleIT extends AbstractAcademicIntegrationTest {
    @Autowired JdbcTemplate jdbc;
    @Autowired GroupPromotionService service;
    @Autowired GroupAvailabilityCoordinator coordinator;
    @MockitoSpyBean DomainEventListener events;
    @Autowired ObjectMapper mapper;

    @Test
    void firstAutomaticBatchCommitsOneFactualOutboxAndMarkerWhileRollbackAndLaterBatchDoNotRepeat()
            throws Exception {
        long cycleId =
                jdbc.queryForObject(
                        """
INSERT INTO semesters(name,date_from,date_to,is_active,semester_type,academic_year)
VALUES ('N4 owned spring cycle',? ,?,false,'SPRING',2025) RETURNING id
""",
                        Long.class,
                        LocalDate.of(2026, 8, 1),
                        LocalDate.of(2026, 8, 31));
        long first = addGroup("НИТ-111");
        var rollback = new IllegalStateException("Owned notification transaction rollback");
        var operation = new AtomicReference<UUID>();
        // Auth invalidation runs outside the local transaction. The real BEFORE_COMMIT listener
        // saves the factual outbox; fail only after that save to roll it back with the marker.
        doAnswer(
                        invocation -> {
                            invocation.callRealMethod();
                            operation.set(
                                    jdbc.queryForObject(
                                            "SELECT operation_id FROM"
                                                + " academic_group_availability_cycle_guards WHERE"
                                                + " cycle_semester_id=?",
                                            UUID.class,
                                            cycleId));
                            assertThat(markerCount(cycleId)).isOne();
                            assertThat(eventCount(cycleId)).isOne();
                            assertThat(groupName(first)).isEqualTo("НИТ-211");
                            throw rollback;
                        })
                .when(events)
                .onDomainEvent(isA(AdministrativeNotificationGroupPromotionCompletedEvent.class));
        try {
            assertThatThrownBy(service::executeAutomaticallyIfDue).isSameAs(rollback);
        } finally {
            doCallRealMethod()
                    .when(events)
                    .onDomainEvent(
                            isA(AdministrativeNotificationGroupPromotionCompletedEvent.class));
        }
        assertThat(markerCount(cycleId)).isZero();
        assertThat(eventCount(cycleId)).isZero();
        assertThat(groupName(first)).isEqualTo("НИТ-111");

        assertThat(operation.get()).isNotNull();
        assertThat(coordinator.recover(operation.get()).phase())
                .isEqualTo(GroupAvailabilityStore.Phase.SUCCEEDED);
        // The committed cycle remains due without repeating the factual notification.
        assertThat(service.executeAutomaticallyIfDue()).isPresent();
        assertThat(groupName(first)).isEqualTo("НИТ-211");
        assertThat(markerCount(cycleId)).isOne();
        assertThat(eventCount(cycleId)).isOne();
        JsonNode fact =
                mapper.readTree(
                                jdbc.queryForObject(
                                        """
SELECT payload::text FROM academic_outbox WHERE event_type='group.promotion.completed'
  AND payload #>> '{payload,cycle_semester_id}'=?
""",
                                        String.class,
                                        Long.toString(cycleId)))
                        .path("payload");
        assertThat(fact.path("cycle_semester_id").asLong()).isEqualTo(cycleId);
        assertThat(fact.path("cycle_date_to").asText()).isEqualTo("2026-08-31");
        assertThat(fact.path("promoted").asLong())
                .isEqualTo(
                        jdbc.queryForObject(
                                "SELECT count(*) FROM group_promotion_cycle_record WHERE"
                                        + " cycle_semester_id=? AND action='PROMOTE'",
                                Long.class,
                                cycleId));
        assertThat(fact.path("graduated").asLong())
                .isEqualTo(
                        jdbc.queryForObject(
                                "SELECT count(*) FROM group_promotion_cycle_record WHERE"
                                        + " cycle_semester_id=? AND action='ARCHIVE'",
                                Long.class,
                                cycleId));
        assertThat(fact.path("skipped").asLong())
                .isEqualTo(
                        jdbc.queryForObject(
                                "SELECT skipped FROM group_promotion_notification_cycle WHERE"
                                        + " cycle_semester_id=?",
                                Long.class,
                                cycleId));

        long later = addGroup("ДОП-111");
        service.executeAutomaticallyIfDue();
        service.executeAutomaticallyIfDue();
        assertThat(groupName(later)).isEqualTo("ДОП-211");
        assertThat(markerCount(cycleId)).isOne();
        assertThat(eventCount(cycleId)).isOne();
        assertThat(
                        jdbc.queryForObject(
                                """
SELECT count(*) FROM academic_outbox WHERE event_type='group.renamed'
  AND payload #>> '{payload,group_id}'=?
  AND payload #>> '{payload,notification_cause}'='AUTOMATIC_GROUP_CYCLE'
""",
                                Long.class,
                                Long.toString(later)))
                .isOne();
    }

    private long addGroup(String name) {
        return jdbc.queryForObject(
                """
                INSERT INTO groups(name,is_active,created_at,duration_status)
                VALUES (?,true,?,'LEGACY_UNKNOWN') RETURNING id
                """,
                Long.class,
                name,
                OffsetDateTime.of(2026, 8, 1, 0, 0, 0, 0, ZoneOffset.UTC));
    }

    private String groupName(long groupId) {
        return jdbc.queryForObject("SELECT name FROM groups WHERE id=?", String.class, groupId);
    }

    private long markerCount(long cycleId) {
        return jdbc.queryForObject(
                "SELECT count(*) FROM group_promotion_notification_cycle WHERE cycle_semester_id=?",
                Long.class,
                cycleId);
    }

    private long eventCount(long cycleId) {
        return jdbc.queryForObject(
                """
                SELECT count(*) FROM academic_outbox WHERE event_type='group.promotion.completed'
                  AND payload #>> '{payload,cycle_semester_id}'=?
                """,
                Long.class,
                Long.toString(cycleId));
    }
}
