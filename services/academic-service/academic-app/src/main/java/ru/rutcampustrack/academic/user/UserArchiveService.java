package ru.rutcampustrack.academic.user;

import org.springframework.cache.CacheManager;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

import ru.rutcampustrack.academic.contract.dto.user.UserArchiveModels.*;
import ru.rutcampustrack.academic.contract.enums.UserRole;
import ru.rutcampustrack.academic.grpc.AttendanceUserImpactGrpcClient;
import ru.rutcampustrack.academic.security.RequestContext;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

@Service
public class UserArchiveService {
    @org.springframework.beans.factory.annotation.Autowired
    private ru.rutcampustrack.academic.identityoperation.AcademicUserIdentityOperations
            identityOperations;

    @org.springframework.beans.factory.annotation.Autowired
    private ru.rutcampustrack.academic.identitycatalog.AuthIdentityDataClient identities;

    @org.springframework.beans.factory.annotation.Autowired
    private ru.rutcampustrack.academic.identityoperation.AcademicIdentityReadFence fences;

    private final TransactionTemplate snapshot;
    private final UserArchiveRepository repository;
    private final UserService users;
    private final AuthUserArchiveClient auth;
    private final AttendanceUserImpactGrpcClient attendance;
    private final RequestContext context;
    private final TransactionTemplate tx;
    private final CacheManager caches;
    private final ApplicationEventPublisher events;

    public UserArchiveService(
            UserArchiveRepository repository,
            UserService users,
            AuthUserArchiveClient auth,
            AttendanceUserImpactGrpcClient attendance,
            RequestContext context,
            PlatformTransactionManager transactionManager,
            @Nullable CacheManager caches,
            ApplicationEventPublisher events) {
        this.repository = repository;
        this.users = users;
        this.auth = auth;
        this.attendance = attendance;
        this.context = context;
        this.tx = new TransactionTemplate(transactionManager);
        this.snapshot = new TransactionTemplate(transactionManager);
        snapshot.setReadOnly(true);
        snapshot.setIsolationLevel(
                org.springframework.transaction.TransactionDefinition.ISOLATION_REPEATABLE_READ);
        this.caches = caches;
        this.events = events;
    }

    public Preview preview(long target) {
        long owner = owner(target);
        long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(3);
        var actor =
                identities.checkAdminAccess(java.util.List.of("USERS_ARCHIVE"), false, deadline);
        var identity =
                identities.readServiceIdentity(java.util.List.of(target), deadline).get(target);
        var local =
                snapshot.execute(
                        status -> {
                            fences.requireRegistryReadable();
                            repository.requireIdentityRevision(identity);
                            repository.checkArchiveSafeguards(owner, target);
                            var observed = repository.observe(target);
                            if ("archived".equals(observed.status()))
                                UserArchiveRepository.conflict("already_archived");
                            return observed;
                        });
        var after = identities.readServiceIdentity(java.util.List.of(target), deadline).get(target);
        if (!identity.equals(after)
                || !actor.equals(
                        identities.checkAdminAccess(
                                java.util.List.of("USERS_ARCHIVE"), false, deadline)))
            throw io.grpc.Status.UNAVAILABLE
                    .withDescription("archive preview changed")
                    .asRuntimeException();
        var remote = attendance.preview(target);
        Instant expires = Instant.now().plus(Duration.ofMinutes(10));
        String digest =
                UserArchiveRepository.digest(
                        "user-archive-v1:"
                                + UUID.randomUUID()
                                + ":"
                                + owner
                                + ":"
                                + target
                                + ":"
                                + local.digest()
                                + ":"
                                + remote.getSnapshotDigest()
                                + ":"
                                + remote.getObservedAtEpochMs());
        tx.executeWithoutResult(status -> repository.savePreview(owner, local, digest, expires));
        return new Preview(
                target,
                local.groupCount(),
                remote.getAttendanceMarksCount(),
                local.headmanCount(),
                local.soleTeacherCount(),
                local.requiresPassword(),
                local.observedAt(),
                Instant.ofEpochMilli(remote.getObservedAtEpochMs()),
                remote.getSnapshotDigest(),
                digest,
                expires);
    }

    public void archive(long target, ArchiveRequest request) {
        owner(target);
        identityOperations.archive(target, request);
    }

    public void restore(long target, RestoreRequest request) {
        owner(target);
        identityOperations.restore(target, request);
    }

    private long owner(long target) {
        if (target <= 0)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "id must be positive");
        if (context.getRole() != UserRole.ADMIN
                || context.getUserId() == null
                || context.getUserId() <= 0) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Нужна роль ADMIN");
        }
        return context.getUserId();
    }

    private void evictAfterCommit(long target, java.util.List<Long> groups) {
        if (caches == null) return;
        TransactionSynchronizationManager.registerSynchronization(
                new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        var userCache = caches.getCache("users");
                        if (userCache != null) userCache.evict(target);
                        for (long group : groups) {
                            var members = caches.getCache("group_members");
                            if (members != null) members.evict(group);
                            var groupCache = caches.getCache("groups");
                            if (groupCache != null) groupCache.evict(group);
                            var rbac = caches.getCache("rbac");
                            if (rbac != null) rbac.evict(target + ":" + group);
                        }
                    }
                });
    }
}
