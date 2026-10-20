package ru.rutcampustrack.academic.identityoperation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import org.junit.jupiter.api.Test;

import ru.rutcampustrack.academic.contract.dto.user.UpdateUserRequest;
import ru.rutcampustrack.academic.contract.enums.UserRole;
import ru.rutcampustrack.academic.identitycatalog.AuthIdentityDataClient;
import ru.rutcampustrack.academic.security.RequestContext;
import ru.rutcampustrack.academic.user.AuthUserArchiveClient;
import ru.rutcampustrack.academic.user.UserArchiveRepository;
import ru.rutcampustrack.authauthority.grpc.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

class AcademicIdentityProfileProjectionIT extends AbstractAcademicIdentityWriterIT {
    @Test
    void existingProfileCommandReadsOnlySelectedColumnsAndPreservesScalarStatus() throws Exception {
        profile(8, "TEACHER", "SUSPENDED");
        var identity =
                IdentityAuthoritySnapshot.newBuilder()
                        .setUserId(8)
                        .setAcademicRevision(2)
                        .setRolesVersion(3)
                        .setCredentialsVersion(1)
                        .setTelegramBindingVersion(1)
                        .setAccountState(AuthorityAccountState.AUTHORITY_ACCOUNT_STATE_ACTIVE)
                        .addGrants(
                                AuthorityRoleGrant.newBuilder()
                                        .setGrantId(10)
                                        .setRole("TEACHER")
                                        .setStatus("ACTIVE"))
                        .build();
        var auth = mock(AuthIdentityDataClient.class);
        when(auth.readServiceIdentity(eq(List.of(8L)), anyLong())).thenReturn(Map.of(8L, identity));
        var http = mock(AcademicIdentityHttpOperations.class);
        var captured = new AtomicReference<AcademicIdentityCoordinator.AcceptedCommand>();
        when(http.execute(any(AcademicIdentityLocalCommand.ApiIntent.class), any()))
                .thenAnswer(
                        invocation -> {
                            Function<UUID, AcademicIdentityCoordinator.AcceptedCommand> build =
                                    invocation.getArgument(1);
                            captured.set(
                                    build.apply(
                                            UUID.fromString(
                                                    "00000000-0000-0000-0000-000000000001")));
                            return null;
                        });
        var mapper = new ObjectMapper().registerModule(new JavaTimeModule());
        var service =
                new AcademicUserIdentityOperations(
                        jdbc,
                        http,
                        mapper,
                        transactions,
                        auth,
                        mock(AcademicIdentityReadFence.class),
                        new RequestContext(),
                        mock(UserArchiveRepository.class),
                        mock(AuthUserArchiveClient.class));
        service.replaceProfile(
                8,
                new UpdateUserRequest(
                        "Петров", "Пётр", null, UserRole.TEACHER, null, "EMP-123", null));
        var local =
                mapper.readValue(
                        captured.get().localPayload().toByteArray(),
                        AcademicIdentityLocalCommand.class);
        assertThat(local.profile().directoryStatus()).isEqualTo("SUSPENDED");
        assertThat(local.profile().lastName()).isEqualTo("Петров");
        assertThat(captured.get().authCommand().getTargets(0).getExpectedAcademicRevision())
                .isEqualTo(2);
        assertThat(captured.get().authCommand().getTargets(0).getDesiredGrants(0).getStatus())
                .isEqualTo("ACTIVE");
    }
}
