package ru.rutcampustrack.academic.group;

import org.springframework.hateoas.EntityModel;
import org.springframework.hateoas.server.RepresentationModelAssembler;
import org.springframework.stereotype.Component;
import ru.rutcampustrack.academic.contract.dto.group.GroupResponse;
import ru.rutcampustrack.academic.entity.Group;

import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.linkTo;
import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.methodOn;

/**
 * Assembles Group entity into HATEOAS EntityModel<GroupResponse>.
 */
@Component
public class GroupAssembler implements RepresentationModelAssembler<Group, EntityModel<GroupResponse>> {

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
                entity.getCode(),
                entity.isActive(),
                entity.getCreatedAt()
        );
    }
}
