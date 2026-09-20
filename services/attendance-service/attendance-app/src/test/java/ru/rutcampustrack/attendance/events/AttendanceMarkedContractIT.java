package ru.rutcampustrack.attendance.events;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import ru.rutcampustrack.attendance.checkin.AttendanceDocument;
import ru.rutcampustrack.attendance.contract.dto.checkin.CheckinRequest;
import ru.rutcampustrack.attendance.integration.AbstractAttendanceIntegrationTest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The retired legacy endpoint must not recreate the former
 * {@code attendance.marked} event contract.
 */
class AttendanceMarkedContractIT extends AbstractAttendanceIntegrationTest {

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @BeforeEach
    void setUp() {
        mongoTemplate.remove(new Query(), AttendanceDocument.class);
        mongoTemplate.getCollection("attendance_outbox").deleteMany(new org.bson.Document());
        redisTemplate.getConnectionFactory().getConnection().serverCommands().flushAll();
    }

    @Test
    void retiredLegacyCheckin_doesNotPublishAttendanceMarkedEvent() throws Exception {
        mockMvc.perform(post("/attendance/checkin")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CheckinRequest(55.7558, 37.6173)))
                        .header("X-User-Id", "100")
                        .header("X-User-Role", "STUDENT")
                        .header("X-Group-Id", "10")
                        .header("X-Is-Headman", "false"))
                .andExpect(status().isGone())
                .andExpect(jsonPath("$.type").value(
                        "https://api.rutcampustrack.ru/problems/legacy-checkin-retired"));

        assertThat(mongoTemplate.getCollection("attendance_outbox").countDocuments()).isZero();
        assertThat(outboxStorage.findPending(100)).isEmpty();
    }
}
