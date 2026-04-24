package ru.rutcampustrack.attendance.integration;

import org.junit.jupiter.api.Test;

import static org.hamcrest.Matchers.lessThanOrEqualTo;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * M13 G3: global Pageable cap через
 * {@link ru.rutcampustrack.shared.web.autoconfigure.PageableDefaultsPostProcessor}.
 * Проверяет, что {@code GET /attendance/excuses/me?size=1000000} возвращает
 * страницу с {@code page.size ≤ 100} (default global cap из shared-web).
 */
class PaginationCapIT extends AbstractAttendanceIntegrationTest {

    @Test
    void getMyTickets_sizeExceedsMax_truncatedToShared100() throws Exception {
        mockMvc.perform(get("/attendance/excuses/me?size=1000000&page=0")
                        .header("X-User-Id", "42")
                        .header("X-User-Role", "STUDENT")
                        .header("X-Group-Id", "1")
                        .header("X-Is-Headman", "false"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page.size", lessThanOrEqualTo(100)));
    }
}
