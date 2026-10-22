package ru.rutcampustrack.academic.botread;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import io.grpc.Status;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import ru.rutcampustrack.academic.grpc.HomeworksForWeekResponse;
import ru.rutcampustrack.academic.identityoperation.AcademicIdentityReadFence;
import ru.rutcampustrack.botreads.grpc.DirectedBotIdentity;

import java.time.LocalDate;
import java.util.List;

class AcademicBotHomeworkQueryTest {
    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final AcademicIdentityReadFence fences = mock(AcademicIdentityReadFence.class);
    private final AcademicBotHomeworkQuery query =
            new AcademicBotHomeworkQuery(jdbc, fences, new Transactions());
    private final DirectedBotIdentity identity =
            DirectedBotIdentity.newBuilder()
                    .setTelegramSenderId(42)
                    .setPrivateChatId(42)
                    .setExpectedUserId(7)
                    .setExpectedRolesVersion(5)
                    .setExpectedBindingVersion(3)
                    .build();
    private final AcademicBotHomeworkQuery.Scope scope =
            new AcademicBotHomeworkQuery.Scope(8, 5, 12, 4);

    @Test
    void fullHomeworkCompositionRunsInSameReadOnlyRepeatableReadSnapshot() {
        rows(scope);
        when(jdbc.queryForList(anyString(), eq(Long.class))).thenReturn(List.of(9L));
        var result =
                query.read(
                        identity,
                        LocalDate.parse("2026-10-18"),
                        LocalDate.parse("2026-10-24"),
                        request -> {
                            assertThat(
                                            TransactionSynchronizationManager
                                                    .isActualTransactionActive())
                                    .isTrue();
                            assertThat(
                                            TransactionSynchronizationManager
                                                    .isCurrentTransactionReadOnly())
                                    .isTrue();
                            assertThat(
                                            TransactionSynchronizationManager
                                                    .getCurrentTransactionIsolationLevel())
                                    .isEqualTo(TransactionDefinition.ISOLATION_REPEATABLE_READ);
                            assertThat(request.getStudentId()).isEqualTo(7);
                            assertThat(request.getGroupId()).isEqualTo(12);
                            assertThat(request.getSemesterId()).isEqualTo(9);
                            return HomeworksForWeekResponse.getDefaultInstance();
                        });
        assertThat(result.scope()).isEqualTo(scope);
        assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
        verify(fences).requireUserReadable(7);
        verify(fences).requireGroupReadable(12);
    }

    @Test
    void missingOrInactiveOwnStudentCannotReachComposition() {
        rows();
        assertCode(Status.Code.PERMISSION_DENIED, () -> read());
    }

    @Test
    void staleLocallyAppliedRolesCannotReachComposition() {
        rows(new AcademicBotHomeworkQuery.Scope(8, 4, 12, 4));
        assertCode(Status.Code.FAILED_PRECONDITION, () -> read());
    }

    @Test
    void pendingUserCannotReachComposition() {
        doThrow(Status.UNAVAILABLE.asRuntimeException()).when(fences).requireUserReadable(7);
        assertCode(Status.Code.UNAVAILABLE, () -> read());
        verifyNoInteractions(jdbc);
    }

    @Test
    void pendingGroupCannotReachComposition() {
        rows(scope);
        doThrow(Status.UNAVAILABLE.asRuntimeException()).when(fences).requireGroupReadable(12);
        assertCode(Status.Code.UNAVAILABLE, () -> read());
    }

    @Test
    void changedRevisionOrGroupIsRejectedByFreshSnapshot() {
        for (var changed :
                List.of(
                        new AcademicBotHomeworkQuery.Scope(9, 5, 12, 4),
                        new AcademicBotHomeworkQuery.Scope(8, 5, 13, 4),
                        new AcademicBotHomeworkQuery.Scope(8, 5, 12, 5))) {
            rows(changed);
            assertCode(
                    Status.Code.FAILED_PRECONDITION, () -> query.requireCurrent(identity, scope));
        }
    }

    @SuppressWarnings("unchecked")
    private void rows(AcademicBotHomeworkQuery.Scope... scopes) {
        when(jdbc.query(anyString(), any(RowMapper.class), eq(7L))).thenReturn(List.of(scopes));
    }

    private void read() {
        query.read(
                identity,
                LocalDate.parse("2026-10-18"),
                LocalDate.parse("2026-10-24"),
                ignored -> {
                    throw new AssertionError("denied eligibility reached homework composition");
                });
    }

    private static void assertCode(
            Status.Code code, org.assertj.core.api.ThrowableAssert.ThrowingCallable action) {
        assertThatThrownBy(action)
                .satisfies(
                        error -> assertThat(Status.fromThrowable(error).getCode()).isEqualTo(code));
    }

    private static class Transactions extends AbstractPlatformTransactionManager {
        @Override
        protected Object doGetTransaction() {
            return new Object();
        }

        @Override
        protected void doBegin(Object transaction, TransactionDefinition definition) {}

        @Override
        protected void doCommit(DefaultTransactionStatus status) {}

        @Override
        protected void doRollback(DefaultTransactionStatus status) {}
    }
}
