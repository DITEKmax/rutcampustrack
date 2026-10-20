package ru.rutcampustrack.schedule.config;

import static org.assertj.core.api.Assertions.assertThat;

import net.javacrumbs.shedlock.core.LockProvider;

import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.scheduling.config.ScheduledTaskHolder;

/** Test profile must not activate scheduling or require a DataSource for distributed locks. */
class SchedulingConfigStructureTest {
    @Test
    void testProfileDoesNotActivateSchedulerOrLockProvider() {
        try (var context = new AnnotationConfigApplicationContext()) {
            context.getEnvironment().setActiveProfiles("test");
            context.register(SchedulingConfig.class);
            context.refresh();
            assertThat(context.getBeansOfType(LockProvider.class)).isEmpty();
            assertThat(context.getBeansOfType(ScheduledTaskHolder.class)).isEmpty();
        }
    }
}
