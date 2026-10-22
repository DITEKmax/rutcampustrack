package ru.rutcampustrack.schedule.security;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import ru.rutcampustrack.schedule.contract.enums.UserRole;

/**
 * Annotation for controller methods that require specific user roles. Enforced by {@link
 * RoleCheckAspect} via AOP.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RequireRole {
    UserRole[] value();
}
