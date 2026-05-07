package ru.rutcampustrack.academic.homework;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;
import ru.rutcampustrack.academic.contract.enums.AccountStatus;
import ru.rutcampustrack.academic.contract.enums.SubjectType;
import ru.rutcampustrack.academic.contract.enums.UserRole;
import ru.rutcampustrack.academic.entity.Group;
import ru.rutcampustrack.academic.entity.Homework;
import ru.rutcampustrack.academic.entity.HomeworkCompletion;
import ru.rutcampustrack.academic.entity.HomeworkWeeklyDigestRun;
import ru.rutcampustrack.academic.entity.Semester;
import ru.rutcampustrack.academic.entity.Subject;
import ru.rutcampustrack.academic.entity.User;
import ru.rutcampustrack.academic.event.DomainEvent;
import ru.rutcampustrack.academic.event.HomeworkDueReminderEvent;
import ru.rutcampustrack.academic.event.HomeworkNotificationItem;
import ru.rutcampustrack.academic.event.HomeworkWeeklyDigestEvent;
import ru.rutcampustrack.academic.repository.GroupRepository;
import ru.rutcampustrack.academic.repository.HomeworkCompletionRepository;
import ru.rutcampustrack.academic.repository.HomeworkRepository;
import ru.rutcampustrack.academic.repository.HomeworkWeeklyDigestRunRepository;
import ru.rutcampustrack.academic.repository.SemesterRepository;
import ru.rutcampustrack.academic.repository.SubjectRepository;
import ru.rutcampustrack.academic.repository.UserRepository;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class HomeworkNotificationJobTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 5, 3);
    private static final LocalDate DUE_DATE = LocalDate.of(2026, 5, 5);
    private static final LocalDate NEXT_MONDAY = LocalDate.of(2026, 5, 4);
    private static final Long SEMESTER_ID = 7L;
    private static final Long GROUP_ID = 10L;
    private static final Long SUBJECT_ID = 42L;
    private static final Long OTHER_SUBJECT_ID = 43L;

    @Mock private HomeworkRepository homeworkRepository;
    @Mock private HomeworkCompletionRepository completionRepository;
    @Mock private HomeworkWeeklyDigestRunRepository digestRunRepository;
    @Mock private SemesterRepository semesterRepository;
    @Mock private GroupRepository groupRepository;
    @Mock private UserRepository userRepository;
    @Mock private SubjectRepository subjectRepository;
    @Mock private ApplicationEventPublisher eventPublisher;

    private HomeworkNotificationJob job;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(Instant.parse("2026-05-03T06:00:00Z"), ZoneOffset.UTC);
        job = new HomeworkNotificationJob(
                homeworkRepository,
                completionRepository,
                digestRunRepository,
                semesterRepository,
                groupRepository,
                userRepository,
                subjectRepository,
                eventPublisher,
                clock);
        when(semesterRepository.findByIsActiveTrue()).thenReturn(Optional.of(activeSemester()));
        when(groupRepository.findAllByIsActiveTrue()).thenReturn(List.of(group(GROUP_ID)));
        when(subjectRepository.findAllById(any())).thenReturn(List.of(
                subject(SUBJECT_ID, "Math"),
                subject(OTHER_SUBJECT_ID, "History")));
    }

    @Test
    void publishDueReminders_sendsOnlyToIncompleteStudentsAndMarksHomeworkSent() {
        Homework homework = homework(100L, SUBJECT_ID, DUE_DATE, 2, "Essay");
        when(homeworkRepository.findBySemesterIdAndLessonDateAndDueReminderSentAtIsNullOrderByGroupIdAscLessonNumberAscIdAsc(
                SEMESTER_ID, DUE_DATE))
                .thenReturn(List.of(homework));
        when(userRepository.findByGroupId(GROUP_ID)).thenReturn(List.of(
                student(1L),
                student(2L),
                user(3L, UserRole.TEACHER)));
        when(completionRepository.findByHomeworkId(100L))
                .thenReturn(List.of(new HomeworkCompletion(100L, 1L)));

        job.publishDueReminders();

        ArgumentCaptor<ApplicationEvent> eventCaptor = ArgumentCaptor.forClass(ApplicationEvent.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());

        DomainEvent event = (DomainEvent) eventCaptor.getValue();
        assertThat(event.getEventType()).isEqualTo("homework.due_reminder");

        HomeworkDueReminderEvent.Payload payload = (HomeworkDueReminderEvent.Payload) event.getPayload();
        assertThat(payload.userId()).isEqualTo(2L);
        assertThat(payload.groupId()).isEqualTo(GROUP_ID);
        assertThat(payload.semesterId()).isEqualTo(SEMESTER_ID);
        assertThat(payload.daysBeforeDue()).isEqualTo(2);
        assertThat(payload.dueDate()).isEqualTo("2026-05-05");
        assertThat(payload.homework().homeworkId()).isEqualTo(100L);
        assertThat(payload.homework().subjectName()).isEqualTo("Math");

        assertThat(homework.getDueReminderSentAt()).isEqualTo(OffsetDateTime.parse("2026-05-03T06:00:00Z"));
        verify(homeworkRepository).saveAll(List.of(homework));
    }

    @Test
    void publishWeeklyDigest_sendsOneDigestPerStudentWithOnlyIncompleteHomework() {
        Homework first = homework(100L, SUBJECT_ID, NEXT_MONDAY, 1, "Essay");
        Homework second = homework(200L, OTHER_SUBJECT_ID, NEXT_MONDAY.plusDays(1), 2, "Read");
        List<Homework> weekHomeworks = List.of(first, second);
        when(digestRunRepository.existsByGroupIdAndWeekStart(GROUP_ID, NEXT_MONDAY)).thenReturn(false);
        when(homeworkRepository.findByGroupIdAndSemesterIdAndLessonDateBetweenOrderByLessonDateAscLessonNumberAscIdAsc(
                GROUP_ID, SEMESTER_ID, NEXT_MONDAY, NEXT_MONDAY.plusDays(6)))
                .thenReturn(weekHomeworks);
        when(userRepository.findByGroupId(GROUP_ID)).thenReturn(List.of(student(1L), student(2L)));
        when(completionRepository.findByHomeworkIdInAndStudentId(eq(List.of(100L, 200L)), eq(1L)))
                .thenReturn(List.of(new HomeworkCompletion(100L, 1L)));
        when(completionRepository.findByHomeworkIdInAndStudentId(eq(List.of(100L, 200L)), eq(2L)))
                .thenReturn(List.of());

        job.publishWeeklyDigest();

        ArgumentCaptor<ApplicationEvent> eventCaptor = ArgumentCaptor.forClass(ApplicationEvent.class);
        verify(eventPublisher, times(2)).publishEvent(eventCaptor.capture());

        List<HomeworkWeeklyDigestEvent.Payload> payloads = eventCaptor.getAllValues().stream()
                .map(DomainEvent.class::cast)
                .peek(event -> assertThat(event.getEventType()).isEqualTo("homework.weekly_digest"))
                .map(event -> (HomeworkWeeklyDigestEvent.Payload) event.getPayload())
                .toList();

        HomeworkWeeklyDigestEvent.Payload firstStudentPayload = payloadForUser(payloads, 1L);
        assertThat(firstStudentPayload.weekStart()).isEqualTo("2026-05-04");
        assertThat(firstStudentPayload.weekEnd()).isEqualTo("2026-05-10");
        assertThat(firstStudentPayload.totalCount()).isEqualTo(1);
        assertThat(firstStudentPayload.items())
                .extracting(HomeworkNotificationItem::homeworkId)
                .containsExactly(200L);

        HomeworkWeeklyDigestEvent.Payload secondStudentPayload = payloadForUser(payloads, 2L);
        assertThat(secondStudentPayload.totalCount()).isEqualTo(2);
        assertThat(secondStudentPayload.items())
                .extracting(HomeworkNotificationItem::homeworkId)
                .containsExactly(100L, 200L);

        ArgumentCaptor<HomeworkWeeklyDigestRun> runCaptor = ArgumentCaptor.forClass(HomeworkWeeklyDigestRun.class);
        verify(digestRunRepository).save(runCaptor.capture());
        assertThat(runCaptor.getValue().getGroupId()).isEqualTo(GROUP_ID);
        assertThat(runCaptor.getValue().getSemesterId()).isEqualTo(SEMESTER_ID);
        assertThat(runCaptor.getValue().getWeekStart()).isEqualTo(NEXT_MONDAY);
    }

    @Test
    void publishDueReminders_skipsWhenActiveSemesterIsOutOfRange() {
        Semester inactiveForToday = activeSemester();
        inactiveForToday.setDateFrom(TODAY.plusDays(1));
        when(semesterRepository.findByIsActiveTrue()).thenReturn(Optional.of(inactiveForToday));

        job.publishDueReminders();

        verify(homeworkRepository, never())
                .findBySemesterIdAndLessonDateAndDueReminderSentAtIsNullOrderByGroupIdAscLessonNumberAscIdAsc(
                        any(), any());
        verify(eventPublisher, never()).publishEvent(any());
    }

    private static HomeworkWeeklyDigestEvent.Payload payloadForUser(List<HomeworkWeeklyDigestEvent.Payload> payloads,
                                                                    Long userId) {
        return payloads.stream()
                .filter(payload -> userId.equals(payload.userId()))
                .findFirst()
                .orElseThrow();
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

    private static Group group(Long id) {
        Group group = new Group();
        ReflectionTestUtils.setField(group, "id", id);
        group.setName("UIT-311");
        group.setActive(true);
        return group;
    }

    private static Subject subject(Long id, String name) {
        Subject subject = new Subject();
        ReflectionTestUtils.setField(subject, "id", id);
        subject.setName(name);
        subject.setType(SubjectType.PRACTICE);
        subject.setGroupId(GROUP_ID);
        return subject;
    }

    private static Homework homework(Long id, Long subjectId, LocalDate lessonDate, int lessonNumber, String title) {
        Homework homework = new Homework(
                GROUP_ID,
                subjectId,
                SEMESTER_ID,
                title,
                "Description",
                "https://example.test/homework",
                99L,
                lessonDate,
                lessonNumber);
        ReflectionTestUtils.setField(homework, "id", id);
        return homework;
    }

    private static User student(Long id) {
        return user(id, UserRole.STUDENT);
    }

    private static User user(Long id, UserRole role) {
        User user = new User();
        ReflectionTestUtils.setField(user, "id", id);
        user.setLogin("user" + id);
        user.setLastName("User");
        user.setFirstName(String.valueOf(id));
        user.setRole(role);
        user.setStatus(AccountStatus.ACTIVE);
        user.setGroupId(GROUP_ID);
        return user;
    }
}
