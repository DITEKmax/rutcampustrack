package ru.rutcampustrack.schedule.events;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.networknt.schema.ValidationMessage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import ru.rutcampustrack.schedule.contract.dto.lesson.CancelLessonRequest;
import ru.rutcampustrack.schedule.contract.dto.item.CreateScheduleItemRequest;
import ru.rutcampustrack.academic.grpc.SemesterStateResponse;
import ru.rutcampustrack.schedule.contract.enums.LessonStatus;
import ru.rutcampustrack.schedule.contract.enums.UserRole;
import ru.rutcampustrack.schedule.contract.enums.WeekType;
import ru.rutcampustrack.schedule.grpc.AcademicGrpcClient;
import ru.rutcampustrack.schedule.integration.AbstractScheduleIntegrationTest;
import ru.rutcampustrack.schedule.recurring.RecurringAssignmentAuthority;
import ru.rutcampustrack.schedule.recurring.RecurringScheduleItemWriter;
import ru.rutcampustrack.schedule.lesson.LessonService;
import ru.rutcampustrack.schedule.lesson.entity.Lesson;
import ru.rutcampustrack.schedule.lesson.repository.LessonRepository;
import ru.rutcampustrack.schedule.security.RequestContext;
import ru.rutcampustrack.shared.outbox.OutboxRecord;

import java.time.LocalDate;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * M02 Группа 8 / M09 G5 — contract test: cancelLesson() → lesson.cancelled
 * event в outbox валидируется против event-schemas/lesson.cancelled.json
 * и содержит full snapshot (start_time/end_time/lesson_number/cancelled_by/
 * cancelled_at) дополнительно к базовым ключам.
 */
class LessonCancelledContractIT extends AbstractScheduleIntegrationTest {

    @Autowired
    LessonService lessonService;

    @Autowired
    LessonRepository lessonRepository;

    @Autowired
    RecurringScheduleItemWriter writer;

    @MockitoBean
    AcademicGrpcClient academicGrpcClient;

    @MockitoBean
    RequestContext requestContext;

    private final ObjectMapper mapper = new ObjectMapper();

    @AfterEach
    void cleanup() {
        resetScheduleData();
    }

    @Test
    void cancelLesson_publishesValidLessonCancelledEvent() throws Exception {
        resetScheduleData();
        Lesson lesson = createLesson();

        Long adminId = 999L;
        when(requestContext.getRole()).thenReturn(UserRole.ADMIN);
        when(requestContext.getUserId()).thenReturn(adminId);
        when(requestContext.isHeadman()).thenReturn(false);

        lessonService.cancelLesson(lesson.getId(), new CancelLessonRequest("Preparation day"));

        Optional<OutboxRecord> cancelled = outboxStorage.findPending(100).stream()
                .filter(r -> "lesson.cancelled".equals(r.eventType()))
                .findFirst();
        assertThat(cancelled).isPresent();

        Set<ValidationMessage> errors = EventSchemaValidator.validate(
                "lesson.cancelled.json", cancelled.get().payload());
        assertThat(errors)
                .as("lesson.cancelled payload должен соответствовать schema")
                .isEmpty();

        // M09 G5 — full snapshot: bot/attendance не должны делать second-look
        // gRPC в schedule; start_time/end_time/lesson_number/cancelled_by/at
        // приходят в том же сообщении.
        JsonNode root = mapper.readTree(cancelled.get().payload());
        JsonNode payload = root.get("payload");
        assertThat(payload.get("lesson_id").asLong()).isEqualTo(lesson.getId());
        assertThat(payload.get("group_id").asLong()).isEqualTo(10L);
        assertThat(payload.get("subject_id").asLong()).isEqualTo(200L);
        assertThat(payload.get("date").asText()).isEqualTo("2026-04-03");
        assertThat(payload.get("start_time").asText()).isEqualTo("10:05");
        assertThat(payload.get("end_time").asText()).isEqualTo("11:25");
        assertThat(payload.get("lesson_number").asInt()).isEqualTo(2);
        assertThat(payload.get("cancel_reason").asText()).isEqualTo("Preparation day");
        assertThat(payload.get("cancelled_by").asLong()).isEqualTo(adminId);
        assertThat(payload.get("cancelled_at").asText()).isNotBlank();
        assertThat(payload.path("assignment_id").asLong()).isEqualTo(501L);
        assertThat(payload.path("lesson_type").asText()).isEqualTo("lecture");
        assertThat(payload.path("lesson_date").asText()).isEqualTo("2026-04-03");
        assertThat(payload.path("actor_user_id").asLong()).isEqualTo(adminId);

        // Entity state тоже должен быть обновлён (source of truth для БД).
        Lesson reloaded = lessonRepository.findById(lesson.getId()).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(LessonStatus.CANCELLED);
        assertThat(reloaded.getCancelledBy()).isEqualTo(adminId);
        assertThat(reloaded.getCancelledAt()).isNotNull();
    }

    private Lesson createLesson() {
        LocalDate date = LocalDate.of(2026, 4, 3);
        when(academicGrpcClient.getSemesterArchiveAuthorityState(1L))
                .thenReturn(SemesterStateResponse.newBuilder().setId(1L).build());
        var authority = new RecurringAssignmentAuthority(501L, 700L, 200L, 10L, 1L,
                "lecture", date, date.plusDays(1));
        var created = writer.write(new CreateScheduleItemRequest(501L, 10L, 200L, 1L,
                (short) 5, (short) 2, null, null, WeekType.ALL, "B-202"), UUID.randomUUID(), 999L,
                authority, date, date);
        Long lessonId = jdbcTemplate.queryForObject("SELECT id FROM lessons WHERE schedule_item_id = ?",
                Long.class, created.scheduleItemId());
        return lessonRepository.findById(lessonId).orElseThrow();
    }
}
