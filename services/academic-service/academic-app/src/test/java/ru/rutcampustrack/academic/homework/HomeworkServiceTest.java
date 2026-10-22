package ru.rutcampustrack.academic.homework;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.persistence.EntityManager;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import ru.rutcampustrack.academic.assignment.TeacherAssignmentReadAuthority;
import ru.rutcampustrack.academic.contract.dto.homework.CreateHomeworkRequest;
import ru.rutcampustrack.academic.contract.dto.homework.UpdateHomeworkRequest;
import ru.rutcampustrack.academic.contract.enums.HomeworkBindingMode;
import ru.rutcampustrack.academic.contract.enums.SubjectType;
import ru.rutcampustrack.academic.contract.enums.UserRole;
import ru.rutcampustrack.academic.entity.Homework;
import ru.rutcampustrack.academic.entity.UserRoleGrant;
import ru.rutcampustrack.academic.event.HomeworkWithdrawnEvent;
import ru.rutcampustrack.academic.exception.AccessDeniedException;
import ru.rutcampustrack.academic.exception.BadRequestException;
import ru.rutcampustrack.academic.grpc.ScheduleGrpcClient;
import ru.rutcampustrack.academic.repository.HeadmanAssistantRepository;
import ru.rutcampustrack.academic.repository.HomeworkBindingArchiveMarkerRepository;
import ru.rutcampustrack.academic.repository.HomeworkCompletionRepository;
import ru.rutcampustrack.academic.repository.HomeworkRepository;
import ru.rutcampustrack.academic.repository.UserRoleGrantRepository;
import ru.rutcampustrack.academic.security.RequestContext;
import ru.rutcampustrack.academic.semester.AcademicSemesterArchiveBarrierTransaction;
import ru.rutcampustrack.schedule.grpc.HomeworkBindingResponse;
import ru.rutcampustrack.schedule.grpc.HomeworkBindingState;
import ru.rutcampustrack.schedule.grpc.LessonInfo;
import ru.rutcampustrack.schedule.grpc.LessonResponse;
import ru.rutcampustrack.shared.web.time.EducationalTime;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

/**
 * Phase 61-03: unit tests for {@link HomeworkService} покрывающие D-03/D-04/D-05/D-06.
 *
 * <p>Детерминизм: {@code Clock.fixed("2026-05-01T09:00:00Z", UTC)} — зелёные тесты не зависят от
 * реальной календарной даты (pattern из других сервисов с Clock).
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
    @Mock private UserRoleGrantRepository grantRepository;

    private Clock clock;

    private HomeworkService service;

    @BeforeEach
    void setUp() {
        clock = Clock.fixed(Instant.parse("2026-05-01T09:00:00Z"), ZoneOffset.UTC);
        service =
                new HomeworkService(
                        homeworkRepository,
                        completionRepository,
                        assistantRepository,
                        requestContext,
                        eventPublisher,
                        scheduleGrpcClient,
                        clock,
                        null,
                        null,
                        grantRepository);
    }

    @Test
    void productionBindingPublishesTeachingTodayAndKeepsPastDatesArchived() {
        stubHeadman();
        LocalDate teachingDay = LocalDate.of(2026, 10, 16);
        Clock realClock =
                Clock.fixed(Instant.parse("2026-10-16T21:00:01Z"), ZoneId.of("Europe/Moscow"));
        Clock teachingClock = Clock.fixed(Instant.parse("2026-10-16T06:00:00Z"), ZoneOffset.UTC);
        var request = dateRequest(teachingDay);
        Homework stored =
                new Homework(
                        GROUP_ID,
                        SUBJECT_ID,
                        SEMESTER_ID,
                        request.title(),
                        request.description(),
                        null,
                        HEADMAN_ID,
                        teachingDay,
                        null,
                        8101L,
                        HEADMAN_ID,
                        request.requestKey(),
                        new byte[] {1},
                        HomeworkBindingMode.DATE);
        stored.acceptCreateIntent(HomeworkCreateIntent.from(request), SubjectType.LECTURE);
        stored.activatePublication();
        ReflectionTestUtils.setField(stored, "id", 1L);
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForObject(
                        any(String.class),
                        org.mockito.ArgumentMatchers.eq(Boolean.class),
                        org.mockito.ArgumentMatchers.eq(SUBJECT_ID),
                        org.mockito.ArgumentMatchers.eq(GROUP_ID)))
                .thenReturn(true);
        org.mockito.Mockito.doReturn(java.util.List.of(true))
                .when(jdbc)
                .query(
                        any(String.class),
                        org.mockito.ArgumentMatchers.<RowMapper<Boolean>>any(),
                        org.mockito.ArgumentMatchers.eq(teachingDay),
                        org.mockito.ArgumentMatchers.eq(SEMESTER_ID));
        HomeworkPublicationPersistence publications =
                mock(
                        HomeworkPublicationPersistence.class,
                        call -> {
                            if (call.getMethod().getName().equals("persistPending")
                                    || call.getMethod().getName().equals("activate")) return stored;
                            return org.mockito.Answers.RETURNS_DEFAULTS.answer(call);
                        });
        var pending =
                HomeworkBindingResponse.newBuilder()
                        .setBindingMode("DATE")
                        .setRevision(1L)
                        .setBindingId(8101L)
                        .setGroupId(GROUP_ID)
                        .setSubjectId(SUBJECT_ID)
                        .setSemesterId(SEMESTER_ID)
                        .setDate(teachingDay.toString())
                        .setState(HomeworkBindingState.HOMEWORK_BINDING_STATE_PENDING)
                        .build();
        when(scheduleGrpcClient.reserveDateHomeworkBinding(
                        anyLong(),
                        anyLong(),
                        anyLong(),
                        any(LocalDate.class),
                        any(UUID.class),
                        any(byte[].class)))
                .thenReturn(pending);
        when(scheduleGrpcClient.confirmHomeworkBinding(anyLong(), anyLong(), any(UUID.class)))
                .thenReturn(
                        pending.toBuilder()
                                .setHomeworkId(1L)
                                .setState(HomeworkBindingState.HOMEWORK_BINDING_STATE_ACTIVE)
                                .build());
        try (var context = instructionalContext(realClock, teachingClock, jdbc, publications)) {
            var production = context.getBean(HomeworkService.class);
            Homework published = production.createHomework(request);
            assertThat(published.getLessonDate()).isEqualTo(teachingDay);
            assertThat(published.getBindingMode()).isEqualTo(HomeworkBindingMode.DATE);
            var lifecycle = context.getBean(HomeworkLifecycle.class);
            assertThat(lifecycle.archived(published)).isFalse();
            var scope = context.getBean(HomeworkScopeValidator.class);
            assertThatThrownBy(
                            () ->
                                    scope.validate(
                                            GROUP_ID,
                                            SUBJECT_ID,
                                            SEMESTER_ID,
                                            teachingDay.minusDays(1)))
                    .isInstanceOf(BadRequestException.class);
            assertThatThrownBy(
                            () -> production.createHomework(dateRequest(teachingDay.minusDays(1))))
                    .isInstanceOf(BadRequestException.class);
            Homework past =
                    new Homework(
                            GROUP_ID,
                            SUBJECT_ID,
                            SEMESTER_ID,
                            "past",
                            null,
                            null,
                            HEADMAN_ID,
                            teachingDay.minusDays(1),
                            null,
                            8102L,
                            HEADMAN_ID,
                            UUID.randomUUID(),
                            new byte[] {2},
                            HomeworkBindingMode.DATE);
            past.acceptCreateIntent(
                    HomeworkCreateIntent.from(dateRequest(teachingDay.minusDays(1))),
                    SubjectType.LECTURE);
            past.activatePublication();
            assertThat(lifecycle.archived(past)).isTrue();
            when(homeworkRepository.findArchive(
                            GROUP_ID, SEMESTER_ID, teachingDay, PageRequest.of(0, 20)))
                    .thenReturn(Page.empty());
            assertThat(production.listHomeworks(GROUP_ID, SEMESTER_ID, PageRequest.of(0, 20), true))
                    .isEmpty();
            verify(homeworkRepository)
                    .findArchive(GROUP_ID, SEMESTER_ID, teachingDay, PageRequest.of(0, 20));
        }
    }

    @Test
    void productionBindingWithoutInstructionalOverrideRetainsRealDateDenial() {
        stubHeadman();
        Clock realClock =
                Clock.fixed(Instant.parse("2026-10-16T21:00:01Z"), ZoneId.of("Europe/Moscow"));
        try (var context =
                instructionalContext(
                        realClock,
                        null,
                        mock(JdbcTemplate.class),
                        mock(HomeworkPublicationPersistence.class))) {
            var date = LocalDate.of(2026, 10, 16);
            assertThatThrownBy(
                            () ->
                                    context.getBean(HomeworkService.class)
                                            .createHomework(dateRequest(date)))
                    .isInstanceOf(BadRequestException.class);
            assertThatThrownBy(
                            () ->
                                    context.getBean(HomeworkScopeValidator.class)
                                            .validate(GROUP_ID, SUBJECT_ID, SEMESTER_ID, date))
                    .isInstanceOf(BadRequestException.class);
        }
    }

    @Test
    void contentEditUsesInstructionalCutoffButRecordsRealAuditTime() {
        stubHeadman();
        var day = LocalDate.of(2026, 10, 16);
        var stored = instructionalDateHomework(day);
        when(homeworkRepository.findById(1L)).thenReturn(Optional.of(stored));
        var realClock =
                Clock.fixed(Instant.parse("2026-10-16T21:00:01Z"), ZoneId.of("Europe/Moscow"));
        var teachingClock = Clock.fixed(Instant.parse("2026-10-16T06:00:00Z"), ZoneOffset.UTC);
        try (var context =
                instructionalContext(
                        realClock,
                        teachingClock,
                        mock(JdbcTemplate.class),
                        mock(HomeworkPublicationPersistence.class))) {
            var edits = context.getBean(HomeworkEditPersistence.class);
            var request =
                    new UpdateHomeworkRequest(
                            "changed",
                            null,
                            null,
                            HomeworkBindingMode.DATE,
                            day,
                            null,
                            UUID.randomUUID(),
                            stored.getRevision(),
                            SubjectType.LECTURE,
                            null,
                            null);
            var edited = edits.content(1L, request);
            assertThat(edited.getTitle()).isEqualTo("changed");
            assertThat(edited.getLessonDate()).isEqualTo(day);
            assertThat(edited.getUpdatedAt().toInstant()).isEqualTo(realClock.instant());
            assertThat(edited.getRevision()).isEqualTo(2L);
            verify(homeworkRepository).saveAndFlush(edited);
            when(homeworkRepository.findById(1L))
                    .thenReturn(Optional.of(instructionalDateHomework(day.minusDays(1))));
            assertThatThrownBy(() -> edits.currentForEdit(1L))
                    .isInstanceOf(ru.rutcampustrack.academic.exception.ConflictException.class);
        }
    }

    @Test
    void manualWithdrawalEmitsOnlyForInstructionallyCurrentDateAndOnlyOnce() {
        var day = LocalDate.of(2026, 10, 16);
        var jdbc = mock(JdbcTemplate.class);
        var markers = mock(HomeworkBindingArchiveMarkerRepository.class);
        when(markers.save(any())).thenAnswer(call -> call.getArgument(0));
        var coordinator =
                new HomeworkBindingArchiveCoordinator(
                        jdbc,
                        mock(EntityManager.class),
                        markers,
                        homeworkRepository,
                        mock(AcademicSemesterArchiveBarrierTransaction.class),
                        eventPublisher);
        boolean priorTransaction = TransactionSynchronizationManager.isActualTransactionActive();
        try (var context = new AnnotationConfigApplicationContext()) {
            context.registerBean(
                    Clock.class,
                    () ->
                            Clock.fixed(
                                    Instant.parse("2026-10-16T21:00:01Z"),
                                    ZoneId.of("Europe/Moscow")));
            context.registerBean(
                    EducationalTime.class,
                    () ->
                            new EducationalTime(
                                    Clock.fixed(
                                            Instant.parse("2026-10-16T06:00:00Z"),
                                            ZoneOffset.UTC)));
            context.refresh();
            context.getAutowireCapableBeanFactory().autowireBean(coordinator);
            TransactionSynchronizationManager.setActualTransactionActive(true);
            var current = instructionalDateHomework(day);
            coordinator.archiveExistingHomework(
                    current,
                    current.getBindingId(),
                    current.getRequestKey(),
                    HEADMAN_ID,
                    "HEADMAN",
                    null);
            assertThat(current.getPublicationState())
                    .isEqualTo(
                            ru.rutcampustrack.academic.contract.enums.HomeworkPublicationState
                                    .ARCHIVED);
            coordinator.archiveExistingHomework(
                    current,
                    current.getBindingId(),
                    current.getRequestKey(),
                    HEADMAN_ID,
                    "HEADMAN",
                    null);
            var past = instructionalDateHomework(day.minusDays(1));
            ReflectionTestUtils.setField(past, "id", 2L);
            ReflectionTestUtils.setField(past, "bindingId", 8102L);
            coordinator.archiveExistingHomework(
                    past, past.getBindingId(), past.getRequestKey(), HEADMAN_ID, "HEADMAN", null);
            verify(eventPublisher, org.mockito.Mockito.times(1))
                    .publishEvent(any(HomeworkWithdrawnEvent.class));
        } finally {
            TransactionSynchronizationManager.setActualTransactionActive(priorTransaction);
        }
    }

    private Homework instructionalDateHomework(LocalDate day) {
        var request = dateRequest(day);
        var stored =
                new Homework(
                        GROUP_ID,
                        SUBJECT_ID,
                        SEMESTER_ID,
                        request.title(),
                        null,
                        null,
                        HEADMAN_ID,
                        day,
                        null,
                        8101L,
                        HEADMAN_ID,
                        request.requestKey(),
                        new byte[] {1},
                        HomeworkBindingMode.DATE);
        stored.acceptCreateIntent(HomeworkCreateIntent.from(request), SubjectType.LECTURE);
        stored.activatePublication();
        ReflectionTestUtils.setField(stored, "id", 1L);
        return stored;
    }

    private AnnotationConfigApplicationContext instructionalContext(
            Clock realClock,
            Clock teachingClock,
            JdbcTemplate jdbc,
            HomeworkPublicationPersistence publications) {
        var context = new AnnotationConfigApplicationContext();
        context.registerBean(Clock.class, () -> realClock);
        if (teachingClock != null)
            context.registerBean(EducationalTime.class, () -> new EducationalTime(teachingClock));
        context.registerBean(JdbcTemplate.class, () -> jdbc);
        context.registerBean(HomeworkRepository.class, () -> homeworkRepository);
        context.registerBean(HomeworkCompletionRepository.class, () -> completionRepository);
        context.registerBean(HeadmanAssistantRepository.class, () -> assistantRepository);
        context.getBeanFactory().registerSingleton("requestContext", requestContext);
        context.registerBean(ScheduleGrpcClient.class, () -> scheduleGrpcClient);
        context.registerBean(UserRoleGrantRepository.class, () -> grantRepository);
        context.registerBean(HomeworkPublicationPersistence.class, () -> publications);
        context.registerBean(
                HomeworkBindingArchiveCoordinator.class,
                () -> mock(HomeworkBindingArchiveCoordinator.class));
        context.registerBean(HomeworkEditHistory.class, () -> mock(HomeworkEditHistory.class));
        context.registerBean(
                HomeworkEditCoordinator.class, () -> mock(HomeworkEditCoordinator.class));
        context.registerBean(
                TeacherAssignmentReadAuthority.class,
                () -> mock(TeacherAssignmentReadAuthority.class));
        context.registerBean(
                HomeworkBindingTransferCoordinator.class,
                () -> mock(HomeworkBindingTransferCoordinator.class));
        context.registerBean(
                AcademicSemesterArchiveBarrierTransaction.class,
                () -> mock(AcademicSemesterArchiveBarrierTransaction.class));
        context.registerBean(ObjectMapper.class, () -> new ObjectMapper());
        context.register(
                HomeworkService.class,
                HomeworkScopeValidator.class,
                HomeworkLifecycle.class,
                HomeworkEditPersistence.class);
        context.refresh();
        return context;
    }

    private CreateHomeworkRequest dateRequest(LocalDate date) {
        return new CreateHomeworkRequest(
                "instructional day",
                null,
                null,
                SUBJECT_ID,
                GROUP_ID,
                SEMESTER_ID,
                date,
                null,
                UUID.randomUUID(),
                HomeworkBindingMode.DATE,
                SubjectType.LECTURE,
                null,
                null);
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
        when(assistantRepository.findByGroupIdAndStudentIdAndIsActiveTrue(anyLong(), anyLong()))
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
        when(scheduleGrpcClient.reserveHomeworkBinding(
                        anyLong(), any(UUID.class), anyLong(), any(byte[].class)))
                .thenReturn(
                        binding(8101L, null, HomeworkBindingState.HOMEWORK_BINDING_STATE_PENDING));
        when(scheduleGrpcClient.confirmHomeworkBinding(anyLong(), anyLong(), any(UUID.class)))
                .thenAnswer(
                        invocation ->
                                binding(
                                        8101L,
                                        (Long) invocation.getArgument(1),
                                        HomeworkBindingState.HOMEWORK_BINDING_STATE_ACTIVE));
        when(homeworkRepository.save(any(Homework.class)))
                .thenAnswer(
                        inv -> {
                            Homework homework = inv.getArgument(0);
                            if (homework.getId() == null) {
                                ReflectionTestUtils.setField(homework, "id", 1L);
                            }
                            return homework;
                        });

        Homework saved = service.createHomework(validRequest(TOMORROW));

        assertThat(saved.getGroupId()).isEqualTo(GROUP_ID);
        assertThat(saved.getSubjectId()).isEqualTo(SUBJECT_ID);
        assertThat(saved.getPublishedBy()).isEqualTo(HEADMAN_ID);
        assertThat(saved.getLessonDate()).isEqualTo(TOMORROW);
        assertThat(saved.getLessonNumber()).isEqualTo(LESSON_NUMBER);
        verify(homeworkRepository).save(any(Homework.class));
        verify(eventPublisher).publishEvent(any());
    }

    @Test
    void createHomework_hashEncodingSeparatesNewlinePayloads() {
        stubHeadman();
        when(scheduleGrpcClient.resolveLesson(GROUP_ID, TOMORROW, LESSON_NUMBER))
                .thenReturn(Optional.of(lessonWithSubject(SUBJECT_ID)));
        when(scheduleGrpcClient.reserveHomeworkBinding(
                        anyLong(), any(UUID.class), anyLong(), any(byte[].class)))
                .thenReturn(
                        binding(8101L, null, HomeworkBindingState.HOMEWORK_BINDING_STATE_PENDING));
        when(scheduleGrpcClient.confirmHomeworkBinding(anyLong(), anyLong(), any(UUID.class)))
                .thenAnswer(
                        invocation ->
                                binding(
                                        8101L,
                                        (Long) invocation.getArgument(1),
                                        HomeworkBindingState.HOMEWORK_BINDING_STATE_ACTIVE));
        when(homeworkRepository.save(any(Homework.class)))
                .thenAnswer(
                        inv -> {
                            Homework homework = inv.getArgument(0);
                            ReflectionTestUtils.setField(homework, "id", System.nanoTime());
                            return homework;
                        });

        service.createHomework(
                new CreateHomeworkRequest(
                        "A\nB",
                        "C",
                        null,
                        SUBJECT_ID,
                        GROUP_ID,
                        SEMESTER_ID,
                        TOMORROW,
                        LESSON_NUMBER));
        service.createHomework(
                new CreateHomeworkRequest(
                        "A",
                        "B\nC",
                        null,
                        SUBJECT_ID,
                        GROUP_ID,
                        SEMESTER_ID,
                        TOMORROW,
                        LESSON_NUMBER));

        ArgumentCaptor<byte[]> hashes = ArgumentCaptor.forClass(byte[].class);
        verify(scheduleGrpcClient, org.mockito.Mockito.times(2))
                .reserveHomeworkBinding(anyLong(), any(UUID.class), anyLong(), hashes.capture());
        assertThat(hashes.getAllValues().get(0)).isNotEqualTo(hashes.getAllValues().get(1));
    }

    // =========================================================================
    // Accepted owner decision: current group management rights permit editing another author's
    // homework.
    // =========================================================================

    // =========================================================================
    // Helpers
    // =========================================================================

    private void stubHeadman() {
        when(requestContext.getRole()).thenReturn(UserRole.STUDENT);
        when(requestContext.isHeadman()).thenReturn(true);
        when(requestContext.getUserId()).thenReturn(HEADMAN_ID);
        when(requestContext.getGroupId()).thenReturn(GROUP_ID);
        UserRoleGrant grant = mock(UserRoleGrant.class);
        when(grant.getGroupId()).thenReturn(GROUP_ID);
        when(grantRepository.findByUserIdAndRoleAndStatus(HEADMAN_ID, "headman", "active"))
                .thenReturn(java.util.List.of(grant));
    }

    private CreateHomeworkRequest validRequest(LocalDate date) {
        return new CreateHomeworkRequest(
                "Title",
                "description",
                null,
                SUBJECT_ID,
                GROUP_ID,
                SEMESTER_ID,
                date,
                LESSON_NUMBER);
    }

    private LessonResponse lessonWithSubject(Long subjectId) {
        return LessonResponse.newBuilder()
                .setGroupId(GROUP_ID)
                .setSubjectId(subjectId)
                .setSemesterId(SEMESTER_ID)
                .setLessonNumber(LESSON_NUMBER)
                .setDate(TOMORROW.toString())
                .setOccurrenceId(8100L)
                .setRevision(1L)
                .setStatus("planned")
                .build();
    }

    private HomeworkBindingResponse binding(
            long bindingId, Long homeworkId, HomeworkBindingState state) {
        LessonInfo current =
                LessonInfo.newBuilder()
                        .setLessonId(8102L)
                        .setGroupId(GROUP_ID)
                        .setSubjectId(SUBJECT_ID)
                        .setStartsAt(TOMORROW + "T09:00")
                        .setLessonNumber(LESSON_NUMBER)
                        .setDate(TOMORROW.toString())
                        .setOccurrenceId(8100L)
                        .setAssignmentId(1L)
                        .setSemesterId(SEMESTER_ID)
                        .setTeacherId(1L)
                        .setLessonType("lecture")
                        .setGeneration(1L)
                        .setRevision(1L)
                        .setStatus("planned")
                        .build();
        HomeworkBindingResponse.Builder response =
                HomeworkBindingResponse.newBuilder()
                        .setBindingId(bindingId)
                        .setOccurrenceId(8100L)
                        .setCurrentLesson(current)
                        .setState(state)
                        .setRevision(
                                state == HomeworkBindingState.HOMEWORK_BINDING_STATE_ACTIVE
                                        ? 2L
                                        : 1L)
                        .setGroupId(GROUP_ID)
                        .setSubjectId(SUBJECT_ID)
                        .setSemesterId(SEMESTER_ID)
                        .setDate(TOMORROW.toString())
                        .setLessonNumber(LESSON_NUMBER);
        if (homeworkId != null) {
            response.setHomeworkId(homeworkId);
        }
        return response.build();
    }

    private Homework existingHomework(Long publishedBy) {
        Homework homework =
                new Homework(
                        GROUP_ID,
                        SUBJECT_ID,
                        SEMESTER_ID,
                        "old",
                        "desc",
                        null,
                        publishedBy,
                        TOMORROW,
                        LESSON_NUMBER);
        ReflectionTestUtils.setField(homework, "id", 1L);
        homework.activatePublication();
        return homework;
    }
}
