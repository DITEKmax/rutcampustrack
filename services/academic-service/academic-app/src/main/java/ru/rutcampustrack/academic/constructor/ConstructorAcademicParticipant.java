package ru.rutcampustrack.academic.constructor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.protobuf.ByteString;
import io.grpc.Status;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import ru.rutcampustrack.constructor.grpc.ConstructorDeletionAction;
import ru.rutcampustrack.constructor.grpc.ConstructorDeletionCommand;
import ru.rutcampustrack.constructor.grpc.ConstructorLessonScope;
import ru.rutcampustrack.constructor.grpc.ConstructorParticipantReceipt;
import ru.rutcampustrack.constructor.grpc.ConstructorProtectedReason;
import ru.rutcampustrack.shared.security.InternalJwtClaims;
import ru.rutcampustrack.shared.security.grpc.ServicePrincipal;

/** Empty-homework witness. This participant never removes or rewrites academic business history. */
@Service
public class ConstructorAcademicParticipant {
    private static final int MAX_LESSONS = 4096;
    private static final ObjectMapper JSON = new ObjectMapper();
    private final JdbcTemplate jdbc;

    public ConstructorAcademicParticipant(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    private record Snapshot(byte[] digest, long protectedCount, boolean unknown) {}

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public ConstructorParticipantReceipt apply(ConstructorDeletionCommand command) {
        validate(command);
        if (!ServicePrincipal.SCHEDULE_SERVICE.equals(ServicePrincipal.CONTEXT_KEY.get()))
            throw Status.PERMISSION_DENIED.asRuntimeException();
        var action = command.getAction();
        InternalJwtClaims actor = ConstructorServiceIdentityInterceptor.ACTOR.get();
        if (actor != null) requireActor(actor, command);
        if (action == ConstructorDeletionAction.CONSTRUCTOR_DELETION_PREVIEW && actor == null)
            throw Status.UNAUTHENTICATED.withDescription("signed preview actor required").asRuntimeException();
        UUID operation = UUID.fromString(command.getOperationId());
        var scope = command.getScope();
        // The same lock is acquired by the homework write trigger, before any
        // eligibility snapshot can be accepted or released.
        jdbc.queryForList("SELECT academic_constructor_lock_scope(?,?)", scope.getSemesterId(), scope.getSubjectId());
        jdbc.queryForList("SELECT academic_constructor_lock_lessons(?::jsonb)", scopeJson(command));
        List<Map<String, Object>> epochs = jdbc.queryForList(
                "SELECT * FROM academic_constructor_deletion_participants WHERE operation_id=? FOR UPDATE", operation);
        Map<String, Object> epoch = epochs.isEmpty() ? null : epochs.getFirst();
        byte[] identity = identity(command);
        if (epoch != null && !MessageDigest.isEqual((byte[]) epoch.get("command"), identity))
            throw conflict();
        if (epoch != null && "DELETED".equals(epoch.get("state"))) {
            if (action != ConstructorDeletionAction.CONSTRUCTOR_DELETION_COMMIT) throw conflict();
            return receipt(command, (byte[]) epoch.get("participant_digest"), "DELETED", 0, false);
        }
        if (epoch != null && "RELEASED".equals(epoch.get("state"))) {
            if (action != ConstructorDeletionAction.CONSTRUCTOR_DELETION_RELEASE) throw conflict();
            return receipt(command, (byte[]) epoch.get("participant_digest"), "RELEASED", 0, false);
        }
        if (action == ConstructorDeletionAction.CONSTRUCTOR_DELETION_RELEASE) {
            if (epoch == null) insert(command, "RELEASED");
            else jdbc.update("UPDATE academic_constructor_deletion_participants SET state='RELEASED' WHERE operation_id=?", operation);
            return receipt(command, command.getParticipantDigest().toByteArray(), "RELEASED", 0, false);
        }
        validateCanonicalScope(command);
        Snapshot current = snapshot(command);
        if (action == ConstructorDeletionAction.CONSTRUCTOR_DELETION_PREVIEW)
            return receipt(command, current.digest(), "PREVIEW", current.protectedCount(), current.unknown());
        if (current.protectedCount() > 0 || !MessageDigest.isEqual(current.digest(), command.getParticipantDigest().toByteArray()))
            throw conflict();
        if (action == ConstructorDeletionAction.CONSTRUCTOR_DELETION_PREPARE) {
            // The dedicated Schedule capability delegates authority for a source
            // admission already committed with fresh HTTP authorization. Recovery
            // deliberately has no stored/renewed user JWT.
            if (epoch == null) insert(command, "PREPARED");
            return receipt(command, current.digest(), epoch == null ? "PREPARED" : (String) epoch.get("state"), 0, false);
        }
        if (epoch == null) throw conflict();
        if (action == ConstructorDeletionAction.CONSTRUCTOR_DELETION_SEAL) {
            if (!List.of("PREPARED", "SEALED").contains(epoch.get("state"))) throw conflict();
            jdbc.update("UPDATE academic_constructor_deletion_participants SET state='SEALED' WHERE operation_id=?", operation);
            return receipt(command, current.digest(), "SEALED", 0, false);
        }
        if (action == ConstructorDeletionAction.CONSTRUCTOR_DELETION_COMMIT) {
            if (!"SEALED".equals(epoch.get("state"))) throw conflict();
            jdbc.update("UPDATE academic_constructor_deletion_participants SET state='DELETED' WHERE operation_id=?", operation);
            return receipt(command, current.digest(), "DELETED", 0, false);
        }
        throw conflict();
    }

    private void insert(ConstructorDeletionCommand command, String state) {
        var scope = command.getScope();
        jdbc.update(
                """
                INSERT INTO academic_constructor_deletion_participants
                    (operation_id,group_id,subject_id,semester_id,request_hash,scope_hash,
                     participant_digest,command,lesson_scope,state)
                VALUES(?,?,?,?,?,?,?,?,?::jsonb,?)
                """,
                UUID.fromString(command.getOperationId()), scope.getGroupId(), scope.getSubjectId(), scope.getSemesterId(),
                command.getRequestHash().toByteArray(), command.getScopeHash().toByteArray(),
                command.getParticipantDigest().toByteArray(), identity(command), scopeJson(command), state);
    }

    private void validateCanonicalScope(ConstructorDeletionCommand command) {
        var scope = command.getScope();
        var rows = jdbc.queryForList(
                """
                SELECT subject.group_id,subject.semester_id,semester.date_from,semester.date_to
                  FROM subjects subject JOIN semesters semester ON semester.id=subject.semester_id
                  JOIN groups target_group ON target_group.id=subject.group_id AND target_group.is_active
                 WHERE subject.id=? AND subject.group_id=? AND subject.semester_id=?
                   AND semester.is_active AND NOT semester.is_archived AND semester.archive_transition='NONE' AND NOT semester.archive_release_pending
                   AND NOT EXISTS (SELECT 1 FROM academic_group_availability_guards guard WHERE guard.group_id=subject.group_id)
                   AND NOT EXISTS (SELECT 1 FROM academic_semester_archive_barriers barrier WHERE barrier.semester_id=semester.id AND barrier.participant_state NOT IN ('RELEASED'))
                   AND NOT EXISTS (SELECT 1 FROM group_deletion_operations op WHERE op.group_id=subject.group_id AND op.phase NOT IN ('COMPLETED','CANCELLED'))
                   AND NOT EXISTS (SELECT 1 FROM subject_deletion_operations op WHERE op.subject_id=subject.id AND op.phase NOT IN ('COMPLETED','CANCELLED'))
                """, scope.getSubjectId(), scope.getGroupId(), scope.getSemesterId());
        if (rows.size() != 1) throw conflict();
        LocalDate from = ((java.sql.Date) rows.getFirst().get("date_from")).toLocalDate();
        LocalDate to = ((java.sql.Date) rows.getFirst().get("date_to")).toLocalDate();
        for (var lesson : scope.getLessonsList()) {
            LocalDate day = LocalDate.parse(lesson.getLessonDate());
            if (day.isBefore(from) || day.isAfter(to)) throw conflict();
        }
    }

    private Snapshot snapshot(ConstructorDeletionCommand command) {
        var scope = command.getScope();
        List<Map<String, Object>> rows = new ArrayList<>();
        for (var row : jdbc.queryForList(
                "SELECT kind,data::text AS data,unknown_scope FROM academic_constructor_history_rows(?,?,?,?::jsonb) LIMIT 10001",
                scope.getGroupId(), scope.getSubjectId(), scope.getSemesterId(), scopeJson(command))) {
            try {
                rows.add(Map.of("kind", row.get("kind"), "data", JSON.readTree((String) row.get("data")),
                        "unknown_scope", row.get("unknown_scope")));
            } catch (Exception invalid) {
                throw conflict();
            }
        }
        Set<Long> protectedOccurrences = new HashSet<>();
        boolean unknown = rows.size() > 10000;
        List<Object> relevant = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            JsonNode data = (JsonNode) row.get("data");
            boolean matched = false;
            for (ConstructorLessonScope lesson : scope.getLessonsList()) {
                if (mentions(data, lesson)) {
                    protectedOccurrences.add(lesson.getOccurrenceId());
                    matched = true;
                }
            }
            if (Boolean.TRUE.equals(row.get("unknown_scope"))) unknown = true;
            if (matched) relevant.add(row);
        }
        if (unknown) scope.getLessonsList().forEach(lesson -> protectedOccurrences.add(lesson.getOccurrenceId()));
        relevant.sort(java.util.Comparator.comparing(value -> ConstructorHashes.canonical(JSON.valueToTree(value))));
        byte[] digest = ConstructorHashes.json(Map.of(
                "group", scope.getGroupId(), "subject", scope.getSubjectId(), "semester", scope.getSemesterId(),
                "rows", relevant, "unknown", unknown));
        return new Snapshot(digest, protectedOccurrences.size(), unknown);
    }

    // The SQL fence uses the same recursive aliases. Natural DATE-mode content
    // protects every logical lesson on that date; no physical ID is guessed.
    static boolean mentions(JsonNode value, ConstructorLessonScope lesson) {
        if (value == null || value.isNull()) return false;
        if (value.isArray()) {
            for (JsonNode item : value) if (mentions(item, lesson)) return true;
            return false;
        }
        if (!value.isObject()) return false;
        var fields = value.fields();
        while (fields.hasNext()) {
            var field = fields.next();
            String key = field.getKey().replace("_", "").toLowerCase(java.util.Locale.ROOT);
            JsonNode id = field.getValue();
            if (id.isIntegralNumber() && (
                    LESSON_KEYS.contains(key) && id.longValue() == lesson.getLessonId()
                    || OCCURRENCE_KEYS.contains(key) && id.longValue() == lesson.getOccurrenceId())) return true;
        }
        for (String[] pair : List.of(
                new String[] {"lesson_date", "lesson_number"}, new String[] {"lessonDate", "lessonNumber"},
                new String[] {"source_date", "source_lesson_number"}, new String[] {"target_date", "target_lesson_number"},
                new String[] {"date", "lessonNumber"})) {
            JsonNode number = value.path(pair[1]);
            if (lesson.getLessonDate().equals(value.path(pair[0]).asText())
                    && (number.isMissingNode() || number.isNull() || number.asInt(-1) == lesson.getLessonNumber())) return true;
        }
        var nested = value.elements();
        while (nested.hasNext()) if (mentions(nested.next(), lesson)) return true;
        return false;
    }

    private static final Set<String> LESSON_KEYS = Set.of(
            "lessonid", "sourcelessonid", "targetlessonid", "originallessonid", "snapshotlessonid", "placementlessonid", "currentlessonid");
    private static final Set<String> OCCURRENCE_KEYS = Set.of(
            "occurrenceid", "targetoccurrenceid", "sourceoccurrenceid", "originaloccurrenceid", "snapshotoccurrenceid",
            "placementoccurrenceid", "selectedoccurrenceid", "scheduleoccurrenceid");

    private static ConstructorParticipantReceipt receipt(
            ConstructorDeletionCommand command, byte[] digest, String state, long count, boolean unknown) {
        var result = ConstructorParticipantReceipt.newBuilder()
                .setOperationId(command.getOperationId()).setRequestHash(command.getRequestHash())
                .setScopeHash(command.getScopeHash()).setParticipantDigest(ByteString.copyFrom(digest))
                .setState(state).setProtectedCount(count);
        if (count > 0) result.addProtectedReasons(ConstructorProtectedReason.newBuilder()
                .setCode(unknown ? "UNKNOWN_HISTORY" : "HOMEWORK").setCount(count));
        return result.build();
    }

    private static byte[] identity(ConstructorDeletionCommand command) {
        return command.toBuilder().setAction(ConstructorDeletionAction.CONSTRUCTOR_DELETION_ACTION_UNSPECIFIED).build().toByteArray();
    }

    private static String scopeJson(ConstructorDeletionCommand command) {
        try {
            return JSON.writeValueAsString(command.getScope().getLessonsList().stream()
                    .map(lesson -> Map.of("lesson_id", lesson.getLessonId(), "occurrence_id", lesson.getOccurrenceId(),
                            "lesson_date", lesson.getLessonDate(), "lesson_number", lesson.getLessonNumber())).toList());
        } catch (Exception impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    private static void validate(ConstructorDeletionCommand command) {
        try {
            if (!UUID.fromString(command.getOperationId()).toString().equals(command.getOperationId())
                    || command.getAction() == ConstructorDeletionAction.CONSTRUCTOR_DELETION_ACTION_UNSPECIFIED
                    || command.getAction() == ConstructorDeletionAction.UNRECOGNIZED
                    || command.getOriginalActorId() <= 0 || command.getRequestHash().size() != 32
                    || command.getScopeHash().size() != 32 || !command.hasScope()
                    || command.getScope().getGroupId() <= 0 || command.getScope().getSubjectId() <= 0
                    || command.getScope().getSemesterId() <= 0 || command.getScope().getScheduleItemIdsCount() == 0
                    || command.getScope().getScheduleItemIdsList().stream().anyMatch(id -> id <= 0)
                    || new HashSet<>(command.getScope().getScheduleItemIdsList()).size() != command.getScope().getScheduleItemIdsCount()
                    || command.getScope().getLessonsCount() > MAX_LESSONS
                    || !MessageDigest.isEqual(ConstructorHashes.sha256(command.getScope().toByteArray()), command.getScopeHash().toByteArray())
                    || command.getAction() != ConstructorDeletionAction.CONSTRUCTOR_DELETION_PREVIEW
                        && command.getParticipantDigest().size() != 32) throw new IllegalArgumentException();
            Set<Long> seen = new HashSet<>();
            for (var lesson : command.getScope().getLessonsList()) {
                if (lesson.getLessonId() <= 0 || !seen.add(lesson.getLessonId()) || lesson.getOccurrenceId() <= 0
                        || !command.getScope().getScheduleItemIdsList().contains(lesson.getScheduleItemId())
                        || lesson.getGeneration() <= 0 || lesson.getLessonRevision() <= 0 || lesson.getOccurrenceRevision() <= 0
                        || lesson.getOriginProvenanceHash().size() != 32 || lesson.getLessonNumber() < 1 || lesson.getLessonNumber() > 8)
                    throw new IllegalArgumentException();
                LocalDate.parse(lesson.getLessonDate());
            }
        } catch (RuntimeException invalid) {
            throw Status.INVALID_ARGUMENT.withDescription("invalid exact constructor scope").asRuntimeException();
        }
    }

    private static void requireActor(InternalJwtClaims actor, ConstructorDeletionCommand command) {
        if (actor.userId() != command.getOriginalActorId() || actor.readOnly() || !"ACTIVE".equals(actor.status())
                || !("ADMIN".equals(actor.domainRole()) || actor.isHeadman()
                    && actor.groupId() != null && actor.groupId() == command.getScope().getGroupId()))
            throw Status.PERMISSION_DENIED.withDescription("constructor actor scope mismatch").asRuntimeException();
    }

    private static io.grpc.StatusRuntimeException conflict() {
        return Status.FAILED_PRECONDITION.withDescription("constructor homework witness changed").asRuntimeException();
    }
}
