package ru.rutcampustrack.academic.integration;

import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import ru.rutcampustrack.academic.contract.dto.group.CreateGroupRequest;
import ru.rutcampustrack.academic.contract.dto.user.*;
import ru.rutcampustrack.academic.contract.dto.user.UserArchiveModels.*;
import ru.rutcampustrack.academic.contract.enums.*;
import ru.rutcampustrack.academic.grpc.AttendanceUserImpactGrpcClient;
import ru.rutcampustrack.academic.group.GroupService;
import ru.rutcampustrack.academic.security.RequestContext;
import ru.rutcampustrack.academic.user.*;
import ru.rutcampustrack.academic.repository.UserRoleGrantReader;
import ru.rutcampustrack.attendance.grpc.UserImpactSnapshot;
import java.time.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class UserArchiveLifecycleIT extends AbstractAcademicIntegrationTest {
    @Autowired JdbcTemplate jdbc;
    @Autowired UserService users;
    @Autowired GroupService groups;
    @Autowired UserArchiveRepository repository;
    @Autowired UserRoleGrantReader grants;
    @Autowired PlatformTransactionManager transactionManager;
    private final AuthUserArchiveClient auth=mock(AuthUserArchiveClient.class);
    private final AttendanceUserImpactGrpcClient attendance=mock(AttendanceUserImpactGrpcClient.class);
    private final RequestContext context=mock(RequestContext.class);
    private final List<Long> userIds=new ArrayList<>(),groupIds=new ArrayList<>();
    private List<Long> previousActive;
    private long owner,target,semester,group;
    private UUID session;
    private UserArchiveService service;
    private LocalDate today;

    @BeforeEach void seedOwnedLifecycle() {
        today=LocalDate.now(ZoneId.of("Europe/Moscow"));
        previousActive=jdbc.queryForList("SELECT id FROM semesters WHERE is_active",Long.class);
        jdbc.update("UPDATE semesters SET is_active=FALSE WHERE is_active");
        semester=jdbc.queryForObject("INSERT INTO semesters(name,date_from,date_to,is_active,created_at) VALUES(?,?,?,TRUE,NOW()) RETURNING id",
                Long.class,"user-archive-"+UUID.randomUUID(),today.minusDays(5),today.plusDays(5));
        group=groups.createGroup(new CreateGroupRequest("УАР-11"+Math.floorMod(System.nanoTime(),10))).getId();
        groupIds.add(group);
        owner=jdbc.queryForObject("""
                INSERT INTO users(login,password_hash,last_name,first_name,role,status,is_headman,password_changed,created_at,updated_at)
                VALUES (?, 'synthetic-hash','Archive','Admin','admin','active',FALSE,FALSE,NOW(),NOW()) RETURNING id
                """,Long.class,"archive-admin-"+UUID.randomUUID().toString().substring(0,8));
        userIds.add(owner);
        jdbc.update("INSERT INTO user_role_grants(user_id,role,status,created_at,updated_at) VALUES(?,'admin','active',NOW(),NOW())",owner);
        target=users.createUser(new CreateUserRequest("Archive","Student",null,UserRole.STUDENT,group,null,
                Math.abs(System.nanoTime())+100000)).getContent().getId();
        userIds.add(target);
        users.updateRoleGrant(target,"TEACHER",new RoleGrantUpdateRequest(RoleGrantStatus.ACTIVE,null,"archive-employee-"+target,null));
        jdbc.update("UPDATE users SET is_headman=TRUE WHERE id=?",target);
        jdbc.update("INSERT INTO user_role_grants(user_id,role,status,group_id,created_at,updated_at) VALUES(?,'headman','active',?,NOW(),NOW())",target,group);
        session=UUID.randomUUID();
        jdbc.update("""
                INSERT INTO auth_sessions(sid,user_id,active_role_grant_id,session_version,current_refresh_jti,refresh_expires_at,created_at,last_seen_at,auth_method)
                VALUES (?,?,(SELECT id FROM user_role_grants WHERE user_id=? AND role='student'),1,?,NOW()+INTERVAL '1 day',NOW(),NOW(),'PASSWORD')
                """,session,target,target,UUID.randomUUID());
        when(context.getUserId()).thenReturn(owner); when(context.getRole()).thenReturn(UserRole.ADMIN);
        when(attendance.preview(target)).thenReturn(UserImpactSnapshot.newBuilder().setUserId(target)
                .setAttendanceMarksCount(7).setObservedAtEpochMs(Instant.now().toEpochMilli()).setSnapshotDigest("b".repeat(64)).build());
        service=new UserArchiveService(repository,users,auth,attendance,context,transactionManager,null);
    }

    @AfterEach void cleanOwnedRows() {
        for(long id:userIds) {
            jdbc.update("DELETE FROM user_archive_receipt WHERE owner_id=? OR target_id=?",id,id);
            jdbc.update("DELETE FROM user_archive_preview WHERE owner_id=? OR target_id=?",id,id);
        }
        Collections.reverse(userIds);
        for(long id:userIds) {
            jdbc.update("DELETE FROM auth_sessions WHERE user_id=?",id);
            jdbc.update("DELETE FROM headman_assistants WHERE student_id=? OR assigned_by=?",id,id);
            jdbc.update("DELETE FROM student_group_history WHERE user_id=?",id);
            jdbc.update("DELETE FROM user_role_grants WHERE user_id=?",id);
            jdbc.update("DELETE FROM users WHERE id=?",id);
        }
        for(long id:groupIds) { jdbc.update("DELETE FROM group_history_coverage WHERE group_id=?",id); jdbc.update("DELETE FROM groups WHERE id=?",id); }
        jdbc.update("DELETE FROM semesters WHERE id=?",semester);
        previousActive.forEach(id->jdbc.update("UPDATE semesters SET is_active=TRUE WHERE id=?",id));
    }

    @Test void protectedArchiveReceiptRollsBackAndRestoreRequiresFreshRoleAssignment() {
        var before=jdbc.queryForMap("SELECT login,password_hash,initial_password,telegram_id FROM users WHERE id=?",target);
        var grantIds=jdbc.queryForList("SELECT id FROM user_role_grants WHERE user_id=? ORDER BY id",Long.class,target);
        Preview preview=service.preview(target);
        assertThat(preview.attendanceMarksCount()).isEqualTo(7);
        assertThat(preview.linkedGroupCount()).isEqualTo(1);
        assertThat(preview.requiresPassword()).isTrue();
        ArchiveRequest request=new ArchiveRequest(UUID.randomUUID(),preview.previewDigest(),"synthetic-password");
        doThrow(new ResponseStatusException(HttpStatus.FORBIDDEN,"denied")).when(auth).confirm(target,request.operationId(),request.previewDigest(),request.password());
        assertThatThrownBy(()->service.archive(target,request)).isInstanceOf(ResponseStatusException.class);
        assertThat(repository.observe(target).status()).isEqualTo("active");
        reset(auth);
        UserArchiveRepository failing=spy(repository);
        doThrow(new IllegalStateException("synthetic receipt write failure")).when(failing)
                .saveReceipt(eq(request.operationId()),eq(owner),eq(target),eq("ARCHIVE"),eq(request.previewDigest()),eq(true));
        var rollback=new UserArchiveService(failing,users,auth,attendance,context,transactionManager,null);
        assertThatThrownBy(()->rollback.archive(target,request)).isInstanceOf(IllegalStateException.class);
        assertThat(repository.observe(target).status()).isEqualTo("active");
        assertThat(jdbc.queryForObject("SELECT active_role_grant_id IS NOT NULL FROM auth_sessions WHERE sid=?",Boolean.class,session)).isTrue();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM student_group_history WHERE user_id=? AND left_at IS NULL",Long.class,target)).isEqualTo(1);
        service.archive(target,request);
        long version=jdbc.queryForObject("SELECT session_version FROM auth_sessions WHERE sid=?",Long.class,session);
        service.archive(target,request);
        assertThat(jdbc.queryForObject("SELECT session_version FROM auth_sessions WHERE sid=?",Long.class,session)).isEqualTo(version);
        doThrow(new ResponseStatusException(HttpStatus.FORBIDDEN,"denied retry")).when(auth).confirm(target,request.operationId(),request.previewDigest(),request.password());
        assertThatThrownBy(()->service.archive(target,request)).isInstanceOf(ResponseStatusException.class);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM user_archive_receipt WHERE operation_id=?",Long.class,request.operationId())).isEqualTo(1);
        var closedHistory=jdbc.queryForList("SELECT id,user_id,group_id,joined_at,left_at,reason FROM student_group_history WHERE user_id=?",target);
        RestoreRequest restore=new RestoreRequest(UUID.randomUUID());
        service.restore(target,restore); service.restore(target,restore);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM user_role_grants WHERE user_id=? AND status='active'",Long.class,target)).isZero();
        assertThat(jdbc.queryForObject("SELECT group_id IS NULL AND NOT is_headman FROM users WHERE id=?",Boolean.class,target)).isTrue();
        assertThat(jdbc.queryForObject("SELECT active_role_grant_id FROM auth_sessions WHERE sid=?",Long.class,session)).isNull();
        assertThat(jdbc.queryForMap("SELECT login,password_hash,initial_password,telegram_id FROM users WHERE id=?",target)).isEqualTo(before);
        assertThat(grants.findByUserId(target).stream().filter(g->g.role().equals("STUDENT")).findFirst().orElseThrow().canUpdate()).isTrue();
        assertThatThrownBy(()->service.restore(target,new RestoreRequest(UUID.randomUUID()))).isInstanceOf(ResponseStatusException.class);
        long destination=groups.createGroup(new CreateGroupRequest("УВС-11"+Math.floorMod(System.nanoTime(),10))).getId(); groupIds.add(destination);
        users.updateRoleGrant(target,"STUDENT",new RoleGrantUpdateRequest(RoleGrantStatus.ACTIVE,destination,null,null));
        assertThat(jdbc.queryForObject("SELECT joined_at FROM student_group_history WHERE user_id=? AND left_at IS NULL",LocalDate.class,target)).isEqualTo(today);
        assertThat(jdbc.queryForList("SELECT id,user_id,group_id,joined_at,left_at,reason FROM student_group_history WHERE user_id=? AND left_at IS NOT NULL",target)).isEqualTo(closedHistory);
        assertThat(jdbc.queryForList("SELECT id FROM user_role_grants WHERE user_id=? ORDER BY id",Long.class,target)).isEqualTo(grantIds);
        assertThat(jdbc.queryForObject("SELECT active_role_grant_id FROM auth_sessions WHERE sid=?",Long.class,session)).isNull();
    }

    @Test void stalePreviewAndLegacyArchiveSeamsCannotChangeAuthority() {
        Preview preview=service.preview(target);
        jdbc.update("UPDATE users SET last_name='Changed' WHERE id=?",target);
        assertThatThrownBy(()->service.archive(target,new ArchiveRequest(UUID.randomUUID(),preview.previewDigest(),"synthetic")))
                .isInstanceOfSatisfying(ResponseStatusException.class,e->assertThat(e.getStatusCode()).isEqualTo(HttpStatus.CONFLICT));
        verifyNoInteractions(auth);
        assertThatThrownBy(()->users.patchUser(target,new PatchUserRequest(null,null,null,null,null,null,null,AccountStatus.ARCHIVED)))
                .isInstanceOf(ResponseStatusException.class);
        var controller=new UserController(users,mock(UserAssembler.class),grants,service);
        assertThatThrownBy(()->controller.archiveUser(target)).isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(()->service.preview(owner)).isInstanceOf(ResponseStatusException.class);
        assertThat(repository.observe(target).status()).isEqualTo("active");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM user_archive_receipt WHERE target_id=?",Long.class,target)).isZero();
    }
}
