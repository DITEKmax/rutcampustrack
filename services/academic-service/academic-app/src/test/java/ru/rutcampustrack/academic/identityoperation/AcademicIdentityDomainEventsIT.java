package ru.rutcampustrack.academic.identityoperation;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import ru.rutcampustrack.authoperations.grpc.*;

class AcademicIdentityDomainEventsIT extends AbstractAcademicIdentityWriterIT {
    @Autowired ObjectMapper mapper;

    @Test
    void archivingHeadmanRetainsPersonalRevocationsWithAcceptedActorAndOneHelperEvent()
            throws Exception {
        seed("ACTIVE", true, true);
        var otherHistory = history(9);
        long dashboards =
                jdbc.queryForObject(
                        "SELECT count(*) FROM academic_outbox WHERE"
                            + " event_type='dashboard.invalidated'",
                        Long.class);
        var prepared =
                IdentityOperationResult.newBuilder()
                        .addTargets(
                                PreparedIdentityTarget.newBuilder()
                                        .setUserId(8)
                                        .setExpectedAcademicRevision(2)
                                        .setFinalRolesVersion(5)
                                        .addGrants(
                                                AdmissionGrant.newBuilder()
                                                        .setGrantId(11)
                                                        .setRole("HEADMAN")
                                                        .setStatus("ARCHIVED")
                                                        .setGroupId(7))
                                        .addGrants(
                                                AdmissionGrant.newBuilder()
                                                        .setGrantId(12)
                                                        .setRole("STUDENT")
                                                        .setStatus("ARCHIVED")
                                                        .setGroupId(7)))
                        .build();
        var receipts =
                apply(
                        command("ARCHIVE", null, null),
                        IdentityOperationKind.IDENTITY_OPERATION_KIND_ARCHIVE,
                        prepared);
        assertThat(
                        jdbc.queryForObject(
                                "SELECT NOT is_active AND revoked_at IS NOT NULL FROM"
                                    + " headman_assistants WHERE id=22",
                                Boolean.class))
                .isTrue();
        assertThat(
                        jdbc.queryForObject(
                                "SELECT archived_at IS NOT NULL AND directory_status='ARCHIVED'"
                                    + " FROM academic_profiles WHERE id=8",
                                Boolean.class))
                .isTrue();
        assertThat(
                        jdbc.queryForObject(
                                "SELECT left_at FROM student_group_history WHERE id=33",
                                java.time.LocalDate.class))
                .isEqualTo(EFFECTIVE);
        assertThat(history(9)).isEqualTo(otherHistory);
        var facts =
                jdbc.queryForList(
                        "SELECT payload::text FROM academic_outbox WHERE event_type IN"
                            + " ('assistant.access.changed','headman.access.changed') AND payload"
                            + " #>> '{payload,actor_user_id}'='99' ORDER BY id",
                        String.class);
        assertThat(facts).hasSize(2);
        var targets = new java.util.HashSet<Long>();
        var types = new java.util.HashSet<String>();
        for (String fact : facts) {
            var envelope = mapper.readTree(fact);
            var payload = envelope.path("payload");
            targets.add(payload.path("user_id").asLong());
            types.add(envelope.path("event_type").asText());
            assertThat(payload.path("actor_user_id").asLong()).isEqualTo(99);
            assertThat(payload.path("after_active").asBoolean()).isFalse();
            assertThat(payload.path("change").asText()).isEqualTo("REVOKED");
        }
        assertThat(targets).containsExactlyInAnyOrder(9L, 8L);
        assertThat(types)
                .containsExactlyInAnyOrder("assistant.access.changed", "headman.access.changed");
        assertThat(
                        jdbc.queryForObject(
                                "SELECT count(*) FROM academic_outbox WHERE"
                                    + " event_type='dashboard.invalidated'",
                                Long.class))
                .isEqualTo(dashboards + 1);
        assertThat(receipts)
                .singleElement()
                .satisfies(
                        receipt -> assertThat(receipt.getCommittedAcademicRevision()).isEqualTo(3));
    }
}
