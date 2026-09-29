package ru.rutcampustrack.academic.homework;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.rutcampustrack.academic.contract.enums.HomeworkPublicationState;
import ru.rutcampustrack.academic.entity.Homework;
import ru.rutcampustrack.academic.entity.HomeworkBindingArchiveMarker;
import ru.rutcampustrack.academic.event.LessonTransferParticipantAppliedEvent;
import ru.rutcampustrack.academic.event.HomeworkUpdatedEvent;
import ru.rutcampustrack.academic.exception.ConflictException;
import ru.rutcampustrack.academic.repository.HomeworkRepository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** Applies one bounded Academic batch under the established per-binding write locks. */
@Service
public class HomeworkBindingTransferCoordinator {

    private final JdbcTemplate jdbc;
    private final HomeworkRepository homeworkRepository;
    private final HomeworkBindingArchiveCoordinator archiveCoordinator;
    private final ApplicationEventPublisher eventPublisher;

    public HomeworkBindingTransferCoordinator(JdbcTemplate jdbc,
                                              HomeworkRepository homeworkRepository,
                                              HomeworkBindingArchiveCoordinator archiveCoordinator,
                                              ApplicationEventPublisher eventPublisher) {
        this.jdbc = jdbc;
        this.homeworkRepository = homeworkRepository;
        this.archiveCoordinator = archiveCoordinator;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public void applyBatch(LessonTransferBatch batch) {
        lockBatch(batch.operationId(), batch.batchIndex());
        Map<String, Object> prior = findReceipt(batch.operationId(), batch.batchIndex());
        if (prior != null) {
            verifyDuplicateReceipt(prior, batch);
            return;
        }

        for (LessonTransferBatch.Binding binding : batch.bindings()) {
            archiveCoordinator.lock(binding.bindingId());
        }

        List<Plan> plans = new ArrayList<>(batch.bindings().size());
        String errorCode = null;
        for (LessonTransferBatch.Binding binding : batch.bindings()) {
            PlanResult result = validateBinding(batch, binding);
            if (result.errorCode() != null) {
                errorCode = result.errorCode();
                break;
            }
            plans.add(result.plan());
        }
        if (errorCode != null) {
            writeReceipt(batch, "ERROR", errorCode);
            publishAcknowledgement(batch, "ERROR", errorCode);
            return;
        }

        for (Plan plan : plans) {
            applyPlan(batch, plan);
        }
        homeworkRepository.flush();
        writeReceipt(batch, "APPLIED", null);
        publishAcknowledgement(batch, "APPLIED", null);
    }

    /** Caller already holds the same binding advisory lock as publication and archive writers. */
    public void applyPendingMarker(Homework homework, long bindingId,
                                   long actorId, UUID requestKey, byte[] bindingPayloadHash) {
        TransferMarker marker = findMarker(bindingId, true);
        if (marker == null) return;
        validateMarkerIdentity(marker, actorId, requestKey, bindingPayloadHash,
                homework.getGroupId(), homework.getSubjectId(), homework.getSemesterId());
        if (marker.state().equals("APPLIED")) {
            if (!Objects.equals(marker.homeworkId(), homework.getId())
                    || !marker.targetDate().equals(homework.getLessonDate())
                    || marker.targetLessonNumber() != homework.getLessonNumber()) {
                throw new ConflictException("homework transfer marker points to different persisted content");
            }
            return;
        }
        if (!marker.sourceDate().equals(homework.getLessonDate())
                || marker.sourceLessonNumber() != homework.getLessonNumber()) {
            throw new ConflictException("pending homework transfer marker has a different source slot");
        }
        if (homework.getPublicationState() == HomeworkPublicationState.ARCHIVED) {
            throw new ConflictException("archived homework cannot apply a pending lesson transfer");
        }
        homework.transferLessonSlot(marker.targetDate(), marker.targetLessonNumber());
    }

    /** Completes a pending-publication marker after the newly created content has an ID. */
    public void associateMaterialized(long bindingId, long actorId, UUID requestKey,
                                      byte[] bindingPayloadHash, long homeworkId,
                                      long groupId, long subjectId, long semesterId) {
        TransferMarker marker = findMarker(bindingId, true);
        if (marker == null) return;
        validateMarkerIdentity(marker, actorId, requestKey, bindingPayloadHash,
                groupId, subjectId, semesterId);
        if (marker.state().equals("APPLIED")) {
            if (!Objects.equals(marker.homeworkId(), homeworkId)) {
                throw new ConflictException("homework transfer marker already points to different content");
            }
            return;
        }
        int updated = jdbc.update("""
                UPDATE homework_binding_transfer_markers
                   SET state = 'APPLIED', homework_id = ?, updated_at = NOW()
                 WHERE binding_id = ? AND operation_id = ? AND state = 'PENDING'
                """, homeworkId, bindingId, marker.operationId());
        if (updated != 1) {
            throw new ConflictException("pending homework transfer marker changed during publication");
        }
    }

    private PlanResult validateBinding(LessonTransferBatch batch,
                                       LessonTransferBatch.Binding binding) {
        if (!List.of("PENDING", "ACTIVE").contains(binding.state())) {
            return PlanResult.error("SOURCE_STATE_CONFLICT");
        }
        HomeworkBindingArchiveMarker archive;
        try {
            archive = archiveCoordinator.findMarker(
                    binding.bindingId(), binding.actorId(), binding.requestKey()).orElse(null);
        } catch (ConflictException identityMismatch) {
            return PlanResult.error("SOURCE_STATE_CONFLICT");
        }
        if (archive != null) {
            return PlanResult.error("SOURCE_STATE_CONFLICT");
        }

        TransferMarker marker = findMarker(binding.bindingId(), true);
        Homework homework = homeworkRepository.findByBindingId(binding.bindingId()).orElse(null);
        if (binding.homeworkId() != null
                && (homework == null || !binding.homeworkId().equals(homework.getId()))) {
            return PlanResult.error("TARGET_DATA_CONFLICT");
        }
        if (homework == null && (binding.homeworkId() != null || "ACTIVE".equals(binding.state()))) {
            return PlanResult.error("SOURCE_STATE_CONFLICT");
        }
        if (homework != null) {
            if (!Objects.equals(homework.getBindingId(), binding.bindingId())
                    || !Objects.equals(homework.getActorId(), binding.actorId())
                    || !Objects.equals(homework.getRequestKey(), binding.requestKey())
                    || !Arrays.equals(homework.getPayloadHash(), HexFormat.of().parseHex(binding.payloadHash()))) {
                return PlanResult.error("TARGET_DATA_CONFLICT");
            }
            if (!Objects.equals(homework.getGroupId(), batch.groupId())
                    || !Objects.equals(homework.getSubjectId(), batch.subjectId())
                    || !Objects.equals(homework.getSemesterId(), batch.semesterId())) {
                return PlanResult.error("SCOPE_MISMATCH");
            }
            if (!batch.sourceDate().equals(homework.getLessonDate())
                    || batch.sourceLessonNumber() != homework.getLessonNumber()
                    || homework.getPublicationState() == HomeworkPublicationState.ARCHIVED) {
                return PlanResult.error("SOURCE_STATE_CONFLICT");
            }
        }
        if (marker != null) {
            if (!marker.hasIdentity(binding.actorId(), binding.requestKey(),
                    HexFormat.of().parseHex(binding.payloadHash()), batch.groupId(),
                    batch.subjectId(), batch.semesterId())
                    || marker.targetLessonId() != batch.sourceLessonId()
                    || !marker.targetDate().equals(batch.sourceDate())
                    || marker.targetLessonNumber() != batch.sourceLessonNumber()) {
                return PlanResult.error("SOURCE_STATE_CONFLICT");
            }
            if (marker.state().equals("APPLIED")
                    && (homework == null || !Objects.equals(marker.homeworkId(), homework.getId()))) {
                return PlanResult.error("TARGET_DATA_CONFLICT");
            }
        }
        return PlanResult.plan(new Plan(binding, homework, marker));
    }

    private void applyPlan(LessonTransferBatch batch, Plan plan) {
        LessonTransferBatch.Binding binding = plan.binding();
        Homework homework = plan.homework();
        String resultState;
        if (homework == null) {
            upsertMarker(batch, binding, null, "PENDING");
            resultState = "PENDING_PUBLICATION";
        } else {
            boolean slotChanged = !batch.targetDate().equals(homework.getLessonDate())
                    || batch.targetLessonNumber() != homework.getLessonNumber();
            homework.transferLessonSlot(batch.targetDate(), batch.targetLessonNumber());
            homeworkRepository.save(homework);
            upsertMarker(batch, binding, homework.getId(), "APPLIED");
            if (slotChanged && homework.getPublicationState() == HomeworkPublicationState.ACTIVE) {
                eventPublisher.publishEvent(new HomeworkUpdatedEvent(this, homework.getId(),
                        homework.getGroupId(), homework.getSubjectId(), homework.getTitle(),
                        homework.getDescription(), homework.getLink(), homework.getLessonDate().toString(),
                        homework.getLessonNumber()));
            }
            resultState = "MOVED";
        }
        jdbc.update("""
                INSERT INTO homework_binding_transfer_history
                    (operation_id, binding_id, operation_hash, binding_payload_hash,
                     binding_revision, actor_id, request_key, homework_id, occurrence_id,
                     group_id, subject_id, semester_id, source_lesson_id, target_lesson_id,
                     source_date, source_lesson_number, target_date, target_lesson_number,
                     result_state)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, batch.operationId(), binding.bindingId(), hashBytes(batch.operationHash()),
                hashBytes(binding.payloadHash()), binding.revision(), binding.actorId(),
                binding.requestKey(), homework == null ? null : homework.getId(),
                batch.occurrenceId(), batch.groupId(), batch.subjectId(), batch.semesterId(),
                batch.sourceLessonId(), batch.targetLessonId(), batch.sourceDate(),
                batch.sourceLessonNumber(), batch.targetDate(), batch.targetLessonNumber(), resultState);
    }

    private void upsertMarker(LessonTransferBatch batch, LessonTransferBatch.Binding binding,
                              Long homeworkId, String state) {
        jdbc.update("""
                INSERT INTO homework_binding_transfer_markers
                    (binding_id, actor_id, request_key, binding_payload_hash, homework_id,
                     operation_id, operation_hash, occurrence_id, group_id, subject_id, semester_id,
                     source_lesson_id, target_lesson_id, source_date, source_lesson_number,
                     target_date, target_lesson_number, state, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, NOW())
                ON CONFLICT (binding_id) DO UPDATE SET
                    actor_id = EXCLUDED.actor_id,
                    request_key = EXCLUDED.request_key,
                    binding_payload_hash = EXCLUDED.binding_payload_hash,
                    homework_id = EXCLUDED.homework_id,
                    operation_id = EXCLUDED.operation_id,
                    operation_hash = EXCLUDED.operation_hash,
                    occurrence_id = EXCLUDED.occurrence_id,
                    group_id = EXCLUDED.group_id,
                    subject_id = EXCLUDED.subject_id,
                    semester_id = EXCLUDED.semester_id,
                    target_lesson_id = EXCLUDED.target_lesson_id,
                    target_date = EXCLUDED.target_date,
                    target_lesson_number = EXCLUDED.target_lesson_number,
                    state = EXCLUDED.state,
                    updated_at = NOW()
                """, binding.bindingId(), binding.actorId(), binding.requestKey(),
                hashBytes(binding.payloadHash()), homeworkId, batch.operationId(),
                hashBytes(batch.operationHash()), batch.occurrenceId(), batch.groupId(),
                batch.subjectId(), batch.semesterId(), batch.sourceLessonId(), batch.targetLessonId(),
                batch.sourceDate(), batch.sourceLessonNumber(), batch.targetDate(),
                batch.targetLessonNumber(), state);
    }

    private void writeReceipt(LessonTransferBatch batch, String result, String errorCode) {
        jdbc.update("""
                INSERT INTO lesson_transfer_receipts
                    (operation_id, batch_index, batch_count, batch_size, operation_hash,
                     batch_hash, source_lesson_id, target_lesson_id, result, error_code)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, batch.operationId(), batch.batchIndex(), batch.batchCount(),
                batch.bindings().size(), hashBytes(batch.operationHash()), hashBytes(batch.batchHash()),
                batch.sourceLessonId(), batch.targetLessonId(), result, errorCode);
    }

    private void publishAcknowledgement(LessonTransferBatch batch, String result, String errorCode) {
        eventPublisher.publishEvent(new LessonTransferParticipantAppliedEvent(this,
                batch.operationId().toString(), batch.batchIndex(), result, errorCode, false,
                batch.operationHash(), batch.sourceLessonId(), batch.targetLessonId()));
    }

    private Map<String, Object> findReceipt(UUID operationId, int batchIndex) {
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT batch_count, batch_size, operation_hash, batch_hash,
                       source_lesson_id, target_lesson_id, result, error_code
                  FROM lesson_transfer_receipts
                 WHERE operation_id = ? AND batch_index = ? FOR UPDATE
                """, operationId, batchIndex);
        return rows.isEmpty() ? null : rows.getFirst();
    }

    private void verifyDuplicateReceipt(Map<String, Object> prior, LessonTransferBatch batch) {
        if (number(prior.get("batch_count")) != batch.batchCount()
                || number(prior.get("batch_size")) != batch.bindings().size()
                || number(prior.get("source_lesson_id")) != batch.sourceLessonId()
                || number(prior.get("target_lesson_id")) != batch.targetLessonId()
                || !Arrays.equals((byte[]) prior.get("operation_hash"), hashBytes(batch.operationHash()))
                || !Arrays.equals((byte[]) prior.get("batch_hash"), hashBytes(batch.batchHash()))) {
            throw new IllegalArgumentException("lesson transfer duplicate batch does not match its durable receipt");
        }
    }

    private TransferMarker findMarker(long bindingId, boolean forUpdate) {
        String suffix = forUpdate ? " FOR UPDATE" : "";
        List<TransferMarker> rows = jdbc.query("""
                SELECT binding_id, actor_id, request_key, binding_payload_hash, homework_id,
                       operation_id, operation_hash, occurrence_id, group_id, subject_id, semester_id,
                       source_lesson_id, target_lesson_id, source_date, source_lesson_number,
                       target_date, target_lesson_number, state
                  FROM homework_binding_transfer_markers WHERE binding_id = ?
                """ + suffix, HomeworkBindingTransferCoordinator::mapMarker, bindingId);
        return rows.isEmpty() ? null : rows.getFirst();
    }

    private void lockBatch(UUID operationId, int batchIndex) {
        jdbc.execute((ConnectionCallback<Void>) connection -> {
            try (var statement = connection.prepareStatement(
                    "SELECT pg_advisory_xact_lock(hashtextextended(?, 0))")) {
                statement.setString(1, operationId + ":" + batchIndex);
                statement.execute();
            }
            return null;
        });
    }

    private static TransferMarker mapMarker(ResultSet rs, int rowNum) throws SQLException {
        return new TransferMarker(rs.getLong("binding_id"), rs.getLong("actor_id"),
                rs.getObject("request_key", UUID.class), rs.getBytes("binding_payload_hash"),
                rs.getObject("homework_id", Long.class), rs.getObject("operation_id", UUID.class),
                rs.getBytes("operation_hash"), rs.getLong("occurrence_id"), rs.getLong("group_id"),
                rs.getLong("subject_id"), rs.getLong("semester_id"),
                rs.getLong("source_lesson_id"), rs.getLong("target_lesson_id"),
                rs.getObject("source_date", LocalDate.class), rs.getInt("source_lesson_number"),
                rs.getObject("target_date", LocalDate.class), rs.getInt("target_lesson_number"),
                rs.getString("state"));
    }

    private void validateMarkerIdentity(TransferMarker marker, long actorId, UUID requestKey,
                                        byte[] payloadHash, long groupId, long subjectId,
                                        long semesterId) {
        if (!marker.hasIdentity(actorId, requestKey, payloadHash, groupId, subjectId, semesterId)) {
            throw new ConflictException("homework transfer marker identity does not match the binding");
        }
    }

    private static byte[] hashBytes(String value) {
        byte[] hash = HexFormat.of().parseHex(value);
        if (hash.length != 32) throw new IllegalArgumentException("transfer hash must be SHA-256");
        return hash;
    }

    private static long number(Object value) {
        return ((Number) value).longValue();
    }

    private record Plan(LessonTransferBatch.Binding binding, Homework homework, TransferMarker marker) { }
    private record PlanResult(Plan plan, String errorCode) {
        static PlanResult plan(Plan plan) { return new PlanResult(plan, null); }
        static PlanResult error(String errorCode) { return new PlanResult(null, errorCode); }
    }

    private record TransferMarker(long bindingId, long actorId, UUID requestKey,
                                  byte[] bindingPayloadHash, Long homeworkId,
                                  UUID operationId, byte[] operationHash,
                                  long occurrenceId, long groupId, long subjectId, long semesterId,
                                  long sourceLessonId, long targetLessonId,
                                  LocalDate sourceDate, int sourceLessonNumber,
                                  LocalDate targetDate, int targetLessonNumber, String state) {
        boolean hasIdentity(long expectedActorId, UUID expectedRequestKey, byte[] expectedHash,
                            long expectedGroupId, long expectedSubjectId, long expectedSemesterId) {
            return actorId == expectedActorId && requestKey.equals(expectedRequestKey)
                    && Arrays.equals(bindingPayloadHash, expectedHash)
                    && groupId == expectedGroupId && subjectId == expectedSubjectId
                    && semesterId == expectedSemesterId;
        }
    }
}
