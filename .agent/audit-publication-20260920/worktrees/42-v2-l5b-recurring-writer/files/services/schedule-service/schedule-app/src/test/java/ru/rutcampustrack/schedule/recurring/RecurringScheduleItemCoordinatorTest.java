package ru.rutcampustrack.schedule.recurring;

import org.junit.jupiter.api.Test;
import ru.rutcampustrack.academic.grpc.GroupResponse;
import ru.rutcampustrack.schedule.contract.dto.item.CreateScheduleItemRequest;
import ru.rutcampustrack.schedule.contract.enums.UserRole;
import ru.rutcampustrack.schedule.exception.RecurringProtocolConflictException;
import ru.rutcampustrack.schedule.grpc.AcademicGrpcClient;
import ru.rutcampustrack.schedule.item.repository.ScheduleItemRepository;
import ru.rutcampustrack.schedule.security.RequestContext;

import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RecurringScheduleItemCoordinatorTest {

    private static final long ASSIGNMENT_ID = 501L;
    private static final long GROUP_ID = 10L;
    private static final long SUBJECT_ID = 20L;
    private static final long SEMESTER_ID = 30L;
    private static final long ACTOR_ID = 99L;

    @Test
    void activeGroupResponseForAnotherIdIsRejectedBeforeLocalWriter() {
        AcademicGrpcClient academic = mock(AcademicGrpcClient.class);
        RequestContext context = mock(RequestContext.class);
        RecurringScheduleItemWriter writer = mock(RecurringScheduleItemWriter.class);
        ScheduleItemRepository items = mock(ScheduleItemRepository.class);

        when(context.getRole()).thenReturn(UserRole.STUDENT);
        when(context.isHeadman()).thenReturn(true);
        when(context.getUserId()).thenReturn(ACTOR_ID);
        when(academic.isHeadman(ACTOR_ID, GROUP_ID)).thenReturn(true);
        when(academic.validateGroup(GROUP_ID)).thenReturn(GroupResponse.newBuilder()
                .setId(GROUP_ID + 1)
                .setIsActive(true)
                .build());

        RecurringScheduleItemCoordinator coordinator = new RecurringScheduleItemCoordinator(
                academic, context, writer, items);

        CreateScheduleItemRequest request = new CreateScheduleItemRequest(
                ASSIGNMENT_ID, GROUP_ID, SUBJECT_ID, SEMESTER_ID,
                (short) 1, (short) 1, LocalTime.of(8, 30), LocalTime.of(10, 0),
                ru.rutcampustrack.schedule.contract.enums.WeekType.ALL, "A-101");

        assertThatThrownBy(() -> coordinator.create(request, UUID.randomUUID()))
                .isInstanceOf(RecurringProtocolConflictException.class)
                .hasMessageContaining("group authority response is inconsistent");

        verify(academic, never()).getActiveSemester();
        verify(academic, never()).getAssignmentsByIds(List.of(ASSIGNMENT_ID));
        verify(writer, never()).write(any(), any(), anyLong(), any(), any(), any());
        verify(items, never()).findById(anyLong());
    }
}
