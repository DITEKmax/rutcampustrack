package ru.rutcampustrack.auth.authority;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ru.rutcampustrack.groupdeletion.grpc.*;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

/** Atomic first irreversible effect checks the original live actor and exact step-up proof. */
@Service
public class AuthGroupDeletionParticipant {
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private final AuthPasswordStepUpService stepUps;

    public AuthGroupDeletionParticipant(
            JdbcTemplate jdbc, ObjectMapper mapper, AuthPasswordStepUpService stepUps) {
        this.jdbc = jdbc;
        this.mapper = mapper;
        this.stepUps = stepUps;
    }

    @Transactional
    public GroupDeletionReceipt apply(GroupDeletionCommand command) {
        validate(command);
        UUID operation = UUID.fromString(command.getOperationId());
        jdbc.queryForList(
                "SELECT pg_advisory_xact_lock(?)",
                ru.rutcampustrack.shared.security.admin.AdminAccessJdbcAuthority.GLOBAL_LOCK);
        jdbc.queryForList(
                "SELECT i.id FROM auth_identities i WHERE i.id=? OR EXISTS(SELECT 1 FROM"
                    + " user_role_grants g WHERE g.user_id=i.id AND g.group_id=?) ORDER BY i.id FOR"
                    + " UPDATE OF i",
                command.getActorId(),
                command.getGroupId());
        if (Boolean.TRUE.equals(
                jdbc.queryForObject(
                        "SELECT EXISTS(SELECT 1 FROM auth_identities i JOIN user_role_grants g ON"
                            + " g.user_id=i.id WHERE g.group_id=? AND i.fence_operation_id IS NOT"
                            + " NULL)",
                        Boolean.class,
                        command.getGroupId()))) throw conflict();
        jdbc.queryForList(
                "SELECT pg_advisory_xact_lock(4670544,?)",
                (int) (command.getGroupId() % 2147483647));
        var saved =
                jdbc.queryForList(
                        "SELECT * FROM auth_group_deletion_participants WHERE operation_id=? FOR"
                                + " UPDATE",
                        operation);
        Map<String, Object> epoch = saved.isEmpty() ? null : saved.getFirst();
        if (epoch != null
                && ((Long) epoch.get("group_id") != command.getGroupId()
                        || (Long) epoch.get("actor_id") != command.getActorId()
                        || !epoch.get("participant_digest").equals(command.getParticipantDigest())
                        || !epoch.get("preview_digest").equals(command.getPreviewDigest())
                        || !java.util.Arrays.equals(
                                (byte[]) epoch.get("password_receipt"),
                                command.getPasswordReceipt().toByteArray()))) throw conflict();
        if (command.getAction() == GroupDeletionAction.PREVIEW) return snapshot(command);
        if (epoch != null && "DELETED".equals(epoch.get("state"))) {
            if (command.getAction() != GroupDeletionAction.COMMIT) throw conflict();
            return stored(command, epoch);
        }
        if (command.getAction() == GroupDeletionAction.RELEASE) {
            if (epoch != null && "COMMITTING".equals(epoch.get("state"))) throw conflict();
            if (epoch != null && "RELEASED".equals(epoch.get("state")))
                return stored(command, epoch);
            if (epoch == null) insert(command, "[]", "{}", "RELEASED");
            else
                jdbc.update(
                        "UPDATE auth_group_deletion_participants SET state='RELEASED' WHERE"
                                + " operation_id=?",
                        operation);
            return epoch == null
                    ? GroupDeletionReceipt.newBuilder()
                            .setCommand(command)
                            .setParticipantDigest(command.getParticipantDigest())
                            .build()
                    : stored(command, epoch);
        }
        if (epoch != null && "RELEASED".equals(epoch.get("state"))) {
            if (command.getAction() == GroupDeletionAction.COMMIT)
                return stored(command, epoch).toBuilder().setCommitRejected(true).build();
            throw conflict();
        }
        if (epoch != null && command.getAction() == GroupDeletionAction.PREPARE)
            return stored(command, epoch);
        var current = snapshot(command);
        if (!current.getParticipantDigest().equals(command.getParticipantDigest()))
            throw conflict();
        if (command.getAction() == GroupDeletionAction.PREPARE) {
            if (epoch == null)
                insert(
                        command,
                        manifest(command.getGroupId()),
                        json(current.getCountsMap()),
                        "PREPARED");
        } else if (command.getAction() == GroupDeletionAction.SEAL) {
            if (epoch == null || !List.of("PREPARED", "SEALED").contains(epoch.get("state")))
                throw conflict();
            jdbc.update(
                    "UPDATE auth_group_deletion_participants SET state='SEALED' WHERE"
                            + " operation_id=?",
                    operation);
        } else if (command.getAction() == GroupDeletionAction.COMMIT) {
            if (epoch == null || !"SEALED".equals(epoch.get("state"))) throw conflict();
            if (!admitCommit(command)) {
                jdbc.update(
                        "UPDATE auth_group_deletion_participants SET state='RELEASED' WHERE"
                                + " operation_id=?",
                        operation);
                return stored(command, epoch).toBuilder().setCommitRejected(true).build();
            }
            jdbc.update(
                    "UPDATE auth_group_deletion_participants SET state='COMMITTING' WHERE"
                            + " operation_id=?",
                    operation);
            jdbc.queryForObject(
                    "SELECT set_config('rutcampustrack.group_delete_operation_id',?,true)",
                    String.class,
                    operation.toString());
            var users =
                    jdbc.queryForList(
                            "SELECT DISTINCT user_id FROM user_role_grants WHERE group_id=? ORDER"
                                    + " BY user_id",
                            Long.class,
                            command.getGroupId());
            jdbc.update(
                    "UPDATE auth_sessions s SET"
                        + " active_role_grant_id=NULL,session_version=session_version+1 FROM"
                        + " user_role_grants g WHERE s.active_role_grant_id=g.id AND g.group_id=?",
                    command.getGroupId());
            jdbc.update("DELETE FROM user_role_grants WHERE group_id=?", command.getGroupId());
            for (long user : users)
                jdbc.queryForList("SELECT append_auth_identity_catalog_snapshot(?)", user);
            jdbc.update(
                    "UPDATE auth_group_deletion_participants SET state='DELETED' WHERE"
                            + " operation_id=?",
                    operation);
        } else throw conflict();
        return current;
    }

    private boolean admitCommit(GroupDeletionCommand command) {
        try {
            var proof =
                    ru.rutcampustrack.authsecurity.grpc.PasswordStepUpReceipt.parseFrom(
                            command.getPasswordReceipt());
            var binding = proof.getBinding();
            var identity = proof.getActor();
            if (!proof.getUnknownFields().asMap().isEmpty()
                    || !binding.getUnknownFields().asMap().isEmpty()
                    || binding.getPurpose()
                            != ru.rutcampustrack.authsecurity.grpc.PasswordStepUpPurpose
                                    .PASSWORD_STEP_UP_PURPOSE_GROUP_DELETE
                    || binding.getTargetId() != command.getGroupId()
                    || !binding.getOperationId().equals(command.getOperationId())
                    || !binding.getPreviewDigest().equals(command.getPreviewDigest())
                    || identity.getUserId() != command.getActorId()) return false;
            return stepUps.admitStoredGroupCommit(proof);
        } catch (com.google.protobuf.InvalidProtocolBufferException invalid) {
            return false;
        }
    }

    private GroupDeletionReceipt snapshot(GroupDeletionCommand c) {
        Map<String, Long> counts = new TreeMap<>();
        counts.put(
                "authGrants",
                jdbc.queryForObject(
                        "SELECT count(*) FROM user_role_grants WHERE group_id=?",
                        Long.class,
                        c.getGroupId()));
        counts.put(
                "accountsAffected",
                jdbc.queryForObject(
                        "SELECT count(DISTINCT user_id) FROM user_role_grants WHERE group_id=?",
                        Long.class,
                        c.getGroupId()));
        return GroupDeletionReceipt.newBuilder()
                .setCommand(c)
                .setParticipantDigest(hash(manifest(c.getGroupId())))
                .putAllCounts(counts)
                .build();
    }

    private String manifest(long group) {
        return jdbc.queryForObject(
                "SELECT coalesce(jsonb_agg(to_jsonb(g) ORDER BY g.id),'[]'::jsonb)::text FROM"
                        + " user_role_grants g WHERE group_id=?",
                String.class,
                group);
    }

    private void insert(GroupDeletionCommand c, String manifest, String counts, String state) {
        jdbc.update(
                "INSERT INTO"
                    + " auth_group_deletion_participants(operation_id,group_id,actor_id,participant_digest,preview_digest,manifest,counts,state,password_receipt)"
                    + " VALUES(?,?,?,?,?,?::jsonb,?::jsonb,?,?)",
                UUID.fromString(c.getOperationId()),
                c.getGroupId(),
                c.getActorId(),
                c.getParticipantDigest(),
                c.getPreviewDigest(),
                manifest,
                counts,
                state,
                c.getPasswordReceipt().toByteArray());
    }

    private GroupDeletionReceipt stored(GroupDeletionCommand c, Map<String, Object> e) {
        try {
            return GroupDeletionReceipt.newBuilder()
                    .setCommand(c)
                    .setParticipantDigest((String) e.get("participant_digest"))
                    .putAllCounts(
                            mapper.readValue(
                                    e.get("counts").toString(),
                                    new TypeReference<Map<String, Long>>() {}))
                    .build();
        } catch (java.io.IOException invalid) {
            throw new IllegalStateException(invalid);
        }
    }

    private String json(Object value) {
        try {
            return mapper.writeValueAsString(value);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static String hash(String value) {
        try {
            return HexFormat.of()
                    .formatHex(
                            MessageDigest.getInstance("SHA-256")
                                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static void validate(GroupDeletionCommand c) {
        if (c.getLessonsCount() != 0
                || c.getGroupId() <= 0
                || c.getActorId() <= 0
                || !c.getUnknownFields().asMap().isEmpty()
                || c.getAction() == GroupDeletionAction.UNRECOGNIZED
                || c.getAction() == GroupDeletionAction.GROUP_DELETION_ACTION_UNSPECIFIED)
            throw conflict();
        if (!UUID.fromString(c.getOperationId()).toString().equals(c.getOperationId()))
            throw conflict();
        if (c.getAction() != GroupDeletionAction.PREVIEW
                && (!c.getParticipantDigest().matches("[0-9a-f]{64}")
                        || !c.getPreviewDigest().matches("[0-9a-f]{64}"))) throw conflict();
    }

    private static RuntimeException conflict() {
        return io.grpc.Status.FAILED_PRECONDITION
                .withDescription("Group deletion exact scope or phase changed")
                .asRuntimeException();
    }
}
