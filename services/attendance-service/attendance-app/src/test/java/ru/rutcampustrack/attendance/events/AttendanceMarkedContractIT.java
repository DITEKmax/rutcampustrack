package ru.rutcampustrack.attendance.events;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.networknt.schema.ValidationMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import ru.rutcampustrack.attendance.checkin.AttendanceDocument;
import ru.rutcampustrack.attendance.contract.dto.checkin.CheckinRequest;
import ru.rutcampustrack.attendance.integration.AbstractAttendanceIntegrationTest;
import ru.rutcampustrack.schedule.grpc.LessonResponse;
import ru.rutcampustrack.shared.outbox.OutboxRecord;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * M02 Группа 8 — contract test: успешный checkin → attendance.marked event
 * в outbox валидируется против event-schemas/attendance.marked.json.
 */
class AttendanceMarkedContractIT extends AbstractAttendanceIntegrationTest {

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @BeforeEach
    void setUp() {
        mongoTemplate.remove(new Query(), AttendanceDocument.class);
        redisTemplate.getConnectionFactory().getConnection().serverCommands().flushAll();
        Mockito.reset(scheduleGrpcClient, semesterCacheService, geofenceService);
    }

    @Test
    void successfulCheckin_publishesValidAttendanceMarkedEvent() throws Exception {
        String today = LocalDate.now(ZoneId.of("Europe/Moscow"))
                .format(DateTimeFormatter.ISO_LOCAL_DATE);
        LessonResponse activeLesson = LessonResponse.newBuilder()
                .setId(1L).setGroupId(10L).setSubjectId(5L).setDate(today)
                .setStartTime("00:00").setEndTime("23:59")
                .setIsGeoBlocked(false).setLessonNumber(1).setStatus("active")
                .build();
        when(scheduleGrpcClient.getActiveLesson(anyLong(), anyString())).thenReturn(activeLesson);
        when(geofenceService.isWithinCampus(anyDouble(), anyDouble())).thenReturn(true);
        when(semesterCacheService.getActiveSemesterId()).thenReturn(1L);

        String body = objectMapper.writeValueAsString(new CheckinRequest(55.7558, 37.6173));
        mockMvc.perform(post("/attendance/checkin")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body)
                        .header("X-User-Id", "100")
                        .header("X-User-Role", "STUDENT")
                        .header("X-Group-Id", "10")
                        .header("X-Is-Headman", "false"))
                .andExpect(status().isCreated());

        Optional<OutboxRecord> marked = outboxStorage.findPending(100).stream()
                .filter(r -> "attendance.marked".equals(r.eventType()))
                .findFirst();
        assertThat(marked).as("attendance.marked должен быть в outbox").isPresent();

        Set<ValidationMessage> errors = EventSchemaValidator.validate(
                "attendance.marked.json", marked.get().payload());
        assertThat(errors)
                .as("attendance.marked payload должен соответствовать schema")
                .isEmpty();
    }
}
