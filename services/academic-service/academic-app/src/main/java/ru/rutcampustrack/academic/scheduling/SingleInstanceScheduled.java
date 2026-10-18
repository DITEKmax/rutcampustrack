package ru.rutcampustrack.academic.scheduling;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Documents an intentional scheduler exception under the current single-worker deployment or for
 * state that each process must refresh independently. This marker supplies no mutex or runtime
 * coordination and makes no multi-replica guarantee. Existing DB locks, CAS and leases remain the
 * authority for durable effects.
 *
 * <p>The scheduler architecture rule requires a nonblank reason. CLASS retention lets that rule
 * inspect the declaration without running the scheduler.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.CLASS)
public @interface SingleInstanceScheduled {
    String reason();
}
