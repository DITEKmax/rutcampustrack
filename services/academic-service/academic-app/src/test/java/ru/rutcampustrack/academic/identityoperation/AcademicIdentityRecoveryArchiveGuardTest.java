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
