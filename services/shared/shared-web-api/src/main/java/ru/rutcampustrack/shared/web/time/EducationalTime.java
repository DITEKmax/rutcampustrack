package ru.rutcampustrack.shared.web.time;

import java.time.Clock;
import java.util.Objects;

/** Explicit educational date/window time; never an unqualified Clock injection candidate. */
public record EducationalTime(Clock clock) {
    public EducationalTime {
        Objects.requireNonNull(clock, "clock");
    }
}
