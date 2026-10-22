package ru.rutcampustrack.academic.notification;

import static org.assertj.core.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import ru.rutcampustrack.academic.assistant.AssistantService;
import ru.rutcampustrack.academic.contract.dto.assistant.AssignAssistantRequest;
import ru.rutcampustrack.academic.contract.dto.user.*;
import ru.rutcampustrack.academic.contract.enums.*;
import ru.rutcampustrack.academic.grpc.AttendanceUserImpactGrpcClient;
import ru.rutcampustrack.academic.integration.AbstractAcademicIntegrationTest;
import ru.rutcampustrack.academic.integration.AcademicIdentityIntegrationFixture;
import ru.rutcampustrack.academic.user.UserArchiveService;
import ru.rutcampustrack.auth.session.model.AuthRole;

import java.util.*;

/** Original NotificationAudienceIT archive parameter; actual protected flow, no ambient test TX. */
class NotificationHeadmanArchiveIT extends AbstractAcademicIntegrationTest {
    @Autowired AssistantService assistants;
    @Autowired UserArchiveService archives;
    @Autowired ObjectMapper mapper;
    @MockitoBean AttendanceUserImpactGrpcClient attendanceImpact;

    @Test
    void userHeadmanCascadesEmitOneRealAssistantRevocation() throws Exception {
        var canonical = identityFixture.canonical();
        long headman = canonical.studentId(),
                group = canonical.groupId(),
                adminId = canonical.adminId();
        var admin = identityFixture.select(adminId, AuthRole.ADMIN);
        long helper =
                identityFixture
                        .seedUser(
                                new CreateUserRequest(
                                        "Synthetic",
                                        "Helper",
                                        null,
                                        UserRole.STUDENT,
                                        group,
                                        null,
                                        900000000555L),
                                admin)
                        .userId();
        var selectedHeadman = identityFixture.select(headman, AuthRole.HEADMAN);
        try (var ignored = identityFixture.request(selectedHeadman)) {
            assistants.assignAssistant(
                    new AssignAssistantRequest(
                            helper, group, List.of(AssistantPermission.MANAGE_EXCUSES)));
        }
        var academic = identityFixture.academicJdbc();
        long baseline =
                academic.queryForObject(
                        "SELECT COALESCE(max(id),0) FROM academic_outbox", Long.class);
        org.mockito.Mockito.when(attendanceImpact.preview(headman))
                .thenReturn(
                        ru.rutcampustrack.attendance.grpc.UserImpactSnapshot.newBuilder()
                                .setUserId(headman)
                                .setObservedAtEpochMs(System.currentTimeMillis())
                                .setSnapshotDigest("44".repeat(32))
                                .build());
        UUID operation = UUID.randomUUID();
        UserArchiveModels.ArchiveRequest request;
        try (var ignored = identityFixture.request(admin, operation)) {
            var preview = archives.preview(headman);
            assertThat(preview.requiresPassword()).isTrue();
            request =
                    new UserArchiveModels.ArchiveRequest(
                            operation,
                            preview.previewDigest(),
                            AcademicIdentityIntegrationFixture.FIRST_FULL_TEST_PASSWORD);
            archives.archive(headman, request);
        }
        // Exact accepted command replay, rather than a new archive attempt against an archived
        // user.
        try (var ignored = identityFixture.request(admin, operation)) {
            archives.archive(headman, request);
        }
        assertThat(
                        academic.queryForObject(
                                "SELECT is_active FROM headman_assistants WHERE group_id=? AND"
                                    + " student_id=?",
                                Boolean.class,
                                group,
                                helper))
                .isFalse();
        var facts =
                academic.query(
                        "SELECT payload::text FROM academic_outbox WHERE id>? AND"
                            + " event_type='assistant.access.changed' AND"
                            + " (payload->'payload'->>'user_id')::bigint=? ORDER BY id",
                        (rs, row) -> {
                            try {
                                return mapper.readTree(rs.getString(1));
                            } catch (java.io.IOException failure) {
                                throw new IllegalStateException(failure);
                            }
                        },
                        baseline,
                        helper);
        assertThat(facts)
                .singleElement()
                .satisfies(
                        fact -> {
                            var payload = fact.path("payload");
                            assertThat(payload.path("change").asText()).isEqualTo("REVOKED");
                            assertThat(payload.path("before_active").asBoolean()).isTrue();
                            assertThat(payload.path("after_active").asBoolean()).isFalse();
                            assertThat(payload.path("group_id").asLong()).isEqualTo(group);
                            assertThat(payload.path("actor_user_id").asLong()).isEqualTo(adminId);
                        });
    }
}
