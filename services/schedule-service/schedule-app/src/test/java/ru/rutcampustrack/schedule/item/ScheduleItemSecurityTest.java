package ru.rutcampustrack.schedule.item;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import ru.rutcampustrack.schedule.contract.dto.item.CreateScheduleItemRequest;
import ru.rutcampustrack.schedule.contract.enums.UserRole;
import ru.rutcampustrack.schedule.contract.enums.WeekType;
import ru.rutcampustrack.schedule.exception.AccessDeniedException;
import ru.rutcampustrack.schedule.exception.RecurringLifecycleNotReadyException;
import ru.rutcampustrack.schedule.grpc.AcademicGrpcClient;
import ru.rutcampustrack.schedule.item.repository.ScheduleItemRepository;
import ru.rutcampustrack.schedule.lesson.LessonGenerationService;
import ru.rutcampustrack.schedule.security.RequestContext;

import java.time.Clock;
import java.time.LocalTime;

/**
 * Unit test (Mockito, no Spring context) for ScheduleItemService.requireHeadmanForGroup guard —
 * covers D-11 (headman only for own group) and D-20 trust boundary T-60-01 mitigation: two-phase
 * check (RequestContext.isHeadman → gRPC AcademicGrpcClient.isHeadman).
 *
 * <p>Note: the guard is private; we drive it through the public createScheduleItem entrypoint. The
 * test stubs validateGroup/getActiveSemester only for the happy paths so the guard decision is the
 * only variable.
 */
@ExtendWith(MockitoExtension.class)
class ScheduleItemSecurityTest {

    private static final Long USER_ID = 1L;
    private static final Long OWN_GROUP = 10L;
    private static final Long OTHER_GROUP = 20L;
    private static final Long SEMESTER_ID = 100L;

    @Mock private ScheduleItemRepository scheduleItemRepository;
    @Mock private AcademicGrpcClient academicGrpcClient;
    @Mock private RequestContext requestContext;
    @Mock private LessonGenerationService lessonGenerationService;

    private ScheduleItemService service;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.systemUTC();
        service =
                new ScheduleItemService(
                        scheduleItemRepository,
                        academicGrpcClient,
                        requestContext,
                        lessonGenerationService,
                        clock);

        // Strict stubs by default — individual tests override what they need.
        lenient().when(requestContext.getUserId()).thenReturn(USER_ID);
    }

    private CreateScheduleItemRequest buildRequest(Long groupId) {
        return new CreateScheduleItemRequest(
                501L,
                groupId,
                500L,
                SEMESTER_ID,
                (short) 1,
                (short) 1,
                LocalTime.of(8, 30),
                LocalTime.of(9, 50),
                WeekType.ALL,
                "A-101");
    }

    // =========================================================================
    // Test 1: headman of the correct group — allowed (no AccessDeniedException)
    // =========================================================================
    @Test
    void requireHeadmanForGroup_headmanOfCorrectGroup_allowed() {
        when(requestContext.getRole()).thenReturn(UserRole.STUDENT);
        when(requestContext.isHeadman()).thenReturn(true);
        when(academicGrpcClient.isHeadman(USER_ID, OWN_GROUP)).thenReturn(true);

        assertThatThrownBy(() -> service.createScheduleItem(buildRequest(OWN_GROUP)))
                .isExactlyInstanceOf(RecurringLifecycleNotReadyException.class);
        verifyNoInteractions(scheduleItemRepository, lessonGenerationService);
    }

    // =========================================================================
    // Test 2: headman of a different group — 403 at gRPC step
    // =========================================================================
    @Test
    void requireHeadmanForGroup_headmanOfWrongGroup_throws403() {
        when(requestContext.getRole()).thenReturn(UserRole.STUDENT);
        when(requestContext.isHeadman()).thenReturn(true);
        when(academicGrpcClient.isHeadman(USER_ID, OTHER_GROUP)).thenReturn(false);

        assertThatThrownBy(() -> service.createScheduleItem(buildRequest(OTHER_GROUP)))
                .isExactlyInstanceOf(AccessDeniedException.class)
                .hasMessageContaining(OTHER_GROUP.toString());

        verifyNoInteractions(scheduleItemRepository, lessonGenerationService);
    }

    // =========================================================================
    // Test 3: non-headman STUDENT — 403 BEFORE gRPC (JWT-only fast fail)
    // =========================================================================
    @Test
    void requireHeadmanForGroup_notHeadman_throws403() {
        when(requestContext.getRole()).thenReturn(UserRole.STUDENT);
        when(requestContext.isHeadman()).thenReturn(false);

        assertThatThrownBy(() -> service.createScheduleItem(buildRequest(OWN_GROUP)))
                .isExactlyInstanceOf(AccessDeniedException.class);

        verifyNoInteractions(scheduleItemRepository, lessonGenerationService);
    }

    // =========================================================================
    // Test 4: ADMIN bypasses both checks (no isHeadman, no gRPC)
    // =========================================================================
    @Test
    void requireHeadmanForGroup_adminBypasses() {
        when(requestContext.getRole()).thenReturn(UserRole.ADMIN);

        assertThatThrownBy(() -> service.createScheduleItem(buildRequest(OWN_GROUP)))
                .isExactlyInstanceOf(RecurringLifecycleNotReadyException.class);
        verifyNoInteractions(scheduleItemRepository, lessonGenerationService);
    }
}
