package ru.rutcampustrack.academic.identityoperation;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.grpc.Status;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import ru.rutcampustrack.academic.security.RequestContext;
import ru.rutcampustrack.academic.user.UserArchiveRepository;

import java.time.Instant;
import java.util.UUID;

class AcademicIdentityRecoveryArchiveGuardTest {
    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final UserArchiveRepository archives = mock(UserArchiveRepository.class);
    private final JdbcAcademicIdentityOperationStore store =
            new JdbcAcademicIdentityOperationStore(
                    jdbc,
                    new ObjectMapper(),
                    mock(RequestContext.class),
                    mock(AcademicIdentityDomainWriter.class),
                    mock(AcademicIdentityCacheInvalidator.class),
                    archives);
    private final UUID allowed = UUID.randomUUID();

    @Test
    void foreignGuardAppearingDuringUnchangedAggregateStillDeniesAdmission() {
        var unchanged =
                new UserArchiveRepository.Local(
                        8, "a".repeat(64), "archived", 1, 0, 0, false, Instant.now());
        when(jdbc.queryForObject(anyString(), eq(Boolean.class), eq(allowed), eq(allowed)))
                .thenReturn(false, true);
        when(archives.observe(8)).thenReturn(unchanged);
        assertThatThrownBy(() -> store.observeArchiveWithinRecoveryGuard(allowed, 8))
                .satisfies(
                        error ->
                                assertThat(Status.fromThrowable(error).getCode())
                                        .isEqualTo(Status.Code.UNAVAILABLE));
        // The digest did not change. The newly installed pending publication barrier wins.
        var order = inOrder(jdbc, archives);
        order.verify(jdbc).queryForObject(anyString(), eq(Boolean.class), eq(allowed), eq(allowed));
        order.verify(archives).observe(8);
        order.verify(jdbc).queryForObject(anyString(), eq(Boolean.class), eq(allowed), eq(allowed));
    }

    @Test
    void alreadyPendingForeignOperationDeniesBeforeAnyAggregateRead() {
        when(jdbc.queryForObject(anyString(), eq(Boolean.class), eq(allowed), eq(allowed)))
                .thenReturn(true);
        assertThatThrownBy(() -> store.observeArchiveWithinRecoveryGuard(allowed, 8))
                .satisfies(
                        error ->
                                assertThat(Status.fromThrowable(error).getCode())
                                        .isEqualTo(Status.Code.UNAVAILABLE));
        verifyNoInteractions(archives);
    }
}
