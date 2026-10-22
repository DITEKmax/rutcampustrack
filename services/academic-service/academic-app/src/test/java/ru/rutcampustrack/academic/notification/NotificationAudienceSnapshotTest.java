package ru.rutcampustrack.academic.notification;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import io.grpc.Status;
import io.grpc.StatusRuntimeException;

import jakarta.persistence.EntityManager;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import ru.rutcampustrack.academic.assignment.TeacherAssignmentReadAuthority;
import ru.rutcampustrack.academic.assistant.AssistantPermissionAuthority;
import ru.rutcampustrack.academic.grpc.NotificationAudienceRequest;
import ru.rutcampustrack.academic.history.HistoricalMembershipService;
import ru.rutcampustrack.academic.identitycatalog.AuthIdentityDataClient;
import ru.rutcampustrack.academic.identitycatalog.IdentityCatalogSynchronizer;
import ru.rutcampustrack.academic.identityoperation.AcademicIdentityReadFence;
import ru.rutcampustrack.academic.repository.*;
import ru.rutcampustrack.authauthority.grpc.*;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

/** Source-only focused composition cases; actual Auth/TLS/JDBC acceptance is a separate fixture. */
class NotificationAudienceSnapshotTest {
    private final IdentityCatalogSynchronizer catalog = mock(IdentityCatalogSynchronizer.class);
    private final AuthIdentityDataClient auth = mock(AuthIdentityDataClient.class);
    private final NotificationAudienceAuthority authority =
            new NotificationAudienceAuthority(catalog, auth);
    private final List<List<Long>> batches = new ArrayList<>();

    @BeforeEach
    void currentData() {
        when(catalog.watermark(anyLong())).thenReturn(9L);
        when(auth.readServiceIdentity(anyCollection(), anyLong()))
                .thenAnswer(
                        call -> {
                            assertThat(
                                            TransactionSynchronizationManager
                                                    .isActualTransactionActive())
                                    .isFalse();
                            Collection<Long> ids = call.getArgument(0);
                            batches.add(List.copyOf(ids));
                            var result = new HashMap<Long, IdentityAuthoritySnapshot>();
                            ids.forEach(id -> result.put(id, identity(id)));
                            return result;
                        });
    }

    @Test
    void batchesWholeAudienceAfterCatchupAndChecksDomainAgain() {
        var snapshot = teachers(201, 2);
        var captures = new AtomicInteger();
        var result =
                authority.resolve(
                        () -> {
                            verify(catalog).await(eq(9L), anyLong());
                            assertThat(
                                            TransactionSynchronizationManager
                                                    .isActualTransactionActive())
                                    .isFalse();
                            captures.incrementAndGet();
                            return snapshot;
                        });
        assertThat(batches)
                .allSatisfy(batch -> assertThat(batch).isNotEmpty().hasSizeLessThanOrEqualTo(200));
        var requested = batches.stream().flatMap(Collection::stream).toList();
        assertThat(requested).hasSize(201).doesNotHaveDuplicates();
        assertThat(new HashSet<>(requested)).isEqualTo(snapshot.candidates().keySet());
        assertThat(captures).hasValue(2);
        assertThat(result.getRecipientsCount()).isEqualTo(201);
        assertThat(result.getRecipientsList())
                .allSatisfy(
                        recipient ->
                                assertThat(recipient.getRolesList()).containsExactly("TEACHER"));
    }

    @Test
    void activeAuthAdminPermissionDataFiltersOnlyClosedCatalogPermission() {
        var snapshot =
                new NotificationAudienceAuthority.LocalSnapshot(
                        UUID.randomUUID().toString(),
                        "group.renamed",
                        Map.of(1L, new TreeSet<>(Set.of("ADMIN", "TEACHER"))),
                        Map.of(
                                1L,
                                new NotificationAudienceAuthority.ProfileStamp(
                                        2, 5, Map.of("TEACHER", 0L))),
                        Map.of(8L, 3L),
                        "GROUPS_MANAGE");
        var admin =
                identity(1).toBuilder()
                        .addGrants(
                                AuthorityRoleGrant.newBuilder()
                                        .setGrantId(100)
                                        .setRole("ADMIN")
                                        .setStatus("ACTIVE"))
                        .setAdminProfile(
                                AuthorityAdminProfile.newBuilder()
                                        .setAssigned(true)
                                        .setMode("LIMITED")
                                        .setVersion(4)
                                        .addPermissions("USERS_VIEW"))
                        .build();
        when(auth.readServiceIdentity(anyCollection(), anyLong())).thenReturn(Map.of(1L, admin));
        var result = authority.resolve(() -> snapshot);
        assertThat(result.getRecipients(0).getRolesList()).containsExactly("TEACHER");
    }

    @Test
    void unavailableSecondBatchNeverReturnsTheFirstBatch() {
        var calls = new AtomicInteger();
        when(auth.readServiceIdentity(anyCollection(), anyLong()))
                .thenAnswer(
                        call -> {
                            Collection<Long> ids = call.getArgument(0);
                            if (calls.incrementAndGet() == 2)
                                throw Status.DEADLINE_EXCEEDED.asRuntimeException();
                            var result = new HashMap<Long, IdentityAuthoritySnapshot>();
                            ids.forEach(id -> result.put(id, identity(id)));
                            return result;
                        });
        assertUnavailable(() -> authority.resolve(() -> teachers(201, 2)));
        assertThat(calls).hasValue(2);
    }

    @Test
    void groupOrProfileChangeDuringAuthReadRejectsWholeResult() {
        var captures = new AtomicInteger();
        assertUnavailable(
                () ->
                        authority.resolve(
                                () -> teachers(1, captures.incrementAndGet() == 1 ? 2 : 3)));
        assertThat(captures).hasValue(2);
    }

    @Test
    void catalogChangeRejectsAnOtherwiseStableCandidateSet() {
        when(catalog.watermark(anyLong())).thenReturn(9L, 10L);
        assertUnavailable(() -> authority.resolve(() -> teachers(1, 2)));
    }

    @Test
    void unknownAccountStateAndUnknownGrantNeverBecomeRecipients() {
        when(auth.readServiceIdentity(anyCollection(), anyLong()))
                .thenReturn(Map.of(1L, identity(1).toBuilder().setAccountStateValue(999).build()));
        assertUnavailable(() -> authority.resolve(() -> teachers(1, 2)));
        when(auth.readServiceIdentity(anyCollection(), anyLong()))
                .thenReturn(
                        Map.of(
                                1L,
                                identity(1).toBuilder()
                                        .addGrants(
                                                AuthorityRoleGrant.newBuilder()
                                                        .setGrantId(2)
                                                        .setRole("FUTURE_ROLE")
                                                        .setStatus("ACTIVE"))
                                        .build()));
        assertUnavailable(() -> authority.resolve(() -> teachers(1, 2)));
    }

    @Test
    void ambientTransactionDeniesBeforeAnyAuthCall() {
        TransactionSynchronizationManager.setActualTransactionActive(true);
        try {
            assertUnavailable(() -> authority.resolve(() -> teachers(1, 2)));
            verifyNoInteractions(catalog, auth);
        } finally {
            TransactionSynchronizationManager.setActualTransactionActive(false);
        }
    }

    @Test
    void pendingPublicationAndAppliedVersionMismatchCannotBecomePartialSuccess() {
        when(auth.readServiceIdentity(anyCollection(), anyLong()))
                .thenReturn(Map.of(1L, identity(1).toBuilder().setRolesVersion(6).build()));
        assertUnavailable(() -> authority.resolve(() -> teachers(1, 2)));
        when(auth.readServiceIdentity(anyCollection(), anyLong()))
                .thenReturn(
                        Map.of(
                                1L,
                                identity(1).toBuilder()
                                        .setAccountState(
                                                AuthorityAccountState
                                                        .AUTHORITY_ACCOUNT_STATE_FENCED)
                                        .build()));
        assertUnavailable(() -> authority.resolve(() -> teachers(1, 2)));
    }

    @Test
    void realResolverKeepsLocalGuardsAndSelectionInTwoReadOnlyRepeatableReadTransactions() {
        var jdbc = mock(JdbcTemplate.class);
        var fences = mock(AcademicIdentityReadFence.class);
        var entities = mock(EntityManager.class);
        var localTransactions = new AtomicInteger();
        var transactions =
                new AbstractPlatformTransactionManager() {
                    @Override
                    protected Object doGetTransaction() {
                        return new Object();
                    }

                    @Override
                    protected void doBegin(Object transaction, TransactionDefinition definition) {
                        assertThat(definition.isReadOnly()).isTrue();
                        assertThat(definition.getIsolationLevel())
                                .isEqualTo(TransactionDefinition.ISOLATION_REPEATABLE_READ);
                        localTransactions.incrementAndGet();
                    }

                    @Override
                    protected void doCommit(DefaultTransactionStatus status) {}

                    @Override
                    protected void doRollback(DefaultTransactionStatus status) {}
                };
        when(catalog.watermark(anyLong()))
                .thenAnswer(
                        call -> {
                            assertThat(
                                            TransactionSynchronizationManager
                                                    .isActualTransactionActive())
                                    .isFalse();
                            return 9L;
                        });
        doAnswer(
                        call -> {
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
                            return null;
                        })
                .when(fences)
                .requireRegistryReadable();
        when(jdbc.queryForList(anyString(), eq(Long.class)))
                .thenAnswer(
                        call -> {
                            assertThat(
                                            TransactionSynchronizationManager
                                                    .isActualTransactionActive())
                                    .isTrue();
                            return List.of();
                        });
        var resolver =
                new NotificationAudienceResolver(
                        mock(HistoricalMembershipService.class),
                        mock(UserRepository.class),
                        mock(GroupRepository.class),
                        mock(SubjectRepository.class),
                        mock(HeadmanAssistantRepository.class),
                        mock(AssistantPermissionAuthority.class),
                        mock(TeacherAssignmentReadAuthority.class),
                        jdbc,
                        fences,
                        authority,
                        transactions,
                        entities);
        var response =
                resolver.resolve(
                        NotificationAudienceRequest.newBuilder()
                                .setRequestId(UUID.randomUUID().toString())
                                .setEventType("group.promotion.completed")
                                .setOccurredAt("2043-03-10T12:00:00Z")
                                .build());
        assertThat(response.getRecipientsCount()).isZero();
        assertThat(localTransactions).hasValue(2);
        verify(fences, times(2)).requireRegistryReadable();
        verify(entities, times(2)).clear();
        verifyNoInteractions(auth);
    }

    private static NotificationAudienceAuthority.LocalSnapshot teachers(int count, long revision) {
        var candidates = new TreeMap<Long, SortedSet<String>>();
        var profiles = new TreeMap<Long, NotificationAudienceAuthority.ProfileStamp>();
        for (long id = 1; id <= count; id++) {
            candidates.put(id, new TreeSet<>(Set.of("TEACHER")));
            profiles.put(
                    id,
                    new NotificationAudienceAuthority.ProfileStamp(
                            revision, 5, Map.of("TEACHER", 0L)));
        }
        return new NotificationAudienceAuthority.LocalSnapshot(
                "00000000-0000-0000-0000-000000000001",
                "lesson.cancelled",
                candidates,
                profiles,
                Map.of(8L, 3L),
                null);
    }

    private static IdentityAuthoritySnapshot identity(long user) {
        return IdentityAuthoritySnapshot.newBuilder()
                .setUserId(user)
                .setRolesVersion(5)
                .setAcademicRevision(2)
                .setCredentialsVersion(1)
                .setTelegramBindingVersion(1)
                .setAccountState(AuthorityAccountState.AUTHORITY_ACCOUNT_STATE_ACTIVE)
                .addGrants(
                        AuthorityRoleGrant.newBuilder()
                                .setGrantId(user)
                                .setRole("TEACHER")
                                .setStatus("ACTIVE"))
                .build();
    }

    private static void assertUnavailable(Runnable action) {
        assertThatThrownBy(action::run)
                .isInstanceOfSatisfying(
                        StatusRuntimeException.class,
                        failure ->
                                assertThat(failure.getStatus().getCode())
                                        .isEqualTo(Status.Code.UNAVAILABLE));
    }
}
