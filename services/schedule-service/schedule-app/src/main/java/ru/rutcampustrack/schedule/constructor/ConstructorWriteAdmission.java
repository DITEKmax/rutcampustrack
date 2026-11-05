package ru.rutcampustrack.schedule.constructor;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Stops new constructor writes during rollback while accepted recovery remains available. */
@Component
public class ConstructorWriteAdmission {
    private final boolean enabled;

    public ConstructorWriteAdmission(@Value("${constructor.writes.enabled:true}") boolean enabled) {
        this.enabled = enabled;
    }

    public void requireEnabled() {
        if (!enabled) {
            throw new ConstructorLifecycleException(
                    503,
                    "CONSTRUCTOR_WRITES_DISABLED",
                    "Изменение конструктора временно недоступно");
        }
    }
}
