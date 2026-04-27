package ru.rutcampustrack.academic.audit;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import ru.rutcampustrack.shared.web.audit.AuditLogEntry;
import ru.rutcampustrack.shared.web.audit.AuditLogStorage;

import java.sql.PreparedStatement;
import java.sql.Timestamp;
import java.sql.Types;

/**
 * M16 G6 — JDBC implementation {@link AuditLogStorage} для academic-app.
 *
 * <p>Пишет в таблицу {@code audit_log} (V19 миграция) через JdbcTemplate
 * (а не JPA repository) потому что:
 * <ul>
 *   <li>Audit-row write — простая INSERT, не нужен ORM overhead.</li>
 *   <li>Нет need в entity-mapping / lazy loading / cascade — write-only.</li>
 *   <li>Минимальные deps между shared-web SPI и academic JPA repos.</li>
 * </ul>
 *
 * <p>JSONB extras сериализуется через Jackson {@link ObjectMapper}
 * (autoconfigured Spring bean). Если serialization fails — extras
 * пишется как {@code null} с warn-логом.
 */
@Component
public class JdbcAuditLogStorage implements AuditLogStorage {

    private static final Logger log = LoggerFactory.getLogger(JdbcAuditLogStorage.class);

    private static final String INSERT_SQL = """
            INSERT INTO audit_log
                (user_id, action, target_type, target_id, correlation_id,
                 extras, succeeded, error_message, created_at)
            VALUES (?, ?, ?, ?, ?, ?::jsonb, ?, ?, ?)
            """;

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public JdbcAuditLogStorage(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public void store(AuditLogEntry entry) {
        String extrasJson = serializeExtras(entry);

        jdbcTemplate.update(con -> {
            PreparedStatement ps = con.prepareStatement(INSERT_SQL);
            setNullableLong(ps, 1, entry.userId());
            ps.setString(2, entry.action());
            setNullableString(ps, 3, entry.targetType());
            setNullableLong(ps, 4, entry.targetId());
            setNullableString(ps, 5, entry.correlationId());
            setNullableString(ps, 6, extrasJson);
            ps.setBoolean(7, entry.succeeded());
            setNullableString(ps, 8, entry.errorMessage());
            ps.setTimestamp(9, Timestamp.from(entry.createdAt()));
            return ps;
        });
    }

    private String serializeExtras(AuditLogEntry entry) {
        if (entry.extras() == null || entry.extras().isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(entry.extras());
        } catch (JsonProcessingException e) {
            log.warn("audit extras serialization failed (entry stored without extras): action={} err={}",
                    entry.action(), e.toString());
            return null;
        }
    }

    private static void setNullableLong(PreparedStatement ps, int idx, Long value)
            throws java.sql.SQLException {
        if (value == null) {
            ps.setNull(idx, Types.BIGINT);
        } else {
            ps.setLong(idx, value);
        }
    }

    private static void setNullableString(PreparedStatement ps, int idx, String value)
            throws java.sql.SQLException {
        if (value == null) {
            ps.setNull(idx, Types.VARCHAR);
        } else {
            ps.setString(idx, value);
        }
    }
}
