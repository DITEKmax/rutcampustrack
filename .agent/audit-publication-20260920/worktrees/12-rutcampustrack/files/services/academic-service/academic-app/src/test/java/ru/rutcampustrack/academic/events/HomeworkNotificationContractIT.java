package ru.rutcampustrack.academic.events;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.networknt.schema.ValidationMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import ru.rutcampustrack.academic.contract.enums.AccountStatus;
import ru.rutcampustrack.academic.contract.enums.SubjectType;
import ru.rutcampustrack.academic.contract.enums.UserRole;
import ru.rutcampustrack.academic.entity.Group;
import ru.rutcampustrack.academic.entity.Homework;
import ru.rutcampustrack.academic.entity.HomeworkCompletion;
import ru.rutcampustrack.academic.entity.Semester;
import ru.rutcampustrack.academic.entity.Subject;
import ru.rutcampustrack.academic.entity.User;
import ru.rutcampustrack.academic.homework.HomeworkNotificationJob;
import ru.rutcampustrack.academic.integration.AbstractAcademicEventIntegrationTest;
import ru.rutcampustrack.academic.repository.GroupRepository;
import ru.rutcampustrack.academic.repository.HomeworkCompletionRepository;
import ru.rutcampustrack.academic.repository.HomeworkRepository;
import ru.rutcampustrack.academic.repository.HomeworkWeeklyDigestRunRepository;
import ru.rutcampustrack.academic.repository.SemesterRepository;
import ru.rutcampustrack.academic.repository.SubjectRepository;
import ru.rutcampustrack.academic.repository.UserRepository;
import ru.rutcampustrack.shared.outbox.OutboxRecord;
import ru.rutcampustrack.shared.outbox.OutboxStorage;

import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * Verifies that the real scheduled homework producer is serialized by the
 * academic outbox listener into both published notification schemas.
 */
class HomeworkNotificationContractIT extends AbstractAcademicEventIntegrationTest {

    private static final long STUDENT_ID = 1701L;
    private static final long GROUP_ID = 141L;
    private static final long SEMESTER_ID = 19L;
    private static final long DUE_HOMEWORK_ID = 9101L;
    private static final long COMPLETED_HOMEWORK_ID = 9102L;
    private static final long PENDING_HOMEWORK_ID = 9103L;
    private static final long SUBJECT_ID = 551L;
    private static final long OTHER_SUBJECT_ID = 552L;
    private static final Instant NOW = Instant.parse("2026-03-05T10:00:00Z");
    private static final LocalDate TODAY = LocalDate.of(2026, 3, 5);

    @Autowired
    private HomeworkNotificationJob notificationJob;

    @Autowired
    private OutboxStorage outboxStorage;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private HomeworkRepository homeworkRepository;

    @MockitoBean
    private HomeworkCompletionRepository completionRepository;

    @MockitoBean
    private HomeworkWeeklyDigestRunRepository digestRunRepository;

    @MockitoBean
    private SemesterRepository semesterRepository;

    @MockitoBean
    private GroupRepository groupRepository;

    @MockitoBean
    private UserRepository userRepository;

    @MockitoBean
    private SubjectRepository subjectRepository;

    @MockitoBean(name = "clock")
    private Clock clock;

    @BeforeEach
    void useStableProducerClock() {
        when(clock.instant()).thenReturn(NOW);
        when(clock.getZone()).thenReturn(ZoneOffset.UTC);
        when(clock.withZone(any(ZoneId.class)))
                .thenAnswer(invocation -> Clock.fixed(NOW, invocation.getArgument(0)));
    }

    @Test
    void dueReminder_serializesRealProducerOutputAgainstSchema() throws Exception {
        Semester semester = activeSemester();
        Group group = activeGroup();
        Subject subject = subject(SUBJECT_ID, "Math");
        Homework homework = homework(DUE_HOMEWORK_ID, SUBJECT_ID, TODAY.plusDays(2), 2, "Essay");
        User student = student(STUDENT_ID);

        when(semesterRepository.findByIsActiveTrue()).thenReturn(Optional.of(semester));
        when(groupRepository.findAllByIsActiveTrue()).thenReturn(List.of(group));
        when(homeworkRepository
                .findBySemesterIdAndLessonDateAndDueReminderSentAtIsNullOrderByGroupIdAscLessonNumberAscIdAsc(
                        SEMESTER_ID, TODAY.plusDays(2)))
                .thenReturn(List.of(homework));
        when(subjectRepository.findAllById(anySet())).thenReturn(List.of(subject));
        when(userRepository.findByGroupId(GROUP_ID)).thenReturn(List.of(student));
        when(completionRepository.findByHomeworkId(DUE_HOMEWORK_ID)).thenReturn(List.of());

        notificationJob.publishDueReminders();

        JsonNode envelope = pendingEvent("homework.due_reminder");
        assertThat(envelope.path("event_type").asText()).isEqualTo("homework.due_reminder");
        assertThat(envelope.path("source").asText()).isEqualTo("academic-service");
        assertThat(envelope.path("event_id").isTextual()).isTrue();
        assertThat(envelope.path("trace_id").isTextual()).isTrue();
        assertThat(envelope.path("payload").path("user_id").asLong()).isEqualTo(STUDENT_ID);
        assertThat(envelope.path("payload").path("due_date").asText()).isEqualTo("2026-03-07");
        assertThat(envelope.path("payload").path("homework").path("homework_id").asLong())
                .isEqualTo(DUE_HOMEWORK_ID);
        assertSchema("homework.due_reminder.json", envelope);
    }

    @Test
    void weeklyDigest_serializesRealProducerOutputAndRecipientItemsAgainstSchema() throws Exception {
        Semester semester = activeSemester();
        Group group = activeGroup();
        Subject math = subject(SUBJECT_ID, "Math");
        Subject history = subject(OTHER_SUBJECT_ID, "History");
        Homework completed = homework(COMPLETED_HOMEWORK_ID, SUBJECT_ID, TODAY.plusDays(4), 1, "Read");
        Homework pending = homework(PENDING_HOMEWORK_ID, OTHER_SUBJECT_ID, TODAY.plusDays(5), 2, "Essay");
        User student = student(STUDENT_ID);
        LocalDate weekStart = LocalDate.of(2026, 3, 9);
        LocalDate weekEnd = LocalDate.of(2026, 3, 15);

        when(semesterRepository.findByIsActiveTrue()).thenReturn(Optional.of(semester));
        when(groupRepository.findAllByIsActiveTrue()).thenReturn(List.of(group));
        when(digestRunRepository.existsByGroupIdAndWeekStart(GROUP_ID, weekStart)).thenReturn(false);
        when(homeworkRepository
                .findByGroupIdAndSemesterIdAndLessonDateBetweenOrderByLessonDateAscLessonNumberAscIdAsc(
                        GROUP_ID, SEMESTER_ID, weekStart, weekEnd))
                .thenReturn(List.of(completed, pending));
        when(subjectRepository.findAllById(anySet())).thenReturn(List.of(math, history));
        when(userRepository.findByGroupId(GROUP_ID)).thenReturn(List.of(student));
        when(completionRepository.findByHomeworkIdInAndStudentId(
                eq(List.of(COMPLETED_HOMEWORK_ID, PENDING_HOMEWORK_ID)), eq(STUDENT_ID)))
                .thenReturn(List.of(new HomeworkCompletion(COMPLETED_HOMEWORK_ID, STUDENT_ID)));

        notificationJob.publishWeeklyDigest();

        JsonNode envelope = pendingEvent("homework.weekly_digest");
        assertThat(envelope.path("event_type").asText()).isEqualTo("homework.weekly_digest");
        assertThat(envelope.path("source").asText()).isEqualTo("academic-service");
        assertThat(envelope.path("payload").path("user_id").asLong()).isEqualTo(STUDENT_ID);
        assertThat(envelope.path("payload").path("week_start").asText()).isEqualTo("2026-03-09");
        assertThat(envelope.path("payload").path("week_end").asText()).isEqualTo("2026-03-15");
        assertThat(envelope.path("payload").path("total_count").asInt()).isEqualTo(1);
        assertThat(envelope.path("payload").path("items")).hasSize(1);
        assertThat(envelope.path("payload").path("items").get(0).path("homework_id").asLong())
                .isEqualTo(PENDING_HOMEWORK_ID);
        assertSchema("homework.weekly_digest.json", envelope);
    }

    private JsonNode pendingEvent(String eventType) throws IOException {
        List<OutboxRecord> records = outboxStorage.findPending(1000).stream()
                .filter(record -> eventType.equals(record.eventType()))
                .toList();
        assertThat(records).as("real producer should leave one %s event in outbox", eventType)
                .hasSize(1);
        return objectMapper.readTree(records.get(0).payload());
    }

    private static void assertSchema(String schema, JsonNode envelope) throws IOException {
        Set<ValidationMessage> errors = EventSchemaValidator.validate(schema, envelope.toString());
        assertThat(errors).as("serialized producer output must satisfy %s", schema).isEmpty();
    }

    private static Semester activeSemester() {
        Semester semester = new Semester();
        ReflectionTestUtils.setField(semester, "id", SEMESTER_ID);
        semester.setName("Spring 2026");
        semester.setDateFrom(TODAY.minusDays(30));
        semester.setDateTo(TODAY.plusDays(30));
        semester.setActive(true);
        return semester;
    }

    private static Group activeGroup() {
        Group group = new Group();
        ReflectionTestUtils.setField(group, "id", GROUP_ID);
        group.setName("Тгп-141");
        group.setActive(true);
        return group;
    }

    private static Subject subject(long id, String name) {
        Subject subject = new Subject();
        ReflectionTestUtils.setField(subject, "id", id);
        subject.setName(name);
        subject.setType(SubjectType.PRACTICE);
        subject.setGroupId(GROUP_ID);
        return subject;
    }

    private static Homework homework(long id, long subjectId, LocalDate lessonDate,
                                     int lessonNumber, String title) {
        Homework homework = new Homework(
                GROUP_ID, subjectId, SEMESTER_ID, title, "Description", "https://example.test/homework",
                STUDENT_ID, lessonDate, lessonNumber);
        ReflectionTestUtils.setField(homework, "id", id);
        return homework;
    }

    private static User student(long id) {
        User user = new User();
        ReflectionTestUtils.setField(user, "id", id);
        user.setLogin("student" + id);
        user.setLastName("Student");
        user.setFirstName("Test");
        user.setRole(UserRole.STUDENT);
        user.setStatus(AccountStatus.ACTIVE);
        user.setGroupId(GROUP_ID);
        return user;
    }
}
