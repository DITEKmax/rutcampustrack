package ru.rutcampustrack.schedule.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Activates Spring @Scheduled cron jobs.
 * Guarded with @Profile("!test") so cron jobs do NOT fire during integration tests
 * (AbstractScheduleIntegrationTest uses @ActiveProfiles("test")).
 */
@Configuration
@Profile("!test")
@EnableScheduling
public class SchedulingConfig {
}
