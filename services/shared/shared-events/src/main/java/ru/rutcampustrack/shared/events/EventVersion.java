package ru.rutcampustrack.shared.events;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Версия schema события. Читается reflection'ом в
 * {@link AbstractEventPublisher} и кладётся в поле
 * {@link DomainEvent#getEventVersion()} перед отправкой.
 *
 * <p>Отсутствие аннотации = версия 1 (неявная совместимость с первыми
 * событиями, которые были добавлены до introduction schema-версионирования).
 *
 * <p>Семантика инкремента:
 * <ul>
 *   <li>+1 — breaking change (удалили/переименовали поле, изменили тип).</li>
 *   <li>minor-добавления полей не требуют bump'а — consumer'ы читают через
 *       {@code FAIL_ON_UNKNOWN_PROPERTIES=false}.</li>
 * </ul>
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Inherited
public @interface EventVersion {

    int value() default 1;
}
