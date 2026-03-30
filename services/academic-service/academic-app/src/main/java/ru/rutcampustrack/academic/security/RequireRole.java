package ru.rutcampustrack.academic.security;

import ru.rutcampustrack.academic.contract.enums.UserRole;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation for controller methods that require specific user roles.
 * Enforced by {@link RoleCheckAspect} via AOP.
 *
 * Usage:
 * <pre>
 *   {@literal @}RequireRole({UserRole.ADMIN})
 *   public ResponseEntity<?> createUser(...) { ... }
 * </pre>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RequireRole {
    UserRole[] value();
}
