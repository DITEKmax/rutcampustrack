package ru.rutcampustrack.schedule.integration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.lessThanOrEqualTo;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * M13 G3: global Pageable cap через
 * {@link ru.rutcampustrack.shared.web.autoconfigure.PageableDefaultsPostProcessor}.
 *
 * <p>Schedule-service имеет локальный override {@code max-page-size: 200} в
 * application.yml (week-range queries ~640 lessons/semester), поэтому
 * здесь мы проверяем, что запрос {@code ?size=1000000} обрезается до
 * {@code ≤ 200} (schedule override), а не неограниченно принимается.</p>
 */
@AutoConfigureMockMvc
class PaginationCapIT extends AbstractScheduleIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void getLessons_sizeExceedsMax_truncatedToServiceCap() throws Exception {
        mockMvc.perform(get("/schedule/groups/1/lessons")
                        .param("dateFrom", "2026-04-01")
                        .param("dateTo", "2026-04-30")
                        .param("size", "1000000")
                        .header("X-User-Id", "42")
                        .header("X-User-Role", "ADMIN")
                        .header("X-Is-Headman", "false"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page.size", lessThanOrEqualTo(200)));
    }
}
