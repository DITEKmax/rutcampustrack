package ru.rutcampustrack.attendance.integration;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;
import ru.rutcampustrack.attendance.checkin.AttendanceDocument;
import ru.rutcampustrack.attendance.contract.enums.AttendanceSource;
import ru.rutcampustrack.attendance.contract.enums.AttendanceStatus;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Verifies INFRA-01: unique compound index on (lesson_id, user_id) rejects duplicates.
 *
 * <p>M08 Группа 3 (P2-8/3) — также покрывает smoke-проверку «MongoConfig.initIndexes()
 * создаёт ВСЕ ожидаемые индексы при startup» (эквивалент freshInstallAppliesAllMigrations
 * для Mongo schema, у которой нет Flyway).
 */
class MongoIndexIT extends AbstractAttendanceIntegrationTest {

    @AfterEach
    void cleanup() {
        attendanceRepository.deleteAll();
    }

    @Test
    void uniqueIndex_rejectsDuplicateLessonUser() {
        AttendanceDocument doc1 = AttendanceDocument.builder()
                .lessonId(1L).userId(100L).groupId(10L).subjectId(5L)
                .semesterId(1L).lessonNumber(1).lessonDate(LocalDate.now())
                .status(AttendanceStatus.PRESENT).source(AttendanceSource.STUDENT_GEO)
                .createdAt(Instant.now()).updatedAt(Instant.now())
                .build();
        attendanceRepository.save(doc1);

        AttendanceDocument doc2 = AttendanceDocument.builder()
                .lessonId(1L).userId(100L).groupId(10L).subjectId(5L)
                .semesterId(1L).lessonNumber(1).lessonDate(LocalDate.now())
                .status(AttendanceStatus.ABSENT).source(AttendanceSource.AUTO_SCHEDULER)
                .createdAt(Instant.now()).updatedAt(Instant.now())
                .build();

        assertThrows(DuplicateKeyException.class, () -> attendanceRepository.save(doc2));
    }

    @Test
    void uniqueIndex_allowsDifferentLessonSameUser() {
        AttendanceDocument doc1 = AttendanceDocument.builder()
                .lessonId(11L).userId(200L).groupId(10L).subjectId(5L)
                .semesterId(1L).lessonNumber(1).lessonDate(LocalDate.now())
                .status(AttendanceStatus.PRESENT).source(AttendanceSource.STUDENT_GEO)
                .createdAt(Instant.now()).updatedAt(Instant.now())
                .build();
        AttendanceDocument doc2 = AttendanceDocument.builder()
                .lessonId(12L).userId(200L).groupId(10L).subjectId(5L)
                .semesterId(1L).lessonNumber(2).lessonDate(LocalDate.now())
                .status(AttendanceStatus.PRESENT).source(AttendanceSource.STUDENT_GEO)
                .createdAt(Instant.now()).updatedAt(Instant.now())
                .build();

        attendanceRepository.save(doc1);
        AttendanceDocument saved = attendanceRepository.save(doc2);

        assertNotNull(saved.getId());
    }

    /**
     * M08 Группа 3 (P2-8/3) — equivalent of freshInstallAppliesAllMigrations для
     * MongoDB schema, у которой нет Flyway. Проверяет, что все 5 named-индексов
     * созданы {@code MongoConfig.initIndexes()} при startup Spring-контекста.
     *
     * <p>Ожидаемые индексы (MongoConfig.java:27-64):
     * <ul>
     *   <li>{@code attendances}: uniq_lesson_user, idx_user_semester_date,
     *       idx_group_semester_subject, idx_lesson_id + default _id_</li>
     *   <li>{@code late_checkin_requests}: lcr_group_status_created + default _id_</li>
     * </ul>
     */
    @Test
    void mongoConfigInitIndexes_createsAllExpectedIndexes() {
        List<String> attendanceIndexes = mongoTemplate.indexOps("attendances")
                .getIndexInfo().stream()
                .map(info -> info.getName())
                .collect(Collectors.toList());

        assertThat(attendanceIndexes)
                .as("MongoConfig.initIndexes() должен создать все named индексы на attendances")
                .contains("uniq_lesson_user", "idx_user_semester_date",
                        "idx_group_semester_subject", "idx_lesson_id", "_id_");

        List<String> lcrIndexes = mongoTemplate.indexOps("late_checkin_requests")
                .getIndexInfo().stream()
                .map(info -> info.getName())
                .collect(Collectors.toList());

        assertThat(lcrIndexes)
                .as("M05 Группа 1 — lcr_group_status_created должен быть создан на startup")
                .contains("lcr_group_status_created", "_id_");
    }
}
