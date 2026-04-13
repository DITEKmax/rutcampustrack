package ru.rutcampustrack.academic.user;

import org.springframework.hateoas.EntityModel;
import org.springframework.hateoas.server.RepresentationModelAssembler;
import org.springframework.stereotype.Component;
import ru.rutcampustrack.academic.contract.dto.user.UserCreatedResponse;
import ru.rutcampustrack.academic.contract.dto.user.UserResponse;
import ru.rutcampustrack.academic.entity.User;

import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.linkTo;
import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.methodOn;

/**
 * Assembles User entity into HATEOAS EntityModel<UserResponse>.
 */
@Component
public class UserAssembler implements RepresentationModelAssembler<User, EntityModel<UserResponse>> {

    @Override
    public EntityModel<UserResponse> toModel(User entity) {
        UserResponse response = toResponse(entity);
        return EntityModel.of(response,
                linkTo(methodOn(UserController.class).getUser(entity.getId())).withSelfRel());
    }

    public EntityModel<UserCreatedResponse> toCreatedModel(User entity, String plainPassword) {
        UserCreatedResponse response = new UserCreatedResponse(
                entity.getId(),
                entity.getLogin(),
                entity.getLastName(),
                entity.getFirstName(),
                entity.getMiddleName(),
                entity.getRole(),
                entity.getStatus(),
                entity.getGroupId(),
                entity.isHeadman(),
                entity.getEmployeeNumber(),
                entity.getTelegramId(),
                entity.getCreatedAt(),
                plainPassword
        );
        response.add(linkTo(methodOn(UserController.class).getUser(entity.getId())).withSelfRel());
        return EntityModel.of(response);
    }

    public UserResponse toResponse(User entity) {
        return toResponse(entity, false);
    }

    /**
     * BUG-006: когда вызывает админ — отдаём plaintext initialPassword пока он не сменён.
     * BUG-004: avatarId присутствует во всех ответах.
     */
    public UserResponse toResponse(User entity, boolean includeInitialPassword) {
        String initial = (includeInitialPassword && !entity.isPasswordChanged())
                ? entity.getInitialPassword()
                : null;
        return new UserResponse(
                entity.getId(),
                entity.getLogin(),
                entity.getLastName(),
                entity.getFirstName(),
                entity.getMiddleName(),
                entity.getRole(),
                entity.getStatus(),
                entity.getGroupId(),
                entity.isHeadman(),
                entity.getEmployeeNumber(),
                entity.getTelegramId(),
                entity.getCreatedAt(),
                entity.getAvatarId(),
                initial
        );
    }

    /** Convenience overload for admin-context list/single endpoints. */
    public EntityModel<UserResponse> toAdminModel(User entity) {
        UserResponse response = toResponse(entity, true);
        return EntityModel.of(response,
                linkTo(methodOn(UserController.class).getUser(entity.getId())).withSelfRel());
    }
}
