package ru.rutcampustrack.academic.identityoperation;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.protobuf.ByteString;

import io.grpc.Status;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import ru.rutcampustrack.academic.security.RequestContext;
import ru.rutcampustrack.academic.user.UserArchiveRepository;
import ru.rutcampustrack.authoperations.grpc.*;
import ru.rutcampustrack.shared.security.InternalJwtClaims;

import java.security.MessageDigest;
import java.sql.ResultSet;
import java.util.List;
import java.util.UUID;

class AcademicIdentityLegacyIntentReplayTest {
    @Test
    @SuppressWarnings("unchecked")
    void olderSavedIntentReplaysOriginalBytesWithoutInventingOriginButChangedRequestConflicts()
            throws Exception {
        var mapper = new ObjectMapper().findAndRegisterModules();
        var jdbc = mock(JdbcTemplate.class);
        var context = mock(RequestContext.class);
        var actor =
                new InternalJwtClaims(
                        100, UUID.randomUUID(), 2, 10, "ADMIN", "ACTIVE", null, false, false);
        when(context.getClaims()).thenReturn(actor);
        UUID id = UUID.randomUUID();
        var intent =
                new AcademicIdentityLocalCommand.ApiIntent("HEADMAN_REPLACE", 8, "0".repeat(64));
        var local =
                new AcademicIdentityLocalCommand(
                        intent,
                        null,
                        null,
                        null,
                        null,
                        null,
                        7L,
                        null,
                        8L,
                        null,
                        "headman-replacement",
                        List.of(new AcademicIdentityLocalCommand.GroupRevision(7, 2)));
        ObjectNode json = mapper.valueToTree(local);
        ((ObjectNode) json.get("intent")).remove("admissionPermission");
        byte[] bytes = mapper.writeValueAsBytes(json);
        byte[] localHash = MessageDigest.getInstance("SHA-256").digest(bytes);
        var command =
                PrepareIdentityOperationRequest.newBuilder()
                        .setOperationId(id.toString())
                        .setKind(IdentityOperationKind.IDENTITY_OPERATION_KIND_HEADMAN_REPLACE)
                        .addTargets(
                                IdentityTargetCommand.newBuilder()
                                        .setUserId(8)
                                        .setExpectedRolesVersion(3)
                                        .setExpectedCredentialsVersion(1)
                                        .setExpectedTelegramBindingVersion(1)
                                        .setExpectedAcademicRevision(2)
                                        .setDesiredAdminProfile(
                                                AdmissionAdminProfile.getDefaultInstance())
                                        .setDesiredAccountState(
                                                IdentityAccountState.IDENTITY_ACCOUNT_STATE_ACTIVE)
                                        .addDesiredGrants(
                                                AdmissionGrant.newBuilder()
                                                        .setRole("HEADMAN")
                                                        .setStatus("ACTIVE")
                                                        .setGroupId(7))
                                        .addDesiredGrants(
                                                AdmissionGrant.newBuilder()
                                                        .setGrantId(22)
                                                        .setRole("STUDENT")
                                                        .setStatus("ACTIVE")
                                                        .setGroupId(7)));
        command.setCommandHash(
                ByteString.copyFrom(
                        MessageDigest.getInstance("SHA-256")
                                .digest(command.build().toByteArray())));
        var saved = command.build();
        when(jdbc.query(
                        startsWith("SELECT actor_id,command_payload"),
                        any(RowMapper.class),
                        eq(id)))
                .thenAnswer(
                        call -> {
                            var row = mock(ResultSet.class);
                            when(row.getLong("actor_id")).thenReturn(100L);
                            when(row.getBytes("local_payload")).thenReturn(bytes);
                            when(row.getBytes("local_command_hash")).thenReturn(localHash);
                            when(row.getBytes("command_payload")).thenReturn(saved.toByteArray());
                            return List.of(((RowMapper<?>) call.getArgument(1)).mapRow(row, 0));
                        });
        var store =
                new JdbcAcademicIdentityOperationStore(
                        jdbc,
                        mapper,
                        context,
                        mock(AcademicIdentityDomainWriter.class),
                        mock(AcademicIdentityCacheInvalidator.class),
                        mock(UserArchiveRepository.class));
        var replay =
                store.acceptedForActor(
                        id.toString(), intent.withAdmissionPermission("USERS_MANAGE"));
        assertThat(replay.authCommand()).isEqualTo(saved);
        assertThat(replay.localPayload().toByteArray()).isEqualTo(bytes);
        assertThat(replay.localCommandHash().toByteArray()).isEqualTo(localHash);
        assertThat(
                        mapper.readValue(
                                        replay.localPayload().toByteArray(),
                                        AcademicIdentityLocalCommand.class)
                                .intent()
                                .admissionPermission())
                .isNull();
        assertThatThrownBy(
                        () ->
                                store.acceptedForActor(
                                        id.toString(),
                                        new AcademicIdentityLocalCommand.ApiIntent(
                                                "HEADMAN_REPLACE",
                                                8,
                                                "1".repeat(64),
                                                "USERS_MANAGE")))
                .satisfies(
                        error ->
                                assertThat(Status.fromThrowable(error).getCode())
                                        .isEqualTo(Status.Code.ALREADY_EXISTS));
    }
}
