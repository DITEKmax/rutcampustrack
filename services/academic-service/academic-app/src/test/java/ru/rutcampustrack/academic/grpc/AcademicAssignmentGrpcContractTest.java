package ru.rutcampustrack.academic.grpc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.grpc.stub.StreamObserver;

import org.junit.jupiter.api.Test;

import ru.rutcampustrack.academic.entity.Assignment;
import ru.rutcampustrack.academic.repository.AssignmentRepository;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Optional;

/** gRPC source contract: historical IDs are atomic and null ends become concrete. */
class AcademicAssignmentGrpcContractTest {

    private static final ZoneId MOSCOW = ZoneId.of("Europe/Moscow");

    @Test
    void lifecycleRpcKeepsFrozenTextCodesAfterDomainTyping() {
        var assignments = mock(AssignmentRepository.class);
        var operations =
                mock(
                        ru.rutcampustrack.academic.repository
                                .AssignmentReplacementOperationRepository.class);
        var operation =
                mock(ru.rutcampustrack.academic.entity.AssignmentReplacementOperation.class);
        var operationId = java.util.UUID.randomUUID();
        var from = LocalDate.of(2026, 9, 1);
        var until = LocalDate.of(2026, 10, 1);
        when(operations.findById(operationId)).thenReturn(Optional.of(operation));
        when(operation.getOperationId()).thenReturn(operationId);
        when(operation.getOperationKind())
                .thenReturn(
                        ru.rutcampustrack.academic.assignment.AssignmentOperationKind
                                .ADOPT_UNASSIGNED);
        when(operation.getState())
                .thenReturn(
                        ru.rutcampustrack.academic.assignment.AssignmentOperationState.PREPARED);
        when(operation.getTargetAssignmentId()).thenReturn(71L);
        when(operation.getEffectiveFrom()).thenReturn(from);
        when(operation.getRequestedCutoff()).thenReturn(from);
        when(operation.getSourceValidUntil()).thenReturn(until);
        when(operation.getTargetValidUntil()).thenReturn(until);
        when(operation.getPayloadHash()).thenReturn(new byte[32]);
        Assignment assignment = assignment(71L, from, until);
        when(assignments.findById(71L)).thenReturn(Optional.of(assignment));
        var service =
                new AcademicGrpcServiceImpl(
                        null,
                        null,
                        null,
                        null,
                        assignments,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        operations,
                        null);
        var frozenCodes =
                java.util.Map.of(
                        ru.rutcampustrack.academic.assignment.AssignmentLifecycle.ACTIVE, "ACTIVE",
                        ru.rutcampustrack.academic.assignment.AssignmentLifecycle.PREPARED,
                                "PREPARED",
                        ru.rutcampustrack.academic.assignment.AssignmentLifecycle.CLOSED, "CLOSED");
        assertThat(frozenCodes.keySet())
                .containsExactlyInAnyOrder(
                        ru.rutcampustrack.academic.assignment.AssignmentLifecycle.values());
        for (var entry : frozenCodes.entrySet()) {
            when(assignment.getLifecycleState()).thenReturn(entry.getKey());
            var observer = new RecordingObserver<PreparedAssignmentCloseResponse>();
            service.getPreparedAssignmentCloseOperation(
                    PreparedAssignmentCloseRequest.newBuilder()
                            .setOperationId(operationId.toString())
                            .build(),
                    observer);
            assertThat(observer.error).isNull();
            assertThat(observer.value.getTargetLifecycleState()).isEqualTo(entry.getValue());
            assertThat(observer.value.getLessonType()).isEqualTo("lecture");
        }
    }

    private static Assignment assignment(Long id, LocalDate from, LocalDate until) {
        Assignment assignment = mock(Assignment.class);
        when(assignment.getId()).thenReturn(id);
        when(assignment.getTeacherId()).thenReturn(7L);
        when(assignment.getSubjectId()).thenReturn(11L);
        when(assignment.getGroupId()).thenReturn(22L);
        when(assignment.getSemesterId()).thenReturn(33L);
        when(assignment.getLessonType())
                .thenReturn(ru.rutcampustrack.academic.contract.enums.SubjectType.LECTURE);
        when(assignment.getValidFrom()).thenReturn(from);
        when(assignment.getValidUntilExclusive()).thenReturn(until);
        when(assignment.getLifecycleState())
                .thenReturn(ru.rutcampustrack.academic.assignment.AssignmentLifecycle.ACTIVE);
        return assignment;
    }

    private static final class RecordingObserver<T> implements StreamObserver<T> {
        private T value;
        private Throwable error;

        @Override
        public void onNext(T value) {
            this.value = value;
        }

        @Override
        public void onError(Throwable error) {
            this.error = error;
        }

        @Override
        public void onCompleted() {}
    }
}
