package ru.rutcampustrack.academic.integration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.lessThanOrEqualTo;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * M13 G3: global Pageable cap через
 * {@link ru.rutcampustrack.shared.web.autoconfigure.PageableDefaultsPostProcessor}.
 * Проверяет, что {@code GET /academic/users?size=1000000} возвращает страницу
 * с {@code page.size ≤ 100} (default global cap из shared-web).
 */
@AutoConfigureMockMvc
class PaginationCapIT extends AbstractAcademicIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void listUsers_sizeExceedsMax_truncatedToShared100() throws Exception {
        Long adminId = jdbcTemplate.queryForObject(
                "SELECT id FROM users WHERE login = 'admin'", Long.class);

        mockMvc.perform(get("/academic/users?size=1000000&page=0")
                        .header("X-User-Id", adminId)
                        .header("X-User-Role", "ADMIN")
                        .header("X-Is-Headman", "false"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page.size", lessThanOrEqualTo(100)));
    }
}
