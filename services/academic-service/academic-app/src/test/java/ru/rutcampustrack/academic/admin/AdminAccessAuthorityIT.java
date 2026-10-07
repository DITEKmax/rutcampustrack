package ru.rutcampustrack.academic.admin;

import org.junit.jupiter.api.*;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.*;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import ru.rutcampustrack.academic.contract.dto.user.AdminAccessInput;
import ru.rutcampustrack.academic.repository.UserRoleGrantWriter;
import ru.rutcampustrack.academic.security.RequestContext;
import ru.rutcampustrack.academic.contract.enums.UserRole;
import ru.rutcampustrack.shared.security.InternalJwtClaims;
import ru.rutcampustrack.shared.security.admin.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;

/** Real PostgreSQL authority, lifecycle and races in an owned ephemeral database. */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AdminAccessAuthorityIT {
    private final PostgreSQLContainer<?> postgres=new PostgreSQLContainer<>("postgres:16-alpine").withLabel("rct.package","admin-permissions-20261008");
    private JdbcTemplate jdbc;
    private TransactionTemplate tx;
    private AdminAccessJdbcAuthority authority;
    private UserRoleGrantWriter grants;
    private RequestContext context;
    private AdminAccessService service;
    @BeforeAll void start() {postgres.start();}
    @AfterAll void stop() {postgres.stop();}
    @BeforeEach void schema() throws Exception {
        DriverManagerDataSource source=new DriverManagerDataSource(postgres.getJdbcUrl(),postgres.getUsername(),postgres.getPassword());
        String schema="admin_"+UUID.randomUUID().toString().replace("-","");
        new JdbcTemplate(source).execute("CREATE SCHEMA "+schema);source.setSchema(schema);
        jdbc=new JdbcTemplate(source);tx=new TransactionTemplate(new DataSourceTransactionManager(source));
        jdbc.execute("CREATE TABLE groups(id BIGSERIAL PRIMARY KEY,name TEXT,archived_at TIMESTAMPTZ)");
        jdbc.execute("CREATE TABLE users(id BIGINT PRIMARY KEY,status TEXT NOT NULL DEFAULT 'active',roles_version BIGINT NOT NULL DEFAULT 1)");
        jdbc.execute("""
          CREATE TABLE user_role_grants(id BIGSERIAL PRIMARY KEY,user_id BIGINT REFERENCES users(id),role TEXT,status TEXT,
             group_id BIGINT,created_at TIMESTAMPTZ,updated_at TIMESTAMPTZ,UNIQUE(user_id,role))
          """);
        jdbc.execute("""
          CREATE TABLE auth_sessions(sid UUID PRIMARY KEY,user_id BIGINT REFERENCES users(id),active_role_grant_id BIGINT,
            session_version BIGINT NOT NULL DEFAULT 1,revoked_at TIMESTAMPTZ,refresh_expires_at TIMESTAMPTZ DEFAULT NOW()+INTERVAL '1 day')
          """);
        for(long id=1;id<=6;id++) jdbc.update("INSERT INTO users(id) VALUES (?)",id);
        jdbc.update("INSERT INTO user_role_grants(user_id,role,status,created_at,updated_at) VALUES (1,'admin','active',NOW(),NOW()),(2,'admin','active',NOW(),NOW()),(3,'admin','archived',NOW(),NOW()),(4,'teacher','active',NOW(),NOW())");
        String v24=new ClassPathResource("db/migration/V24__auth_session_authority.sql").getContentAsString(StandardCharsets.UTF_8);
        jdbc.execute(v24.substring(v24.indexOf("CREATE OR REPLACE FUNCTION bump_user_roles_version()"),v24.indexOf("CREATE TABLE auth_sessions")));
        jdbc.execute(new ClassPathResource("db/migration/V51__admin_access_permissions.sql").getContentAsString(StandardCharsets.UTF_8));
        authority=new AdminAccessJdbcAuthority(jdbc);grants=new UserRoleGrantWriter(jdbc);context=new RequestContext();
        service=service(context);select(context,1);
    }
    private AdminAccessService service(RequestContext ctx) {
        return new AdminAccessService(jdbc,authority,new AcademicAdminAccess(authority,ctx,jdbc),ctx,grants);
    }
    private void select(RequestContext ctx,long id) {
        UUID session=UUID.randomUUID();
        Long grant=jdbc.queryForObject("SELECT id FROM user_role_grants WHERE user_id=? AND role='admin'",Long.class,id);
        jdbc.update("INSERT INTO auth_sessions(sid,user_id,active_role_grant_id) VALUES (?,?,?)",session,id,grant);
        ctx.setRole(UserRole.ADMIN);ctx.setUserId(id);
        ctx.setClaims(new InternalJwtClaims(id,session,1,jdbc.queryForObject("SELECT roles_version FROM users WHERE id=?",Long.class,id),"ADMIN","ACTIVE",null,false,false));
    }
    private AdminAccessProfile put(long target,String mode,List<String> permissions,long version) {
        return tx.execute(status->service.put(target,new AdminAccessInput(mode,permissions),version));
    }
    @Test void migrationMakesOnlyExistingActiveAdministratorsFullAndKeepsRevokedTombstone() {
        assertThat(authority.profile(1).full()).isTrue();assertThat(authority.profile(2).permissions()).containsExactlyElementsOf(AdminPermission.allCodes());
        assertThat(authority.profile(3)).isEqualTo(new AdminAccessProfile(3,false,null,List.of(),1));
        assertThat(authority.profile(4).version()).isZero();
        jdbc.update("DELETE FROM admin_access_profiles WHERE user_id=2");
        RequestContext missing=new RequestContext();select(missing,2);
        assertThatThrownBy(()->authority.current(missing.getClaims())).isInstanceOf(AdminAccessException.class);
    }
    @Test void limitedAssignmentRoundTripsValidatesDependenciesAndNeverDelegatesFull() {
        var limited=put(4,"LIMITED",List.of("USERS_VIEW","USERS_MANAGE"),0);
        assertThat(limited.permissions()).containsExactly("USERS_VIEW","USERS_MANAGE");assertThat(limited.full()).isFalse();
        assertThat(authority.profile(4)).isEqualTo(limited);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM user_role_grants WHERE user_id=4 AND role='teacher' AND status='active'",Long.class)).isOne();
        RequestContext actor=new RequestContext();select(actor,4);
        assertThatThrownBy(()->tx.execute(status->service(actor).put(5,new AdminAccessInput("FULL",List.of()),0L)))
            .isInstanceOf(AdminAccessException.class).hasMessageContaining("ADMIN_FULL_REQUIRED");
        assertThatThrownBy(()->put(5,"LIMITED",List.of("USERS_MANAGE"),0)).isInstanceOf(AdminAccessException.class);
        assertThatThrownBy(()->put(5,"LIMITED",List.of("USERS_VIEW","USERS_VIEW"),0)).isInstanceOf(AdminAccessException.class);
        assertThatThrownBy(()->put(5,"LIMITED",List.of("ADMIN_ACCESS_MANAGE"),0)).isInstanceOf(AdminAccessException.class);
        assertThatThrownBy(()->put(5,"LIMITED",List.of(),0)).isInstanceOf(AdminAccessException.class);
        assertThatThrownBy(()->put(5,"FULL",List.of("USERS_VIEW"),0)).isInstanceOf(AdminAccessException.class);
    }
    @Test void staleCasAndRevokeRegrantDoNotResurrectSelectionOrOtherRoleLogin() {
        put(4,"LIMITED",List.of("USERS_VIEW"),0);
        RequestContext target=new RequestContext();select(target,4);UUID oldSession=target.getClaims().sessionId();
        tx.executeWithoutResult(status->service.revoke(4,1L));
        assertThat(authority.profile(4).version()).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT active_role_grant_id IS NULL FROM auth_sessions WHERE sid=?",Boolean.class,oldSession)).isTrue();
        assertThat(jdbc.queryForObject("SELECT revoked_at IS NULL FROM auth_sessions WHERE sid=?",Boolean.class,oldSession)).isTrue();
        assertThat(jdbc.queryForObject("SELECT status FROM user_role_grants WHERE user_id=4 AND role='teacher'",String.class)).isEqualTo("active");
        put(4,"FULL",List.of(),2);
        assertThat(authority.profile(4).version()).isEqualTo(3);
        assertThatThrownBy(()->tx.executeWithoutResult(status->service.revoke(4,1L))).isInstanceOf(AdminAccessException.class)
          .satisfies(error->assertThat(((AdminAccessException)error).getBody().getProperties()).containsKey("currentProfile"));
        assertThat(jdbc.queryForObject("SELECT active_role_grant_id IS NULL FROM auth_sessions WHERE sid=?",Boolean.class,oldSession)).isTrue();
        assertThatThrownBy(()->authority.current(target.getClaims())).isInstanceOf(AdminAccessException.class);
    }
    @Test void credentialsAndActiveAdminTargetProtectionApplyToMixedRoleAccountsAndSelf() {
        put(4,"LIMITED",List.of("USERS_VIEW","USERS_MANAGE","USER_CREDENTIALS_MANAGE","USERS_ARCHIVE"),0);
        RequestContext limited=new RequestContext();select(limited,4);
        assertThat(authority.canReadCredentials(limited.getClaims(),5)).isTrue();
        assertThat(authority.canReadCredentials(limited.getClaims(),2)).isFalse();
        assertThat(authority.canReadCredentials(limited.getClaims(),4)).isFalse();
        assertThatThrownBy(()->authority.requireMutableTarget(limited.getClaims(),4)).isInstanceOf(AdminAccessException.class);
        assertThatThrownBy(()->authority.requireMutableTarget(limited.getClaims(),2)).isInstanceOf(AdminAccessException.class);
        assertThatThrownBy(()->put(1,"LIMITED",List.of("USERS_VIEW"),1)).isInstanceOf(AdminAccessException.class)
          .hasMessageContaining("ADMIN_ACCESS_SELF_CHANGE_DENIED");
    }

    @Test void plainJsonApiRoundTripsAndRejectsDirectLimitedMutationAndStaleCas() throws Exception {
        var factory=new org.springframework.aop.framework.ProxyFactory(service);
        factory.setProxyTargetClass(true);
        factory.addAdvice(new org.springframework.transaction.interceptor.TransactionInterceptor(tx.getTransactionManager(),
          new org.springframework.transaction.interceptor.MatchAlwaysTransactionAttributeSource()));
        var mvc=org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup(
          new AdminAccessController((AdminAccessService)factory.getProxy(),new AcademicAdminAccess(authority,context,jdbc)))
          .setControllerAdvice(new ru.rutcampustrack.shared.web.exception.GlobalExceptionHandler()).build();
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put("/academic/users/4/admin-access")
          .contentType("application/json").content("{\"mode\":\"LIMITED\",\"permissions\":[\"USERS_VIEW\"],\"expectedVersion\":0}"))
          .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk())
          .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.version").value(1))
          .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header().string("Cache-Control","no-store"));
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/academic/users/4/admin-access"))
          .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.permissions[0]").value("USERS_VIEW"));
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete("/academic/users/4/admin-access")
          .contentType("application/json").content("{\"expectedVersion\":0}"))
          .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isConflict())
          .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.code").value("ADMIN_ACCESS_CONFLICT"))
          .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.currentProfile.version").value(1));
        select(context,4);
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/academic/admin-access/me"))
          .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk())
          .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.mode").value("LIMITED"));
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put("/academic/users/5/admin-access")
          .contentType("application/json").content("{\"mode\":\"FULL\",\"permissions\":[],\"expectedVersion\":0}"))
          .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isForbidden())
          .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.code").value("ADMIN_FULL_REQUIRED"));
        assertThat(authority.profile(5).assigned()).isFalse();
    }
    @Test void everyAssemblerCredentialProjectionUsesFreshCapabilityAndTargetProtection() {
        put(4,"LIMITED",List.of("USERS_VIEW","USERS_MANAGE"),0);
        var assembler=new ru.rutcampustrack.academic.user.UserAssembler(new ru.rutcampustrack.academic.repository.UserRoleGrantReader(jdbc));
        org.springframework.test.util.ReflectionTestUtils.setField(assembler,"adminAccess",new AcademicAdminAccess(authority,context,jdbc));
        var user=new ru.rutcampustrack.academic.entity.User();org.springframework.test.util.ReflectionTestUtils.setField(user,"id",5L);user.setRole(UserRole.STUDENT);
        user.setStatus(ru.rutcampustrack.academic.contract.enums.AccountStatus.ACTIVE);user.setInitialPassword("synthetic-initial");
        assertThat(assembler.toAdminModel(user).getContent().getInitialPassword()).isEqualTo("synthetic-initial");
        assertThat(assembler.toCreatedModel(user,"synthetic-created").getContent().getInitialPassword()).isEqualTo("synthetic-created");
        select(context,4);
        assertThat(assembler.toAdminModel(user).getContent().getInitialPassword()).isNull();
        assertThat(assembler.toCreatedModel(user,"synthetic-created").getContent().getInitialPassword()).isNull();
        select(context,1);put(4,"LIMITED",List.of("USERS_VIEW","USER_CREDENTIALS_MANAGE"),1);select(context,4);
        assertThat(assembler.toAdminModel(user).getContent().getInitialPassword()).isEqualTo("synthetic-initial");
        org.springframework.test.util.ReflectionTestUtils.setField(user,"id",2L);user.setRole(UserRole.ADMIN);
        assertThat(assembler.toAdminModel(user).getContent().getInitialPassword()).isNull();
        org.springframework.test.util.ReflectionTestUtils.setField(user,"id",5L);user.setPasswordChanged(true);
        assertThat(assembler.toAdminModel(user).getContent().getInitialPassword()).isNull();
    }
    @Test void groupPickerIncludesDraftAndExcludesArchivedWithoutRegistryMetadata() {
        jdbc.update("INSERT INTO groups(name,archived_at) VALUES ('Тест-2',NULL),('Тест-1',NULL),('Тест-старый',NOW()),('Другой',NULL)");
        var picker=new ru.rutcampustrack.academic.group.GroupOptionsController(jdbc);
        var page=picker.options("тест",0,1);
        assertThat(page.totalElements()).isEqualTo(2);assertThat(page.totalPages()).isEqualTo(2);
        assertThat(page.items().getFirst().name()).isEqualTo("Тест-1");
        assertThat(picker.options("тест",1,1).items().getFirst().name()).isEqualTo("Тест-2");
        assertThatThrownBy(()->picker.options("",0,101)).isInstanceOf(AdminAccessException.class);
    }

    @Test void canonicalHeadmanAssignmentProtectsBothCandidateAndPreviousActiveAdmin() {
        put(4,"LIMITED",List.of("USERS_VIEW"),0);
        put(6,"LIMITED",List.of("GROUPS_VIEW","GROUPS_MANAGE"),0);select(context,6);
        var groupRepo=org.mockito.Mockito.mock(ru.rutcampustrack.academic.repository.GroupRepository.class);
        var userRepo=org.mockito.Mockito.mock(ru.rutcampustrack.academic.repository.UserRepository.class);
        var group=new ru.rutcampustrack.academic.entity.Group();org.springframework.test.util.ReflectionTestUtils.setField(group,"id",10L);group.setActive(true);
        org.mockito.Mockito.when(groupRepo.findById(10L)).thenReturn(Optional.of(group));
        Map<Long,ru.rutcampustrack.academic.entity.User> entities=new HashMap<>();
        for(long id:List.of(4L,5L,6L)) {
            var user=new ru.rutcampustrack.academic.entity.User();org.springframework.test.util.ReflectionTestUtils.setField(user,"id",id);
            entities.put(id,user);org.mockito.Mockito.when(userRepo.findById(id)).thenReturn(Optional.of(user));
            org.mockito.Mockito.when(userRepo.findByIdForUpdate(id)).thenReturn(Optional.of(user));
            org.mockito.Mockito.when(userRepo.findActiveStudentGrantGroupId(id)).thenReturn(Optional.of(10L));
        }
        var headmen=new ru.rutcampustrack.academic.group.GroupHeadmanAssignmentService(groupRepo,userRepo,grants,
            org.mockito.Mockito.mock(ru.rutcampustrack.academic.repository.HeadmanAssistantRepository.class),
            org.mockito.Mockito.mock(org.springframework.context.ApplicationEventPublisher.class),null);
        org.springframework.test.util.ReflectionTestUtils.setField(headmen,"adminAccess",new AcademicAdminAccess(authority,context,jdbc));
        org.mockito.Mockito.when(userRepo.findActiveHeadmenByGrantGroupId(10L)).thenReturn(List.of());
        assertThatThrownBy(()->tx.execute(status->headmen.assign(10L,new ru.rutcampustrack.academic.contract.dto.group.AssignHeadmanRequest(4L,null))))
            .isInstanceOf(AdminAccessException.class).hasMessageContaining("ADMIN_TARGET_PROTECTED");
        org.mockito.Mockito.when(userRepo.findActiveHeadmenByGrantGroupId(10L)).thenReturn(List.of(entities.get(4L)));
        assertThatThrownBy(()->tx.execute(status->headmen.assign(10L,new ru.rutcampustrack.academic.contract.dto.group.AssignHeadmanRequest(5L,4L))))
            .isInstanceOf(AdminAccessException.class).hasMessageContaining("ADMIN_TARGET_PROTECTED");
        assertThat(entities.get(4L).isHeadman()).isFalse();assertThat(entities.get(5L).isHeadman()).isFalse();
    }
    @Test void userHeadmanPatchRequiresGroupsManageBeforeAnyMutation() {
        put(4,"LIMITED",List.of("USERS_VIEW","USERS_MANAGE"),0);select(context,4);
        var users=org.mockito.Mockito.mock(ru.rutcampustrack.academic.repository.UserRepository.class);
        var assistants=org.mockito.Mockito.mock(ru.rutcampustrack.academic.repository.HeadmanAssistantRepository.class);
        var headmen=org.mockito.Mockito.mock(ru.rutcampustrack.academic.group.GroupHeadmanAssignmentService.class);
        var userService=users(users,assistants,null,headmen);
        long version=jdbc.queryForObject("SELECT roles_version FROM users WHERE id=5",Long.class);
        for(boolean assign:List.of(true,false)) {
            var patch=new ru.rutcampustrack.academic.contract.dto.user.PatchUserRequest(null,null,null,assign,null,null,null,null);
            assertThatThrownBy(()->tx.execute(status->userService.patchUser(5L,patch)))
                .isInstanceOf(AdminAccessException.class).hasMessageContaining("ADMIN_PERMISSION_DENIED");
        }
        org.mockito.Mockito.verifyNoInteractions(users,assistants,headmen);
        assertThat(jdbc.queryForObject("SELECT roles_version FROM users WHERE id=5",Long.class)).isEqualTo(version);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM user_role_grants WHERE user_id=5",Long.class)).isZero();
    }
    @Test void ownTeacherRoleChangeCommitsAndProjectsSuccessWithoutCredentialsFromStaleClaims() {
        grants.upsertRole(1L,"teacher","active",null);select(context,1);
        UUID sid=context.getClaims().sessionId();long oldVersion=context.getClaims().rolesVersion();
        var entity=new ru.rutcampustrack.academic.entity.User();org.springframework.test.util.ReflectionTestUtils.setField(entity,"id",1L);
        entity.setRole(UserRole.ADMIN);entity.setStatus(ru.rutcampustrack.academic.contract.enums.AccountStatus.ACTIVE);
        entity.setInitialPassword("synthetic-must-not-leak");
        var repository=org.mockito.Mockito.mock(ru.rutcampustrack.academic.repository.UserRepository.class);
        org.mockito.Mockito.when(repository.findByIdForUpdate(1L)).thenReturn(Optional.of(entity));
        org.mockito.Mockito.when(repository.save(entity)).thenReturn(entity);
        var reader=new ru.rutcampustrack.academic.repository.UserRoleGrantReader(jdbc);
        var assembler=new ru.rutcampustrack.academic.user.UserAssembler(reader);
        org.springframework.test.util.ReflectionTestUtils.setField(assembler,"adminAccess",new AcademicAdminAccess(authority,context,jdbc));
        var service=users(repository,org.mockito.Mockito.mock(ru.rutcampustrack.academic.repository.HeadmanAssistantRepository.class),assembler,null);
        var proxy=new org.springframework.aop.framework.ProxyFactory(service);proxy.setProxyTargetClass(true);
        proxy.addAdvice(new org.springframework.transaction.interceptor.TransactionInterceptor(tx.getTransactionManager(),
            new org.springframework.transaction.interceptor.MatchAlwaysTransactionAttributeSource()));
        var controller=new ru.rutcampustrack.academic.user.UserController((ru.rutcampustrack.academic.user.UserService)proxy.getProxy(),assembler,reader,null);
        var response=controller.updateRoleGrant(1L,"TEACHER",new ru.rutcampustrack.academic.contract.dto.user.RoleGrantUpdateRequest(
            ru.rutcampustrack.academic.contract.enums.RoleGrantStatus.SUSPENDED,null,null,null));
        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody().getContent().getInitialPassword()).isNull();
        assertThat(jdbc.queryForObject("SELECT status FROM user_role_grants WHERE user_id=1 AND role='teacher'",String.class)).isEqualTo("suspended");
        assertThat(jdbc.queryForObject("SELECT roles_version FROM users WHERE id=1",Long.class)).isGreaterThan(oldVersion);
        assertThat(jdbc.queryForObject("SELECT g.role FROM auth_sessions s JOIN user_role_grants g ON g.id=s.active_role_grant_id WHERE s.sid=?",String.class,sid)).isEqualTo("admin");
        assertThatThrownBy(()->authority.current(context.getClaims())).isInstanceOf(AdminAccessException.class);
    }
    private ru.rutcampustrack.academic.user.UserService users(
            ru.rutcampustrack.academic.repository.UserRepository users,
            ru.rutcampustrack.academic.repository.HeadmanAssistantRepository assistants,
            ru.rutcampustrack.academic.user.UserAssembler assembler,
            ru.rutcampustrack.academic.group.GroupHeadmanAssignmentService headmen) {
        var result=new ru.rutcampustrack.academic.user.UserService(users,assistants,
            org.mockito.Mockito.mock(ru.rutcampustrack.academic.repository.StudentGroupHistoryRepository.class),context,assembler,null,
            org.mockito.Mockito.mock(org.springframework.context.ApplicationEventPublisher.class),null,null,null,grants,
            new ru.rutcampustrack.academic.repository.UserRoleGrantReader(jdbc),headmen);
        org.springframework.test.util.ReflectionTestUtils.setField(result,"adminAccess",new AcademicAdminAccess(authority,context,jdbc));
        org.springframework.test.util.ReflectionTestUtils.setField(result,"adminAccessService",service);
        return result;
    }
    @Test void genericSemesterStatusAndOperationCannotContinueDeleteWithoutDataDelete() {
        put(4,"LIMITED",List.of("SEMESTERS_VIEW","SEMESTERS_MANAGE"),0);select(context,4);
        var semesters=org.mockito.Mockito.mock(ru.rutcampustrack.academic.repository.SemesterRepository.class);
        var operations=org.mockito.Mockito.mock(ru.rutcampustrack.academic.repository.SemesterArchiveOperationRepository.class);
        var operation=new ru.rutcampustrack.academic.entity.SemesterArchiveOperation(UUID.randomUUID(),UUID.randomUUID(),1,10,
            ru.rutcampustrack.academic.contract.enums.SemesterArchiveAction.DELETE);
        org.mockito.Mockito.when(semesters.findByIdUncached(10L)).thenReturn(Optional.of(new ru.rutcampustrack.academic.entity.Semester()));
        org.mockito.Mockito.when(operations.findTopBySemesterIdOrderByCreatedAtDescOperationIdDesc(10L)).thenReturn(Optional.of(operation));
        org.mockito.Mockito.when(operations.findById(operation.getOperationId())).thenReturn(Optional.of(operation));
        var archive=new ru.rutcampustrack.academic.semester.SemesterArchiveService(context,
            org.mockito.Mockito.mock(ru.rutcampustrack.academic.semester.SemesterArchiveCommandTransaction.class),operations,semesters,
            org.mockito.Mockito.mock(ru.rutcampustrack.academic.semester.SemesterArchiveCoordinator.class));
        org.springframework.test.util.ReflectionTestUtils.setField(archive,"adminAccess",new AcademicAdminAccess(authority,context,jdbc));
        assertThatThrownBy(()->archive.status(10)).isInstanceOf(AdminAccessException.class).hasMessageContaining("ADMIN_PERMISSION_DENIED");
        assertThatThrownBy(()->archive.getOperation(operation.getOperationId())).isInstanceOf(AdminAccessException.class).hasMessageContaining("ADMIN_PERMISSION_DENIED");
    }
    @Test void concurrentFullRevocationsSerializeAndCannotRemoveLastFull() throws Exception {
        RequestContext other=new RequestContext();select(other,2);
        ExecutorService pool=Executors.newFixedThreadPool(2);CyclicBarrier barrier=new CyclicBarrier(2);
        try {
            Future<Boolean> first=pool.submit(()->raceRevoke(service,2,barrier));
            Future<Boolean> second=pool.submit(()->raceRevoke(service(other),1,barrier));
            assertThat(List.of(first.get(10,TimeUnit.SECONDS),second.get(10,TimeUnit.SECONDS))).containsExactlyInAnyOrder(true,false);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM admin_access_profiles WHERE assigned AND mode='FULL'",Long.class)).isOne();
        } finally {pool.shutdownNow();}
    }
    private boolean raceRevoke(AdminAccessService actor,long target,CyclicBarrier barrier) throws Exception {
        barrier.await(5,TimeUnit.SECONDS);
        try {tx.executeWithoutResult(status->actor.revoke(target,1L));return true;}
        catch(AdminAccessException denied) {return false;}
    }
    @Test void actorDowngradeWhileWaitingForGlobalLockCannotUseOldFullAdmission() throws Exception {
        RequestContext other=new RequestContext();select(other,2);
        ExecutorService pool=Executors.newSingleThreadExecutor();CountDownLatch began=new CountDownLatch(1);
        try {
            Future<?>[] waiting=new Future<?>[1];
            tx.executeWithoutResult(status->{
                authority.lockGlobal();
                waiting[0]=pool.submit(()->{began.countDown();tx.executeWithoutResult(t->service(other).put(5,new AdminAccessInput("FULL",List.of()),0L));});
                try {assertThat(began.await(5,TimeUnit.SECONDS)).isTrue();} catch(InterruptedException e) {throw new RuntimeException(e);}
                service.put(2,new AdminAccessInput("LIMITED",List.of("USERS_VIEW")),1L);
            });
            assertThatThrownBy(()->waiting[0].get(10,TimeUnit.SECONDS)).isInstanceOf(ExecutionException.class).hasCauseInstanceOf(AdminAccessException.class);
            assertThat(authority.profile(5).assigned()).isFalse();
        } finally {pool.shutdownNow();}
    }
    @Test void archiveTombstoneVersionAndLastFullGuardCoverLegacyGrantLifecycle() {
        tx.executeWithoutResult(status->{authority.lockGlobal();authority.protectLastFull(2);grants.upsertRole(2L,"admin","archived",null);});
        assertThat(authority.profile(2).version()).isEqualTo(2);
        assertThatThrownBy(()->tx.executeWithoutResult(status->{authority.lockGlobal();authority.protectLastFull(1);grants.upsertRole(1L,"admin","archived",null);}))
            .isInstanceOf(AdminAccessException.class).hasMessageContaining("LAST_FULL_ADMIN");
        assertThat(authority.profile(1).full()).isTrue();
        assertThatThrownBy(()->tx.executeWithoutResult(status->grants.upsertRole(3L,"admin","active",null))).isInstanceOf(org.springframework.dao.DataAccessException.class);
    }
}
