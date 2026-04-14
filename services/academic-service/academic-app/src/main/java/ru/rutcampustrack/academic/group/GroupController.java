package ru.rutcampustrack.academic.group;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PagedResourcesAssembler;
import org.springframework.hateoas.EntityModel;
import org.springframework.hateoas.PagedModel;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import ru.rutcampustrack.academic.contract.api.GroupApi;
import ru.rutcampustrack.academic.contract.dto.group.CreateGroupRequest;
import ru.rutcampustrack.academic.contract.dto.group.GroupResponse;
import ru.rutcampustrack.academic.contract.dto.group.GroupStatus;
import ru.rutcampustrack.academic.contract.dto.group.PromotionSummary;
import ru.rutcampustrack.academic.contract.dto.group.UpdateGroupRequest;
import ru.rutcampustrack.academic.contract.dto.user.UserResponse;
import ru.rutcampustrack.academic.entity.Group;
import ru.rutcampustrack.academic.entity.User;
import ru.rutcampustrack.academic.security.RequireRole;
import ru.rutcampustrack.academic.user.UserAssembler;

import static ru.rutcampustrack.academic.contract.enums.UserRole.ADMIN;
import static ru.rutcampustrack.academic.contract.enums.UserRole.STUDENT;
import static ru.rutcampustrack.academic.contract.enums.UserRole.TEACHER;

/**
 * REST controller implementing GroupApi contract.
 * Delegates all business logic to GroupService.
 */
@RestController
public class GroupController implements GroupApi {

    private final GroupService groupService;
    private final GroupAssembler groupAssembler;
    private final UserAssembler userAssembler;
    private final GroupPromotionService promotionService;

    public GroupController(GroupService groupService,
                           GroupAssembler groupAssembler,
                           UserAssembler userAssembler,
                           GroupPromotionService promotionService) {
        this.groupService = groupService;
        this.groupAssembler = groupAssembler;
        this.userAssembler = userAssembler;
        this.promotionService = promotionService;
    }

    @Override
    @RequireRole({ADMIN})
    public ResponseEntity<EntityModel<GroupResponse>> createGroup(CreateGroupRequest request) {
        Group group = groupService.createGroup(request);
        return ResponseEntity.status(201).body(groupAssembler.toModel(group));
    }

    @Override
    public ResponseEntity<EntityModel<GroupResponse>> getGroup(Long id) {
        Group group = groupService.findGroupById(id);
        return ResponseEntity.ok(groupAssembler.toModel(group));
    }

    @Override
    public ResponseEntity<PagedModel<EntityModel<GroupResponse>>> listGroups(
            Boolean active,
            Pageable pageable,
            PagedResourcesAssembler<GroupResponse> assembler) {
        Page<Group> page = groupService.listGroups(active, pageable);
        Page<GroupResponse> responsePage = page.map(groupAssembler::toResponse);
        return ResponseEntity.ok(assembler.toModel(responsePage,
                response -> EntityModel.of(response)));
    }

    @Override
    @RequireRole({ADMIN})
    public ResponseEntity<EntityModel<GroupResponse>> updateGroup(Long id, UpdateGroupRequest request) {
        Group group = groupService.updateGroup(id, request);
        return ResponseEntity.ok(groupAssembler.toModel(group));
    }

    @Override
    @RequireRole({ADMIN})
    public ResponseEntity<Void> deleteGroup(Long id) {
        groupService.deleteGroup(id);
        return ResponseEntity.noContent().build();
    }

    @Override
    @RequireRole({ADMIN, TEACHER})
    public ResponseEntity<PagedModel<EntityModel<GroupResponse>>> listGroupsByStatus(
            GroupStatus status,
            String search,
            org.springframework.data.domain.Pageable pageable,
            PagedResourcesAssembler<GroupResponse> assembler) {
        Page<Group> page = groupService.listGroups(status, search, pageable);
        Page<GroupResponse> responsePage = page.map(groupAssembler::toResponse);
        return ResponseEntity.ok(assembler.toModel(responsePage,
                response -> EntityModel.of(response)));
    }

    @Override
    @RequireRole({ADMIN})
    public ResponseEntity<PromotionSummary> promotePreview() {
        return ResponseEntity.ok(promotionService.preview());
    }

    @Override
    @RequireRole({ADMIN})
    public ResponseEntity<PromotionSummary> promote() {
        return ResponseEntity.ok(promotionService.execute());
    }

    @Override
    @RequireRole({STUDENT})
    public ResponseEntity<PagedModel<EntityModel<UserResponse>>> getMyGroupMembers(
            Pageable pageable,
            PagedResourcesAssembler<UserResponse> assembler) {
        Page<User> page = groupService.getMyGroupMembers(pageable);
        Page<UserResponse> responsePage = page.map(userAssembler::toResponse);
        return ResponseEntity.ok(assembler.toModel(responsePage,
                response -> EntityModel.of(response)));
    }
}
