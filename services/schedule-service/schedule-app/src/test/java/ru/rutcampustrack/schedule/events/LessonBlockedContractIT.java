package ru.rutcampustrack.schedule.events;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.networknt.schema.ValidationMessage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
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
 * Contract test: blockLessonByHeadman() -> lesson.blocked outbox event.
 */
class LessonBlockedContractIT extends AbstractScheduleIntegrationTest {

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
    void blockLesson_publishesValidLessonBlockedEvent() throws Exception {
        ScheduleItem item = createScheduleItem();
        Lesson lesson = createLesson(item.getId());

        Long headmanId = 77L;
        when(requestContext.getRole()).thenReturn(UserRole.STUDENT);
        when(requestContext.getUserId()).thenReturn(headmanId);
        when(requestContext.isHeadman()).thenReturn(true);
        when(academicGrpcClient.isHeadman(headmanId, 10L)).thenReturn(true);

        lessonService.blockLessonByHeadman(lesson.getId());

        Optional<OutboxRecord> blocked = outboxStorage.findPending(100).stream()
                .filter(r -> "lesson.blocked".equals(r.eventType()))
                .findFirst();
        assertThat(blocked).isPresent();

        Set<ValidationMessage> errors = EventSchemaValidator.validate(
                "lesson.blocked.json", blocked.get().payload());
        assertThat(errors)
                .as("lesson.blocked payload должен соответствовать schema")
                .isEmpty();

        JsonNode payload = mapper.readTree(blocked.get().payload()).get("payload");
        assertThat(payload.get("lesson_id").asLong()).isEqualTo(lesson.getId());
        assertThat(payload.get("group_id").asLong()).isEqualTo(10L);
        assertThat(payload.get("subject_id").asLong()).isEqualTo(200L);
        assertThat(payload.get("date").asText()).isEqualTo("2026-05-20");
        assertThat(payload.get("start_time").asText()).isEqualTo("12:20");
        assertThat(payload.get("end_time").asText()).isEqualTo("13:50");
        assertThat(payload.get("lesson_number").asInt()).isEqualTo(3);
        assertThat(payload.get("room").asText()).isEqualTo("B-303");
        assertThat(payload.get("blocked_by").asLong()).isEqualTo(headmanId);
        assertThat(payload.get("blocked_at").asText()).isNotBlank();

        Lesson reloaded = lessonRepository.findById(lesson.getId()).orElseThrow();
        assertThat(reloaded.isBlockedByHeadman()).isTrue();
        assertThat(reloaded.getBlockedByUserId()).isEqualTo(headmanId);
        assertThat(reloaded.getBlockedAt()).isNotNull();
    }

    private ScheduleItem createScheduleItem() {
        ScheduleItem item = new ScheduleItem();
        item.setGroupId(10L);
        item.setSubjectId(200L);
        item.setSemesterId(1L);
        item.setDayOfWeek((short) 3);
        item.setLessonNumber((short) 3);
        item.setStartTime(LocalTime.of(12, 20));
        item.setEndTime(LocalTime.of(13, 50));
        item.setWeekType(WeekType.ALL);
        item.setRoom("B-303");
        item.setActive(true);
        item.setCreatedAt(OffsetDateTime.now());
        return scheduleItemRepository.save(item);
    }

    private Lesson createLesson(Long itemId) {
        Lesson lesson = new Lesson();
        lesson.setScheduleItemId(itemId);
        lesson.setDate(LocalDate.of(2026, 5, 20));
        lesson.setStatus(LessonStatus.PLANNED);
        lesson.setCreatedAt(OffsetDateTime.now());
        return lessonRepository.save(lesson);
    }
}
