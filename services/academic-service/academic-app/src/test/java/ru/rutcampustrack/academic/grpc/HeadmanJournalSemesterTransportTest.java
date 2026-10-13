package ru.rutcampustrack.academic.grpc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.grpc.ManagedChannel;
import io.grpc.Metadata;
import io.grpc.Server;
import io.grpc.ServerInterceptors;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.netty.NettyChannelBuilder;
import io.grpc.netty.NettyServerBuilder;
import io.grpc.stub.MetadataUtils;
import java.net.InetSocketAddress;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import ru.rutcampustrack.academic.assistant.AssistantPermissionAuthority;
import ru.rutcampustrack.academic.entity.Semester;
import ru.rutcampustrack.academic.entity.Group;
import ru.rutcampustrack.academic.history.HistoricalMembershipService;
import ru.rutcampustrack.academic.repository.SemesterRepository;
import ru.rutcampustrack.academic.repository.GroupRepository;
import ru.rutcampustrack.shared.security.InternalJwtProperties;
import ru.rutcampustrack.shared.security.InternalJwtTestFactory;
import ru.rutcampustrack.shared.security.InternalJwtValidator;
import ru.rutcampustrack.shared.security.PublicKeyProvider;

/** Real socket regression for the user journal calling the Schedule-only semester boundary. */
class HeadmanJournalSemesterTransportTest {
    private static final Metadata.Key<String> SHARED_SECRET =
            Metadata.Key.of("x-grpc-secret", Metadata.ASCII_STRING_MARSHALLER);

    @Test
    void signedJournalActorReachesRosterButScheduleSemesterStateRejectsBeforeRepository()
            throws Exception {
        var signer = new InternalJwtTestFactory();
        var provider = mock(PublicKeyProvider.class);
        when(provider.getPublicKey()).thenReturn(signer.publicKey());
        var validator =
                new InternalJwtValidator(
                        provider, new InternalJwtProperties(null, 60, 0, false, null, null, null));
        var authority = mock(AssistantPermissionAuthority.class);
        when(authority.allows(any(), org.mockito.ArgumentMatchers.eq(7L), any()))
                .thenReturn(true);
        var reads = mock(AcademicReadService.class);
        when(reads.fetchHistoricalGroupMembers(7L, LocalDate.of(2026, 4, 20), 3L))
                .thenReturn(
                        new HistoricalMembershipService.RosterSnapshot(
                                7L, LocalDate.of(2026, 4, 20), 3L, List.of()));
        var semesters = mock(SemesterRepository.class);
        var semester = new Semester();
        ReflectionTestUtils.setField(semester, "id", 3L);
        when(semesters.findByIdUncached(3L)).thenReturn(Optional.of(semester));
        var groups = mock(GroupRepository.class);
        var group = mock(Group.class);
        when(group.isActive()).thenReturn(true);
        when(groups.findById(7L)).thenReturn(Optional.of(group));
        var service = mock(AcademicGrpcServiceImpl.class, CALLS_REAL_METHODS);
        ReflectionTestUtils.setField(service, "academicReadService", reads);
        ReflectionTestUtils.setField(service, "assistantPermissionAuthority", authority);
        ReflectionTestUtils.setField(service, "semesterRepository", semesters);
        ReflectionTestUtils.setField(service, "groupRepository", groups);
        String sharedSecret = UUID.randomUUID().toString();
        var sharedAuth = new GrpcAuthInterceptor();
        ReflectionTestUtils.setField(sharedAuth, "expectedSecret", sharedSecret);
        Server server =
                NettyServerBuilder.forAddress(new InetSocketAddress("127.0.0.1", 0))
                        .addService(
                                ServerInterceptors.intercept(
                                        service,
                                        sharedAuth,
                                        new StudentHomeworkGrpcIdentityInterceptor(validator),
                                        new AssignmentCloseServiceIdentityInterceptor("")))
                        .build()
                        .start();
        ManagedChannel channel =
                NettyChannelBuilder.forAddress("127.0.0.1", server.getPort())
                        .usePlaintext()
                        .build();
        try {
            Metadata headers = new Metadata();
            headers.put(SHARED_SECRET, sharedSecret);
            headers.put(
                    StudentHomeworkGrpcIdentityInterceptor.INTERNAL_TOKEN,
                    signer.validToken(
                            41L, UUID.randomUUID(), 1, 1, "HEADMAN", "ACTIVE", 7L, true, false));
            var stub =
                    AcademicGrpcServiceGrpc.newBlockingStub(channel)
                            .withInterceptors(MetadataUtils.newAttachHeadersInterceptor(headers))
                            .withDeadlineAfter(3, TimeUnit.SECONDS);
            assertThat(
                            stub.checkAssistantPermission(
                                            AssistantPermissionCheckRequest.newBuilder()
                                                    .setGroupId(7)
                                                    .setPermission("VIEW_STATS")
                                                    .build())
                                    .getAllowed())
                    .isTrue();
            var roster =
                    stub.getGroupMembers(
                            GroupMembersRequest.newBuilder()
                                    .setGroupId(7)
                                    .setAsOfDate("2026-04-20")
                                    .setSemesterId(3)
                                    .build());
            assertThat(roster.getAsOfDate()).isEqualTo("2026-04-20");
            assertThat(roster.getSemesterId()).isEqualTo(3);
            assertThatThrownBy(
                            () ->
                                    stub.getSemesterState(
                                            SemesterStateRequest.newBuilder()
                                                    .setSemesterId(3)
                                                    .build()))
                    .isInstanceOfSatisfying(
                            StatusRuntimeException.class,
                            error -> {
                                assertThat(error.getStatus().getCode())
                                        .isEqualTo(Status.Code.UNAUTHENTICATED);
                                assertThat(error.getStatus().getDescription())
                                        .isEqualTo("Service identity requires authenticated TLS");
                            });
            verifyNoInteractions(semesters);
            // Same intended journal result as the retained RED, now using the user boundary.
            assertThat(
                            stub.getHeadmanJournalSemesterState(
                                            HeadmanJournalSemesterStateRequest.newBuilder()
                                                    .setGroupId(7)
                                                    .setSemesterId(3)
                                                    .build())
                                    .getId())
                    .isEqualTo(3);
            Metadata sharedOnly = new Metadata();
            sharedOnly.put(SHARED_SECRET, sharedSecret);
            var unsigned =
                    AcademicGrpcServiceGrpc.newBlockingStub(channel)
                            .withInterceptors(
                                    MetadataUtils.newAttachHeadersInterceptor(sharedOnly))
                            .withDeadlineAfter(3, TimeUnit.SECONDS);
            var request =
                    HeadmanJournalSemesterStateRequest.newBuilder()
                            .setGroupId(7)
                            .setSemesterId(3)
                            .build();
            assertStatus(
                    () -> unsigned.getHeadmanJournalSemesterState(request),
                    Status.Code.UNAUTHENTICATED);
            Metadata forgedHeaders = new Metadata();
            forgedHeaders.put(SHARED_SECRET, sharedSecret);
            forgedHeaders.put(
                    StudentHomeworkGrpcIdentityInterceptor.INTERNAL_TOKEN,
                    new InternalJwtTestFactory()
                            .validToken(
                                    41L,
                                    UUID.randomUUID(),
                                    1,
                                    1,
                                    "HEADMAN",
                                    "ACTIVE",
                                    7L,
                                    true,
                                    false));
            var forged =
                    AcademicGrpcServiceGrpc.newBlockingStub(channel)
                            .withInterceptors(
                                    MetadataUtils.newAttachHeadersInterceptor(forgedHeaders))
                            .withDeadlineAfter(3, TimeUnit.SECONDS);
            assertStatus(
                    () -> forged.getHeadmanJournalSemesterState(request),
                    Status.Code.UNAUTHENTICATED);
            assertStatus(
                    () ->
                            stub.getHeadmanJournalSemesterState(
                                    request.toBuilder().setGroupId(9).build()),
                    Status.Code.PERMISSION_DENIED);
            assertStatus(
                    () ->
                            stub.getHeadmanJournalSemesterState(
                                    request.toBuilder().setSemesterId(0).build()),
                    Status.Code.INVALID_ARGUMENT);
            assertStatus(
                    () ->
                            stub.getHeadmanJournalSemesterState(
                                    request.toBuilder().setSemesterId(999).build()),
                    Status.Code.NOT_FOUND);
            semester.setArchived(true);
            semester.setStateVersion(9);
            semester.setArchiveTransition(
                    ru.rutcampustrack.academic.contract.enums.SemesterTransition.RESTORING);
            var archived = stub.getHeadmanJournalSemesterState(request);
            assertThat(archived.getArchived()).isTrue();
            assertThat(archived.getWriteBlocked()).isTrue();
            assertThat(archived.getStateVersion()).isEqualTo(9);
            assertThat(archived.getTransition()).isEqualTo(SemesterTransition.RESTORING);
            semester.setArchived(false);
            semester.setArchiveTransition(
                    ru.rutcampustrack.academic.contract.enums.SemesterTransition.ARCHIVING);
            assertThat(stub.getHeadmanJournalSemesterState(request).getWriteBlocked()).isTrue();
            semester.setArchiveTransition(
                    ru.rutcampustrack.academic.contract.enums.SemesterTransition.NONE);
            semester.setReleasePending(true);
            var releasing = stub.getHeadmanJournalSemesterState(request);
            assertThat(releasing.getArchived()).isFalse();
            assertThat(releasing.getWriteBlocked()).isTrue();
            assertThat(releasing.getReleasePending()).isTrue();
            semester.setReleasePending(false);
            assertThat(stub.getHeadmanJournalSemesterState(request).getWriteBlocked()).isFalse();
            semester.setArchiveTransition(
                    ru.rutcampustrack.academic.contract.enums.SemesterTransition.DELETING);
            semester.setDeletionPhase(
                    ru.rutcampustrack.academic.contract.enums.SemesterDeletionPhase.PREPARING);
            semester.setTransitionOperationId(UUID.randomUUID());
            assertStatus(
                    () -> stub.getHeadmanJournalSemesterState(request), Status.Code.NOT_FOUND);
        } finally {
            channel.shutdownNow().awaitTermination(5, TimeUnit.SECONDS);
            server.shutdownNow().awaitTermination(5, TimeUnit.SECONDS);
        }
    }

    private static void assertStatus(Runnable action, Status.Code code) {
        assertThatThrownBy(action::run)
                .isInstanceOfSatisfying(
                        StatusRuntimeException.class,
                        error -> assertThat(error.getStatus().getCode()).isEqualTo(code));
    }
}
