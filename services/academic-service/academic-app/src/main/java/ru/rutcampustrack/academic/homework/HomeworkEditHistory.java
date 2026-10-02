package ru.rutcampustrack.academic.homework;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import ru.rutcampustrack.academic.contract.dto.homework.HomeworkHistoryResponse;
import ru.rutcampustrack.academic.contract.dto.homework.HomeworkSnapshot;
import ru.rutcampustrack.academic.contract.dto.homework.UpdateHomeworkRequest;
import ru.rutcampustrack.academic.entity.Homework;
import ru.rutcampustrack.academic.exception.ConflictException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/** Uses the caller's semester/binding transaction and its shared mutation lock. */
@Component
public class HomeworkEditHistory {
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;

    public HomeworkEditHistory(JdbcTemplate jdbc, ObjectMapper mapper) {
        this.jdbc = jdbc;
        this.mapper = mapper;
    }

    public byte[] commandHash(UpdateHomeworkRequest request) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(json(request).getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException unavailable) {
            throw new IllegalStateException(unavailable);
        }
    }

    public Homework replay(Homework homework, long actor, UUID key, byte[] hash) {
        List<Receipt> rows = jdbc.query("""
                SELECT command_hash, result_revision, result_snapshot::text
                  FROM homework_edit_receipts
                 WHERE homework_id = ? AND actor_id = ? AND request_key = ?
                """, (rs, index) -> new Receipt(rs.getBytes(1), rs.getLong(2),
                read(rs.getString(3), HomeworkSnapshot.class)), homework.getId(), actor, key);
        if (rows.isEmpty()) return null;
        Receipt receipt = rows.getFirst();
        if (!Arrays.equals(receipt.hash(), hash)) {
            throw new ConflictException("requestKey уже использован для другого изменения ДЗ");
        }
        return homework.recordedResult(receipt.snapshot(), receipt.revision());
    }

    public void record(Homework homework, long actor, UUID key, byte[] hash,
                       HomeworkSnapshot before, OffsetDateTime occurredAt, boolean changed) {
        HomeworkSnapshot after = homework.snapshot();
        jdbc.update("""
                INSERT INTO homework_edit_receipts
                    (homework_id, semester_id, actor_id, request_key, command_hash, result_revision, result_snapshot)
                VALUES (?, ?, ?, ?, ?, ?, ?::jsonb)
                """, homework.getId(), homework.getSemesterId(), actor, key, hash, homework.getRevision(), json(after));
        if (changed) append(homework, actor, before, occurredAt, "EDITED");
    }

    public void append(Homework homework, long actor, HomeworkSnapshot before,
                       OffsetDateTime occurredAt, String action) {
        jdbc.update("""
                INSERT INTO homework_edit_history
                    (homework_id, semester_id, revision, actor_id, occurred_at, action, before_snapshot, after_snapshot)
                VALUES (?, ?, ?, ?, ?, ?, ?::jsonb, ?::jsonb)
                """, homework.getId(), homework.getSemesterId(), homework.getRevision(), actor, occurredAt, action,
                json(before), json(homework.snapshot()));
    }

    public Page<HomeworkHistoryResponse> history(long homeworkId, Pageable requested) {
        // Caller-supplied sort never changes chronological ordering.
        Pageable page = PageRequest.of(requested.getPageNumber(), Math.min(requested.getPageSize(), 100));
        Long count = jdbc.queryForObject("SELECT COUNT(*) FROM homework_edit_history WHERE homework_id = ?",
                Long.class, homeworkId);
        List<HomeworkHistoryResponse> rows = jdbc.query("""
                SELECT id, revision, actor_id, occurred_at, action, before_snapshot::text, after_snapshot::text
                  FROM homework_edit_history WHERE homework_id = ?
                 ORDER BY revision ASC, id ASC LIMIT ? OFFSET ?
                """, (rs, index) -> new HomeworkHistoryResponse(rs.getLong(1), rs.getLong(2),
                rs.getLong(3), rs.getObject(4, OffsetDateTime.class), rs.getString(5),
                read(rs.getString(6), HomeworkSnapshot.class), read(rs.getString(7), HomeworkSnapshot.class)),
                homeworkId, page.getPageSize(), page.getOffset());
        return new PageImpl<>(rows, page, count == null ? 0 : count);
    }

    private String json(Object value) {
        try { return mapper.writeValueAsString(value); }
        catch (JsonProcessingException invalid) { throw new IllegalStateException("homework snapshot encoding failed", invalid); }
    }

    private <T> T read(String json, Class<T> type) {
        try { return mapper.readValue(json, type); }
        catch (JsonProcessingException invalid) { throw new IllegalStateException("homework snapshot decoding failed", invalid); }
    }

    private record Receipt(byte[] hash, long revision, HomeworkSnapshot snapshot) {}
}
