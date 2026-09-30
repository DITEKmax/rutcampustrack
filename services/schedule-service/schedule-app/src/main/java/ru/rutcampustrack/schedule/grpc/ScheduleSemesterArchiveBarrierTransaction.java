package ru.rutcampustrack.schedule.grpc;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.rutcampustrack.schedule.event.HomeworkBindingArchivedEvent;
import ru.rutcampustrack.schedule.event.SemesterArchiveEffectLedger;
import ru.rutcampustrack.schedule.exception.ConflictException;

import java.util.Arrays;
import java.util.Map;
import java.util.UUID;

/** Persists Schedule's local version fence and reports only durable drain evidence. */
@Service
public class ScheduleSemesterArchiveBarrierTransaction {

    private final JdbcTemplate jdbc;
    private final SemesterArchiveEffectLedger effectLedger;
    private final ApplicationEventPublisher eventPublisher;
    private final ScheduleSemesterDeletionSnapshotReader deletionSnapshots;

    public ScheduleSemesterArchiveBarrierTransaction(JdbcTemplate jdbc,
                                                     SemesterArchiveEffectLedger effectLedger,
                                                     ApplicationEventPublisher eventPublisher,
                                                     ScheduleSemesterDeletionSnapshotReader deletionSnapshots) {
        this.jdbc = jdbc;
        this.effectLedger = effectLedger;
        this.eventPublisher = eventPublisher;
        this.deletionSnapshots = deletionSnapshots;
    }

    @Transactional
    public SetSemesterArchiveBarrierResponse apply(SetSemesterArchiveBarrierRequest request) {
        UUID operationId = uuid(request.getOperationId());
        long semesterId = request.getSemesterId();
        long stateVersion = request.getStateVersion();
        if (semesterId <= 0 || stateVersion < 0) {
            throw new IllegalArgumentException("semester archive barrier identity is invalid");
        }
        lockSemester(semesterId);

        Map<String, Object> current = readBarrier(semesterId);
        SemesterArchiveBarrierCommand command = request.getCommand();
        if (command == SemesterArchiveBarrierCommand.SEMESTER_ARCHIVE_BARRIER_PREPARE_ARCHIVE) {
            prepareArchive(operationId, semesterId, stateVersion, current);
            BlockingStatus blocking = blockingStatus(semesterId);
            String reason = blocking.reason();
            String state = reason == null ? "READY" : "PENDING";
            updateState(operationId, semesterId, stateVersion, state, reason);
            return response(operationId, semesterId, stateVersion, state, reason,
                    blocking.pendingBinding(), null);
        }
        if (command == SemesterArchiveBarrierCommand.SEMESTER_ARCHIVE_BARRIER_RECONCILE_HOMEWORK_BINDING) {
            return reconcileHomeworkBinding(request, operationId, semesterId, stateVersion, current);
        }
        if (command == SemesterArchiveBarrierCommand.SEMESTER_ARCHIVE_BARRIER_PREPARE_RESTORE) {
            prepareRestore(operationId, semesterId, stateVersion, current);
            return response(operationId, semesterId, stateVersion, "PREPARED_RESTORE", null);
        }
        if (command == SemesterArchiveBarrierCommand.SEMESTER_ARCHIVE_BARRIER_RELEASE_RESTORE) {
            releaseRestore(operationId, semesterId, stateVersion, current);
            return response(operationId, semesterId, stateVersion, "RELEASED", null);
        }
        if (command == SemesterArchiveBarrierCommand.SEMESTER_ARCHIVE_BARRIER_PREPARE_DELETE) {
            String expectedDigest = requiredDigest(request.getExpectedParticipantDigest());
            ScheduleSemesterDeletionSnapshotReader.Snapshot snapshot = deletionSnapshots.read(semesterId);
            prepareDelete(operationId, semesterId, stateVersion, expectedDigest, snapshot, current);
            BlockingStatus blocking = blockingStatus(semesterId);
            SetSemesterArchiveBarrierResponse prepared = deletionResponse(operationId, semesterId, stateVersion,
                    blocking.reason() == null ? "READY" : "PENDING", blocking.reason(), snapshot);
            if (blocking.pendingBinding() != null) {
                prepared = prepared.toBuilder().setPendingBinding(response(operationId, semesterId,
                        stateVersion, "PENDING", blocking.reason(), blocking.pendingBinding(), null)
                        .getPendingBinding()).build();
            }
            return prepared;
        }
        if (command == SemesterArchiveBarrierCommand.SEMESTER_ARCHIVE_BARRIER_SEAL_DELETE) {
            String expectedDigest = requiredDigest(request.getExpectedParticipantDigest());
            ScheduleSemesterDeletionSnapshotReader.Snapshot snapshot = deletionSnapshots.read(semesterId);
            sealDelete(operationId, semesterId, stateVersion, expectedDigest, snapshot, current);
            BlockingStatus blocking = blockingStatus(semesterId);
            return deletionResponse(operationId, semesterId, stateVersion,
                    blocking.reason() == null ? "READY" : "PENDING", blocking.reason(), snapshot);
        }
        if (command == SemesterArchiveBarrierCommand.SEMESTER_ARCHIVE_BARRIER_RELEASE_DELETE) {
            String expectedDigest = requiredDigest(request.getExpectedParticipantDigest());
            releaseDelete(operationId, semesterId, stateVersion, expectedDigest, current);
            DeletionReceipt receipt = readDeletionReceipt(semesterId);
            return deletionResponse(operationId, semesterId, stateVersion, "RELEASED", null,
                    receipt == null ? null : receipt.snapshot());
        }
        if (command == SemesterArchiveBarrierCommand.SEMESTER_ARCHIVE_BARRIER_COMMIT_DELETE) {
            String expectedDigest = requiredDigest(request.getExpectedParticipantDigest());
            return commitDelete(operationId, semesterId, stateVersion, expectedDigest, current);
        }
        throw new IllegalArgumentException("semester archive barrier command is unspecified");
    }

    private void prepareDelete(UUID operationId, long semesterId, long version, String expectedDigest,
                               ScheduleSemesterDeletionSnapshotReader.Snapshot snapshot,
                               Map<String, Object> current) {
        if (current == null) {
            insertDeleteBarrier(operationId, semesterId, version, "DELETE_PREPARING", null,
                    expectedDigest, snapshot);
            return;
        }
        if (sameIdentity(current, operationId, version)) {
            if (expectedDigest.equals(current.get("expected_participant_digest"))
                    && ("DELETE_PREPARING".equals(current.get("participant_state"))
                    || "DELETE_SEALED".equals(current.get("participant_state")))) return;
        } else if ("RELEASED".equals(current.get("participant_state"))
                && version > number(current.get("state_version"))) {
            replaceDeleteBarrier(operationId, semesterId, version, "DELETE_PREPARING", null,
                    expectedDigest, snapshot);
            return;
        } else if ("READY".equals(current.get("participant_state"))
                && version > number(current.get("state_version"))) {
            replaceDeleteBarrier(operationId, semesterId, version, "DELETE_PREPARING", null,
                    expectedDigest, snapshot);
            return;
        }
        throw new ConflictException("Schedule delete barrier is fenced by another operation/version");
    }

    private void sealDelete(UUID operationId, long semesterId, long version, String expectedDigest,
                            ScheduleSemesterDeletionSnapshotReader.Snapshot snapshot,
                            Map<String, Object> current) {
        if (current == null || !sameIdentity(current, operationId, version)) {
            throw new ConflictException("Schedule delete seal does not match its prepared barrier");
        }
        if (!expectedDigest.equals(current.get("expected_participant_digest"))) {
            throw new ConflictException("Schedule delete seal has a different preview digest");
        }
        String state = String.valueOf(current.get("participant_state"));
        if ("DELETE_SEALED".equals(state)) return;
        if (!"DELETE_PREPARING".equals(state)) {
            throw new ConflictException("Schedule delete barrier is not in preparation");
        }
        BlockingStatus blocking = blockingStatus(semesterId);
        if (blocking.reason() != null) {
            updateDeleteSnapshot(operationId, semesterId, version, "DELETE_PREPARING",
                    blocking.reason(), expectedDigest, snapshot);
            return;
        }
        updateDeleteSnapshot(operationId, semesterId, version, "DELETE_SEALED",
                null, expectedDigest, snapshot);
    }

    private void releaseDelete(UUID operationId, long semesterId, long version, String expectedDigest,
                               Map<String, Object> current) {
        if (current != null && sameIdentity(current, operationId, version)
                && "RELEASED".equals(current.get("participant_state"))) {
            if (!expectedDigest.equals(current.get("expected_participant_digest"))) {
                throw new ConflictException("Schedule delete release replay has a different preview digest");
            }
            return;
        }
        if (current == null) {
            insertDeleteBarrier(operationId, semesterId, version, "RELEASED", null,
                    expectedDigest, deletionSnapshots.read(semesterId));
            return;
        }
        if (!sameIdentity(current, operationId, version)
                && version > number(current.get("state_version"))
                && ("READY".equals(current.get("participant_state"))
                || "RELEASED".equals(current.get("participant_state")))) {
            replaceDeleteBarrier(operationId, semesterId, version, "RELEASED", null,
                    expectedDigest, deletionSnapshots.read(semesterId));
            return;
        }
        if (!sameIdentity(current, operationId, version)
                || !("DELETE_PREPARING".equals(current.get("participant_state"))
                || "DELETE_SEALED".equals(current.get("participant_state")))) {
            throw new ConflictException("Schedule delete release does not match its local fence");
        }
        if (!expectedDigest.equals(current.get("expected_participant_digest"))) {
            throw new ConflictException("Schedule delete release has a different preview digest");
        }
        updateState(operationId, semesterId, version, "RELEASED", null);
    }

    private SetSemesterArchiveBarrierResponse commitDelete(UUID operationId, long semesterId, long version,
                                                           String expectedDigest,
                                                           Map<String, Object> current) {
        if (current != null && sameIdentity(current, operationId, version)
                && "DELETED".equals(current.get("participant_state"))) {
            DeletionReceipt receipt = readDeletionReceipt(semesterId);
            if (receipt == null || !expectedDigest.equals(receipt.expectedDigest())) {
                throw new ConflictException("Schedule deletion replay has a different durable receipt");
            }
            return deletionResponse(operationId, semesterId, version, "DELETED", null, receipt.snapshot());
        }
        if (current == null || !sameIdentity(current, operationId, version)
                || !"DELETE_SEALED".equals(current.get("participant_state"))) {
            throw new ConflictException("Schedule commit requires the exact sealed delete barrier");
        }
        DeletionReceipt receipt = readDeletionReceipt(semesterId);
        BlockingStatus blocking = blockingStatus(semesterId);
        ScheduleSemesterDeletionSnapshotReader.Snapshot currentSnapshot = deletionSnapshots.read(semesterId);
        if (blocking.reason() != null
                || receipt == null || !expectedDigest.equals(receipt.expectedDigest())
                || !expectedDigest.equals(receipt.participantDigest())
                || !expectedDigest.equals(currentSnapshot.participantDigest())
                || receipt.scheduleTemplates() != currentSnapshot.scheduleTemplates()
                || receipt.oneOffLessons() != currentSnapshot.oneOffLessons()
                || receipt.lessons() != currentSnapshot.lessons()) {
            throw new ConflictException("Schedule domain changed after its deletion fence was sealed");
        }

        setDeletionContext(operationId, version, expectedDigest);
        jdbc.update("""
                UPDATE lesson_homework_bindings binding
                   SET state = 'ARCHIVED', homework_id = NULL, revision = binding.revision + 1, updated_at = now()
                  FROM lesson_occurrences occurrence
                 WHERE occurrence.id = binding.occurrence_id AND occurrence.semester_id = ?
                   AND (binding.state <> 'ARCHIVED' OR binding.homework_id IS NOT NULL)
                """, semesterId);
        jdbc.update("""
                UPDATE schedule_one_off_lessons item
                   SET physical_lesson_id = NULL
                 WHERE item.semester_id = ? AND item.physical_lesson_id IS NOT NULL
                """, semesterId);
        jdbc.update("""
                DELETE FROM lesson_lifecycle_entries entry
                 USING lesson_occurrences occurrence
                 WHERE occurrence.id = entry.occurrence_id AND occurrence.semester_id = ?
                """, semesterId);
        jdbc.update("DELETE FROM lessons WHERE semester_id = ?", semesterId);
        jdbc.update("DELETE FROM lesson_occurrences WHERE semester_id = ?", semesterId);
        jdbc.update("DELETE FROM schedule_items WHERE semester_id = ?", semesterId);
        jdbc.update("DELETE FROM schedule_one_off_lessons WHERE semester_id = ?", semesterId);
        updateState(operationId, semesterId, version, "DELETED", null);
        return deletionResponse(operationId, semesterId, version, "DELETED", null, receipt.snapshot());
    }

    private void prepareArchive(UUID operationId, long semesterId, long version, Map<String, Object> current) {
        if (current == null) {
            insertBarrier(operationId, semesterId, version, "PENDING", null);
            return;
        }
        if (sameIdentity(current, operationId, version)) {
            String state = String.valueOf(current.get("participant_state"));
            if ("PENDING".equals(state) || "READY".equals(state)) return;
        } else if ("RELEASED".equals(current.get("participant_state"))
                && version > number(current.get("state_version"))) {
            replaceBarrier(operationId, semesterId, version, "PENDING", null);
            return;
        }
        throw new IllegalStateException("Schedule archive barrier is fenced by another operation/version");
    }

    private void prepareRestore(UUID operationId, long semesterId, long version, Map<String, Object> current) {
        if (current != null && sameIdentity(current, operationId, version)
                && "PREPARED_RESTORE".equals(current.get("participant_state"))) {
            return;
        }
        if (current == null || !("READY".equals(current.get("participant_state"))
                || "RELEASED".equals(current.get("participant_state")))
                || version <= number(current.get("state_version"))) {
            throw new IllegalStateException("Schedule archive barrier is not ready for this restore epoch");
        }
        replaceBarrier(operationId, semesterId, version, "PREPARED_RESTORE", null);
    }

    private void releaseRestore(UUID operationId, long semesterId, long version, Map<String, Object> current) {
        if (current != null && sameIdentity(current, operationId, version)
                && "RELEASED".equals(current.get("participant_state"))) {
            return;
        }
        if (current == null || !sameIdentity(current, operationId, version)
                || !"PREPARED_RESTORE".equals(current.get("participant_state"))) {
            throw new IllegalStateException("Schedule restore fence does not match the confirmed release epoch");
        }
        updateState(operationId, semesterId, version, "RELEASED", null);
    }

    private BlockingStatus blockingStatus(long semesterId) {
        Long unfinishedTransfers = jdbc.queryForObject("""
                SELECT count(*) FROM lesson_transfer_operations transfer
                JOIN lesson_occurrences occurrence ON occurrence.id = transfer.occurrence_id
                 WHERE occurrence.semester_id = ? AND transfer.state <> 'COMPLETED'
                """, Long.class, semesterId);
        if (unfinishedTransfers != null && unfinishedTransfers > 0) {
            return new BlockingStatus("Ожидается завершение или reconciliation переноса пары в расписании", null);
        }
        PendingBinding pendingBinding = firstPendingBinding(semesterId);
        if (pendingBinding != null) {
            return new BlockingStatus(
                    "Ожидается подтверждение принятой публикации домашнего задания в расписании",
                    pendingBinding);
        }
        return new BlockingStatus(effectLedger.firstUnprovenEffectReason(semesterId), null);
    }

    private PendingBinding firstPendingBinding(long semesterId) {
        return jdbc.query("""
                SELECT binding.binding_id, binding.occurrence_id, binding.actor_id,
                       binding.request_key, binding.payload_hash, binding.revision
                  FROM lesson_homework_bindings binding
                  JOIN lesson_occurrences occurrence ON occurrence.id = binding.occurrence_id
                 WHERE occurrence.semester_id = ? AND binding.state = 'PENDING'
                 ORDER BY binding.binding_id
                 LIMIT 1
                """, resultSet -> resultSet.next() ? new PendingBinding(
                resultSet.getLong("binding_id"), resultSet.getLong("occurrence_id"),
                resultSet.getLong("actor_id"), resultSet.getObject("request_key", UUID.class),
                resultSet.getBytes("payload_hash"), resultSet.getLong("revision")) : null,
                semesterId);
    }

    private SetSemesterArchiveBarrierResponse reconcileHomeworkBinding(
            SetSemesterArchiveBarrierRequest request, UUID operationId, long semesterId, long stateVersion,
            Map<String, Object> barrier) {
        String barrierState = barrier == null ? null : String.valueOf(barrier.get("participant_state"));
        boolean deletePreparing = "DELETE_PREPARING".equals(barrierState);
        boolean pending = "PENDING".equals(barrierState) || deletePreparing;
        boolean ready = "READY".equals(barrierState);
        SemesterArchiveHomeworkBindingResolution requestedResolution = request.getBindingResolution();
        if (request.getBindingResolution()
                    == SemesterArchiveHomeworkBindingResolution.SEMESTER_ARCHIVE_HOMEWORK_BINDING_RESOLUTION_UNSPECIFIED
                || !request.hasBinding()
                || barrier == null || !sameIdentity(barrier, operationId, stateVersion)
                || !pending && !(ready && requestedResolution
                    == SemesterArchiveHomeworkBindingResolution.SEMESTER_ARCHIVE_HOMEWORK_BINDING_CONFIRM_MATERIALIZED)) {
            throw new ConflictException("Schedule archive binding reconciliation does not match an open epoch");
        }
        SemesterArchiveHomeworkBindingIdentity identity = request.getBinding();
        if (identity.getBindingId() <= 0 || identity.getActorId() <= 0
                || identity.getRequestKey().isBlank() || identity.getPayloadHash().size() != 32) {
            throw new IllegalArgumentException("Schedule archive binding reconciliation identity is invalid");
        }
        if (requestedResolution == SemesterArchiveHomeworkBindingResolution.SEMESTER_ARCHIVE_HOMEWORK_BINDING_CANCEL_UNPUBLISHED
                && (!pending || identity.getOccurrenceId() <= 0 || identity.getRevision() <= 0)) {
            throw new ConflictException("Schedule cancellation requires the exact pending reservation epoch");
        }
        UUID requestKey = uuid(identity.getRequestKey());
        BindingSnapshot binding = readBinding(identity.getBindingId());
        if (binding == null || binding.semesterId() != semesterId || binding.actorId() != identity.getActorId()
                || !binding.requestKey().equals(requestKey)
                || !Arrays.equals(binding.payloadHash(), identity.getPayloadHash().toByteArray())) {
            throw new ConflictException("Schedule archive binding reconciliation tuple changed");
        }
        if (identity.getOccurrenceId() > 0 && identity.getOccurrenceId() != binding.occurrenceId()) {
            throw new ConflictException("Schedule archive binding occurrence changed");
        }

        String terminalEventId = null;
        switch (requestedResolution) {
            case SEMESTER_ARCHIVE_HOMEWORK_BINDING_CONFIRM_MATERIALIZED -> {
                long homeworkId = request.getHomeworkId();
                if (homeworkId <= 0) throw new IllegalArgumentException("homework_id must be positive");
                if ("ACTIVE".equals(binding.state()) && Long.valueOf(homeworkId).equals(binding.homeworkId())) {
                    if (identity.getRevision() > 0
                            && identity.getRevision() != binding.revision()
                            && identity.getRevision() + 1 != binding.revision()) {
                        throw new ConflictException("Schedule confirmation replay has a different binding revision");
                    }
                    // Exact replay is receipt-only after Schedule reached READY.
                } else if (pending && "PENDING".equals(binding.state()) && binding.homeworkId() == null
                        && identity.getOccurrenceId() == binding.occurrenceId()
                        && identity.getRevision() == binding.revision()) {
                    if (deletePreparing) setDeleteBindingConfirmationContext(operationId, stateVersion, binding);
                    int updated = jdbc.update("""
                            UPDATE lesson_homework_bindings
                               SET homework_id = ?, state = 'ACTIVE', revision = revision + 1,
                                   updated_at = NOW()
                             WHERE binding_id = ? AND revision = ? AND state = 'PENDING'
                               AND homework_id IS NULL
                            """, homeworkId, binding.bindingId(), binding.revision());
                    if (updated != 1) {
                        throw new ConflictException("Schedule reservation changed during materialized confirmation");
                    }
                } else {
                    throw new ConflictException("Schedule binding is not confirmable for this accepted content");
                }
            }
            case SEMESTER_ARCHIVE_HOMEWORK_BINDING_CANCEL_UNPUBLISHED -> {
                if ("ARCHIVED".equals(binding.state()) && binding.homeworkId() == null
                        && identity.getOccurrenceId() == binding.occurrenceId()
                        && identity.getRevision() + 1 == binding.revision()) {
                    terminalEventId = findTerminalEventId(semesterId, binding.bindingId(),
                            binding.actorId(), requestKey, binding.revision());
                    if (terminalEventId == null) {
                        throw new ConflictException("Schedule terminal binding has no durable cancellation event");
                    }
                } else if (pending && "PENDING".equals(binding.state()) && binding.homeworkId() == null
                        && identity.getOccurrenceId() == binding.occurrenceId()
                        && identity.getRevision() == binding.revision()) {
                    setCancellationContext(operationId, stateVersion, binding);
                    int updated = jdbc.update("""
                            UPDATE lesson_homework_bindings
                               SET state = 'ARCHIVED', revision = revision + 1, updated_at = NOW()
                             WHERE binding_id = ? AND occurrence_id = ? AND actor_id = ?
                               AND request_key = ? AND payload_hash = ? AND revision = ?
                               AND state = 'PENDING' AND homework_id IS NULL
                            """, binding.bindingId(), binding.occurrenceId(), binding.actorId(),
                            requestKey, binding.payloadHash(), binding.revision());
                    if (updated != 1) {
                        throw new ConflictException("Schedule reservation changed before terminal cancellation");
                    }
                    HomeworkBindingArchivedEvent event = new HomeworkBindingArchivedEvent(this,
                            binding.bindingId(), binding.actorId(), requestKey, binding.occurrenceId(),
                            binding.currentLessonId(), null, binding.revision() + 1, semesterId);
                    eventPublisher.publishEvent(event);
                    terminalEventId = event.getEventId().toString();
                } else {
                    throw new ConflictException("Schedule reservation is not cancellable as unpublished");
                }
            }
            default -> throw new IllegalArgumentException("Unsupported Schedule archive binding resolution");
        }
        Map<String, Object> freshBarrier = readBarrier(semesterId);
        String state = String.valueOf(freshBarrier.get("participant_state"));
        return response(operationId, semesterId, stateVersion, state, null, null, terminalEventId);
    }

    private BindingSnapshot readBinding(long bindingId) {
        return jdbc.query("""
                SELECT binding.binding_id, binding.occurrence_id, occurrence.semester_id,
                       binding.current_lesson_id, binding.homework_id, binding.actor_id,
                       binding.request_key, binding.payload_hash, binding.state, binding.revision
                  FROM lesson_homework_bindings binding
                  JOIN lesson_occurrences occurrence ON occurrence.id = binding.occurrence_id
                 WHERE binding.binding_id = ? FOR UPDATE OF binding
                """, resultSet -> resultSet.next() ? new BindingSnapshot(
                resultSet.getLong("binding_id"), resultSet.getLong("occurrence_id"),
                resultSet.getLong("semester_id"), resultSet.getLong("current_lesson_id"),
                resultSet.getObject("homework_id", Long.class), resultSet.getLong("actor_id"),
                resultSet.getObject("request_key", UUID.class), resultSet.getBytes("payload_hash"),
                resultSet.getString("state"), resultSet.getLong("revision")) : null,
                bindingId);
    }

    private void setCancellationContext(UUID operationId, long stateVersion, BindingSnapshot binding) {
        jdbc.query("""
                SELECT set_config('rutcampustrack.archive_cancel_operation_id', ?, TRUE),
                       set_config('rutcampustrack.archive_cancel_state_version', ?, TRUE),
                       set_config('rutcampustrack.archive_cancel_binding_id', ?, TRUE),
                       set_config('rutcampustrack.archive_cancel_occurrence_id', ?, TRUE),
                       set_config('rutcampustrack.archive_cancel_actor_id', ?, TRUE),
                       set_config('rutcampustrack.archive_cancel_request_key', ?, TRUE),
                       set_config('rutcampustrack.archive_cancel_payload_hash', ?, TRUE),
                       set_config('rutcampustrack.archive_cancel_revision', ?, TRUE)
                """, (ResultSetExtractor<Void>) resultSet -> {
                    if (!resultSet.next()) {
                        throw new IllegalStateException("Schedule cancellation context was not installed");
                    }
                    return null;
                }, operationId.toString(), Long.toString(stateVersion),
                Long.toString(binding.bindingId()), Long.toString(binding.occurrenceId()),
                Long.toString(binding.actorId()), binding.requestKey().toString(),
                java.util.HexFormat.of().formatHex(binding.payloadHash()), Long.toString(binding.revision()));
    }

    private String findTerminalEventId(long semesterId, long bindingId, long actorId,
                                       UUID requestKey, long revision) {
        return jdbc.query("""
                SELECT event_id::TEXT FROM schedule_semester_archive_effect_ledger
                 WHERE target = 'ACADEMIC' AND event_type = 'homework.binding.archived'
                   AND semester_id = ? AND event_payload #>> '{payload,binding_id}' = ?
                   AND event_payload #>> '{payload,actor_id}' = ?
                   AND event_payload #>> '{payload,request_key}' = ?
                   AND event_payload #>> '{payload,binding_revision}' = ?
                   AND event_payload #>> '{payload,homework_id}' IS NULL
                 ORDER BY created_at DESC LIMIT 1
                """, resultSet -> resultSet.next() ? resultSet.getString(1) : null,
                semesterId, Long.toString(bindingId), Long.toString(actorId),
                requestKey.toString(), Long.toString(revision));
    }

    private void insertBarrier(UUID operationId, long semesterId, long version, String state, String reason) {
        jdbc.update("""
                INSERT INTO schedule_semester_archive_barriers
                    (semester_id, operation_id, state_version, participant_state, blocking_reason)
                VALUES (?, ?, ?, ?, ?)
                """, semesterId, operationId, version, state, reason);
    }

    private void replaceBarrier(UUID operationId, long semesterId, long version, String state, String reason) {
        int updated = jdbc.update("""
                UPDATE schedule_semester_archive_barriers
                   SET operation_id = ?, state_version = ?, participant_state = ?,
                       blocking_reason = ?, expected_participant_digest = NULL,
                       participant_digest = NULL, schedule_templates_count = NULL,
                       one_off_lessons_count = NULL, lessons_count = NULL, updated_at = now()
                 WHERE semester_id = ?
                """, operationId, version, state, reason, semesterId);
        requireOne(updated, "could not install Schedule restore barrier");
    }

    private void insertDeleteBarrier(UUID operationId, long semesterId, long version, String state,
                                     String reason, String expectedDigest,
                                     ScheduleSemesterDeletionSnapshotReader.Snapshot snapshot) {
        jdbc.update("""
                INSERT INTO schedule_semester_archive_barriers
                    (semester_id, operation_id, state_version, participant_state, blocking_reason,
                     expected_participant_digest, participant_digest,
                     schedule_templates_count, one_off_lessons_count, lessons_count)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, semesterId, operationId, version, state, reason, expectedDigest,
                snapshot == null ? null : snapshot.participantDigest(),
                snapshot == null ? null : snapshot.scheduleTemplates(),
                snapshot == null ? null : snapshot.oneOffLessons(),
                snapshot == null ? null : snapshot.lessons());
    }

    private void replaceDeleteBarrier(UUID operationId, long semesterId, long version, String state,
                                      String reason, String expectedDigest,
                                      ScheduleSemesterDeletionSnapshotReader.Snapshot snapshot) {
        int updated = jdbc.update("""
                UPDATE schedule_semester_archive_barriers
                   SET operation_id = ?, state_version = ?, participant_state = ?, blocking_reason = ?,
                       expected_participant_digest = ?, participant_digest = ?,
                       schedule_templates_count = ?, one_off_lessons_count = ?, lessons_count = ?,
                       updated_at = now()
                 WHERE semester_id = ?
                """, operationId, version, state, reason, expectedDigest,
                snapshot == null ? null : snapshot.participantDigest(),
                snapshot == null ? null : snapshot.scheduleTemplates(),
                snapshot == null ? null : snapshot.oneOffLessons(),
                snapshot == null ? null : snapshot.lessons(), semesterId);
        requireOne(updated, "could not install Schedule deletion barrier");
    }

    private void updateDeleteSnapshot(UUID operationId, long semesterId, long version, String state,
                                      String reason, String expectedDigest,
                                      ScheduleSemesterDeletionSnapshotReader.Snapshot snapshot) {
        int updated = jdbc.update("""
                UPDATE schedule_semester_archive_barriers
                   SET participant_state = ?, blocking_reason = ?, expected_participant_digest = ?,
                       participant_digest = ?, schedule_templates_count = ?,
                       one_off_lessons_count = ?, lessons_count = ?, updated_at = now()
                 WHERE semester_id = ? AND operation_id = ? AND state_version = ?
                """, state, reason, expectedDigest, snapshot.participantDigest(),
                snapshot.scheduleTemplates(), snapshot.oneOffLessons(), snapshot.lessons(),
                semesterId, operationId, version);
        requireOne(updated, "Schedule deletion barrier identity changed during snapshot update");
    }

    private DeletionReceipt readDeletionReceipt(long semesterId) {
        return jdbc.query("""
                SELECT expected_participant_digest, participant_digest, schedule_templates_count,
                       one_off_lessons_count, lessons_count
                  FROM schedule_semester_archive_barriers
                 WHERE semester_id = ?
                """, resultSet -> {
            if (!resultSet.next() || resultSet.getString("participant_digest") == null) return null;
            return new DeletionReceipt(resultSet.getString("expected_participant_digest"),
                    resultSet.getString("participant_digest"), resultSet.getLong("schedule_templates_count"),
                    resultSet.getLong("one_off_lessons_count"), resultSet.getLong("lessons_count"));
        }, semesterId);
    }

    private void setDeletionContext(UUID operationId, long version, String expectedDigest) {
        jdbc.query("""
                SELECT set_config('rutcampustrack.schedule_delete_operation_id', ?, TRUE),
                       set_config('rutcampustrack.schedule_delete_state_version', ?, TRUE),
                       set_config('rutcampustrack.schedule_delete_participant_digest', ?, TRUE)
                """, resultSet -> {
            if (!resultSet.next()) {
                throw new IllegalStateException("Schedule deletion context was not installed");
            }
            return null;
        }, operationId.toString(), Long.toString(version), expectedDigest);
    }

    private void setDeleteBindingConfirmationContext(UUID operationId, long version, BindingSnapshot binding) {
        jdbc.query("""
                SELECT set_config('rutcampustrack.schedule_delete_binding_operation_id', ?, TRUE),
                       set_config('rutcampustrack.schedule_delete_binding_state_version', ?, TRUE),
                       set_config('rutcampustrack.schedule_delete_binding_id', ?, TRUE),
                       set_config('rutcampustrack.schedule_delete_binding_revision', ?, TRUE)
                """, resultSet -> {
            if (!resultSet.next()) {
                throw new IllegalStateException("Schedule delete binding context was not installed");
            }
            return null;
        }, operationId.toString(), Long.toString(version), Long.toString(binding.bindingId()),
                Long.toString(binding.revision()));
    }

    private void updateState(UUID operationId, long semesterId, long version, String state, String reason) {
        int updated = jdbc.update("""
                UPDATE schedule_semester_archive_barriers
                   SET participant_state = ?, blocking_reason = ?, updated_at = now()
                 WHERE semester_id = ? AND operation_id = ? AND state_version = ?
                """, state, reason, semesterId, operationId, version);
        requireOne(updated, "Schedule barrier identity changed during update");
    }

    private Map<String, Object> readBarrier(long semesterId) {
        return jdbc.query("""
                SELECT operation_id, state_version, participant_state, expected_participant_digest
                  FROM schedule_semester_archive_barriers
                 WHERE semester_id = ? FOR UPDATE
                """, resultSet -> {
            if (!resultSet.next()) return null;
            Map<String, Object> row = new java.util.HashMap<>();
            row.put("operation_id", resultSet.getObject("operation_id", UUID.class));
            row.put("state_version", resultSet.getLong("state_version"));
            row.put("participant_state", resultSet.getString("participant_state"));
            row.put("expected_participant_digest", resultSet.getString("expected_participant_digest"));
            return row;
        },
                semesterId);
    }

    private void lockSemester(long semesterId) {
        ScheduleSemesterArchiveWriteFence.lockSemester(jdbc, semesterId);
    }

    private static void requireOne(int updated, String message) {
        if (updated != 1) throw new IllegalStateException(message);
    }

    private static boolean sameIdentity(Map<String, Object> row, UUID operationId, long version) {
        return operationId.equals(row.get("operation_id")) && version == number(row.get("state_version"));
    }

    private static long number(Object value) {
        return ((Number) value).longValue();
    }

    private static UUID uuid(String value) {
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException invalid) {
            throw new IllegalArgumentException("operation_id must be a UUID", invalid);
        }
    }

    private static SetSemesterArchiveBarrierResponse response(UUID operationId,
                                                              long semesterId,
                                                              long version,
                                                              String state,
                                                              String reason) {
        return response(operationId, semesterId, version, state, reason, null, null);
    }

    private static SetSemesterArchiveBarrierResponse response(UUID operationId,
                                                              long semesterId,
                                                              long version,
                                                              String state,
                                                              String reason,
                                                              PendingBinding pendingBinding,
                                                              String terminalEventId) {
        SemesterArchiveParticipantState wireState = switch (state) {
            case "PENDING", "DELETE_PREPARING" -> SemesterArchiveParticipantState.SEMESTER_ARCHIVE_PARTICIPANT_PENDING;
            case "READY", "DELETE_SEALED" -> SemesterArchiveParticipantState.SEMESTER_ARCHIVE_PARTICIPANT_READY;
            case "PREPARED_RESTORE" ->
                    SemesterArchiveParticipantState.SEMESTER_ARCHIVE_PARTICIPANT_PREPARED_RESTORE;
            case "RELEASED" -> SemesterArchiveParticipantState.SEMESTER_ARCHIVE_PARTICIPANT_RELEASED;
            case "DELETED" -> SemesterArchiveParticipantState.SEMESTER_ARCHIVE_PARTICIPANT_DELETED;
            default -> throw new IllegalArgumentException("unsupported persisted archive barrier state " + state);
        };
        SetSemesterArchiveBarrierResponse.Builder response = SetSemesterArchiveBarrierResponse.newBuilder()
                .setOperationId(operationId.toString())
                .setSemesterId(semesterId)
                .setStateVersion(version)
                .setState(wireState);
        if (reason != null) response.setBlockingReason(reason);
        if (pendingBinding != null) {
            response.setPendingBinding(SemesterArchiveHomeworkBindingIdentity.newBuilder()
                    .setBindingId(pendingBinding.bindingId())
                    .setOccurrenceId(pendingBinding.occurrenceId())
                    .setActorId(pendingBinding.actorId())
                    .setRequestKey(pendingBinding.requestKey().toString())
                    .setPayloadHash(com.google.protobuf.ByteString.copyFrom(pendingBinding.payloadHash()))
                    .setRevision(pendingBinding.revision())
                    .build());
        }
        if (terminalEventId != null) response.setTerminalEventId(terminalEventId);
        return response.build();
    }

    private static String requiredDigest(String digest) {
        if (digest == null || !digest.matches("[0-9a-fA-F]{64}")) {
            throw new IllegalArgumentException("expected participant digest must be a SHA-256 hex value");
        }
        return digest.toLowerCase(java.util.Locale.ROOT);
    }

    private static SetSemesterArchiveBarrierResponse deletionResponse(
            UUID operationId, long semesterId, long version, String state, String reason,
            ScheduleSemesterDeletionSnapshotReader.Snapshot snapshot) {
        SetSemesterArchiveBarrierResponse.Builder response = SetSemesterArchiveBarrierResponse.newBuilder()
                .setOperationId(operationId.toString())
                .setSemesterId(semesterId)
                .setStateVersion(version)
                .setState(switch (state) {
                    case "PENDING" -> SemesterArchiveParticipantState.SEMESTER_ARCHIVE_PARTICIPANT_PENDING;
                    case "READY" -> SemesterArchiveParticipantState.SEMESTER_ARCHIVE_PARTICIPANT_READY;
                    case "RELEASED" -> SemesterArchiveParticipantState.SEMESTER_ARCHIVE_PARTICIPANT_RELEASED;
                    case "DELETED" -> SemesterArchiveParticipantState.SEMESTER_ARCHIVE_PARTICIPANT_DELETED;
                    default -> throw new IllegalArgumentException("unsupported Schedule delete state " + state);
                });
        if (reason != null) response.setBlockingReason(reason);
        if (snapshot != null) {
            response.setParticipantDigest(snapshot.participantDigest())
                    .setScheduleTemplatesCount(snapshot.scheduleTemplates())
                    .setOneOffLessonsCount(snapshot.oneOffLessons())
                    .setLessonsCount(snapshot.lessons());
        }
        return response.build();
    }

    private record BlockingStatus(String reason, PendingBinding pendingBinding) { }

    private record PendingBinding(long bindingId, long occurrenceId, long actorId,
                                  UUID requestKey, byte[] payloadHash, long revision) {
        private PendingBinding {
            payloadHash = payloadHash == null ? null : payloadHash.clone();
        }
    }

    private record BindingSnapshot(long bindingId, long occurrenceId, long semesterId,
                                   long currentLessonId, Long homeworkId, long actorId,
                                   UUID requestKey, byte[] payloadHash, String state, long revision) {
        private BindingSnapshot {
            payloadHash = payloadHash == null ? null : payloadHash.clone();
        }
    }

    private record DeletionReceipt(String expectedDigest, String participantDigest,
                                   long scheduleTemplates, long oneOffLessons, long lessons) {
        private ScheduleSemesterDeletionSnapshotReader.Snapshot snapshot() {
            return new ScheduleSemesterDeletionSnapshotReader.Snapshot(scheduleTemplates, oneOffLessons,
                    lessons, participantDigest, 0);
        }
    }
}
