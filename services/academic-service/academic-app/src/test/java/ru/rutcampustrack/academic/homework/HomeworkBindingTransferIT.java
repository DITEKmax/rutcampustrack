package ru.rutcampustrack.academic.homework;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import ru.rutcampustrack.academic.contract.enums.HomeworkPublicationState;
import ru.rutcampustrack.academic.contract.enums.SemesterArchiveAction;
import ru.rutcampustrack.academic.entity.Homework;
import ru.rutcampustrack.academic.event.HomeworkBindingArchivedEventConsumer;
import ru.rutcampustrack.academic.event.LessonTransferEventConsumer;
import ru.rutcampustrack.academic.integration.AbstractAcademicIntegrationTest;
import ru.rutcampustrack.academic.repository.HomeworkRepository;
import ru.rutcampustrack.academic.semester.AcademicSemesterArchiveBarrierTransaction;
import ru.rutcampustrack.academic.semester.SemesterArchiveCommandTransaction;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Real Academic PostgreSQL participant and publication checks for chained transfers. */
class HomeworkBindingTransferIT extends AbstractAcademicIntegrationTest {

    private static final String CONSUMER_ID = LessonTransferEventConsumer.CONSUMER_ID;
    private static final LocalDate SOURCE_DATE = LocalDate.of(2090, 1, 2);
    private static final LocalDate MIDDLE_DATE = LocalDate.of(2090, 1, 9);
    private static final LocalDate TARGET_DATE = LocalDate.of(2090, 1, 16);

    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private PlatformTransactionManager transactionManager;
    @Autowired private HomeworkPublicationPersistence publicationPersistence;
    @Autowired private HomeworkRepository homeworkRepository;
    @Autowired private LessonTransferEventConsumer transferConsumer;
    @Autowired private HomeworkBindingArchivedEventConsumer archiveConsumer;
    @Autowired private AcademicSemesterArchiveBarrierTransaction archiveBarrier;
    @Autowired private SemesterArchiveCommandTransaction archiveCommands;
    @Autowired private ObjectMapper objectMapper;

    private final List<UUID> eventIds = new ArrayList<>();
    private final List<UUID> operationIds = new ArrayList<>();
    private final List<UUID> archiveOperationIds = new ArrayList<>();
    private final List<Long> bindingIds = new ArrayList<>();
    private final List<Long> activeSemesterIds = new ArrayList<>();
    private long actorId;
    private long groupId;
    private long subjectId;
    private long semesterId;

    @BeforeEach
    void setUpFixture() {
        eventIds.clear();
        operationIds.clear();
        archiveOperationIds.clear();
        bindingIds.clear();
        activeSemesterIds.clear();
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            activeSemesterIds.addAll(jdbcTemplate.queryForList(
                    "SELECT id FROM semesters WHERE is_active = true", Long.class));
            actorId = jdbcTemplate.queryForObject("SELECT id FROM users WHERE login = 'student'", Long.class);
            groupId = jdbcTemplate.queryForObject("SELECT id FROM groups WHERE name = 'ИВТ-211'", Long.class);
            subjectId = jdbcTemplate.queryForObject("""
                    INSERT INTO subjects (name, type, group_id)
                    VALUES (?, 'lecture', ?) RETURNING id
                    """, Long.class, "transfer-it-subject-" + UUID.randomUUID(), groupId);
            jdbcTemplate.update("INSERT INTO subject_lesson_types (subject_id, lesson_type) VALUES (?, 'lecture')",
                    subjectId);
            int year = ThreadLocalRandom.current().nextInt(2200, 3000);
            semesterId = jdbcTemplate.queryForObject("""
                    INSERT INTO semesters (name, date_from, date_to, is_active, created_at)
                    VALUES (?, ?, ?, false, NOW()) RETURNING id
                    """, Long.class, "transfer-it-semester-" + UUID.randomUUID(),
                    LocalDate.of(year, 1, 1), LocalDate.of(year, 12, 31));
        });
    }

    @AfterEach
    void cleanFixture() {
        if (groupId == 0) return;
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            for (UUID eventId : eventIds) {
                jdbcTemplate.update("DELETE FROM event_consumer_processed WHERE consumer_id = ? AND event_id = ?",
                        CONSUMER_ID, eventId);
                jdbcTemplate.update("DELETE FROM academic_outbox WHERE event_type = 'semester.archive.effect.ack' "
                                + "AND payload #>> '{payload,source_event_id}' = ?",
                        eventId.toString());
            }
            for (UUID operationId : operationIds) {
                jdbcTemplate.update("DELETE FROM academic_outbox WHERE payload #>> '{payload,operation_id}' = ?",
                        operationId.toString());
            }
            for (UUID operationId : archiveOperationIds) {
                jdbcTemplate.update("DELETE FROM academic_semester_archive_publication_admissions WHERE operation_id = ?",
                        operationId);
                jdbcTemplate.update("DELETE FROM academic_outbox WHERE payload #>> '{payload,operation_id}' = ?",
                        operationId.toString());
                jdbcTemplate.update("DELETE FROM semester_archive_operations WHERE operation_id = ?", operationId);
            }
            jdbcTemplate.update("DELETE FROM academic_semester_archive_barriers WHERE semester_id = ?", semesterId);
            jdbcTemplate.update("UPDATE semesters SET is_active = false, is_archived = false, "
                    + "archive_transition = 'NONE', state_version = 0, archive_release_pending = false WHERE id = ?",
                    semesterId);
            for (UUID eventId : eventIds) {
                jdbcTemplate.update("DELETE FROM academic_semester_archive_effect_receipts WHERE source_event_id = ?",
                        eventId);
            }
            for (Long bindingId : bindingIds) {
                jdbcTemplate.update("""
                        DELETE FROM academic_outbox
                         WHERE payload #>> '{payload,homework_id}' IN
                               (SELECT id::text FROM homeworks WHERE binding_id = ?)
                        """, bindingId);
                jdbcTemplate.update("DELETE FROM homework_binding_archives WHERE binding_id = ?", bindingId);
                jdbcTemplate.update("DELETE FROM homework_binding_transfer_markers WHERE binding_id = ?", bindingId);
                jdbcTemplate.update("DELETE FROM homeworks WHERE binding_id = ?", bindingId);
            }
            jdbcTemplate.update("DELETE FROM subject_lesson_types WHERE subject_id = ?", subjectId);
            jdbcTemplate.update("DELETE FROM subjects WHERE id = ?", subjectId);
            jdbcTemplate.update("DELETE FROM semesters WHERE id = ?", semesterId);
            for (Long activeSemesterId : activeSemesterIds) {
                jdbcTemplate.update("UPDATE semesters SET is_active = true WHERE id = ?", activeSemesterId);
            }
        });
    }

    @Test
    void activeAndPendingPublicationFollowAThroughBToCAndAckIsDurable() throws Exception {
        long activeBindingId = nextBindingId();
        bindingIds.add(activeBindingId);
        UUID activeRequestKey = UUID.randomUUID();
        byte[] activeHash = hashByte(11);
        Homework active = publicationPersistence.persistPending(groupId, subjectId, semesterId,
                "Preserved title", "Preserved description", "https://example.test/homework",
                actorId, SOURCE_DATE, 1, activeBindingId, activeRequestKey, activeHash);
        active = publicationPersistence.activate(active.getId(), actorId, activeRequestKey,
                activeBindingId, activeHash);
        long activeHomeworkId = active.getId();
        jdbcTemplate.update("UPDATE homeworks SET due_reminder_sent_at = NOW() WHERE id = ?", activeHomeworkId);

        Map<String, Object> activeFirst = event(activeBindingId, activeHomeworkId, actorId,
                activeRequestKey, activeHash, "ACTIVE", 1, 1, "a", 10001, 10002,
                501, SOURCE_DATE, 1, MIDDLE_DATE, 2);
        Map<String, Object> activeSecond = event(activeBindingId, activeHomeworkId, actorId,
                activeRequestKey, activeHash, "ACTIVE", 2, 2, "b", 10002, 10003,
                502, MIDDLE_DATE, 2, TARGET_DATE, 3);
        operationIds.add(UUID.fromString(((Map<?, ?>) activeFirst.get("payload")).get("operation_id").toString()));
        operationIds.add(UUID.fromString(((Map<?, ?>) activeSecond.get("payload")).get("operation_id").toString()));
        transferConsumer.onEvent(activeFirst);
        transferConsumer.onEvent(activeSecond);
        transferConsumer.onEvent(activeSecond);

        Homework movedActive = homeworkRepository.findById(activeHomeworkId).orElseThrow();
        assertThat(movedActive.getLessonDate()).isEqualTo(TARGET_DATE);
        assertThat(movedActive.getLessonNumber()).isEqualTo(3);
        assertThat(movedActive.getPublicationState()).isEqualTo(HomeworkPublicationState.ACTIVE);
        assertThat(movedActive.getTitle()).isEqualTo("Preserved title");
        assertThat(movedActive.getDescription()).isEqualTo("Preserved description");
        assertThat(movedActive.getLink()).isEqualTo("https://example.test/homework");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT due_reminder_sent_at IS NULL FROM homeworks WHERE id = ?", Boolean.class, activeHomeworkId))
                .isTrue();
        assertThat(jdbcTemplate.queryForObject("""
                SELECT encode(payload_hash, 'hex') FROM homeworks WHERE id = ?
                """, String.class, activeHomeworkId)).isEqualTo(java.util.HexFormat.of().formatHex(activeHash));
        assertThat(jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM homework_binding_transfer_history WHERE binding_id = ?
                """, Integer.class, activeBindingId)).isEqualTo(2);
        assertThat(jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM lesson_transfer_receipts WHERE operation_id IN (?, ?)
                """, Integer.class, operationIds.get(0), operationIds.get(1))).isEqualTo(2);
        assertThat(jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM academic_outbox
                 WHERE event_type = 'homework.updated'
                   AND payload #>> '{payload,homework_id}' = ?
                """, Integer.class, Long.toString(activeHomeworkId))).isEqualTo(2);
        List<String> activeAcks = jdbcTemplate.queryForList("""
                SELECT payload::text FROM academic_outbox
                 WHERE event_type = 'lesson.transfer.participant.applied'
                   AND payload #>> '{payload,operation_id}' IN (?, ?)
                """, String.class, operationIds.get(0).toString(), operationIds.get(1).toString());
        assertThat(activeAcks).hasSize(2);
        for (String ack : activeAcks) {
            JsonNode payload = objectMapper.readTree(ack).path("payload");
            assertThat(payload.path("transfer_payload_hash").asText()).hasSize(64);
            assertThat(payload.has("payload_hash")).isFalse();
        }

        long pendingBindingId = nextBindingId();
        bindingIds.add(pendingBindingId);
        UUID pendingRequestKey = UUID.randomUUID();
        byte[] pendingHash = hashByte(12);
        Map<String, Object> pendingFirst = event(pendingBindingId, null, actorId,
                pendingRequestKey, pendingHash, "PENDING", 1, 1, "c", 20001, 20002,
                601, SOURCE_DATE, 1, MIDDLE_DATE, 2);
        Map<String, Object> pendingSecond = event(pendingBindingId, null, actorId,
                pendingRequestKey, pendingHash, "PENDING", 2, 2, "d", 20002, 20003,
                602, MIDDLE_DATE, 2, TARGET_DATE, 3);
        UUID pendingFirstOperation = UUID.fromString(
                ((Map<?, ?>) pendingFirst.get("payload")).get("operation_id").toString());
        UUID pendingSecondOperation = UUID.fromString(
                ((Map<?, ?>) pendingSecond.get("payload")).get("operation_id").toString());
        operationIds.add(pendingFirstOperation);
        operationIds.add(pendingSecondOperation);
        transferConsumer.onEvent(pendingFirst);
        transferConsumer.onEvent(pendingSecond);

        assertThat(jdbcTemplate.queryForMap("""
                SELECT source_lesson_id, target_lesson_id, source_date, target_date, state
                  FROM homework_binding_transfer_markers WHERE binding_id = ?
                """, pendingBindingId))
                .containsEntry("source_lesson_id", 20001L)
                .containsEntry("target_lesson_id", 20003L)
                .containsEntry("source_date", java.sql.Date.valueOf(SOURCE_DATE))
                .containsEntry("target_date", java.sql.Date.valueOf(TARGET_DATE))
                .containsEntry("state", "PENDING");

        Homework materialized = publicationPersistence.persistPending(groupId, subjectId, semesterId,
                "Late publication", "Late description", null, actorId, SOURCE_DATE, 1,
                pendingBindingId, pendingRequestKey, pendingHash);
        assertThat(materialized.getLessonDate()).isEqualTo(TARGET_DATE);
        assertThat(materialized.getLessonNumber()).isEqualTo(3);
        assertThat(materialized.getPublicationState()).isEqualTo(HomeworkPublicationState.PENDING);
        Homework activated = publicationPersistence.activate(materialized.getId(), actorId,
                pendingRequestKey, pendingBindingId, pendingHash);
        assertThat(activated.getPublicationState()).isEqualTo(HomeworkPublicationState.ACTIVE);
        assertThat(activated.getLessonDate()).isEqualTo(TARGET_DATE);
        assertThat(jdbcTemplate.queryForMap("""
                SELECT homework_id, source_lesson_id, target_lesson_id, source_date, target_date, state
                  FROM homework_binding_transfer_markers WHERE binding_id = ?
                """, pendingBindingId))
                .containsEntry("homework_id", materialized.getId())
                .containsEntry("source_lesson_id", 20001L)
                .containsEntry("target_lesson_id", 20003L)
                .containsEntry("source_date", java.sql.Date.valueOf(SOURCE_DATE))
                .containsEntry("target_date", java.sql.Date.valueOf(TARGET_DATE))
                .containsEntry("state", "APPLIED");
        assertThat(jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM homework_binding_transfer_history WHERE binding_id = ?
                """, Integer.class, pendingBindingId)).isEqualTo(2);
        assertThat(jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM lesson_transfer_receipts WHERE operation_id IN (?, ?)
                """, Integer.class, pendingFirstOperation, pendingSecondOperation)).isEqualTo(2);
    }

    @Test
    void completedEmptyTransferCancellationPreservesTransferAndTerminalEventIdentities() {
        long bindingId = nextBindingId();
        bindingIds.add(bindingId);
        UUID bindingRequestKey = UUID.randomUUID();
        byte[] bindingHash = hashByte(23);
        long occurrenceId = 701L;
        Map<String, Object> transferEvent = event(bindingId, null, actorId,
                bindingRequestKey, bindingHash, "PENDING", 1, 1, "e", 20001, 20002,
                occurrenceId, SOURCE_DATE, 1, MIDDLE_DATE, 2);
        UUID transferSourceEventId = UUID.fromString((String) transferEvent.get("event_id"));
        transferConsumer.onEvent(transferEvent);

        long adminId = jdbcTemplate.queryForObject("SELECT id FROM users WHERE login = 'admin'", Long.class);
        var archive = archiveCommands.startOrReplay(
                semesterId, adminId, UUID.randomUUID(), SemesterArchiveAction.ARCHIVE);
        archiveOperationIds.add(archive.getOperationId());
        AcademicSemesterArchiveBarrierTransaction.BindingResolution resolution =
                archiveBarrier.preparePendingBindingResolution(archive.getOperationId(), semesterId,
                        archive.getStateVersion(), bindingId, occurrenceId, actorId,
                        bindingRequestKey, bindingHash, 1L);
        assertThat(resolution.kind()).isEqualTo(
                AcademicSemesterArchiveBarrierTransaction.BindingResolutionKind.CANCEL_UNPUBLISHED);

        UUID cancellationEventId = UUID.randomUUID();
        archiveBarrier.recordCancellationEvent(archive.getOperationId(), semesterId,
                archive.getStateVersion(), bindingId, bindingRequestKey, occurrenceId,
                1L, cancellationEventId);
        Map<String, Object> cancellationEvent = archivedBindingEvent(
                bindingId, occurrenceId, actorId, bindingRequestKey, cancellationEventId, semesterId);
        archiveConsumer.onEvent(cancellationEvent);
        archiveConsumer.onEvent(cancellationEvent);

        Map<String, Object> marker = jdbcTemplate.queryForMap("""
                SELECT state, source_event_id, batch_index, operation_id
                  FROM homework_binding_transfer_markers WHERE binding_id = ?
                """, bindingId);
        assertThat(marker.get("state")).isEqualTo("CANCELLED_UNPUBLISHED");
        assertThat(marker.get("source_event_id")).isEqualTo(transferSourceEventId);
        assertThat(marker.get("source_event_id")).isNotEqualTo(cancellationEventId);
        assertThat(marker.get("batch_index")).isEqualTo(0);

        Map<String, Object> admission = jdbcTemplate.queryForMap("""
                SELECT source_event_id, terminal_event_id, resolution_state, consumed_at
                  FROM academic_semester_archive_publication_admissions
                 WHERE operation_id = ? AND binding_id = ?
                """, archive.getOperationId(), bindingId);
        assertThat(admission.get("source_event_id")).isEqualTo(transferSourceEventId);
        assertThat(admission.get("terminal_event_id")).isEqualTo(cancellationEventId);
        assertThat(admission.get("source_event_id")).isNotEqualTo(admission.get("terminal_event_id"));
        assertThat(admission.get("resolution_state")).isEqualTo("CANCELLED_UNPUBLISHED");
        assertThat(admission.get("consumed_at")).isNotNull();
        assertThat(jdbcTemplate.queryForObject("""
                SELECT count(*) FROM lesson_transfer_receipts
                 WHERE source_event_id = ? AND result = 'APPLIED'
                """, Long.class, transferSourceEventId)).isEqualTo(1L);
        assertThat(jdbcTemplate.queryForObject("""
                SELECT count(*) FROM academic_semester_archive_effect_receipts
                 WHERE source_event_id = ? AND event_type = 'homework.binding.archived'
                   AND state = 'APPLIED'
                """, Long.class, cancellationEventId)).isEqualTo(1L);
        Map<String, Object> effectReceipt = jdbcTemplate.queryForMap("""
                SELECT payload_hash, acknowledgement_event_id
                  FROM academic_semester_archive_effect_receipts WHERE source_event_id = ?
                """, cancellationEventId);
        UUID firstAcknowledgementEventId = (UUID) effectReceipt.get("acknowledgement_event_id");
        List<Map<String, Object>> acknowledgements = jdbcTemplate.queryForList("""
                SELECT payload ->> 'event_id' AS event_id,
                       payload #>> '{payload,source_event_id}' AS source_event_id,
                       payload #>> '{payload,target}' AS target,
                       payload #>> '{payload,source_event_type}' AS source_event_type,
                       payload #>> '{payload,semester_id}' AS semester_id,
                       payload #>> '{payload,payload_hash}' AS payload_hash,
                       payload #>> '{payload,result}' AS result
                  FROM academic_outbox
                 WHERE event_type = 'semester.archive.effect.ack'
                   AND payload #>> '{payload,source_event_id}' = ?
                """, cancellationEventId.toString());
        assertThat(acknowledgements).hasSize(2);
        List<String> acknowledgementEventIds = acknowledgements.stream()
                .map(acknowledgement -> (String) acknowledgement.get("event_id")).toList();
        assertThat(acknowledgementEventIds).doesNotHaveDuplicates()
                .contains(firstAcknowledgementEventId.toString());
        String effectHash = java.util.HexFormat.of().formatHex((byte[]) effectReceipt.get("payload_hash"));
        assertThat(acknowledgements).allSatisfy(acknowledgement -> assertThat(acknowledgement)
                .containsEntry("source_event_id", cancellationEventId.toString())
                .containsEntry("target", "ACADEMIC")
                .containsEntry("source_event_type", "homework.binding.archived")
                .containsEntry("semester_id", Long.toString(semesterId))
                .containsEntry("payload_hash", effectHash)
                .containsEntry("result", "APPLIED"));
    }

    @Test
    void oneOffV2MovesSamePublicationAndCompletionOnceAndRejectsTamperedOrigins() throws Exception {
        long bindingId = nextBindingId();
        bindingIds.add(bindingId);
        UUID requestKey = UUID.randomUUID();
        byte[] bindingHash = hashByte(21);
        Homework homework = publicationPersistence.persistPending(groupId, subjectId, semesterId,
                "One-off title", "One-off description", "https://example.test/one-off", actorId,
                SOURCE_DATE, 1, bindingId, requestKey, bindingHash);
        homework = publicationPersistence.activate(homework.getId(), actorId, requestKey, bindingId, bindingHash);
        long homeworkId = homework.getId();
        jdbcTemplate.update("INSERT INTO homework_completions(homework_id, student_id) VALUES (?, ?)", homeworkId, actorId);
        Map<String, Object> originalCompletion = jdbcTemplate.queryForMap(
                "SELECT id, student_id, completed_at FROM homework_completions WHERE homework_id = ?", homeworkId);
        Map<String, Object> transfer = oneOffEvent(event(bindingId, homeworkId, actorId, requestKey, bindingHash,
                "ACTIVE", 1, 1, "a", 10001, 10002, 501, SOURCE_DATE, 1, TARGET_DATE, 3));
        UUID operation = UUID.fromString(((Map<?, ?>) transfer.get("payload")).get("operation_id").toString());
        operationIds.add(operation);
        transferConsumer.onEvent(transfer);
        transferConsumer.onEvent(transfer); // Same delivery id keeps the existing dedup semantics.
        Map<String, Object> freshDelivery = copyTransfer(transfer);
        transferConsumer.onEvent(freshDelivery);
        Homework moved = homeworkRepository.findById(homeworkId).orElseThrow();
        assertThat(moved.getBindingId()).isEqualTo(bindingId);
        assertThat(moved.getLessonDate()).isEqualTo(TARGET_DATE);
        assertThat(moved.getLessonNumber()).isEqualTo(3);
        assertThat(moved.getTitle()).isEqualTo("One-off title");
        assertThat(moved.getDescription()).isEqualTo("One-off description");
        assertThat(moved.getLink()).isEqualTo("https://example.test/one-off");
        assertThat(moved.getPublicationState()).isEqualTo(HomeworkPublicationState.ACTIVE);
        assertThat(jdbcTemplate.queryForMap("SELECT id, student_id, completed_at FROM homework_completions WHERE homework_id = ?", homeworkId))
                .isEqualTo(originalCompletion);
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM homework_binding_transfer_history WHERE binding_id = ?", Long.class, bindingId)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM lesson_transfer_receipts WHERE operation_id = ?", Long.class, operation)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM academic_outbox WHERE event_type = 'lesson.transfer.participant.applied' AND payload #>> '{payload,operation_id}' = ?", Long.class, operation.toString())).isEqualTo(1);

        for (int variant = 0; variant < 7; variant++) {
            Map<String, Object> tampered = copyTransfer(transfer);
            Map<String, Object> source = transferSnapshot(tampered, "source");
            Map<String, Object> target = transferSnapshot(tampered, "target");
            switch (variant) {
                case 0 -> { source.put("one_off_lesson_id", 90002L); target.put("one_off_lesson_id", 90002L); }
                case 1 -> { tampered.put("event_version", 1); source.remove("one_off_lesson_id"); target.remove("one_off_lesson_id");
                    source.put("schedule_item_id", 30001L); target.put("schedule_item_id", 30001L); }
                case 2 -> source.put("schedule_item_id", 30001L);
                case 3 -> { source.put("one_off_lesson_id", null); target.put("one_off_lesson_id", null); }
                case 4 -> { source.put("one_off_lesson_id", -1L); target.put("one_off_lesson_id", -1L); }
                case 5 -> target.put("one_off_lesson_id", 90002L);
                case 6 -> target.put("group_id", groupId + 1);
                default -> throw new AssertionError();
            }
            assertThatThrownBy(() -> transferConsumer.onEvent(tampered)).isInstanceOf(IllegalArgumentException.class);
        }
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM homework_binding_transfer_history WHERE binding_id = ?", Long.class, bindingId)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM lesson_transfer_receipts WHERE operation_id = ?", Long.class, operation)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForMap("SELECT id, student_id, completed_at FROM homework_completions WHERE homework_id = ?", homeworkId)).isEqualTo(originalCompletion);
    }

    private Map<String, Object> oneOffEvent(Map<String, Object> envelope) {
        envelope.put("event_version", 2);
        for (String field : List.of("source", "target")) {
            Map<String, Object> snapshot = transferSnapshot(envelope, field);
            snapshot.put("schedule_item_id", null);
            snapshot.put("one_off_lesson_id", 90001L);
            snapshot.put("assigned_teacher_id", 30003L);
            snapshot.put("lesson_type", "lecture");
            snapshot.put("week_type_snapshot", "all");
            snapshot.put("day_of_week", LocalDate.parse((String) snapshot.get("date")).getDayOfWeek().getValue());
        }
        return envelope;
    }

    private Map<String, Object> copyTransfer(Map<String, Object> original) {
        Map<String, Object> copy = new LinkedHashMap<>(original);
        UUID eventId = UUID.randomUUID();
        eventIds.add(eventId);
        copy.put("event_id", eventId.toString());
        Map<String, Object> payload = new LinkedHashMap<>((Map<String, Object>) original.get("payload"));
        payload.put("source", new LinkedHashMap<>(transferSnapshot(original, "source")));
        payload.put("target", new LinkedHashMap<>(transferSnapshot(original, "target")));
        copy.put("payload", payload);
        return copy;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> transferSnapshot(Map<String, Object> envelope, String field) {
        return (Map<String, Object>) ((Map<String, Object>) envelope.get("payload")).get(field);
    }

    private long nextBindingId() {
        return ThreadLocalRandom.current().nextLong(10_000_000_000L, 9_000_000_000_000L);
    }

    private static byte[] hashByte(int marker) {
        byte[] result = new byte[32];
        result[0] = (byte) marker;
        return result;
    }

    private Map<String, Object> event(long bindingId, Long homeworkId, long bindingActor,
                                      UUID bindingRequestKey, byte[] bindingHash, String bindingState,
                                      long bindingRevision, long sourceOccurrenceRevision,
                                      String hashPrefix,
                                      long sourceLessonId, long targetLessonId, long occurrenceId,
                                      LocalDate sourceDate, int sourceNumber,
                                      LocalDate targetDate, int targetNumber) {
        UUID operationId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        eventIds.add(eventId);
        Map<String, Object> binding = new LinkedHashMap<>();
        binding.put("binding_id", bindingId);
        binding.put("homework_id", homeworkId);
        binding.put("state", bindingState);
        binding.put("revision", bindingRevision);
        binding.put("actor_id", bindingActor);
        binding.put("request_key", bindingRequestKey.toString());
        binding.put("payload_hash", java.util.HexFormat.of().formatHex(bindingHash));

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("operation_id", operationId.toString());
        payload.put("request_key", UUID.randomUUID().toString());
        payload.put("actor_id", actorId);
        payload.put("transfer_payload_hash", hashPrefix.repeat(64));
        payload.put("occurrence_id", occurrenceId);
        payload.put("group_id", groupId);
        payload.put("semester_id", semesterId);
        payload.put("transfer_revision", sourceOccurrenceRevision + 1);
        payload.put("source_lesson_id", sourceLessonId);
        payload.put("target_lesson_id", targetLessonId);
        payload.put("source", snapshot(sourceLessonId, occurrenceId, sourceDate, sourceNumber,
                1, sourceOccurrenceRevision));
        payload.put("target", snapshot(targetLessonId, occurrenceId, targetDate, targetNumber,
                1, sourceOccurrenceRevision + 1));
        payload.put("batch_index", 0);
        payload.put("batch_count", 1);
        payload.put("bindings", List.of(binding));
        Map<String, Object> envelope = new LinkedHashMap<>();
        envelope.put("event_type", "lesson.transfer.requested");
        envelope.put("event_id", eventId.toString());
        envelope.put("event_version", 1);
        envelope.put("occurred_at", "2026-09-29T12:00:00Z");
        envelope.put("source", "schedule-service");
        envelope.put("trace_id", UUID.randomUUID().toString());
        envelope.put("payload", payload);
        return envelope;
    }

    private Map<String, Object> archivedBindingEvent(long bindingId, long occurrenceId,
                                                     long bindingActor, UUID requestKey,
                                                     UUID eventId, long eventSemesterId) {
        eventIds.add(eventId);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("binding_id", bindingId);
        payload.put("actor_id", bindingActor);
        payload.put("request_key", requestKey.toString());
        payload.put("occurrence_id", occurrenceId);
        payload.put("lesson_id", 20001L);
        payload.put("semester_id", eventSemesterId);
        payload.put("homework_id", null);
        payload.put("binding_revision", 2L);
        Map<String, Object> envelope = new LinkedHashMap<>();
        envelope.put("event_type", "homework.binding.archived");
        envelope.put("event_id", eventId.toString());
        envelope.put("event_version", 1);
        envelope.put("occurred_at", "2026-09-30T12:00:00Z");
        envelope.put("source", "schedule-service");
        envelope.put("trace_id", UUID.randomUUID().toString());
        envelope.put("payload", payload);
        return envelope;
    }

    private Map<String, Object> snapshot(long lessonId, long occurrenceId, LocalDate date,
                                         int lessonNumber, long lessonRevision, long occurrenceRevision) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("lesson_id", lessonId);
        snapshot.put("schedule_item_id", 30001L);
        snapshot.put("occurrence_id", occurrenceId);
        snapshot.put("assignment_id", 30002L);
        snapshot.put("group_id", groupId);
        snapshot.put("subject_id", subjectId);
        snapshot.put("semester_id", semesterId);
        snapshot.put("generation", lessonId % 10000 == 3 ? 3 : lessonId % 10000 == 2 ? 2 : 1);
        snapshot.put("lesson_revision", lessonRevision);
        snapshot.put("occurrence_revision", occurrenceRevision);
        snapshot.put("date", date.toString());
        snapshot.put("lesson_number", lessonNumber);
        snapshot.put("day_of_week", date.getDayOfWeek().getValue() - 1);
        snapshot.put("start_time", "09:00");
        snapshot.put("end_time", "10:30");
        snapshot.put("room", "Room A");
        snapshot.put("status", "planned");
        return snapshot;
    }
}
