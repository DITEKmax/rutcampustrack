package ru.rutcampustrack.schedule.events;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.networknt.schema.ValidationMessage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import ru.rutcampustrack.schedule.contract.dto.lesson.CancelLessonRequest;
import ru.rutcampustrack.schedule.contract.enums.LessonStatus;
import ru.rutcampustrack.schedule.contract.enums.UserRole;
import ru.rutcampustrack.schedule.contract.enums.WeekType;
import ru.rutcampustrack.schedule.grpc.AcademicGrpcClient;
import ru.rutcampustrack.schedule.integration.AbstractScheduleIntegrationTest;
import ru.rutcampustrack.schedule.item.entity.ScheduleItem;
import ru.rutcampustrack.schedule.item.repository.ScheduleItemRepository;
import ru.rutcampustrack.schedule.lesson.LessonService;
import ru.rutcampustrack.schedule.lesson.entity.Lesson;
import ru.rutcampustrack.schedule.lesson.repository.LessonRepository;
import ru.rutcampustrack.schedule.security.RequestContext;
import ru.rutcampustrack.shared.outbox.OutboxRecord;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.Set;

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
    ScheduleItemRepository scheduleItemRepository;

    @MockitoBean
    AcademicGrpcClient academicGrpcClient;

    @MockitoBean
    RequestContext requestContext;

    private final ObjectMapper mapper = new ObjectMapper();

    @AfterEach
    void cleanup() {
        lessonRepository.deleteAll();
        scheduleItemRepository.deleteAll();
        drainOutbox();
    }

    @Test
    void cancelLesson_publishesValidLessonCancelledEvent() throws Exception {
        ScheduleItem item = createScheduleItem();
        Lesson lesson = createLesson(item.getId());

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
        assertThat(payload.get("start_time").asText()).isEqualTo("10:10");
        assertThat(payload.get("end_time").asText()).isEqualTo("11:45");
        assertThat(payload.get("lesson_number").asInt()).isEqualTo(2);
        assertThat(payload.get("cancel_reason").asText()).isEqualTo("Preparation day");
        assertThat(payload.get("cancelled_by").asLong()).isEqualTo(adminId);
        assertThat(payload.get("cancelled_at").asText()).isNotBlank();

        // Entity state тоже должен быть обновлён (source of truth для БД).
        Lesson reloaded = lessonRepository.findById(lesson.getId()).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(LessonStatus.CANCELLED);
        assertThat(reloaded.getCancelledBy()).isEqualTo(adminId);
        assertThat(reloaded.getCancelledAt()).isNotNull();
    }

    private ScheduleItem createScheduleItem() {
        ScheduleItem item = new ScheduleItem();
        item.setGroupId(10L);
        item.setSubjectId(200L);
        item.setSemesterId(1L);
        item.setDayOfWeek((short) 5);
        item.setLessonNumber((short) 2);
        item.setStartTime(LocalTime.of(10, 10));
        item.setEndTime(LocalTime.of(11, 45));
        item.setWeekType(WeekType.ALL);
        item.setRoom("B-202");
        item.setActive(true);
        item.setCreatedAt(OffsetDateTime.now());
        return scheduleItemRepository.save(item);
    }

    private Lesson createLesson(Long itemId) {
        Lesson lesson = new Lesson();
        lesson.setScheduleItemId(itemId);
        lesson.setDate(LocalDate.of(2026, 4, 3));
        lesson.setStatus(LessonStatus.PLANNED);
        lesson.setCreatedAt(OffsetDateTime.now());
        return lessonRepository.save(lesson);
    }
}
