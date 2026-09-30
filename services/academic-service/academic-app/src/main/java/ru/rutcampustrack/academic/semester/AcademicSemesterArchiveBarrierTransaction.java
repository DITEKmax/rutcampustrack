package ru.rutcampustrack.academic.semester;

import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import ru.rutcampustrack.academic.contract.enums.SemesterArchiveAction;
import ru.rutcampustrack.academic.contract.enums.SemesterTransition;
import ru.rutcampustrack.academic.contract.enums.SemesterDeletionPhase;
import ru.rutcampustrack.academic.exception.ConflictException;

import java.sql.PreparedStatement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Academic's durable local write fence, publication admission and epoch release. */
@Service
public class AcademicSemesterArchiveBarrierTransaction {

    private static final int BARRIER_LOCK_NAMESPACE = 5_452_097;

    private final JdbcTemplate jdbc;

    public AcademicSemesterArchiveBarrierTransaction(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Installs the local fence in the same transaction as central transition + operation/outbox. */
    public void install(UUID operationId, long semesterId, long stateVersion,
                        SemesterArchiveAction action) {
        requireTransaction();
        lockSemester(semesterId);
        Map<String, Object> current = barrier(semesterId);
        if (action == SemesterArchiveAction.ARCHIVE) {
            if (current != null && operationId.equals(current.get("operation_id"))
                    && stateVersion == number(current.get("state_version"))
                    && "PENDING".equals(current.get("participant_state"))) {
                return;
            }
            if (current != null && (!"RELEASED".equals(current.get("participant_state"))
                    || stateVersion <= number(current.get("state_version")))) {
                throw new ConflictException("Academic archive barrier is fenced by another epoch");
            }
            if (current == null) {
                jdbc.update("""
                        INSERT INTO academic_semester_archive_barriers
                            (semester_id, operation_id, state_version, participant_state)
                        VALUES (?, ?, ?, 'PENDING')
                        """, semesterId, operationId, stateVersion);
            } else {
                replace(operationId, semesterId, stateVersion, "PENDING", null);
            }
            capturePendingPublications(operationId, semesterId, stateVersion);
            return;
        }

        boolean releasedArchivedDeletion = current != null && "RELEASED".equals(current.get("participant_state"))
                && Boolean.TRUE.equals(jdbc.queryForObject("""
                    SELECT EXISTS (SELECT 1 FROM semester_archive_operations
                     WHERE operation_id = ? AND semester_id = ? AND state_version = ?
                       AND action = 'DELETE' AND delete_phase = 'CANCELLED' AND prior_state = 'ARCHIVED'
                       AND NOT irreversible_intent)
                    """, Boolean.class, current.get("operation_id"), semesterId, current.get("state_version")));
        if (current == null || !("READY".equals(current.get("participant_state")) || releasedArchivedDeletion)
                || stateVersion <= number(current.get("state_version"))) {
            if (current != null && operationId.equals(current.get("operation_id"))
                    && stateVersion == number(current.get("state_version"))
                    && "PREPARED_RESTORE".equals(current.get("participant_state"))) {
                return;
            }
            throw new ConflictException("Academic archive barrier is not ready for this restore epoch");
        }
        replace(operationId, semesterId, stateVersion, "PREPARED_RESTORE", null);
    }

    public void installDeletion(UUID operationId, long semesterId, long stateVersion) {
        requireTransaction();
        lockSemester(semesterId);
        Map<String, Object> current = barrier(semesterId);
        if (current != null && sameIdentity(current, operationId, stateVersion)
                && "DELETE_PREPARING".equals(current.get("participant_state"))) return;
        if (current != null && ("DELETED".equals(current.get("participant_state"))
                || !("READY".equals(current.get("participant_state"))
                || "RELEASED".equals(current.get("participant_state")))
                || stateVersion <= number(current.get("state_version")))) {
            throw new ConflictException("Academic delete barrier is fenced by another operation/version");
        }
        if (current == null) {
            jdbc.update("""
                    INSERT INTO academic_semester_archive_barriers
                        (semester_id, operation_id, state_version, participant_state)
                    VALUES (?, ?, ?, 'DELETE_PREPARING')
                    """, semesterId, operationId, stateVersion);
        } else {
            replace(operationId, semesterId, stateVersion, "DELETE_PREPARING", null);
        }
        capturePendingPublications(operationId, semesterId, stateVersion);
    }

    /** Seal is a distinct durable step, called only after Schedule has drained and returned READY. */
    @Transactional
    public String sealArchive(UUID operationId, long semesterId, long stateVersion) {
        requireTransaction();
        lockSemester(semesterId);
        Map<String, Object> current = barrier(semesterId);
        if (!sameIdentity(current, operationId, stateVersion)) {
            throw new ConflictException("Academic archive seal does not match the current barrier epoch");
        }
        if ("READY".equals(current.get("participant_state"))) return null;
        if (!"PENDING".equals(current.get("participant_state"))
                || !authority(semesterId, stateVersion, SemesterTransition.ARCHIVING, false)) {
            throw new ConflictException("Academic archive seal requires the exact active archive transition");
        }

        String reason = firstPendingReason(semesterId);
        if (reason == null) {
            updateState(operationId, semesterId, stateVersion, "READY", null);
            return null;
        }
        updateState(operationId, semesterId, stateVersion, "PENDING", reason);
        return reason;
    }

    /** Local half of release is safe only against the exact committed central release-pending epoch. */
    @Transactional
    public void releaseRestore(UUID operationId, long semesterId, long stateVersion) {
        requireTransaction();
        lockSemester(semesterId);
        Map<String, Object> current = barrier(semesterId);
        if (sameIdentity(current, operationId, stateVersion)
                && "RELEASED".equals(current.get("participant_state"))) return;
        if (!sameIdentity(current, operationId, stateVersion)
                || !"PREPARED_RESTORE".equals(current.get("participant_state"))
                || !authority(semesterId, stateVersion, SemesterTransition.NONE, true)) {
            throw new ConflictException("Academic restore release does not match the confirmed central epoch");
        }
        updateState(operationId, semesterId, stateVersion, "RELEASED", null);
    }

    @Transactional
    public String prepareDelete(UUID operationId, long semesterId, long stateVersion) {
        requireTransaction();
        lockSemester(semesterId);
        Map<String, Object> current = barrier(semesterId);
        if (!sameIdentity(current, operationId, stateVersion)
                || !"DELETE_PREPARING".equals(current.get("participant_state"))
                || !deletionAuthority(operationId, semesterId, stateVersion, SemesterDeletionPhase.PREPARING)) {
            throw new ConflictException("Academic delete preparation does not match the current barrier epoch");
        }
        return firstPendingDeleteReason(operationId, semesterId, stateVersion);
    }

    @Transactional
    public String sealDelete(UUID operationId, long semesterId, long stateVersion) {
        requireTransaction();
        lockSemester(semesterId);
        Map<String, Object> current = barrier(semesterId);
        if (!sameIdentity(current, operationId, stateVersion)) {
            throw new ConflictException("Academic delete seal does not match the current barrier epoch");
        }
        if ("DELETE_SEALED".equals(current.get("participant_state"))) return null;
        if (!"DELETE_PREPARING".equals(current.get("participant_state"))
                || !deletionAuthority(operationId, semesterId, stateVersion, SemesterDeletionPhase.PREPARING)) {
            throw new ConflictException("Academic delete seal requires the exact preparation phase");
        }
        String reason = firstPendingDeleteReason(operationId, semesterId, stateVersion);
        if (reason == null) {
            updateState(operationId, semesterId, stateVersion, "DELETE_SEALED", null);
        } else {
            updateState(operationId, semesterId, stateVersion, "DELETE_PREPARING", reason);
        }
        return reason;
    }

    @Transactional
    public void releaseDelete(UUID operationId, long semesterId, long stateVersion) {
        requireTransaction();
        lockSemester(semesterId);
        Map<String, Object> current = barrier(semesterId);
        if (sameIdentity(current, operationId, stateVersion)
                && "RELEASED".equals(current.get("participant_state"))) return;
        if (!sameIdentity(current, operationId, stateVersion)
                || !("DELETE_PREPARING".equals(current.get("participant_state"))
                || "DELETE_SEALED".equals(current.get("participant_state")))
                || !deletionAuthority(operationId, semesterId, stateVersion, SemesterDeletionPhase.RELEASING)) {
            throw new ConflictException("Academic delete release does not match the confirmed release phase");
        }
        updateState(operationId, semesterId, stateVersion, "RELEASED", null);
    }

    /** Deletes Academic domain rows and the semester last, after all remote DELETED receipts are durable. */
    @Transactional
    public void deleteOwnDomainAndSemester(UUID operationId, long semesterId, long stateVersion,
                                           String expectedAcademicDigest) {
        requireTransaction();
        lockSemester(semesterId);
        Map<String, Object> current = barrier(semesterId);
        if (!sameIdentity(current, operationId, stateVersion)
                || !("DELETE_SEALED".equals(current.get("participant_state"))
                || "DELETED".equals(current.get("participant_state")))
                || !deletionAuthority(operationId, semesterId, stateVersion, SemesterDeletionPhase.DELETING)
                || !exactRemoteDeleteReceipts(operationId, semesterId, stateVersion, expectedAcademicDigest)) {
            throw new ConflictException("Academic delete lacks the exact irreversible participant proof");
        }
        setLocal("rutcampustrack.semester_delete_operation_id", operationId.toString());
        setLocal("rutcampustrack.semester_delete_state_version", Long.toString(stateVersion));
        setLocal("rutcampustrack.semester_delete_academic_digest", expectedAcademicDigest);

        // The row-level delete guards admit this unobservable in-transaction state change only
        // for the exact operation above. Every other writer serializes on this same semester lock.
        int neutralized = jdbc.update("""
                UPDATE semesters
                   SET is_archived = FALSE, archive_transition = 'NONE',
                       deletion_phase = NULL, transition_operation_id = NULL
                 WHERE id = ? AND state_version = ?
                   AND archive_transition = 'DELETING'
                   AND transition_operation_id = ? AND deletion_phase = 'DELETING'
                """, semesterId, stateVersion, operationId);
        requireOne(neutralized, "Academic deletion authority changed before final row removal");

        jdbc.update("""
                UPDATE homework_binding_archives archived
                   SET homework_id = NULL
                  FROM homeworks homework
                 WHERE archived.homework_id = homework.id AND homework.semester_id = ?
                """, semesterId);
        jdbc.update("""
                DELETE FROM homework_completions completion
                 USING homeworks homework
                 WHERE completion.homework_id = homework.id AND homework.semester_id = ?
                """, semesterId);
        jdbc.update("DELETE FROM homeworks WHERE semester_id = ?", semesterId);
        jdbc.update("DELETE FROM assignments WHERE semester_id = ?", semesterId);
        updateState(operationId, semesterId, stateVersion, "DELETED", null);
        int deleted = jdbc.update("DELETE FROM semesters WHERE id = ?", semesterId);
        requireOne(deleted, "Academic semester disappeared before final deletion");
    }

    /** Ordinary write entrypoint: acquire this lock before binding/row locks, then fail closed. */
    public void lockOrdinaryWrite(long semesterId) {
        requireTransaction();
        lockSemester(semesterId);
        Map<String, Object> state = authorityState(semesterId);
        Map<String, Object> barrier = barrier(semesterId);
        if (authorityBlocks(state) || barrier != null && isFenced(String.valueOf(barrier.get("participant_state")))) {
            throw new ConflictException("Семестр временно заблокирован переходом архивации или восстановления");
        }
    }

    /** Schedule-confirmed publication admission is deliberately limited to one immutable binding identity. */
    public void admitPendingPublication(UUID requestKey, long semesterId, long bindingId,
                                        long actorId, byte[] payloadHash) {
        requireTransaction();
        lockSemester(semesterId);
        Map<String, Object> state = authorityState(semesterId);
        Map<String, Object> current = barrier(semesterId);
        if ((current == null || "RELEASED".equals(current.get("participant_state")))
                && !authorityBlocks(state)) {
            return;
        }
        if (!isPreparing(current) || !preparationAuthority(semesterId, current)) {
            throw new ConflictException("Публикация домашнего задания не принята до seal архивации");
        }
        int updated = jdbc.update("""
                UPDATE academic_semester_archive_publication_admissions
                   SET payload_hash = coalesce(payload_hash, ?)
                 WHERE operation_id = ? AND state_version = ? AND semester_id = ?
                   AND binding_id = ? AND actor_id = ? AND request_key = ?
                   AND (payload_hash IS NULL OR payload_hash = ?) AND consumed_at IS NULL
                   AND resolution_state = 'ADMITTED'
                """, payloadHash, current.get("operation_id"), current.get("state_version"),
                semesterId, bindingId, actorId, requestKey, payloadHash);
        if (updated != 1) {
            throw new ConflictException("Принятая публикация не совпадает с admission архивации");
        }
        setLocal("rutcampustrack.archive_publication_request_key", requestKey.toString());
    }

    /**
     * Resolves one exact Schedule PENDING reservation. The transaction takes the
     * same semester-then-binding locks as publication writes, so an absent row is
     * durable negative proof before the Schedule-only cancellation RPC.
     */
    @Transactional
    public BindingResolution preparePendingBindingResolution(
            UUID operationId, long semesterId, long stateVersion, long bindingId,
            long occurrenceId, long actorId, UUID requestKey, byte[] payloadHash, long revision) {
        requireTransaction();
        if (operationId == null || semesterId <= 0 || stateVersion < 0 || bindingId <= 0
                || occurrenceId <= 0 || actorId <= 0 || requestKey == null
                || payloadHash == null || payloadHash.length != 32 || revision <= 0) {
            throw new IllegalArgumentException("Schedule archive binding identity is invalid");
        }
        lockSemester(semesterId);
        Map<String, Object> current = barrier(semesterId);
        boolean archivePreparation = sameIdentity(current, operationId, stateVersion)
                && "PENDING".equals(current.get("participant_state"))
                && authorityBlocksArchive(authorityState(semesterId), stateVersion);
        boolean deletePreparation = sameIdentity(current, operationId, stateVersion)
                && "DELETE_PREPARING".equals(current.get("participant_state"))
                && deletionAuthority(operationId, semesterId, stateVersion, SemesterDeletionPhase.PREPARING);
        if (!archivePreparation && !deletePreparation) {
            throw new ConflictException("Schedule pending binding does not match an active Academic preparation epoch");
        }
        lockBinding(bindingId);

        Map<String, Object> homework = jdbc.query("""
                SELECT id, semester_id, binding_id, actor_id, request_key, payload_hash, publication_state
                  FROM homeworks WHERE binding_id = ? FOR UPDATE
                """, resultSet -> resultSet.next() ? Map.of(
                "id", resultSet.getLong("id"),
                "semester_id", resultSet.getLong("semester_id"),
                "binding_id", resultSet.getLong("binding_id"),
                "actor_id", resultSet.getLong("actor_id"),
                "request_key", resultSet.getObject("request_key", UUID.class),
                "payload_hash", resultSet.getBytes("payload_hash"),
                "publication_state", resultSet.getString("publication_state")) : null, bindingId);
        if (homework != null && (number(homework.get("semester_id")) != semesterId
                || number(homework.get("binding_id")) != bindingId
                || number(homework.get("actor_id")) != actorId
                || !requestKey.equals(homework.get("request_key"))
                || !Arrays.equals((byte[]) homework.get("payload_hash"), payloadHash))) {
            throw new ConflictException("Schedule pending binding points to different Academic content");
        }

        List<Map<String, Object>> admissionRows = jdbc.queryForList("""
                SELECT actor_id, request_key, payload_hash, source_event_id, admitted_homework_id, consumed_at,
                       resolution_state, terminal_event_id, schedule_occurrence_id, schedule_revision
                  FROM academic_semester_archive_publication_admissions
                 WHERE operation_id = ? AND state_version = ? AND semester_id = ? AND binding_id = ?
                 FOR UPDATE
                """, operationId, stateVersion, semesterId, bindingId);
        Map<String, Object> admission = admissionRows.isEmpty() ? null : admissionRows.getFirst();

        Long homeworkId = homework == null ? null : number(homework.get("id"));
        if (admission == null) {
            jdbc.update("""
                    INSERT INTO academic_semester_archive_publication_admissions
                        (operation_id, state_version, semester_id, binding_id, actor_id,
                         request_key, payload_hash, admitted_homework_id)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                    """, operationId, stateVersion, semesterId, bindingId, actorId,
                    requestKey, payloadHash, homeworkId);
            admission = jdbc.queryForMap("""
                    SELECT actor_id, request_key, payload_hash, source_event_id, admitted_homework_id, consumed_at,
                           resolution_state, terminal_event_id, schedule_occurrence_id, schedule_revision
                      FROM academic_semester_archive_publication_admissions
                     WHERE operation_id = ? AND state_version = ? AND semester_id = ? AND binding_id = ?
                     FOR UPDATE
                    """, operationId, stateVersion, semesterId, bindingId);
        }
        if (admission.get("payload_hash") != null
                && !Arrays.equals((byte[]) admission.get("payload_hash"), payloadHash)) {
            throw new ConflictException("Schedule archive binding hash conflicts with Academic admission");
        }
        if (number(admission.get("actor_id")) != actorId
                || !requestKey.equals(admission.get("request_key"))) {
            throw new ConflictException("Schedule archive binding actor or request key conflicts with Academic admission");
        }
        if (admission.get("admitted_homework_id") != null
                && !admission.get("admitted_homework_id").equals(homeworkId)) {
            throw new ConflictException("Schedule archive binding points to a different admitted homework");
        }

        String resolution = String.valueOf(admission.get("resolution_state"));
        if ("CANCELLED_UNPUBLISHED".equals(resolution)) {
            Long savedOccurrence = (Long) admission.get("schedule_occurrence_id");
            Long savedRevision = (Long) admission.get("schedule_revision");
            UUID savedEvent = (UUID) admission.get("terminal_event_id");
            if (savedOccurrence == null || savedOccurrence != occurrenceId
                    || savedRevision == null || savedRevision != revision || savedEvent == null) {
                throw new ConflictException("Schedule terminal cancellation replay has a different reservation identity");
            }
            return new BindingResolution(BindingResolutionKind.TERMINAL,
                    bindingId, savedOccurrence, actorId, requestKey, savedRevision,
                    (byte[]) admission.get("payload_hash"), null);
        }
        if ("CANCEL_REQUESTED".equals(resolution)) {
            Long savedOccurrence = (Long) admission.get("schedule_occurrence_id");
            Long savedRevision = (Long) admission.get("schedule_revision");
            if (savedOccurrence == null || savedRevision == null
                    || savedOccurrence != occurrenceId || savedRevision != revision) {
                throw new ConflictException("Schedule cancellation replay has a different reservation epoch");
            }
            return new BindingResolution(BindingResolutionKind.CANCEL_UNPUBLISHED,
                    bindingId, savedOccurrence, actorId, requestKey, savedRevision,
                    (byte[]) admission.get("payload_hash"), null);
        }
        if (!"ADMITTED".equals(resolution) || admission.get("consumed_at") != null) {
            throw new ConflictException("Academic archive admission is already terminal");
        }

        if (homeworkId != null) {
            if (admission.get("admitted_homework_id") == null) {
                int admitted = jdbc.update("""
                        UPDATE academic_semester_archive_publication_admissions
                           SET admitted_homework_id = ?, payload_hash = coalesce(payload_hash, ?)
                         WHERE operation_id = ? AND state_version = ? AND semester_id = ? AND binding_id = ?
                           AND resolution_state = 'ADMITTED' AND consumed_at IS NULL
                           AND admitted_homework_id IS NULL
                        """, homeworkId, payloadHash, operationId, stateVersion, semesterId, bindingId);
                if (admitted != 1) throw new ConflictException("Academic publication admission changed during reconciliation");
            }
            String publicationState = String.valueOf(homework.get("publication_state"));
            if ("PENDING".equals(publicationState)) {
                return new BindingResolution(BindingResolutionKind.CONFIRM_MATERIALIZED,
                        bindingId, occurrenceId, actorId, requestKey, revision, payloadHash, homeworkId);
            }
            throw new ConflictException("Schedule pending reservation has no matching pending Academic publication");
        }

        UUID transferEventId = (UUID) admission.get("source_event_id");
        if (!hasExactCompletedEmptyTransfer(bindingId, actorId, requestKey, payloadHash,
                semesterId, transferEventId)) {
            return new BindingResolution(BindingResolutionKind.WAIT_FOR_TRANSFER_RECEIPT,
                    bindingId, occurrenceId, actorId, requestKey, revision, payloadHash, null);
        }
        int requested = jdbc.update("""
                UPDATE academic_semester_archive_publication_admissions
                   SET payload_hash = coalesce(payload_hash, ?), resolution_state = 'CANCEL_REQUESTED',
                       schedule_occurrence_id = ?, schedule_revision = ?
                 WHERE operation_id = ? AND state_version = ? AND semester_id = ? AND binding_id = ?
                   AND actor_id = ? AND request_key = ? AND (payload_hash IS NULL OR payload_hash = ?)
                   AND resolution_state = 'ADMITTED' AND admitted_homework_id IS NULL
                   AND consumed_at IS NULL
                """, payloadHash, occurrenceId, revision, operationId, stateVersion,
                semesterId, bindingId, actorId, requestKey, payloadHash);
        if (requested != 1) throw new ConflictException("Academic negative-proof fence changed during cancellation");
        return new BindingResolution(BindingResolutionKind.CANCEL_UNPUBLISHED,
                bindingId, occurrenceId, actorId, requestKey, revision, payloadHash, null);
    }

    /** Bounded candidates let the coordinator redrive a committed PENDING publication after Schedule is ACTIVE. */
    @Transactional(readOnly = true)
    public List<PendingHomeworkPublication> pendingMaterializedPublications(
            UUID operationId, long semesterId, long stateVersion, int limit) {
        if (limit <= 0 || limit > 32) throw new IllegalArgumentException("archive redrive limit must be 1..32");
        return jdbc.query("""
                SELECT admission.binding_id, admission.actor_id, admission.request_key,
                       admission.payload_hash, homework.id AS homework_id
                  FROM academic_semester_archive_publication_admissions admission
                  JOIN homeworks homework ON homework.id = admission.admitted_homework_id
                 WHERE admission.operation_id = ? AND admission.state_version = ?
                   AND admission.semester_id = ? AND admission.resolution_state = 'ADMITTED'
                   AND admission.consumed_at IS NULL AND homework.publication_state = 'PENDING'
                   AND homework.binding_id = admission.binding_id
                   AND homework.actor_id = admission.actor_id
                   AND homework.request_key = admission.request_key
                   AND homework.payload_hash = admission.payload_hash
                 ORDER BY admission.binding_id LIMIT ?
                """, (resultSet, rowNum) -> new PendingHomeworkPublication(
                resultSet.getLong("binding_id"), resultSet.getLong("actor_id"),
                resultSet.getObject("request_key", UUID.class), resultSet.getBytes("payload_hash"),
                resultSet.getLong("homework_id")), operationId, stateVersion, semesterId, limit);
    }

    /**
     * Records that a delete-scoped pending publication reached its exact Schedule binding
     * identity. The content remains pending and is removed only after irreversible intent.
     */
    @Transactional
    public void markDeletionPublicationDrained(UUID operationId, long semesterId, long stateVersion,
                                               long bindingId, long actorId, UUID requestKey,
                                               byte[] payloadHash, long homeworkId) {
        requireTransaction();
        lockSemester(semesterId);
        Map<String, Object> current = barrier(semesterId);
        if (!sameIdentity(current, operationId, stateVersion)
                || !"DELETE_PREPARING".equals(current.get("participant_state"))
                || !deletionAuthority(operationId, semesterId, stateVersion, SemesterDeletionPhase.PREPARING)
                || bindingId <= 0 || actorId <= 0 || requestKey == null
                || payloadHash == null || payloadHash.length != 32 || homeworkId <= 0) {
            throw new ConflictException("Materialized publication is outside the exact delete preparation");
        }
        int updated = jdbc.update("""
                UPDATE academic_semester_archive_publication_admissions admission
                   SET consumed_at = coalesce(consumed_at, now())
                  FROM homeworks homework
                 WHERE admission.operation_id = ? AND admission.state_version = ?
                   AND admission.semester_id = ? AND admission.binding_id = ?
                   AND admission.actor_id = ? AND admission.request_key = ?
                   AND admission.payload_hash = ? AND admission.admitted_homework_id = homework.id
                   AND homework.id = ? AND homework.semester_id = ?
                   AND homework.binding_id = admission.binding_id
                   AND homework.actor_id = admission.actor_id
                   AND homework.request_key = admission.request_key
                   AND homework.payload_hash = admission.payload_hash
                   AND homework.publication_state = 'PENDING'
                   AND admission.resolution_state = 'ADMITTED'
                """, operationId, stateVersion, semesterId, bindingId, actorId, requestKey,
                payloadHash, homeworkId, semesterId);
        if (updated == 0) {
            Boolean exactReplay = jdbc.queryForObject("""
                    SELECT EXISTS (
                        SELECT 1 FROM academic_semester_archive_publication_admissions admission
                        JOIN homeworks homework ON homework.id = admission.admitted_homework_id
                         WHERE admission.operation_id = ? AND admission.state_version = ?
                           AND admission.semester_id = ? AND admission.binding_id = ?
                           AND admission.actor_id = ? AND admission.request_key = ?
                           AND admission.payload_hash = ? AND admission.admitted_homework_id = ?
                           AND admission.resolution_state = 'ADMITTED' AND admission.consumed_at IS NOT NULL
                           AND homework.semester_id = ? AND homework.publication_state = 'PENDING'
                    )
                    """, Boolean.class, operationId, stateVersion, semesterId, bindingId,
                    actorId, requestKey, payloadHash, homeworkId, semesterId);
            if (!Boolean.TRUE.equals(exactReplay)) {
                throw new ConflictException("Schedule confirmation has no exact pending Academic publication");
            }
        }
    }

    @Transactional(readOnly = true)
    public List<BindingResolution> pendingCancellations(UUID operationId, long semesterId,
                                                        long stateVersion, int limit) {
        if (limit <= 0 || limit > 32) throw new IllegalArgumentException("archive cancellation limit must be 1..32");
        return jdbc.query("""
                SELECT binding_id, schedule_occurrence_id, actor_id, request_key,
                       payload_hash, schedule_revision
                  FROM academic_semester_archive_publication_admissions
                 WHERE operation_id = ? AND state_version = ? AND semester_id = ?
                   AND resolution_state = 'CANCEL_REQUESTED'
                   AND schedule_occurrence_id IS NOT NULL AND schedule_revision IS NOT NULL
                 ORDER BY binding_id LIMIT ?
                """, (resultSet, rowNum) -> new BindingResolution(
                BindingResolutionKind.CANCEL_UNPUBLISHED, resultSet.getLong("binding_id"),
                resultSet.getLong("schedule_occurrence_id"), resultSet.getLong("actor_id"),
                resultSet.getObject("request_key", UUID.class), resultSet.getLong("schedule_revision"),
                resultSet.getBytes("payload_hash"), null), operationId, stateVersion, semesterId, limit);
    }

    @Transactional
    public void recordCancellationEvent(UUID operationId, long semesterId, long stateVersion,
                                       long bindingId, UUID requestKey, long occurrenceId,
                                       long revision, UUID terminalEventId) {
        requireTransaction();
        lockSemester(semesterId);
        int updated = jdbc.update("""
                UPDATE academic_semester_archive_publication_admissions
                   SET terminal_event_id = coalesce(terminal_event_id, ?)
                 WHERE operation_id = ? AND state_version = ? AND semester_id = ? AND binding_id = ?
                   AND request_key = ? AND schedule_occurrence_id = ? AND schedule_revision = ?
                   AND resolution_state IN ('CANCEL_REQUESTED', 'CANCELLED_UNPUBLISHED')
                   AND (terminal_event_id IS NULL OR terminal_event_id = ?)
                """, terminalEventId, operationId, stateVersion, semesterId, bindingId,
                requestKey, occurrenceId, revision, terminalEventId);
        if (updated != 1) throw new ConflictException("Schedule cancellation event does not match its Academic tombstone");
    }

    /**
     * Fences an exact no-content Schedule cancellation while Academic is in
     * PENDING. A pre-fence Schedule effect may arrive without an archive
     * request tombstone; when its admission was captured, its exact event and
     * completed transfer receipt become the durable cancellation proof here.
     * Returns false for effects outside an archive epoch or with no admission.
     */
    public boolean trackPendingArchivedBinding(long semesterId, long bindingId, long actorId,
                                               UUID requestKey, long occurrenceId,
                                               long terminalRevision, UUID terminalEventId,
                                               byte[] effectPayloadHash) {
        requireTransaction();
        lockSemester(semesterId);
        Map<String, Object> current = barrier(semesterId);
        if (current == null || !isPreparing(current)) {
            return false;
        }
        long stateVersion = number(current.get("state_version"));
        if (!preparationAuthority(semesterId, current)) {
            throw new ConflictException("Schedule cancellation arrived outside Academic's active archive epoch");
        }
        lockBinding(bindingId);

        Integer exactEffect = jdbc.queryForObject("""
                SELECT count(*) FROM academic_semester_archive_effect_receipts
                 WHERE source_event_id = ? AND event_type = 'homework.binding.archived'
                   AND semester_id = ? AND binding_id = ? AND payload_hash = ?
                   AND state = 'PENDING'
                """, Integer.class, terminalEventId, semesterId, bindingId, effectPayloadHash);
        if (exactEffect == null || exactEffect != 1) {
            throw new ConflictException("Schedule cancellation has no exact pending Academic effect receipt");
        }

        Map<String, Object> homework = jdbc.query("""
                SELECT actor_id, request_key FROM homeworks WHERE binding_id = ? FOR UPDATE
                """, resultSet -> resultSet.next() ? Map.of(
                "actor_id", resultSet.getLong("actor_id"),
                "request_key", resultSet.getObject("request_key", UUID.class)) : null,
                bindingId);
        if (homework != null) {
            if (number(homework.get("actor_id")) != actorId
                    || !requestKey.equals(homework.get("request_key"))) {
                throw new ConflictException("Schedule no-content cancellation points to different Academic content");
            }
            return false;
        }

        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT actor_id, request_key, payload_hash, source_event_id, admitted_homework_id,
                       consumed_at, resolution_state, terminal_event_id,
                       schedule_occurrence_id, schedule_revision
                  FROM academic_semester_archive_publication_admissions
                 WHERE operation_id = ? AND state_version = ? AND semester_id = ? AND binding_id = ?
                 FOR UPDATE
                """, current.get("operation_id"), stateVersion, semesterId, bindingId);
        if (rows.isEmpty()) return false;
        Map<String, Object> admission = rows.getFirst();
        if (number(admission.get("actor_id")) != actorId
                || !requestKey.equals(admission.get("request_key"))) {
            throw new ConflictException("Schedule no-content cancellation does not match Academic admission");
        }
        byte[] bindingPayloadHash = (byte[]) admission.get("payload_hash");
        if (bindingPayloadHash == null || bindingPayloadHash.length != 32) {
            throw new ConflictException("Academic cancellation admission has no exact binding hash");
        }

        long originalRevision = terminalRevision - 1;
        String resolution = String.valueOf(admission.get("resolution_state"));
        if ("CANCEL_REQUESTED".equals(resolution)
                || "CANCELLED_UNPUBLISHED".equals(resolution)) {
            Long savedOccurrence = (Long) admission.get("schedule_occurrence_id");
            Long savedRevision = (Long) admission.get("schedule_revision");
            UUID savedEvent = (UUID) admission.get("terminal_event_id");
            if (savedOccurrence == null || savedOccurrence != occurrenceId
                    || savedRevision == null || savedRevision != originalRevision
                    || savedEvent != null && !savedEvent.equals(terminalEventId)) {
                throw new ConflictException("Schedule cancellation replay has a different reservation identity");
            }
            if (savedEvent == null) {
                int recorded = jdbc.update("""
                        UPDATE academic_semester_archive_publication_admissions
                           SET terminal_event_id = ?
                         WHERE operation_id = ? AND state_version = ? AND semester_id = ? AND binding_id = ?
                           AND resolution_state = 'CANCEL_REQUESTED' AND terminal_event_id IS NULL
                        """, terminalEventId, current.get("operation_id"), stateVersion, semesterId, bindingId);
                if (recorded != 1) throw new ConflictException("Academic cancellation event changed during replay");
            }
            if (!hasExactCompletedEmptyTransfer(bindingId, actorId, requestKey,
                    bindingPayloadHash, semesterId, (UUID) admission.get("source_event_id"))) {
                throw new ConflictException("Schedule cancellation is waiting for its exact transfer receipt");
            }
            return true;
        }
        if (!"ADMITTED".equals(resolution) || admission.get("consumed_at") != null
                || admission.get("admitted_homework_id") != null) {
            throw new ConflictException("Academic admission cannot be terminally cancelled as unpublished");
        }
        if (!hasExactCompletedEmptyTransfer(bindingId, actorId, requestKey,
                bindingPayloadHash, semesterId, (UUID) admission.get("source_event_id"))) {
            throw new ConflictException("Schedule cancellation is waiting for its exact transfer receipt");
        }
        int requested = jdbc.update("""
                UPDATE academic_semester_archive_publication_admissions
                   SET resolution_state = 'CANCEL_REQUESTED', terminal_event_id = ?,
                       schedule_occurrence_id = ?, schedule_revision = ?
                 WHERE operation_id = ? AND state_version = ? AND semester_id = ? AND binding_id = ?
                   AND actor_id = ? AND request_key = ? AND payload_hash = ?
                   AND resolution_state = 'ADMITTED' AND admitted_homework_id IS NULL
                   AND consumed_at IS NULL
                """, terminalEventId, occurrenceId, originalRevision,
                current.get("operation_id"), stateVersion, semesterId, bindingId,
                actorId, requestKey, bindingPayloadHash);
        if (requested != 1) throw new ConflictException("Academic negative-proof fence changed during cancellation");
        return true;
    }

    /** Settles the exact no-content terminal event after its durable effect receipt is APPLIED. */
    public void completeTrackedNoContentEffect(long semesterId, long bindingId, long actorId,
                                                UUID requestKey, UUID sourceEventId) {
        requireTransaction();
        lockSemester(semesterId);
        Map<String, Object> current = barrier(semesterId);
        if (current == null || !isPreparing(current)) return;
        int updated = jdbc.update("""
                UPDATE academic_semester_archive_publication_admissions
                   SET resolution_state = 'CANCELLED_UNPUBLISHED', consumed_at = now()
                 WHERE operation_id = ? AND state_version = ? AND semester_id = ?
                   AND binding_id = ? AND actor_id = ? AND request_key = ?
                   AND admitted_homework_id IS NULL AND consumed_at IS NULL
                   AND resolution_state = 'CANCEL_REQUESTED'
                   AND terminal_event_id = ?
                """, current.get("operation_id"), current.get("state_version"), semesterId,
                bindingId, actorId, requestKey, sourceEventId);
        if (updated != 1) {
            throw new ConflictException("Academic cancellation tombstone is missing or has already changed");
        }
    }

    /** Applies a validated Schedule transfer batch only while its exact archive epoch remains PENDING. */
    public void lockTransferSemester(long semesterId) {
        requireTransaction();
        lockSemester(semesterId);
    }

    public void admitTransferBatch(UUID eventId, UUID operationId, int batchIndex,
                                   String operationHash, String batchHash, long semesterId) {
        requireTransaction();
        lockSemester(semesterId);
        Map<String, Object> state = authorityState(semesterId);
        Map<String, Object> current = barrier(semesterId);
        if (current == null || "RELEASED".equals(current.get("participant_state"))) {
            if (authorityBlocks(state)) {
                throw new ConflictException("Schedule transfer cannot mutate a blocked Academic semester");
            }
            return;
        }
        if (!isPreparing(current)
                || !preparationAuthority(semesterId, current)) {
            throw new ConflictException("Schedule transfer arrived after Academic archive seal");
        }
        setLocal("rutcampustrack.archive_transfer_event_id", eventId.toString());
        setLocal("rutcampustrack.archive_transfer_operation_id", operationId.toString());
        setLocal("rutcampustrack.archive_transfer_batch_index", Integer.toString(batchIndex));
        setLocal("rutcampustrack.archive_transfer_operation_hash", operationHash);
        setLocal("rutcampustrack.archive_transfer_batch_hash", batchHash);
        setLocal("rutcampustrack.archive_transfer_semester_id", Long.toString(semesterId));
    }

    public void selectTransferBinding(long bindingId, String bindingHash) {
        requireTransaction();
        setLocal("rutcampustrack.archive_transfer_binding_id", Long.toString(bindingId));
        setLocal("rutcampustrack.archive_transfer_binding_hash", bindingHash);
    }

    /** Records a null-content publication accepted by an exact trusted transfer batch. */
    public void trackPendingTransferPublication(long semesterId, long bindingId, long actorId,
                                                UUID requestKey, byte[] payloadHash, UUID sourceEventId) {
        requireTransaction();
        lockSemester(semesterId);
        Map<String, Object> current = barrier(semesterId);
        Map<String, Object> state = authorityState(semesterId);
        String admittedEvent = jdbc.queryForObject(
                "SELECT current_setting('rutcampustrack.archive_transfer_event_id', TRUE)",
                String.class);
        if (current == null || "RELEASED".equals(current.get("participant_state"))) {
            if (authorityBlocks(state)) {
                throw new ConflictException("Transfer publication arrived while the central semester is blocked");
            }
            return;
        }
        if (current == null || !isPreparing(current)
                || !preparationAuthority(semesterId, current)
                || !sourceEventId.toString().equals(admittedEvent)) {
            throw new ConflictException("Transfer publication is not covered by the current accepted archive batch");
        }
        int inserted = jdbc.update("""
                INSERT INTO academic_semester_archive_publication_admissions
                    (operation_id, state_version, semester_id, binding_id, actor_id,
                     request_key, payload_hash, source_event_id)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (operation_id, state_version, binding_id) DO NOTHING
                """, current.get("operation_id"), current.get("state_version"), semesterId,
                bindingId, actorId, requestKey, payloadHash, sourceEventId);
        if (inserted == 0) {
            Integer exact = jdbc.queryForObject("""
                    SELECT COUNT(*) FROM academic_semester_archive_publication_admissions
                     WHERE operation_id = ? AND state_version = ? AND semester_id = ?
                       AND binding_id = ? AND actor_id = ? AND request_key = ?
                       AND payload_hash = ? AND source_event_id = ?
                    """, Integer.class, current.get("operation_id"), current.get("state_version"),
                    semesterId, bindingId, actorId, requestKey, payloadHash, sourceEventId);
            if (exact == null || exact != 1) {
                throw new ConflictException("Accepted transfer conflicts with an existing publication admission");
            }
        }
    }

    /** Opens the narrow tracked-event mutation context after its source envelope was validated. */
    public void beginArchiveEffect(UUID eventId, long semesterId) {
        requireTransaction();
        lockSemester(semesterId);
        Map<String, Object> state = authorityState(semesterId);
        Map<String, Object> current = barrier(semesterId);
        if (authorityBlocks(state) || current != null && isFenced(String.valueOf(current.get("participant_state")))) {
            if (current == null || !isPreparing(current)
                    || !preparationAuthority(semesterId, current)) {
                throw new ConflictException("Archive effect arrived after Academic archive seal");
            }
        }
        setLocal("rutcampustrack.archive_effect_event_id", eventId.toString());
    }

    /** Returns true when a direct RPC continuation must wait for its durable tracked Schedule event. */
    public boolean mustWaitForTrackedArchiveEffect(long semesterId) {
        requireTransaction();
        lockSemester(semesterId);
        Map<String, Object> state = authorityState(semesterId);
        Map<String, Object> barrier = barrier(semesterId);
        return barrier != null && isFenced(String.valueOf(barrier.get("participant_state")))
                || authorityBlocks(state);
    }

    private void capturePendingPublications(UUID operationId, long semesterId, long stateVersion) {
        jdbc.update("""
                INSERT INTO academic_semester_archive_publication_admissions
                    (operation_id, state_version, semester_id, binding_id, actor_id, request_key,
                     payload_hash, admitted_homework_id)
                SELECT ?, ?, semester_id, binding_id, actor_id, request_key, payload_hash, id
                  FROM homeworks
                 WHERE semester_id = ? AND publication_state = 'PENDING'
                   AND NOT EXISTS (SELECT 1 FROM homework_binding_archives archived
                                    WHERE archived.binding_id = homeworks.binding_id)
                ON CONFLICT (operation_id, state_version, binding_id) DO NOTHING
                """, operationId, stateVersion, semesterId);
        jdbc.update("""
                INSERT INTO academic_semester_archive_publication_admissions
                    (operation_id, state_version, semester_id, binding_id, actor_id,
                     request_key, payload_hash, source_event_id)
                SELECT ?, ?, transfer.semester_id, transfer.binding_id, transfer.actor_id,
                       transfer.request_key, transfer.binding_payload_hash, transfer.source_event_id
                  FROM homework_binding_transfer_markers transfer
                 WHERE transfer.semester_id = ? AND transfer.state = 'PENDING'
                   AND transfer.homework_id IS NULL
                   AND NOT EXISTS (SELECT 1 FROM homework_binding_archives archived
                                    WHERE archived.binding_id = transfer.binding_id)
                ON CONFLICT (operation_id, state_version, binding_id) DO NOTHING
                """, operationId, stateVersion, semesterId);
    }

    private String firstPendingReason(long semesterId) {
        Long pendingPublication = jdbc.queryForObject("""
                SELECT count(*) FROM homeworks
                 WHERE semester_id = ? AND publication_state = 'PENDING'
                """, Long.class, semesterId);
        if (pendingPublication != null && pendingPublication > 0) {
            return "Ожидается подтверждение принятой публикации домашнего задания в Academic";
        }
        Long pendingAdmission = jdbc.queryForObject("""
                SELECT count(*) FROM academic_semester_archive_publication_admissions
                 WHERE semester_id = ? AND consumed_at IS NULL
                   AND resolution_state IN ('ADMITTED', 'CANCEL_REQUESTED')
                """, Long.class, semesterId);
        if (pendingAdmission != null && pendingAdmission > 0) {
            return "Ожидается завершение ранее принятой публикации домашнего задания";
        }
        Long pendingTransfer = jdbc.queryForObject("""
                SELECT count(*) FROM homework_binding_transfer_markers
                 WHERE semester_id = ? AND state = 'PENDING'
                   AND NOT EXISTS (SELECT 1 FROM homework_binding_archives archived
                                    WHERE archived.binding_id = homework_binding_transfer_markers.binding_id)
                """, Long.class, semesterId);
        if (pendingTransfer != null && pendingTransfer > 0) {
            return "Ожидается применение принятого переноса привязки домашнего задания";
        }
        Long pendingEffect = jdbc.queryForObject("""
                SELECT count(*) FROM academic_semester_archive_effect_receipts
                 WHERE semester_id = ? AND state <> 'APPLIED'
                """, Long.class, semesterId);
        if (pendingEffect != null && pendingEffect > 0) {
            return "Ожидается точный ACK применения эффекта Schedule";
        }
        return null;
    }

    private String firstPendingDeleteReason(UUID operationId, long semesterId, long stateVersion) {
        Long unownedPendingPublication = jdbc.queryForObject("""
                SELECT count(*) FROM homeworks homework
                 WHERE homework.semester_id = ? AND homework.publication_state = 'PENDING'
                   AND NOT EXISTS (
                       SELECT 1 FROM academic_semester_archive_publication_admissions admission
                        WHERE admission.operation_id = ? AND admission.state_version = ?
                          AND admission.semester_id = homework.semester_id
                          AND admission.binding_id = homework.binding_id
                          AND admission.actor_id = homework.actor_id
                          AND admission.request_key = homework.request_key
                          AND admission.payload_hash = homework.payload_hash
                          AND admission.admitted_homework_id = homework.id
                          AND admission.resolution_state = 'ADMITTED'
                          AND admission.consumed_at IS NOT NULL)
                """, Long.class, semesterId, operationId, stateVersion);
        if (unownedPendingPublication != null && unownedPendingPublication > 0) {
            return "Ожидается сверка незавершённой публикации домашнего задания";
        }
        Long pendingAdmission = jdbc.queryForObject("""
                SELECT count(*) FROM academic_semester_archive_publication_admissions
                 WHERE operation_id = ? AND state_version = ? AND semester_id = ?
                   AND consumed_at IS NULL AND resolution_state IN ('ADMITTED', 'CANCEL_REQUESTED')
                """, Long.class, operationId, stateVersion, semesterId);
        if (pendingAdmission != null && pendingAdmission > 0) {
            return "Ожидается завершение ранее принятой публикации домашнего задания";
        }
        Long pendingTransfer = jdbc.queryForObject("""
                SELECT count(*) FROM homework_binding_transfer_markers
                 WHERE semester_id = ? AND state = 'PENDING'
                   AND NOT EXISTS (SELECT 1 FROM homework_binding_archives archived
                                    WHERE archived.binding_id = homework_binding_transfer_markers.binding_id)
                """, Long.class, semesterId);
        if (pendingTransfer != null && pendingTransfer > 0) {
            return "Ожидается завершение ранее принятого переноса привязки домашнего задания";
        }
        Long pendingEffect = jdbc.queryForObject("""
                SELECT count(*) FROM academic_semester_archive_effect_receipts
                 WHERE semester_id = ? AND state <> 'APPLIED'
                """, Long.class, semesterId);
        if (pendingEffect != null && pendingEffect > 0) {
            return "Ожидается точный ACK применения эффекта Schedule";
        }
        return null;
    }

    private boolean hasExactCompletedEmptyTransfer(long bindingId, long actorId, UUID requestKey,
                                                   byte[] payloadHash, long semesterId,
                                                   UUID sourceEventId) {
        if (sourceEventId == null) {
            Boolean hasMarker = jdbc.queryForObject("""
                    SELECT EXISTS(SELECT 1 FROM homework_binding_transfer_markers WHERE binding_id = ?)
                    """, Boolean.class, bindingId);
            return !Boolean.TRUE.equals(hasMarker);
        }
        Boolean exact = jdbc.queryForObject("""
                SELECT EXISTS (
                    SELECT 1
                      FROM homework_binding_transfer_markers marker
                      JOIN lesson_transfer_receipts receipt
                        ON receipt.source_event_id = marker.source_event_id
                       AND receipt.operation_id = marker.operation_id
                       AND receipt.batch_index = marker.batch_index
                       AND receipt.operation_hash = marker.operation_hash
                      JOIN homework_binding_transfer_history history
                        ON history.source_event_id = marker.source_event_id
                       AND history.batch_index = marker.batch_index
                       AND history.operation_id = marker.operation_id
                       AND history.operation_hash = marker.operation_hash
                       AND history.binding_id = marker.binding_id
                       AND history.binding_payload_hash = marker.binding_payload_hash
                     WHERE marker.binding_id = ? AND marker.actor_id = ?
                       AND marker.request_key = ? AND marker.binding_payload_hash = ?
                       AND marker.semester_id = ? AND marker.source_event_id = ?
                       AND marker.source_event_id IS NOT NULL AND marker.batch_index IS NOT NULL
                       AND marker.state = 'PENDING' AND marker.homework_id IS NULL
                       AND receipt.result = 'APPLIED' AND receipt.error_code IS NULL
                       AND history.actor_id = marker.actor_id
                       AND history.request_key = marker.request_key
                       AND history.semester_id = marker.semester_id
                       AND history.homework_id IS NULL
                       AND history.result_state = 'PENDING_PUBLICATION'
                )
                """, Boolean.class, bindingId, actorId, requestKey, payloadHash, semesterId,
                sourceEventId);
        return Boolean.TRUE.equals(exact);
    }

    private void lockBinding(long bindingId) {
        jdbc.execute((ConnectionCallback<Void>) connection -> {
            try (PreparedStatement statement = connection.prepareStatement("SELECT pg_advisory_xact_lock(?)")) {
                statement.setLong(1, bindingId);
                statement.execute();
            }
            return null;
        });
    }

    private boolean authority(long semesterId, long version, SemesterTransition transition,
                              boolean releasePending) {
        Map<String, Object> state = authorityState(semesterId);
        return number(state.get("state_version")) == version
                && transition.name().equals(state.get("archive_transition"))
                && Boolean.FALSE.equals(state.get("is_active"))
                && Boolean.FALSE.equals(state.get("is_archived"))
                && Boolean.valueOf(releasePending).equals(state.get("archive_release_pending"));
    }

    private boolean deletionAuthority(UUID operationId, long semesterId, long version,
                                      SemesterDeletionPhase phase) {
        return jdbc.queryForObject("""
                SELECT EXISTS (
                    SELECT 1 FROM semesters
                     WHERE id = ? AND state_version = ? AND archive_transition = 'DELETING'
                       AND transition_operation_id = ? AND deletion_phase = ?
                       AND NOT is_active AND NOT archive_release_pending
                )
                """, Boolean.class, semesterId, version, operationId, phase.name());
    }

    private boolean exactRemoteDeleteReceipts(UUID operationId, long semesterId, long version,
                                              String academicDigest) {
        Boolean exact = jdbc.queryForObject("""
                SELECT EXISTS (
                    SELECT 1 FROM semester_archive_operations
                     WHERE operation_id = ? AND semester_id = ? AND action = 'DELETE'
                       AND state_version = ? AND delete_phase = 'DELETING'
                       AND irreversible_intent
                       AND academic_participant_digest = ?
                       AND schedule_status = 'DELETED' AND attendance_status = 'DELETED'
                )
                """, Boolean.class, operationId, semesterId, version, academicDigest);
        return Boolean.TRUE.equals(exact);
    }

    private static boolean isPreparing(Map<String, Object> current) {
        return current != null && ("PENDING".equals(current.get("participant_state"))
                || "DELETE_PREPARING".equals(current.get("participant_state")));
    }

    private boolean preparationAuthority(long semesterId, Map<String, Object> current) {
        return isPreparing(current) && ("PENDING".equals(current.get("participant_state"))
                ? authorityBlocksArchive(authorityState(semesterId), number(current.get("state_version")))
                : deletionAuthority((UUID) current.get("operation_id"), semesterId,
                    number(current.get("state_version")), SemesterDeletionPhase.PREPARING));
    }

    private boolean authorityBlocksArchive(Map<String, Object> state, long version) {
        return number(state.get("state_version")) == version
                && "ARCHIVING".equals(state.get("archive_transition"))
                && Boolean.FALSE.equals(state.get("is_active"))
                && Boolean.FALSE.equals(state.get("is_archived"))
                && Boolean.FALSE.equals(state.get("archive_release_pending"));
    }

    private static boolean authorityBlocks(Map<String, Object> state) {
        return Boolean.TRUE.equals(state.get("is_archived"))
                || Boolean.TRUE.equals(state.get("archive_release_pending"))
                || !"NONE".equals(state.get("archive_transition"));
    }

    private static boolean isFenced(String state) {
        return "PENDING".equals(state) || "READY".equals(state) || "PREPARED_RESTORE".equals(state)
                || "DELETE_PREPARING".equals(state) || "DELETE_SEALED".equals(state)
                || "DELETED".equals(state);
    }

    private Map<String, Object> authorityState(long semesterId) {
        return jdbc.queryForMap("""
                SELECT state_version, archive_transition, is_active, is_archived,
                       archive_release_pending, deletion_phase, transition_operation_id
                  FROM semesters WHERE id = ?
                """, semesterId);
    }

    private Map<String, Object> barrier(long semesterId) {
        return jdbc.query("""
                SELECT operation_id, state_version, participant_state
                  FROM academic_semester_archive_barriers WHERE semester_id = ? FOR UPDATE
                """, resultSet -> resultSet.next() ? Map.of(
                "operation_id", resultSet.getObject("operation_id", UUID.class),
                "state_version", resultSet.getLong("state_version"),
                "participant_state", resultSet.getString("participant_state")) : null,
                semesterId);
    }

    private void replace(UUID operationId, long semesterId, long stateVersion,
                         String state, String reason) {
        int updated = jdbc.update("""
                UPDATE academic_semester_archive_barriers
                   SET operation_id = ?, state_version = ?, participant_state = ?,
                       blocking_reason = ?, updated_at = now()
                 WHERE semester_id = ?
                """, operationId, stateVersion, state, reason, semesterId);
        requireOne(updated, "Academic archive barrier changed during epoch update");
    }

    private void updateState(UUID operationId, long semesterId, long stateVersion,
                             String state, String reason) {
        int updated = jdbc.update("""
                UPDATE academic_semester_archive_barriers
                   SET participant_state = ?, blocking_reason = ?, updated_at = now()
                 WHERE semester_id = ? AND operation_id = ? AND state_version = ?
                """, state, reason, semesterId, operationId, stateVersion);
        requireOne(updated, "Academic archive barrier identity changed during update");
    }

    private void lockSemester(long semesterId) {
        if (semesterId <= 0) throw new IllegalArgumentException("semesterId must be positive");
        jdbc.execute((ConnectionCallback<Void>) connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT pg_advisory_xact_lock(?, ?)")) {
                statement.setInt(1, BARRIER_LOCK_NAMESPACE);
                statement.setInt(2, (int) (semesterId % Integer.MAX_VALUE));
                statement.execute();
            }
            return null;
        });
    }

    private void setLocal(String name, String value) {
        jdbc.queryForObject("SELECT set_config(?, ?, TRUE)", String.class, name, value);
    }

    private static boolean sameIdentity(Map<String, Object> current, UUID operationId, long version) {
        return current != null && operationId.equals(current.get("operation_id"))
                && version == number(current.get("state_version"));
    }

    private static long number(Object value) {
        return ((Number) value).longValue();
    }

    public enum BindingResolutionKind {
        CONFIRM_MATERIALIZED,
        CANCEL_UNPUBLISHED,
        WAIT_FOR_TRANSFER_RECEIPT,
        TERMINAL
    }

    public record BindingResolution(BindingResolutionKind kind, Long bindingId, Long occurrenceId,
                                    Long actorId, UUID requestKey, long revision,
                                    byte[] payloadHash, Long homeworkId) { }

    public record PendingHomeworkPublication(long bindingId, long actorId, UUID requestKey,
                                             byte[] payloadHash, long homeworkId) { }

    private static void requireOne(int rows, String message) {
        if (rows != 1) throw new IllegalStateException(message);
    }

    private static void requireTransaction() {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("Academic archive fence requires an active database transaction");
        }
    }
}
