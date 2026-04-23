package ru.rutcampustrack.attendance.integration;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationRunner;
import ru.rutcampustrack.attendance.checkin.AttendanceDocument;
import ru.rutcampustrack.attendance.contract.enums.AttendanceSource;
import ru.rutcampustrack.attendance.contract.enums.AttendanceStatus;

import java.time.Instant;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * M09 Группа 1 (04 P0-6) — регрессионный guard: startup orphan-cleanup
 * удалён. Ранее Application боотstrap сам вызывал
 * {@code cleanupOrphans} → gRPC {@code getLessonsByIds} → mass-delete
 * attendance docs, что в продe сгрёбывало всё при outage schedule-service.
 *
 * <p>Здесь: (1) бросаем orphan-doc в Mongo до старта контекста не нужно
 * (контекст уже стартовал в {@link AbstractAttendanceIntegrationTest}),
 * но мы верифицируем пост-фактум, что (a) bootstrap runner существует,
 * (b) за стартап он не дёрнул {@link #scheduleGrpcClient} (mock), и
 * (c) orphan-docs сохранены после manual повторного запуска runner'а.
 */
class StartupOrphanCleanupRemovedIT extends AbstractAttendanceIntegrationTest {

    @Autowired
    private ApplicationRunner ensureAttendanceUniqueIndex;

    @AfterEach
    void cleanup() {
        attendanceRepository.deleteAll();
    }

    @Test
    void startupRunner_doesNotInvokeScheduleGrpcClient() {
        // scheduleGrpcClient @MockitoBean — если бы cleanupOrphans остался,
        // startup дёрнул бы getLessonsByIds() → Mockito зафиксировал бы вызов.
        verifyNoInteractions(scheduleGrpcClient);
    }

    @Test
    void rerunRunner_withOrphanDocsAndDeadSchedule_preservesDocs() throws Exception {
        // Seed orphan attendance doc с lesson_id, который в schedule не
        // существует (и, shared mock, даже не возвращает пустой список — мы
        // просто не вызываем его). До M09 такой doc был бы удалён mass-wipe'ом.
        AttendanceDocument orphan = AttendanceDocument.builder()
                .lessonId(999_999L).userId(42L).groupId(10L).subjectId(5L)
                .semesterId(1L).lessonNumber(1).lessonDate(LocalDate.now())
                .status(AttendanceStatus.PRESENT).source(AttendanceSource.STUDENT_GEO)
                .createdAt(Instant.now()).updatedAt(Instant.now())
                .build();
        attendanceRepository.save(orphan);

        long before = attendanceRepository.count();
        assertThat(before).isGreaterThanOrEqualTo(1L);

        // Повторный вызов runner'а вручную — эмулирует «перезапуск сервиса».
        ensureAttendanceUniqueIndex.run(null);

        // Orphan doc целый: runner больше не удаляет по lesson_id.
        assertThat(attendanceRepository.count()).isEqualTo(before);
        assertThat(attendanceRepository.findById(orphan.getId())).isPresent();
        verifyNoInteractions(scheduleGrpcClient);
    }
}
