package ru.rutcampustrack.academic.user;

import org.springframework.hateoas.EntityModel;
import org.springframework.hateoas.server.RepresentationModelAssembler;
import org.springframework.stereotype.Component;
import ru.rutcampustrack.academic.contract.dto.user.UserCreatedResponse;
import ru.rutcampustrack.academic.contract.dto.user.RoleGrantViewResponse;
import ru.rutcampustrack.academic.contract.dto.user.UserResponse;
import ru.rutcampustrack.academic.entity.User;
import ru.rutcampustrack.academic.repository.UserRoleGrantReader;

import java.util.List;

import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.linkTo;
import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.methodOn;

/**
 * Assembles User entity into HATEOAS EntityModel<UserResponse>.
 */
@Component
public class UserAssembler implements RepresentationModelAssembler<User, EntityModel<UserResponse>> {

    private final UserRoleGrantReader grantReader;

    public UserAssembler(UserRoleGrantReader grantReader) {
        this.grantReader = grantReader;
    }

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
        response.setRoles(roleViews(entity));
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
        return toResponse(entity, includeInitialPassword, roleViews(entity));
    }

    public UserResponse toResponse(User entity,
                                   boolean includeInitialPassword,
                                   List<RoleGrantViewResponse> roles) {
        String initial = (includeInitialPassword && !entity.isPasswordChanged())
                ? entity.getInitialPassword()
                : null;
        UserResponse response = new UserResponse(
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
        response.setRoles(roles);
        return response;
    }

    /** Convenience overload for admin-context list/single endpoints. */
    public EntityModel<UserResponse> toAdminModel(User entity) {
        UserResponse response = toResponse(entity, true);
        return EntityModel.of(response,
                linkTo(methodOn(UserController.class).getUser(entity.getId())).withSelfRel());
    }

    private List<RoleGrantViewResponse> roleViews(User entity) {
        List<RoleGrantViewResponse> grants = grantReader.findByUserId(entity.getId());
        if (!grants.isEmpty()) return grants;
        // Source-era fixtures can predate V24. Keep their response useful while
        // production data is projected from the durable grant table.
        String role = entity.getRole().name();
        String status = entity.getStatus().name();
        boolean active = "ACTIVE".equals(status);
        List<String> applicable = switch (role) {
            case "STUDENT" -> List.of("ACTIVE", "EXPELLED", "GRADUATED", "SUSPENDED", "ARCHIVED");
            case "TEACHER" -> List.of("ACTIVE", "DISMISSED", "SUSPENDED", "ARCHIVED");
            case "ADMIN" -> List.of("ACTIVE", "ARCHIVED");
            default -> List.of();
        };
        boolean canUpdate = ("STUDENT".equals(role) || "TEACHER".equals(role))
                && !"ARCHIVED".equals(status);
        String blockedReason = "ARCHIVED".equals(status)
                ? "Архивная роль доступна только для чтения"
                : "ADMIN".equals(role) ? "Статус ADMIN изменяется отдельным решением владельца" : null;
        return List.of(new RoleGrantViewResponse(
                role,
                status,
                entity.getGroupId(),
                null,
                active,
                !active,
                applicable,
                canUpdate,
                blockedReason));
    }
}
