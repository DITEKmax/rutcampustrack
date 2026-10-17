package ru.rutcampustrack.academic.security;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import ru.rutcampustrack.academic.contract.enums.UserRole;

/**
 * Annotation for controller methods that require specific user roles. Enforced by {@link
 * RoleCheckAspect} via AOP.
 *
 * <p>Usage:
 *
 * <pre>
 *   {@literal @}RequireRole({UserRole.ADMIN})
 *   public ResponseEntity<?> createUser(...) { ... }
 * </pre>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RequireRole {
    UserRole[] value();

    String[] admin() default {};

    boolean adminShared() default false;

    boolean adminAny() default false;
}
