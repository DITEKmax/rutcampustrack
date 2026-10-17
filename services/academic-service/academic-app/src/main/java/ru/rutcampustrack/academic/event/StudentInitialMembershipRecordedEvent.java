package ru.rutcampustrack.academic.event;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.util.Map;
import java.util.TreeMap;
import ru.rutcampustrack.academic.entity.Semester;
import ru.rutcampustrack.academic.entity.StudentGroupHistory;

/** Private identity-only enrollment event. No user or credential DTO is serialized. */
public final class StudentInitialMembershipRecordedEvent extends DomainEvent {
    public static final String TYPE = "student.initial-membership.recorded";
    private static final ObjectMapper CANONICAL_JSON = new ObjectMapper();

    public StudentInitialMembershipRecordedEvent(StudentGroupHistory history, Semester semester) {
        super(TYPE, TYPE, payload(history, semester));
    }

    private static Map<String, Object> payload(StudentGroupHistory history, Semester semester) {
        if (history.getId() == null
                || history.getId() <= 0
                || history.getUserId() == null
                || history.getUserId() <= 0
                || history.getGroupId() == null
                || history.getGroupId() <= 0
                || semester.getId() == null
                || semester.getId() <= 0
                || !"initial-enrollment".equals(history.getReason())
                || !semester.getDateFrom().equals(history.getJoinedAt()))
            throw new IllegalArgumentException(
                    "Initial membership requires a persisted canonical tuple");
        return new java.util.LinkedHashMap<>(
                Map.of(
                        "history_id",
                        history.getId(),
                        "student_id",
                        history.getUserId(),
                        "group_id",
                        history.getGroupId(),
                        "semester_id",
                        semester.getId(),
                        "joined_on",
                        history.getJoinedAt().toString()));
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> membership() {
        return (Map<String, Object>) getPayload();
    }

    /** Called by synchronous admission while the canonical semester lock is held. */
    void captureSemesterEnd(LocalDate end) {
        LocalDate joined = LocalDate.parse((String) membership().get("joined_on"));
        if (end == null || end.isBefore(joined))
            throw new IllegalArgumentException("Invalid canonical semester bounds");
        membership().put("semester_ends_on", end.toString());
    }

    public byte[] payloadHash() {
        if (!membership().containsKey("semester_ends_on"))
            throw new IllegalStateException("Semester admission is required before hashing");
        try {
            return MessageDigest.getInstance("SHA-256")
                    .digest(CANONICAL_JSON.writeValueAsBytes(new TreeMap<>(membership())));
        } catch (JsonProcessingException | NoSuchAlgorithmException failure) {
            throw new IllegalStateException("Cannot hash initial membership identity", failure);
        }
    }
}
