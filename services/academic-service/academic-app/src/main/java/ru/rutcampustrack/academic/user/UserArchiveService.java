package ru.rutcampustrack.academic.user;

import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.cache.CacheManager;
import org.springframework.lang.Nullable;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import ru.rutcampustrack.academic.contract.dto.user.UserArchiveModels.*;
import ru.rutcampustrack.academic.contract.enums.UserRole;
import ru.rutcampustrack.academic.grpc.AttendanceUserImpactGrpcClient;
import ru.rutcampustrack.academic.security.RequestContext;
import java.time.Instant;
import java.time.Duration;
import java.util.UUID;

@Service
public class UserArchiveService {
    private final UserArchiveRepository repository;
    private final UserService users;
    private final AuthUserArchiveClient auth;
    private final AttendanceUserImpactGrpcClient attendance;
    private final RequestContext context;
    private final TransactionTemplate tx;
    private final CacheManager caches;
    public UserArchiveService(UserArchiveRepository repository, UserService users, AuthUserArchiveClient auth,
            AttendanceUserImpactGrpcClient attendance, RequestContext context,
            PlatformTransactionManager transactionManager, @Nullable CacheManager caches) {
        this.repository=repository; this.users=users; this.auth=auth; this.attendance=attendance;
        this.context=context; this.tx=new TransactionTemplate(transactionManager); this.caches=caches;
    }
    public Preview preview(long target) {
        long owner=owner(target);
        var local=tx.execute(status -> {
            repository.lock(owner,target,UUID.randomUUID());
            repository.checkArchiveSafeguards(owner,target);
            var snapshot=repository.observe(target);
            if ("archived".equals(snapshot.status())) UserArchiveRepository.conflict("already_archived");
            return snapshot;
        });
        var remote=attendance.preview(target);
        Instant expires=Instant.now().plus(Duration.ofMinutes(10));
        String digest=UserArchiveRepository.digest("user-archive-v1:"+UUID.randomUUID()+":"+owner+":"+target
                +":"+local.digest()+":"+remote.getSnapshotDigest()+":"+remote.getObservedAtEpochMs());
        tx.executeWithoutResult(status -> repository.savePreview(owner,local,digest,expires));
        return new Preview(target,local.groupCount(),remote.getAttendanceMarksCount(),local.headmanCount(),
                local.soleTeacherCount(),local.requiresPassword(),local.observedAt(),
                Instant.ofEpochMilli(remote.getObservedAtEpochMs()),remote.getSnapshotDigest(),digest,expires);
    }

    public void archive(long target, ArchiveRequest request) {
        long owner=owner(target);
        boolean proof=Boolean.TRUE.equals(tx.execute(status -> prepare(owner,target,request)));
        if (proof) auth.confirm(target,request.operationId(),request.previewDigest(),request.password());
        tx.executeWithoutResult(status -> {
            // Recheck after the remote proof; changed Academic authority cannot reuse the old preview.
            boolean currentProof=prepare(owner,target,request);
            if (currentProof && !proof) UserArchiveRepository.conflict("archive_preview_stale");
            if (repository.receipt(request.operationId())!=null) return;
            var groups=repository.cacheGroups(target);
            repository.revokeHeadmanHelpers(target);
            users.archiveUser(target);
            repository.revokeHelpers(target);
            repository.saveReceipt(request.operationId(),owner,target,"ARCHIVE",request.previewDigest(),currentProof);
            evictAfterCommit(target,groups);
        });
    }

    private boolean prepare(long owner,long target,ArchiveRequest request) {
        repository.lock(owner,target,request.operationId());
        repository.checkArchiveSafeguards(owner,target);
        var receipt=repository.receipt(request.operationId());
        if (receipt!=null) {
            receipt.check(owner,target,"ARCHIVE",request.previewDigest());
            return receipt.requiresPassword() || repository.observe(target).requiresPassword();
        }
        var local=repository.observe(target);
        if ("archived".equals(local.status())) UserArchiveRepository.conflict("already_archived");
        repository.validatePreview(owner,target,request.previewDigest(),local);
        return local.requiresPassword();
    }

    public void restore(long target,RestoreRequest request) {
        long owner=owner(target);
        tx.executeWithoutResult(status -> {
            repository.lock(owner,target,request.operationId());
            var receipt=repository.receipt(request.operationId());
            if (receipt!=null) { receipt.check(owner,target,"RESTORE",null); return; }
            var local=repository.observe(target);
            if (!"archived".equals(local.status())) UserArchiveRepository.conflict("user_not_archived");
            var groups=repository.cacheGroups(target);
            repository.restore(target);
            repository.revokeHelpers(target);
            repository.saveReceipt(request.operationId(),owner,target,"RESTORE",null,false);
            evictAfterCommit(target,groups);
        });
    }
    private long owner(long target) {
        if (target<=0) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"id must be positive");
        if (context.getRole()!=UserRole.ADMIN || context.getUserId()==null || context.getUserId()<=0) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Нужна роль ADMIN");
        }
        return context.getUserId();
    }
    private void evictAfterCommit(long target,java.util.List<Long> groups) {
        if (caches==null) return;
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void afterCommit() {
                var userCache=caches.getCache("users"); if (userCache!=null) userCache.evict(target);
                for (long group:groups) {
                    var members=caches.getCache("group_members"); if (members!=null) members.evict(group);
                    var groupCache=caches.getCache("groups"); if (groupCache!=null) groupCache.evict(group);
                    var rbac=caches.getCache("rbac"); if (rbac!=null) rbac.evict(target+":"+group);
                }
            }
        });
    }
}
