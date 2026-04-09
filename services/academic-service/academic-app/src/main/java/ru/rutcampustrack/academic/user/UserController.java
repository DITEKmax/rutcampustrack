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
import ru.rutcampustrack.academic.contract.dto.user.UpdateUserRequest;
import ru.rutcampustrack.academic.contract.dto.user.UserCreatedResponse;
import ru.rutcampustrack.academic.contract.dto.user.UserResponse;
import ru.rutcampustrack.academic.contract.enums.UserRole;
import ru.rutcampustrack.academic.entity.User;
import ru.rutcampustrack.academic.security.RequireRole;

import java.util.List;

import static ru.rutcampustrack.academic.contract.enums.UserRole.ADMIN;
import static ru.rutcampustrack.academic.contract.enums.UserRole.STUDENT;

/**
 * REST controller implementing UserApi contract.
 * Delegates all business logic to UserService.
 * Role enforcement via @RequireRole AOP aspect.
 */
@RestController
public class UserController implements UserApi {

    private final UserService userService;
    private final UserAssembler userAssembler;

    public UserController(UserService userService, UserAssembler userAssembler) {
        this.userService = userService;
        this.userAssembler = userAssembler;
    }

    @Override
    @RequireRole({ADMIN})
    public ResponseEntity<EntityModel<UserCreatedResponse>> createUser(CreateUserRequest request) {
        return ResponseEntity.status(201).body(userService.createUser(request));
    }

    @Override
    @RequireRole({ADMIN})
    public ResponseEntity<EntityModel<UserResponse>> getUser(Long id) {
        User user = userService.findUserById(id);
        return ResponseEntity.ok(userAssembler.toModel(user));
    }

    @Override
    @RequireRole({ADMIN})
    public ResponseEntity<PagedModel<EntityModel<UserResponse>>> listUsers(
            UserRole role,
            Pageable pageable,
            PagedResourcesAssembler<UserResponse> assembler) {
        Page<User> page = userService.listUsers(role, pageable);
        Page<UserResponse> responsePage = page.map(userAssembler::toResponse);
        return ResponseEntity.ok(assembler.toModel(responsePage,
                response -> EntityModel.of(response)));
    }

    @Override
    @RequireRole({ADMIN})
    public ResponseEntity<EntityModel<UserResponse>> updateUser(Long id, UpdateUserRequest request) {
        User user = userService.updateUser(id, request);
        return ResponseEntity.ok(userAssembler.toModel(user));
    }

    @Override
    @RequireRole({ADMIN})
    public ResponseEntity<EntityModel<UserResponse>> patchUser(Long id, PatchUserRequest request) {
        User user = userService.patchUser(id, request);
        return ResponseEntity.ok(userAssembler.toModel(user));
    }

    @Override
    @RequireRole({ADMIN})
    public ResponseEntity<Void> archiveUser(Long id) {
        userService.archiveUser(id);
        return ResponseEntity.noContent().build();
    }

    @Override
    @RequireRole({ADMIN})
    public ResponseEntity<EntityModel<UserResponse>> transferStudent(Long id, TransferStudentRequest request) {
        User user = userService.transferStudent(id, request);
        return ResponseEntity.ok(userAssembler.toModel(user));
    }

    @Override
    @RequireRole({STUDENT})
    public ResponseEntity<EntityModel<UserResponse>> getMe() {
        User user = userService.getMe();
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
}
