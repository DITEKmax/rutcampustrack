package ru.rutcampustrack.academic.group;

import org.springframework.hateoas.EntityModel;
import org.springframework.hateoas.server.RepresentationModelAssembler;
import org.springframework.stereotype.Component;
import ru.rutcampustrack.academic.contract.dto.group.GroupResponse;
import ru.rutcampustrack.academic.contract.dto.group.AdminGroupResponse;
import ru.rutcampustrack.academic.contract.dto.group.AdminGroupStatus;
import ru.rutcampustrack.academic.entity.Group;
import ru.rutcampustrack.academic.repository.GroupRegistryReadRepository;

import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.linkTo;
import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.methodOn;

/**
 * Assembles Group entity into HATEOAS EntityModel<GroupResponse>.
 */
@Component
public class GroupAssembler implements RepresentationModelAssembler<Group, EntityModel<GroupResponse>> {

    private final GroupNameParser nameParser;

    public GroupAssembler(GroupNameParser nameParser) {
        this.nameParser = nameParser;
    }

    @Override
    public EntityModel<GroupResponse> toModel(Group entity) {
        GroupResponse response = toResponse(entity);
        return EntityModel.of(response,
                linkTo(methodOn(GroupController.class).getGroup(entity.getId())).withSelfRel());
    }

    public GroupResponse toResponse(Group entity) {
        return new GroupResponse(
                entity.getId(),
                entity.getName(),
                entity.getAlphabeticCode(),
                entity.getNumericCode(),
                entity.getCurrentCourse(),
                entity.getTrainingDurationYears(),
                entity.getDurationStatus(),
                entity.isActive(),
                entity.getCreatedAt()
        );
    }

    /** Convert the server-side registry projection without client-side status rules. */
    public AdminGroupResponse toRegistryResponse(GroupRegistryReadRepository.GroupRegistryRow row) {
        ProgramType programType = row.programTypeDigit() == null
                ? null : ProgramType.fromDigit(row.programTypeDigit());
        AdminGroupStatus status = !row.active()
                ? AdminGroupStatus.ARCHIVED
                : row.headmanFio() == null ? AdminGroupStatus.DRAFT : AdminGroupStatus.ACTIVE;
        String draftReason = status == AdminGroupStatus.DRAFT
                ? "Староста не назначен" : null;
        return new AdminGroupResponse(
                row.id(), row.name(), row.alphabeticCode(), row.numericCode(), row.currentCourse(),
                row.programTypeDigit(), programType == null ? null : programType.getStudyMode(),
                programType == null ? null : programType.getEducationLevel(),
                row.trainingDurationYears(), row.durationStatus(), status, draftReason, row.studentCount(),
                row.headmanFio(), row.createdAt());
    }

    public AdminGroupResponse toCreatedRegistryResponse(Group entity) {
        ProgramType programType = ProgramType.fromDigit(entity.getNumericCode().charAt(1) - '0');
        return new AdminGroupResponse(
                entity.getId(), entity.getName(), entity.getAlphabeticCode(),
                entity.getNumericCode(), entity.getCurrentCourse(),
                programType.getDigit(), programType.getStudyMode(), programType.getEducationLevel(),
                entity.getTrainingDurationYears(), entity.getDurationStatus(),
                AdminGroupStatus.DRAFT, "Староста не назначен", 0L, null,
                entity.getCreatedAt());
    }

}
