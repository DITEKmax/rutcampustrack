package ru.rutcampustrack.schedule.recurring;

import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import ru.rutcampustrack.schedule.contract.dto.item.UpdateScheduleItemRequest;
import ru.rutcampustrack.schedule.contract.enums.LessonStatus;
import ru.rutcampustrack.schedule.contract.enums.UserRole;
import ru.rutcampustrack.schedule.contract.enums.WeekType;
import ru.rutcampustrack.schedule.exception.RecurringLifecycleNotReadyException;
import ru.rutcampustrack.schedule.grpc.AcademicGrpcClient;
import ru.rutcampustrack.schedule.item.ScheduleItemService;
import ru.rutcampustrack.schedule.item.entity.ScheduleItem;
import ru.rutcampustrack.schedule.item.repository.ScheduleItemRepository;
import ru.rutcampustrack.schedule.lesson.LessonGenerationService;
import ru.rutcampustrack.schedule.lesson.LessonService;
import ru.rutcampustrack.schedule.lesson.entity.Lesson;
import ru.rutcampustrack.schedule.lesson.repository.LessonRepository;
import ru.rutcampustrack.schedule.oneoff.repository.OneOffLessonRepository;
import ru.rutcampustrack.schedule.security.RequestContext;
import ru.rutcampustrack.schedule.subject.SubjectDeletedCascadeService;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RecurringLifecycleGateTest {

    @Test
    void updateAndDeleteAreGatedAfterAuthorizationWithoutMutation() {
        ScheduleItemRepository items = mock(ScheduleItemRepository.class);
        AcademicGrpcClient academic = mock(AcademicGrpcClient.class);
        RequestContext context = mock(RequestContext.class);
        LessonGenerationService generation = mock(LessonGenerationService.class);
        ScheduleItem item = scheduleItem(7L);
        when(items.findById(7L)).thenReturn(Optional.of(item));
        when(context.getRole()).thenReturn(UserRole.STUDENT);
        when(context.isHeadman()).thenReturn(true);
        when(context.getUserId()).thenReturn(42L);
        when(academic.isHeadman(42L, 10L)).thenReturn(true);

        ScheduleItemService service = new ScheduleItemService(
                items, academic, context, generation, Clock.systemUTC());

        UpdateScheduleItemRequest request = new UpdateScheduleItemRequest(
                20L, (short) 2, (short) 1, LocalTime.of(10, 0), LocalTime.of(11, 0),
                WeekType.ALL, "B-202");
        assertThatThrownBy(() -> service.updateScheduleItem(7L, request))
                .isInstanceOf(RecurringLifecycleNotReadyException.class);
        assertThatThrownBy(() -> service.deleteScheduleItem(7L))
                .isInstanceOf(RecurringLifecycleNotReadyException.class);
        verify(items, never()).save(any());
        verify(items, never()).delete(any());
    }

    @Test
    void legacyGenerationMethodsFailClosedBeforeAnyRepositoryMutation() {
        LessonGenerationService service = new LessonGenerationService(
                mock(LessonRepository.class), Clock.systemUTC(), mock(ApplicationEventPublisher.class));

        assertThatThrownBy(() -> service.generateLessons(null, LocalDate.MIN, LocalDate.MAX, WeekType.ALL))
                .isInstanceOf(RecurringLifecycleNotReadyException.class);
        assertThatThrownBy(() -> service.regenerateFromDate(null, LocalDate.MIN, LocalDate.MAX,
                WeekType.ALL, LocalDate.MIN))
                .isInstanceOf(RecurringLifecycleNotReadyException.class);
        assertThatThrownBy(() -> service.deletePlannedLessonsFromToday(7L))
                .isInstanceOf(RecurringLifecycleNotReadyException.class);
    }

    @Test
    void canonicalRestoreIsRejectedBeforeStateOrEventMutation() {
        LessonRepository lessons = mock(LessonRepository.class);
        ScheduleItemRepository items = mock(ScheduleItemRepository.class);
        AcademicGrpcClient academic = mock(AcademicGrpcClient.class);
        RequestContext context = mock(RequestContext.class);
        ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);

        Lesson lesson = new Lesson();
        lesson.setScheduleItemId(7L);
        lesson.setOccurrenceId(8L);
        lesson.setGroupId(10L);
        lesson.setStatus(LessonStatus.CANCELLED);
        when(lessons.findById(11L)).thenReturn(Optional.of(lesson));
        when(items.findById(7L)).thenReturn(Optional.of(scheduleItem(7L)));
        when(context.getRole()).thenReturn(UserRole.ADMIN);

        LessonService service = new LessonService(lessons, items, academic, context, events);

        assertThatThrownBy(() -> service.restoreLesson(11L))
                .isInstanceOf(RecurringLifecycleNotReadyException.class);
        verify(lessons, never()).save(any());
        verify(events, never()).publishEvent(any());
    }

    @Test
    void subjectCascadeRefusesRetainedCanonicalHistoryBeforeEventsOrDeletes() {
        ScheduleItemRepository items = mock(ScheduleItemRepository.class);
        OneOffLessonRepository oneOffs = mock(OneOffLessonRepository.class);
        LessonRepository lessons = mock(LessonRepository.class);
        ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);
        when(items.findBySubjectId(20L)).thenReturn(List.of(scheduleItem(7L)));
        when(oneOffs.findBySubjectId(20L)).thenReturn(List.of());
        when(lessons.findIdsBySubjectId(20L)).thenReturn(List.of(100L));
        when(lessons.countCanonicalReferencesBySubjectId(20L)).thenReturn(1L);

        SubjectDeletedCascadeService service = new SubjectDeletedCascadeService(
                items, oneOffs, lessons, events);

        assertThatThrownBy(() -> service.cascade(20L))
                .isInstanceOf(RecurringLifecycleNotReadyException.class);
        verify(events, never()).publishEvent(any());
        verify(items, never()).deleteAll(any());
        verify(oneOffs, never()).deleteAll(any());
    }

    private static ScheduleItem scheduleItem(long id) {
        ScheduleItem item = new ScheduleItem();
        item.setGroupId(10L);
        item.setSubjectId(20L);
        item.setSemesterId(30L);
        item.setDayOfWeek((short) 1);
        item.setLessonNumber((short) 1);
        item.setStartTime(LocalTime.of(8, 30));
        item.setEndTime(LocalTime.of(10, 0));
        item.setWeekType(WeekType.ALL);
        item.setRoom("A-101");
        item.setActive(true);
        return item;
    }
}
