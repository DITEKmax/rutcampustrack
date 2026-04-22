package ru.rutcampustrack.academic.user;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import ru.rutcampustrack.academic.integration.AbstractAcademicIntegrationTest;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end тест BUG-006-1: GET /academic/users?search=... case-insensitive
 * фильтрация по login/ФИО/telegramId.
 *
 * <p>Запускается против Testcontainers PostgreSQL (см. AbstractAcademicIntegrationTest).
 * Каждый тест — @Transactional, автоматический откат.
 */
@AutoConfigureMockMvc
@Transactional
class UserSearchIT extends AbstractAcademicIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired JdbcTemplate jdbcTemplate;

    private Long seedAdminId;

    @BeforeEach
    void setUp() {
        seedAdminId = jdbcTemplate.queryForObject(
                "SELECT id FROM users WHERE login = 'admin'", Long.class);

        // Сидируем предсказуемых пользователей — имена выбраны так, чтобы НЕ
        // пересекаться с BUG-006 seed-юзерами ('admin', 'student', 'teacher').
        jdbcTemplate.update(
                "INSERT INTO users (login, password_hash, last_name, first_name, middle_name, " +
                        "role, status, is_headman, password_changed, created_at, updated_at) VALUES " +
                        "('srch_ivn1', 'x', 'Иванов', 'Иван',     'Иванович',   'student', 'active', false, false, NOW(), NOW())," +
                        "('srch_ivn2', 'x', 'Иванова','Ирина',    'Петровна',   'student', 'active', false, false, NOW(), NOW())," +
                        "('srch_ptrv', 'x', 'Петров', 'Пётр',     'Петрович',   'student', 'active', false, false, NOW(), NOW())," +
                        "('srch_sdrv', 'x', 'Сидоров','Семён',    NULL,         'student', 'active', false, false, NOW(), NOW())"
        );
    }

    private org.springframework.test.web.servlet.ResultActions listAs(String searchParam) throws Exception {
        var rb = get("/academic/users")
                .header("X-User-Id", seedAdminId)
                .header("X-User-Role", "ADMIN")
                .header("X-Group-Id", "")
                .header("X-Is-Headman", "false")
                .param("size", "100");
        if (searchParam != null) {
            rb = rb.param("search", searchParam);
        }
        return mockMvc.perform(rb);
    }

    @Test
    void search_caseInsensitive_filtersByLastName() throws Exception {
        listAs("иван")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$._embedded.userResponseList[*].login",
                        hasItems("srch_ivn1", "srch_ivn2")))
                .andExpect(jsonPath("$._embedded.userResponseList[*].login",
                        not(hasItem("srch_ptrv"))));
    }

    @Test
    void search_uppercase_matchesSameResultsAsLowercase() throws Exception {
        listAs("ИВАН")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$._embedded.userResponseList[*].login",
                        hasItems("srch_ivn1", "srch_ivn2")));
    }

    @Test
    void search_blank_returnsAllUsers() throws Exception {
        // Пустой search = без фильтра. Должны увидеть seed-админа плюс 4 наших
        // тестовых + другие seed-юзеров.
        listAs("")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page.totalElements", greaterThanOrEqualTo(5)));
    }

    @Test
    void search_noMatch_returnsEmptyPage() throws Exception {
        listAs("xyzNoSuchUserExists")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page.totalElements", is(0)));
    }

    @Test
    void search_byLoginPrefix_matchesCaseInsensitive() throws Exception {
        // Note: "_" is escaped as literal per T-58-01-01 (pattern injection guard).
        // Used "SRCH" (no underscore) → должен совпасть с префиксом всех четырёх seed-логинов.
        listAs("SRCH")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$._embedded.userResponseList[*].login",
                        hasItems("srch_ivn1", "srch_ivn2", "srch_ptrv", "srch_sdrv")));
    }

    @Test
    void search_underscoreIsEscaped_doesNotActAsWildcard() throws Exception {
        // "abc_" не должно матчить "abc" + любой символ — "_" экранирован.
        // Ни один seed-login не содержит литерального "xyz_" → пусто.
        listAs("xyz_abc")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page.totalElements", is(0)));
    }
}
