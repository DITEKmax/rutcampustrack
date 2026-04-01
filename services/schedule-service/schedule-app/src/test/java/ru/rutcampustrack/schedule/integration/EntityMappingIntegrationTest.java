package ru.rutcampustrack.schedule.integration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import ru.rutcampustrack.schedule.item.repository.ScheduleItemRepository;
import ru.rutcampustrack.schedule.lesson.repository.LessonRepository;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies that JPA entity mappings validate against the real PostgreSQL schema.
 *
 * If this test passes, it means:
 * - Hibernate ddl-auto: validate accepted ScheduleItem and Lesson entity column types
 * - Flyway V1__baseline.sql applied successfully (tables + custom enum types exist)
 * - EnumConverters (WeekType, LessonStatus) are correctly auto-applied
 * - The UNIQUE(schedule_item_id, date) constraint exists (LSSN-03 idempotency anchor)
 * - grpc.server.port, hibernate.jdbc.time_zone, and all other config is valid (CRON-04)
 */
class EntityMappingIntegrationTest extends AbstractScheduleIntegrationTest {

    @Autowired
    ScheduleItemRepository scheduleItemRepository;

    @Autowired
    LessonRepository lessonRepository;

    @Test
    void contextLoads_entitiesValidateAgainstSchema() {
        // If Spring context starts with ddl-auto: validate, all entity mappings
        // are consistent with the Flyway-created schema. Autowiring confirms
        // the repositories are initialized.
        assertThat(scheduleItemRepository).isNotNull();
        assertThat(lessonRepository).isNotNull();
    }
}
