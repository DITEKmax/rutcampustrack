package ru.rutcampustrack.academic.user;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PagedResourcesAssembler;
import org.springframework.hateoas.CollectionModel;
import org.springframework.hateoas.EntityModel;
import org.springframework.hateoas.PagedModel;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import ru.rutcampustrack.academic.contract.api.UserApi;
import ru.rutcampustrack.academic.contract.dto.user.CreateUserRequest;
import ru.rutcampustrack.academic.contract.dto.user.PatchUserRequest;
import ru.rutcampustrack.academic.contract.dto.user.TransferStudentRequest;
import ru.rutcampustrack.academic.contract.dto.user.UpdateAvatarRequest;
import ru.rutcampustrack.academic.contract.dto.user.UpdateUserRequest;
import ru.rutcampustrack.academic.contract.dto.user.UserCreatedResponse;
import ru.rutcampustrack.academic.contract.dto.user.UserResponse;
import ru.rutcampustrack.academic.contract.dto.user.UserSummaryResponse;
import ru.rutcampustrack.academic.contract.dto.user.TeacherLookupResponse;
import ru.rutcampustrack.academic.contract.enums.AccountStatus;
import ru.rutcampustrack.academic.contract.enums.UserRole;
import ru.rutcampustrack.academic.entity.User;
import ru.rutcampustrack.academic.repository.UserRoleGrantReader;
import ru.rutcampustrack.academic.security.RequireRole;
import ru.rutcampustrack.shared.web.audit.AdminAction;

import java.util.List;

import static ru.rutcampustrack.academic.contract.enums.UserRole.ADMIN;
import static ru.rutcampustrack.academic.contract.enums.UserRole.STUDENT;
import static ru.rutcampustrack.academic.contract.enums.UserRole.TEACHER;

/**
 * REST controller implementing UserApi contract.
 * Delegates all business logic to UserService.
 * Role enforcement via @RequireRole AOP aspect.
 */
@RestController
public class UserController implements UserApi {

    private final UserService userService;
    private final UserAssembler userAssembler;
    private final UserRoleGrantReader grantReader;
    private final UserArchiveService archiveService;

    public UserController(UserService userService,
                          UserAssembler userAssembler,
                          UserRoleGrantReader grantReader,
                          UserArchiveService archiveService) {
        this.userService = userService;
        this.userAssembler = userAssembler;
        this.grantReader = grantReader;
        this.archiveService = archiveService;
    }

    @Override
    @RequireRole({ADMIN})
    @AdminAction("user.create")
    public ResponseEntity<EntityModel<UserCreatedResponse>> createUser(CreateUserRequest request) {
        return ResponseEntity.status(201).body(userService.createUser(request));
    }

    @Override
    @RequireRole({ADMIN})
    public ResponseEntity<EntityModel<UserResponse>> getUser(Long id) {
        User user = userService.findUserById(id);
        // BUG-006: ADMIN видит initialPassword пока пользователь его не сменил.
        return ResponseEntity.ok(userAssembler.toAdminModel(user));
    }

    @Override
    @RequireRole({ADMIN})
    public ResponseEntity<PagedModel<EntityModel<UserResponse>>> listUsers(
            String search,
            String role,
            AccountStatus status,
            String roleStatus,
            Pageable pageable,
            PagedResourcesAssembler<UserResponse> assembler) {
        Page<User> page = userService.listUsers(search, role, status, roleStatus, pageable);
        var grants = grantReader.findByUserIds(page.getContent().stream().map(User::getId).toList());
        Page<UserResponse> responsePage = page.map(u -> userAssembler.toResponse(
                u, false, grants.getOrDefault(u.getId(), java.util.List.of())));
        return ResponseEntity.ok(assembler.toModel(responsePage,
                response -> EntityModel.of(response)));
    }

    @Override
    @RequireRole({ADMIN})
    @AdminAction("user.role.update")
    public ResponseEntity<EntityModel<UserResponse>> updateRoleGrant(
            Long id,
            String role,
            ru.rutcampustrack.academic.contract.dto.user.RoleGrantUpdateRequest request) {
        User user = userService.updateRoleGrant(id, role, request);
        return ResponseEntity.ok(userAssembler.toAdminModel(user));
    }

    @Override
    @RequireRole({ADMIN})
    @AdminAction("user.update")
    public ResponseEntity<EntityModel<UserResponse>> updateUser(Long id, UpdateUserRequest request) {
        User user = userService.updateUser(id, request);
        return ResponseEntity.ok(userAssembler.toModel(user));
    }

    @Override
    @RequireRole({ADMIN})
    @AdminAction("user.patch")
    public ResponseEntity<EntityModel<UserResponse>> patchUser(Long id, PatchUserRequest request) {
        User user = userService.patchUser(id, request);
        return ResponseEntity.ok(userAssembler.toModel(user));
    }

    @Override
    @RequireRole({ADMIN})
    @AdminAction("user.archive")
    public ResponseEntity<Void> archiveUser(Long id) {
        throw new org.springframework.web.server.ResponseStatusException(
                org.springframework.http.HttpStatus.CONFLICT, "protectedroute_required");
    }

    @Override @RequireRole({ADMIN})
    public ResponseEntity<ru.rutcampustrack.academic.contract.dto.user.UserArchiveModels.Preview> previewUserArchive(Long id) {
        return ResponseEntity.ok().cacheControl(org.springframework.http.CacheControl.noStore()).body(archiveService.preview(id));
    }
    @Override @RequireRole({ADMIN}) @AdminAction("user.archive")
    public ResponseEntity<Void> archiveUserProtected(Long id,
            ru.rutcampustrack.academic.contract.dto.user.UserArchiveModels.ArchiveRequest request) {
        archiveService.archive(id,request);
        return ResponseEntity.noContent().cacheControl(org.springframework.http.CacheControl.noStore()).build();
    }
    @Override @RequireRole({ADMIN}) @AdminAction("user.restore")
    public ResponseEntity<Void> restoreUser(Long id,
            ru.rutcampustrack.academic.contract.dto.user.UserArchiveModels.RestoreRequest request) {
        archiveService.restore(id,request);
        return ResponseEntity.noContent().cacheControl(org.springframework.http.CacheControl.noStore()).build();
    }

    @Override
    @RequireRole({ADMIN})
    @AdminAction("user.transfer")
    public ResponseEntity<EntityModel<UserResponse>> transferStudent(Long id, TransferStudentRequest request) {
        User user = userService.transferStudent(id, request);
        return ResponseEntity.ok(userAssembler.toModel(user));
    }

    @Override
    @RequireRole({ADMIN, TEACHER, STUDENT})
    public ResponseEntity<EntityModel<UserResponse>> getMe() {
        User user = userService.getMe();
        return ResponseEntity.ok(userAssembler.toModel(user));
    }

    @Override
    @RequireRole({ADMIN, TEACHER, STUDENT})
    public ResponseEntity<EntityModel<UserResponse>> updateMyAvatar(UpdateAvatarRequest request) {
        User user = userService.updateMyAvatar(request.avatarId());
        return ResponseEntity.ok(userAssembler.toModel(user));
    }

    @Override
    @RequireRole({STUDENT})
    public ResponseEntity<CollectionModel<EntityModel<UserResponse>>> listTeachers() {
        List<User> teachers = userService.listTeachers();
        List<EntityModel<UserResponse>> models = teachers.stream()
                .map(userAssembler::toModel)
                .toList();
        return ResponseEntity.ok(CollectionModel.of(models));
    }

    @Override
    @RequireRole({STUDENT})
    public ResponseEntity<PagedModel<EntityModel<TeacherLookupResponse>>> searchTeachers(
            String search,
            org.springframework.data.domain.Pageable pageable,
            PagedResourcesAssembler<TeacherLookupResponse> assembler) {
        Page<User> page = userService.searchActiveTeachers(search, pageable);
        Page<TeacherLookupResponse> responsePage = page.map(u -> new TeacherLookupResponse(
                u.getId(), u.getDisplayName(), u.getEmployeeNumber()));
        return ResponseEntity.ok(assembler.toModel(responsePage,
                response -> EntityModel.of(response)));
    }

    @Override
    @RequireRole({STUDENT, TEACHER, ADMIN})
    public ResponseEntity<CollectionModel<EntityModel<UserSummaryResponse>>> getUsersByIds(List<Long> ids) {
        List<User> users = userService.findUsersByIds(ids);
        List<EntityModel<UserSummaryResponse>> models = users.stream()
                .map(u -> EntityModel.of(new UserSummaryResponse(u.getId(), u.getDisplayName())))
                .toList();
        return ResponseEntity.ok(CollectionModel.of(models));
    }
}
