package ru.rutcampustrack.academic.homework;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.context.ApplicationEventPublisher;
import ru.rutcampustrack.academic.contract.dto.homework.CreateHomeworkRequest;
import ru.rutcampustrack.academic.contract.dto.homework.UpdateHomeworkRequest;
import ru.rutcampustrack.academic.contract.enums.UserRole;
import ru.rutcampustrack.academic.entity.Homework;
import ru.rutcampustrack.academic.exception.AccessDeniedException;
import ru.rutcampustrack.academic.exception.BadRequestException;
import ru.rutcampustrack.academic.grpc.ScheduleGrpcClient;
import ru.rutcampustrack.academic.repository.HeadmanAssistantRepository;
import ru.rutcampustrack.academic.repository.HomeworkCompletionRepository;
import ru.rutcampustrack.academic.repository.HomeworkRepository;
import ru.rutcampustrack.academic.security.RequestContext;
import ru.rutcampustrack.schedule.grpc.LessonResponse;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Phase 61-03: unit tests for {@link HomeworkService} покрывающие D-03/D-04/D-05/D-06.
 *
 * <p>Детерминизм: {@code Clock.fixed("2026-05-01T09:00:00Z", UTC)} — зелёные тесты
 * не зависят от реальной календарной даты (pattern из других сервисов с Clock).
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class HomeworkServiceTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 5, 1);
    private static final LocalDate YESTERDAY = TODAY.minusDays(1);
    private static final LocalDate TOMORROW = TODAY.plusDays(1);

    private static final Long GROUP_ID = 10L;
    private static final Long SUBJECT_ID = 42L;
    private static final Long OTHER_SUBJECT_ID = 99L;
    private static final Long SEMESTER_ID = 5L;
    private static final Long HEADMAN_ID = 100L;
    private static final Long OTHER_USER_ID = 200L;
    private static final int LESSON_NUMBER = 3;

    @Mock private HomeworkRepository homeworkRepository;
    @Mock private HomeworkCompletionRepository completionRepository;
    @Mock private HeadmanAssistantRepository assistantRepository;
    @Mock private RequestContext requestContext;
    @Mock private ApplicationEventPublisher eventPublisher;
    @Mock private ScheduleGrpcClient scheduleGrpcClient;

    private Clock clock;

    private HomeworkService service;

    @BeforeEach
    void setUp() {
        clock = Clock.fixed(Instant.parse("2026-05-01T09:00:00Z"), ZoneOffset.UTC);
        service = new HomeworkService(
                homeworkRepository,
                completionRepository,
                assistantRepository,
                requestContext,
                eventPublisher,
                scheduleGrpcClient,
                clock);
    }

    // =========================================================================
    // D-06: только HEADMAN (STUDENT + is_headman=true) создаёт ДЗ
    // =========================================================================

    @Test
    void createHomework_throwsForbidden_whenNotHeadman() {
        when(requestContext.getRole()).thenReturn(UserRole.STUDENT);
        when(requestContext.isHeadman()).thenReturn(false);
        when(requestContext.getGroupId()).thenReturn(GROUP_ID);
        when(requestContext.getUserId()).thenReturn(OTHER_USER_ID);
        when(assistantRepository.findByGroupIdAndStudentId(anyLong(), anyLong()))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.createHomework(validRequest(TOMORROW)))
                .isInstanceOf(AccessDeniedException.class);

        verify(homeworkRepository, never()).save(any());
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void createHomework_throwsForbidden_whenAdmin() {
        // D-06: ADMIN больше не создаёт ДЗ — только staroста.
        when(requestContext.getRole()).thenReturn(UserRole.ADMIN);
        when(requestContext.isHeadman()).thenReturn(false);

        assertThatThrownBy(() -> service.createHomework(validRequest(TOMORROW)))
                .isInstanceOf(AccessDeniedException.class);

        verify(homeworkRepository, never()).save(any());
    }

    // =========================================================================
    // D-03: дата пары не может быть в прошлом
    // =========================================================================

    @Test
    void createHomework_throwsBadRequest_whenDateInPast() {
        stubHeadman();

        assertThatThrownBy(() -> service.createHomework(validRequest(YESTERDAY)))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("прошедш");

        verify(scheduleGrpcClient, never()).resolveLesson(anyLong(), any(), anyInt());
        verify(homeworkRepository, never()).save(any());
    }

    // =========================================================================
    // D-04: пара существует + subject matches
    // =========================================================================

    @Test
    void createHomework_throwsBadRequest_whenLessonNotFound() {
        stubHeadman();
        when(scheduleGrpcClient.resolveLesson(GROUP_ID, TOMORROW, LESSON_NUMBER))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.createHomework(validRequest(TOMORROW)))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("пары нет в расписании");

        verify(homeworkRepository, never()).save(any());
    }

    @Test
    void createHomework_throwsBadRequest_whenSubjectMismatch() {
        stubHeadman();
        when(scheduleGrpcClient.resolveLesson(GROUP_ID, TOMORROW, LESSON_NUMBER))
                .thenReturn(Optional.of(lessonWithSubject(OTHER_SUBJECT_ID)));

        assertThatThrownBy(() -> service.createHomework(validRequest(TOMORROW)))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("предмет");

        verify(homeworkRepository, never()).save(any());
    }

    // =========================================================================
    // Happy path
    // =========================================================================

    @Test
    void createHomework_persistsAndPublishesEvent_whenValid() {
        stubHeadman();
        when(scheduleGrpcClient.resolveLesson(GROUP_ID, TOMORROW, LESSON_NUMBER))
                .thenReturn(Optional.of(lessonWithSubject(SUBJECT_ID)));
        when(homeworkRepository.save(any(Homework.class))).thenAnswer(inv -> inv.getArgument(0));

        Homework saved = service.createHomework(validRequest(TOMORROW));

        assertThat(saved.getGroupId()).isEqualTo(GROUP_ID);
        assertThat(saved.getSubjectId()).isEqualTo(SUBJECT_ID);
        assertThat(saved.getPublishedBy()).isEqualTo(HEADMAN_ID);
        assertThat(saved.getLessonDate()).isEqualTo(TOMORROW);
        assertThat(saved.getLessonNumber()).isEqualTo(LESSON_NUMBER);
        verify(homeworkRepository).save(any(Homework.class));
        verify(eventPublisher).publishEvent(any());
    }

    // =========================================================================
    // D-05: только автор редактирует / удаляет
    // =========================================================================

    @Test
    void updateHomework_throwsForbidden_whenNotAuthor() {
        stubHeadman();
        Homework hw = existingHomework(OTHER_USER_ID);
        when(homeworkRepository.findById(1L)).thenReturn(Optional.of(hw));

        assertThatThrownBy(() -> service.updateHomework(1L,
                new UpdateHomeworkRequest("t", "d", null)))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("автор");

        verify(homeworkRepository, never()).save(any());
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void deleteHomework_throwsForbidden_whenNotAuthor() {
        stubHeadman();
        Homework hw = existingHomework(OTHER_USER_ID);
        when(homeworkRepository.findById(1L)).thenReturn(Optional.of(hw));

        assertThatThrownBy(() -> service.deleteHomework(1L))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("автор");

        verify(homeworkRepository, never()).delete(any());
    }

    @Test
    void updateHomework_succeeds_whenAuthor() {
        stubHeadman();
        Homework hw = existingHomework(HEADMAN_ID);
        when(homeworkRepository.findById(1L)).thenReturn(Optional.of(hw));
        when(homeworkRepository.save(any(Homework.class))).thenAnswer(inv -> inv.getArgument(0));

        Homework result = service.updateHomework(1L,
                new UpdateHomeworkRequest("new title", "new desc", "https://ex.com"));

        assertThat(result.getTitle()).isEqualTo("new title");
        assertThat(result.getDescription()).isEqualTo("new desc");
        assertThat(result.getLink()).isEqualTo("https://ex.com");
        verify(homeworkRepository).save(any(Homework.class));
        verify(eventPublisher).publishEvent(any());
    }

    @Test
    void deleteHomework_succeeds_whenAuthor() {
        stubHeadman();
        Homework hw = existingHomework(HEADMAN_ID);
        when(homeworkRepository.findById(1L)).thenReturn(Optional.of(hw));

        service.deleteHomework(1L);

        verify(homeworkRepository).delete(hw);
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private void stubHeadman() {
        when(requestContext.getRole()).thenReturn(UserRole.STUDENT);
        when(requestContext.isHeadman()).thenReturn(true);
        when(requestContext.getUserId()).thenReturn(HEADMAN_ID);
        when(requestContext.getGroupId()).thenReturn(GROUP_ID);
    }

    private CreateHomeworkRequest validRequest(LocalDate date) {
        return new CreateHomeworkRequest(
                "Title", "description", null,
                SUBJECT_ID, GROUP_ID, SEMESTER_ID,
                date, LESSON_NUMBER);
    }

    private LessonResponse lessonWithSubject(Long subjectId) {
        return LessonResponse.newBuilder()
                .setGroupId(GROUP_ID)
                .setSubjectId(subjectId)
                .setLessonNumber(LESSON_NUMBER)
                .setStatus("planned")
                .build();
    }

    private Homework existingHomework(Long publishedBy) {
        return new Homework(
                GROUP_ID, SUBJECT_ID, SEMESTER_ID,
                "old", "desc", null, publishedBy,
                TOMORROW, LESSON_NUMBER);
    }
}
