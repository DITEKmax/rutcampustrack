package ru.rutcampustrack.academic.group;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import io.grpc.Status;

import org.junit.jupiter.api.Test;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import ru.rutcampustrack.academic.contract.dto.user.UserResponse;
import ru.rutcampustrack.academic.entity.User;
import ru.rutcampustrack.academic.identityoperation.AcademicIdentityReadFence;
import ru.rutcampustrack.academic.repository.UserRepository;
import ru.rutcampustrack.academic.security.RequestContext;
import ru.rutcampustrack.academic.user.UserAssembler;

import java.util.ArrayList;
import java.util.List;

/**
 * Transaction-boundary proof only; the real peer-read failure-window DB criterion remains separate.
 */
class GroupRosterSnapshotTest {
    @Test
    void guardRosterAndLocalRoleLabelsResolveBeforeSameRepeatableReadSnapshotEnds() {
        var fixture = new Fixture();
        var member = new User();
        member.setId(8L);
        var response = mock(UserResponse.class);
        var steps = new ArrayList<String>();
        doAnswer(
                        call -> {
                            inSnapshot();
                            steps.add("actor");
                            return null;
                        })
                .when(fixture.fences)
                .requireUserReadable(8L);
        doAnswer(
                        call -> {
                            inSnapshot();
                            steps.add("group");
                            return null;
                        })
                .when(fixture.fences)
                .requireGroupReadable(7L);
        when(fixture.users.findActiveStudentsByGrantGroupId(7L, fixture.page))
                .thenAnswer(
                        call -> {
                            inSnapshot();
                            steps.add("roster");
                            return new PageImpl<>(List.of(member));
                        });
        when(fixture.assembler.toResponse(member))
                .thenAnswer(
                        call -> {
                            inSnapshot();
                            steps.add("local roles and labels");
                            return response;
                        });
        var result = fixture.service.getMyGroupMembers(fixture.page);
        assertThat(result.getContent()).containsExactly(response);
        assertThat(steps).containsExactly("actor", "group", "roster", "local roles and labels");
        assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
    }

    @Test
    void pendingGroupGuardPreventsRosterAndRoleMetadataReads() {
        var fixture = new Fixture();
        var denied = Status.UNAVAILABLE.asRuntimeException();
        doThrow(denied).when(fixture.fences).requireGroupReadable(7L);
        assertThatThrownBy(() -> fixture.service.getMyGroupMembers(fixture.page)).isSameAs(denied);
        verifyNoInteractions(fixture.users, fixture.assembler);
    }

    private static void inSnapshot() {
        assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isTrue();
        assertThat(TransactionSynchronizationManager.isCurrentTransactionReadOnly()).isTrue();
        assertThat(TransactionSynchronizationManager.getCurrentTransactionIsolationLevel())
                .isEqualTo(TransactionDefinition.ISOLATION_REPEATABLE_READ);
    }

    private static final class Fixture {
        final UserRepository users = mock(UserRepository.class);
        final UserAssembler assembler = mock(UserAssembler.class);
        final AcademicIdentityReadFence fences = mock(AcademicIdentityReadFence.class);
        final PageRequest page = PageRequest.of(0, 20);
        final GroupService service;

        Fixture() {
            var context = mock(RequestContext.class);
            when(context.getUserId()).thenReturn(8L);
            when(context.getGroupId()).thenReturn(7L);
            var target = new GroupService(null, users, context, null, null);
            target.setAvailabilityJdbc(null, fences);
            ReflectionTestUtils.setField(target, "memberAssembler", assembler);
            var proxy = new ProxyFactory(target);
            proxy.addAdvice(
                    new TransactionInterceptor(
                            new LocalTransactions(), new AnnotationTransactionAttributeSource()));
            service = (GroupService) proxy.getProxy();
        }
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
