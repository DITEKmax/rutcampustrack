package ru.rutcampustrack.academic.identityoperation;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import io.grpc.Status;

import jakarta.persistence.EntityManager;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.cache.CacheManager;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

import ru.rutcampustrack.academic.dashboard.DashboardScaleObservationService;
import ru.rutcampustrack.academic.repository.GroupRegistryReadRepository;
import ru.rutcampustrack.academic.repository.UserRoleGrantRepository;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

/** Publication timing cases only; the actual split-DB dashboard IT supplies acceptance evidence. */
class AcademicIdentityPublicationCaptureTest {
    @Test
    void publicationWaitsForCommitAndDeduplicatesWithinTheTransactionEvenWithoutCaches() {
        var observations = mock(DashboardScaleObservationService.class);
        var invalidator = invalidator(observations);
        new TransactionTemplate(new LocalTransactions())
                .executeWithoutResult(
                        status -> {
                            invalidator.afterPublicationCommit();
                            invalidator.afterPublicationCommit();
                            verifyNoInteractions(observations);
                        });
        verify(observations, times(1)).capturePending();
        verifyNoMoreInteractions(observations);
    }

    @Test
    void rolledBackPublicationNeverCapturesOrPretendsThatTheFenceWasReleased() {
        var observations = mock(DashboardScaleObservationService.class);
        var invalidator = invalidator(observations);
        new TransactionTemplate(new LocalTransactions())
                .executeWithoutResult(
                        status -> {
                            invalidator.afterPublicationCommit();
                            status.setRollbackOnly();
                        });
        verifyNoInteractions(observations);
    }

    @Test
    void ordinaryApplyCacheEvictionDoesNotAttemptPublicationCapture() {
        var observations = mock(DashboardScaleObservationService.class);
        var invalidator = invalidator(observations);
        new TransactionTemplate(new LocalTransactions())
                .executeWithoutResult(status -> invalidator.afterCommit(List.of(1L), List.of(2L)));
        verifyNoInteractions(observations);
    }

    @Test
    void failedCaptureDoesNotUndoAlreadyCommittedPublication() {
        var observations = mock(DashboardScaleObservationService.class);
        doThrow(Status.UNAVAILABLE.asRuntimeException()).when(observations).capturePending();
        var invalidator = invalidator(observations);
        var result =
                new TransactionTemplate(new LocalTransactions())
                        .execute(
                                status -> {
                                    invalidator.afterPublicationCommit();
                                    return "committed";
                                });
        assertThat(result).isEqualTo("committed");
        verify(observations).capturePending();
    }

    @Test
    void capturePendingWithoutAChangeDoesNotReadCountsOrWriteASnapshot() {
        var jdbc = mock(JdbcTemplate.class);
        var grants = mock(UserRoleGrantRepository.class);
        var groups = mock(GroupRegistryReadRepository.class);
        when(jdbc.queryForObject(anyString(), eq(Boolean.class))).thenReturn(false);
        var service = observations(jdbc, grants, groups);
        service.capturePending();
        verify(jdbc).execute(anyString());
        verify(jdbc).queryForObject(anyString(), eq(Boolean.class));
        verifyNoMoreInteractions(jdbc);
        verifyNoInteractions(grants, groups);
    }

    @Test
    void anotherRegistryFenceLeavesTheOriginalChangeUnsettled() {
        var jdbc = mock(JdbcTemplate.class);
        var grants = mock(UserRoleGrantRepository.class);
        var groups = mock(GroupRegistryReadRepository.class);
        when(jdbc.queryForObject(anyString(), eq(Boolean.class))).thenReturn(true);
        when(groups.countAll((String) null)).thenThrow(Status.UNAVAILABLE.asRuntimeException());
        var service = observations(jdbc, grants, groups);
        assertThatThrownBy(service::capturePending)
                .isInstanceOf(io.grpc.StatusRuntimeException.class);
        // Every marker settlement/snapshot write occurs after the guarded aggregate succeeds.
        verify(jdbc).execute(anyString());
        verify(jdbc).queryForObject(anyString(), eq(Boolean.class));
        verifyNoMoreInteractions(jdbc);
    }

    @SuppressWarnings("unchecked")
    private static AcademicIdentityCacheInvalidator invalidator(
            DashboardScaleObservationService observations) {
        ObjectProvider<CacheManager> caches = mock(ObjectProvider.class);
        ObjectProvider<DashboardScaleObservationService> provider = mock(ObjectProvider.class);
        when(provider.getObject()).thenReturn(observations);
        return new AcademicIdentityCacheInvalidator(caches, provider);
    }

    private static DashboardScaleObservationService observations(
            JdbcTemplate jdbc, UserRoleGrantRepository grants, GroupRegistryReadRepository groups) {
        return new DashboardScaleObservationService(
                jdbc,
                mock(EntityManager.class),
                grants,
                groups,
                Clock.fixed(Instant.parse("2026-10-01T12:00:00Z"), ZoneOffset.UTC),
                new LocalTransactions());
    }

    private static final class LocalTransactions extends AbstractPlatformTransactionManager {
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
