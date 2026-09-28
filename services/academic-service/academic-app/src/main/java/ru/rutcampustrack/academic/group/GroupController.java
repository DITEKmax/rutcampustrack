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
import ru.rutcampustrack.academic.contract.dto.group.CreateAdminGroupRequest;
import ru.rutcampustrack.academic.contract.dto.group.AdminGroupRegistryResponse;
import ru.rutcampustrack.academic.contract.dto.group.AdminGroupResponse;
import ru.rutcampustrack.academic.contract.dto.group.AdminGroupStatus;
import ru.rutcampustrack.academic.contract.dto.group.AssignHeadmanRequest;
import ru.rutcampustrack.academic.contract.dto.group.GroupResponse;
import ru.rutcampustrack.academic.contract.dto.group.GroupStatus;
import ru.rutcampustrack.academic.contract.dto.group.HeadmanAssignmentPreviewResponse;
import ru.rutcampustrack.academic.contract.dto.group.HeadmanAssignmentResponse;
import ru.rutcampustrack.academic.contract.dto.group.HeadmanRosterResponse;
import ru.rutcampustrack.academic.contract.dto.group.PromotionSummary;
import ru.rutcampustrack.academic.contract.dto.group.PromotionPreviewRequest;
import ru.rutcampustrack.academic.contract.dto.group.PromotionExecuteRequest;
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
    private final GroupHeadmanAssignmentService headmanAssignmentService;

    public GroupController(GroupService groupService,
                           GroupAssembler groupAssembler,
                           UserAssembler userAssembler,
                           GroupPromotionService promotionService,
                           GroupHeadmanAssignmentService headmanAssignmentService) {
        this.groupService = groupService;
        this.groupAssembler = groupAssembler;
        this.userAssembler = userAssembler;
        this.promotionService = promotionService;
        this.headmanAssignmentService = headmanAssignmentService;
    }

    @Override
    @RequireRole({ADMIN})
    public ResponseEntity<EntityModel<GroupResponse>> createGroup(CreateGroupRequest request) {
        Group group = groupService.createGroup(request);
        return ResponseEntity.status(201).body(groupAssembler.toModel(group));
    }

    @Override
    @RequireRole({ADMIN})
    public ResponseEntity<AdminGroupResponse> createAdminGroup(CreateAdminGroupRequest request) {
        Group group = groupService.createAdminGroup(request);
        return ResponseEntity.status(201).body(groupAssembler.toCreatedRegistryResponse(group));
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
    public ResponseEntity<Void> restoreGroup(Long id) {
        groupService.restoreGroup(id);
        return ResponseEntity.noContent().build();
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
    public AdminGroupRegistryResponse listAdminGroups(
            AdminGroupStatus status, String search, Pageable pageable) {
        GroupService.GroupRegistryPage result = groupService.listAdminGroups(status, search, pageable);
        var items = result.page().getContent().stream()
                .map(groupAssembler::toRegistryResponse)
                .toList();
        var counts = result.counts();
        return new AdminGroupRegistryResponse(
                items,
                result.page().getNumber(),
                result.page().getSize(),
                result.page().getTotalElements(),
                result.page().getTotalPages(),
                counts.activeCount(), counts.draftCount(), counts.archivedCount());
    }

    @Override
    @RequireRole({ADMIN})
    public HeadmanRosterResponse getHeadmanRoster(Long id) {
        return headmanAssignmentService.roster(id);
    }

    @Override
    @RequireRole({ADMIN})
    public HeadmanAssignmentPreviewResponse previewHeadman(
            Long id, Long studentId) {
        return headmanAssignmentService.preview(id, studentId);
    }

    @Override
    @RequireRole({ADMIN})
    public HeadmanAssignmentResponse assignHeadman(Long id, AssignHeadmanRequest request) {
        return headmanAssignmentService.assign(id, request);
    }

    @Override
    @RequireRole({ADMIN})
    public ResponseEntity<PromotionSummary> promotePreview(PromotionPreviewRequest request) {
        return ResponseEntity.ok(promotionService.preview(request.getGroupId()));
    }

    @Override
    @RequireRole({ADMIN})
    public ResponseEntity<PromotionSummary> promote(PromotionExecuteRequest request) {
        return ResponseEntity.ok(promotionService.execute(request));
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
