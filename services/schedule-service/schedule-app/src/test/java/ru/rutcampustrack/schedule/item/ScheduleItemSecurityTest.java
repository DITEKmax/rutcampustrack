package ru.rutcampustrack.schedule.item;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import ru.rutcampustrack.schedule.contract.enums.UserRole;
import ru.rutcampustrack.schedule.contract.dto.item.CreateScheduleItemRequest;
import ru.rutcampustrack.schedule.contract.enums.WeekType;
import ru.rutcampustrack.schedule.exception.AccessDeniedException;
import ru.rutcampustrack.schedule.grpc.AcademicGrpcClient;
import ru.rutcampustrack.schedule.item.repository.ScheduleItemRepository;
import ru.rutcampustrack.schedule.lesson.LessonGenerationService;
import ru.rutcampustrack.schedule.security.RequestContext;

import java.time.Clock;
import java.time.LocalTime;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit test (Mockito, no Spring context) for ScheduleItemService.requireHeadmanForGroup
 * guard — covers D-11 (headman only for own group) and D-20 trust boundary
 * T-60-01 mitigation: two-phase check (RequestContext.isHeadman → gRPC
 * AcademicGrpcClient.isHeadman).
 *
 * Note: the guard is private; we drive it through the public createScheduleItem
 * entrypoint. The test stubs validateGroup/getActiveSemester only for the happy
 * paths so the guard decision is the only variable.
 */
@ExtendWith(MockitoExtension.class)
class ScheduleItemSecurityTest {

    private static final Long USER_ID = 1L;
    private static final Long OWN_GROUP = 10L;
    private static final Long OTHER_GROUP = 20L;
    private static final Long SEMESTER_ID = 100L;

    @Mock
    private ScheduleItemRepository scheduleItemRepository;
    @Mock
    private AcademicGrpcClient academicGrpcClient;
    @Mock
    private RequestContext requestContext;
    @Mock
    private LessonGenerationService lessonGenerationService;

    private ScheduleItemService service;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.systemUTC();
        service = new ScheduleItemService(
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
                groupId, 500L, SEMESTER_ID,
                (short) 1, (short) 1,
                LocalTime.of(8, 30), LocalTime.of(10, 0),
                WeekType.ALL, "A-101");
    }

    // =========================================================================
    // Test 1: headman of the correct group — allowed (no AccessDeniedException)
    // =========================================================================
    @Test
    void requireHeadmanForGroup_headmanOfCorrectGroup_allowed() {
        when(requestContext.getRole()).thenReturn(UserRole.STUDENT);
        when(requestContext.isHeadman()).thenReturn(true);
        when(academicGrpcClient.isHeadman(USER_ID, OWN_GROUP)).thenReturn(true);

        // Stub happy-path downstream calls so we don't fail before the assertion.
        // If guard passes, the test may still throw for other reasons (e.g. gRPC
        // getActiveSemester) — mock only enough to isolate the guard.
        // Here we let it throw NPE on getActiveSemester; that's fine as long as
        // it is NOT AccessDeniedException.
        assertThatThrownBy(() -> service.createScheduleItem(buildRequest(OWN_GROUP)))
                .isNotInstanceOf(AccessDeniedException.class);

        // Verify the two-phase guard executed fully.
        verify(academicGrpcClient).isHeadman(USER_ID, OWN_GROUP);
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
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining(OTHER_GROUP.toString());

        // validateGroup must NOT run — guard denies before downstream calls.
        verify(academicGrpcClient, never()).validateGroup(any());
    }

    // =========================================================================
    // Test 3: non-headman STUDENT — 403 BEFORE gRPC (JWT-only fast fail)
    // =========================================================================
    @Test
    void requireHeadmanForGroup_notHeadman_throws403() {
        when(requestContext.getRole()).thenReturn(UserRole.STUDENT);
        when(requestContext.isHeadman()).thenReturn(false);

        assertThatThrownBy(() -> service.createScheduleItem(buildRequest(OWN_GROUP)))
                .isInstanceOf(AccessDeniedException.class);

        // gRPC isHeadman must NOT be called — we short-circuit on JWT claim.
        verify(academicGrpcClient, never()).isHeadman(any(), any());
        verify(academicGrpcClient, never()).validateGroup(any());
    }

    // =========================================================================
    // Test 4: ADMIN bypasses both checks (no isHeadman, no gRPC)
    // =========================================================================
    @Test
    void requireHeadmanForGroup_adminBypasses() {
        when(requestContext.getRole()).thenReturn(UserRole.ADMIN);

        // Admin reaches downstream code that we haven't stubbed — so we expect
        // _some_ exception, but NOT AccessDeniedException.
        assertThatCode(() -> {
            try {
                service.createScheduleItem(buildRequest(OWN_GROUP));
            } catch (AccessDeniedException e) {
                throw e; // re-throw AccessDenied so the assertion below fails
            } catch (RuntimeException e) {
                // swallow other runtime exceptions — they prove ADMIN passed the guard
            }
        }).doesNotThrowAnyException();

        // ADMIN must bypass without gRPC isHeadman call.
        verify(academicGrpcClient, never()).isHeadman(any(), any());
        // isHeadman JWT flag is irrelevant for ADMIN — guard returns before checking.
        verify(requestContext, never()).isHeadman();
    }
}
