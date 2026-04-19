package ru.rutcampustrack.shared.web.audit;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marker annotation (NEW-72): помечает метод контроллера/сервиса, вызов
 * которого должен попасть в audit log.
 *
 * <p>В M01 — только marker + aspect-заглушка ({@link AdminActionAspect}).
 * Реальный audit handler (запись в Loki с userId/action/resource/outcome)
 * будет реализован в M04 (observability).
 *
 * @param value короткое имя действия для audit-записи (например {@code "user.archive"}).
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface AdminAction {

    String value() default "";
}
