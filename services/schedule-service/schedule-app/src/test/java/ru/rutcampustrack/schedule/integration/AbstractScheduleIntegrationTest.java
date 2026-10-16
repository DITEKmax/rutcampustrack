package ru.rutcampustrack.schedule.integration;

import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;

import ru.rutcampustrack.schedule.contract.enums.LessonSlot;
import ru.rutcampustrack.schedule.item.entity.ScheduleItem;
import ru.rutcampustrack.schedule.item.repository.ScheduleItemRepository;
import ru.rutcampustrack.schedule.lesson.entity.Lesson;
import ru.rutcampustrack.schedule.lesson.repository.LessonRepository;
import ru.rutcampustrack.shared.outbox.OutboxRecord;
import ru.rutcampustrack.shared.outbox.OutboxStorage;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Abstract base class for Schedule Service integration tests.
 *
 * <p>Provides: - Static PostgreSQL 16 Testcontainer with schedule_db (shared across all tests in
 * JVM) - Flyway auto-applies V1__baseline.sql creating schedule_items + lessons tables
 * - @MockitoBean RabbitTemplate prevents DomainEventListener from failing without RabbitMQ -
 * RabbitAutoConfiguration excluded via spring.autoconfigure.exclude - @ActiveProfiles("test")
 * activates application-test.yml and excludes @Profile("!test") beans
 *
 * <p>No Redis exclusion needed — schedule-service does not use Redis. gRPC server port set to -1 in
 * application-test.yml — no Netty port binding in tests.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Import(InternalJwtTestConfig.class)
public abstract class AbstractScheduleIntegrationTest {

    @MockitoBean protected RabbitTemplate rabbitTemplate;

    @Autowired(required = false)
    protected OutboxStorage outboxStorage;

    @Autowired protected JdbcTemplate jdbcTemplate;

    @Autowired(required = false)
    private PlatformTransactionManager transactionManager;

    @Autowired private ScheduleItemRepository fixtureItems;

    @Autowired private LessonRepository fixtureLessons;

    private static final AtomicLong FIXTURE_ASSIGNMENTS = new AtomicLong(1_000_000);

    /**
     * Synthetic setup retains the same mandatory identity and wall-time snapshots as production.
     */
    protected ScheduleItem saveFixtureScheduleItem(ScheduleItem item) {
        if (item.getAssignmentId() == null)
            item.setAssignmentId(FIXTURE_ASSIGNMENTS.incrementAndGet());
        LessonSlot slot = LessonSlot.forNumber(item.getLessonNumber());
        item.setStartTime(slot.startTime());
        item.setEndTime(slot.endTime());
        if (item.getCreatedAt() == null) item.setCreatedAt(OffsetDateTime.now());
        return fixtureItems.saveAndFlush(item);
    }

    protected Lesson saveFixtureLesson(Lesson lesson) {
        ScheduleItem item = fixtureItems.findById(lesson.getScheduleItemId()).orElseThrow();
        jdbcTemplate.update(
                """
INSERT INTO schedule_assignment_fences
    (assignment_id, group_id, subject_id, semester_id, assigned_teacher_id,
     lesson_type, valid_from, cap_until_exclusive, creation_cap_until_exclusive)
VALUES (?, ?, ?, ?, 700, 'lecture', DATE '2000-01-01', DATE '2200-01-01', DATE '2200-01-01')
ON CONFLICT (assignment_id) DO NOTHING
""",
                item.getAssignmentId(),
                item.getGroupId(),
                item.getSubjectId(),
                item.getSemesterId());
        Long occurrenceId =
                jdbcTemplate.queryForObject(
                        """
                        INSERT INTO lesson_occurrences
                            (schedule_item_id, occurrence_date, assignment_id, group_id,
                             subject_id, semester_id, assigned_teacher_id, lesson_type)
                        VALUES (?, ?, ?, ?, ?, ?, 700, 'lecture') RETURNING id
                        """,
                        Long.class,
                        item.getId(),
                        lesson.getDate(),
                        item.getAssignmentId(),
                        item.getGroupId(),
                        item.getSubjectId(),
                        item.getSemesterId());
        lesson.setOccurrenceId(occurrenceId);
        lesson.setAssignmentId(item.getAssignmentId());
        lesson.setGroupId(item.getGroupId());
        lesson.setSubjectId(item.getSubjectId());
        lesson.setSemesterId(item.getSemesterId());
        lesson.setAssignedTeacherId(700L);
        lesson.setLessonType("lecture");
        lesson.setLessonNumber(item.getLessonNumber());
        lesson.setDayOfWeek(item.getDayOfWeek());
        lesson.setStartTime(item.getStartTime());
        lesson.setEndTime(item.getEndTime());
        lesson.setRoomSnapshot(item.getRoom());
        lesson.setWeekTypeSnapshot(item.getWeekType().name().toLowerCase(java.util.Locale.ROOT));
        lesson.setGeneration(1L);
        lesson.setRevision(1L);
        if (lesson.getCreatedAt() == null) lesson.setCreatedAt(OffsetDateTime.now());
        Lesson saved = fixtureLessons.saveAndFlush(lesson);
        jdbcTemplate.update(
                "UPDATE lesson_occurrences SET current_lesson_id = ? WHERE id = ?",
                saved.getId(),
                occurrenceId);
        return saved;
    }

    /**
     * M02: удаляет все pending outbox-записи (markSent в tx). Вызывать из @AfterEach в тестах
     * которые пишут в outbox, чтобы следующий тест стартовал с чистым findPending.
     */
    protected void drainOutbox() {
        if (outboxStorage == null || transactionManager == null) return;
        new TransactionTemplate(transactionManager)
                .executeWithoutResult(
                        status -> {
                            List<OutboxRecord> pending = outboxStorage.findPending(1000);
                            pending.forEach(r -> outboxStorage.markSent(r.id()));
                        });
    }

    /**
     * V17/V18 retain physical lessons and reject ordinary DELETE statements. Integration fixtures
     * use an isolated TRUNCATE reset instead of weakening the production retention trigger for test
     * cleanup.
     */
    protected void resetScheduleData() {
        jdbcTemplate.execute(
                """
                TRUNCATE TABLE
                    schedule_subject_deletion_participants,
                    schedule_outbox,
                    schedule_semester_archive_barriers,
                    lesson_homework_bindings,
                    schedule_assignment_rebind_ledger,
                    schedule_assignment_replacement_templates,
                    schedule_assignment_replacement_operations,
                    schedule_transfer_replay,
                    lesson_restore_authorities,
                    lesson_lifecycle_entries,
                    lessons,
                    lesson_occurrences,
                    schedule_recurring_create_replay,
                    schedule_assignment_fences,
                    schedule_items,
                    schedule_one_off_lessons,
                    iso_parity_reconciliation
                    RESTART IDENTITY CASCADE
                """);
    }

    static final PostgreSQLContainer<?> POSTGRES;

    static {
        // Each test JVM owns its synthetic database; do not reuse another run's container.
        POSTGRES =
                new PostgreSQLContainer<>("postgres:16")
                        .withDatabaseName("schedule_db")
                        .withUsername("rct_user")
                        .withPassword("rct_dev_pass")
                        .withReuse(false);
        POSTGRES.start();
    }

    @DynamicPropertySource
    static void overrideProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add(
                "spring.autoconfigure.exclude",
                () -> "org.springframework.boot.autoconfigure.amqp.RabbitAutoConfiguration");
    }
}
