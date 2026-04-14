package ru.rutcampustrack.academic.user;

import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;
import ru.rutcampustrack.academic.contract.enums.AccountStatus;
import ru.rutcampustrack.academic.contract.enums.UserRole;
import ru.rutcampustrack.academic.entity.User;

/**
 * JPA Specifications for dynamic User queries.
 *
 * <p>Used by {@link ru.rutcampustrack.academic.repository.UserRepository} via
 * {@link org.springframework.data.jpa.repository.JpaSpecificationExecutor}.
 *
 * <p>BUG-006 / Plan 58-01: случай-независимый поиск по ФИО/login/telegramId.
 * Values are parameterized by JPA Criteria API (prepared statements) → защита от
 * SQL injection (T-58-01-01). Дополнительно экранируем спец-символы LIKE (% и _)
 * через escape-char «\\», чтобы пользовательский ввод не расширял паттерн.
 */
public final class UserSpecifications {

    /** Escape character used with {@code cb.like(..., ESCAPE_CHAR)}. */
    private static final char ESCAPE_CHAR = '\\';

    private UserSpecifications() {
        // utility
    }

    /**
     * Case-insensitive contains-match по login / last_name / first_name /
     * middle_name / telegram_id. Возвращает {@code null} если строка пустая или null —
     * {@link Specification#where(Specification)} интерпретирует null как «без ограничений».
     */
    public static Specification<User> matchesSearch(String q) {
        if (q == null || q.isBlank()) {
            return null;
        }
        String normalized = q.trim().toLowerCase();
        String pattern = "%" + escapeLike(normalized) + "%";
        return (root, query, cb) -> {
            Predicate byLogin = cb.like(cb.lower(root.get("login")), pattern, ESCAPE_CHAR);
            Predicate byLastName = cb.like(cb.lower(root.get("lastName")), pattern, ESCAPE_CHAR);
            Predicate byFirstName = cb.like(cb.lower(root.get("firstName")), pattern, ESCAPE_CHAR);
            Predicate byMiddleName = cb.like(
                    cb.lower(cb.coalesce(root.get("middleName"), "")),
                    pattern,
                    ESCAPE_CHAR);
            // telegram_id — BIGINT. Приводим к тексту через Hibernate-функцию `str(...)`
            // (реализуется диалектом как CAST(... AS VARCHAR)). Null-safe через COALESCE
            // на случай user без telegramId.
            Expression<String> telegramAsString = cb.function(
                    "str",
                    String.class,
                    cb.coalesce(root.get("telegramId"), 0L));
            Predicate byTelegram = cb.like(telegramAsString, pattern, ESCAPE_CHAR);
            return cb.or(byLogin, byLastName, byFirstName, byMiddleName, byTelegram);
        };
    }

    /** Exact match по роли. Null → без ограничений. */
    public static Specification<User> matchesRole(UserRole role) {
        if (role == null) {
            return null;
        }
        return (root, query, cb) -> cb.equal(root.get("role"), role);
    }

    /** Exact match по статусу. Null → без ограничений. */
    public static Specification<User> matchesStatus(AccountStatus status) {
        if (status == null) {
            return null;
        }
        return (root, query, cb) -> cb.equal(root.get("status"), status);
    }

    /**
     * Экранирует LIKE-спецсимволы {@code %} и {@code _} (а также сам escape-символ)
     * префиксом {@code '\\'}. Предотвращает pattern-injection от untrusted search input.
     */
    static String escapeLike(String s) {
        StringBuilder sb = new StringBuilder(s.length() + 4);
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == ESCAPE_CHAR || c == '%' || c == '_') {
                sb.append(ESCAPE_CHAR);
            }
            sb.append(c);
        }
        return sb.toString();
    }
}
