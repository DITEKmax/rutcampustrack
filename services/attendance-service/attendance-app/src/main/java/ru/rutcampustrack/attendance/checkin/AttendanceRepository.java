package ru.rutcampustrack.attendance.checkin;

import org.springframework.data.mongodb.repository.Aggregation;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.time.Instant;
import java.util.Optional;

public interface AttendanceRepository extends MongoRepository<AttendanceDocument, String> {

    /**
     * Look up an attendance document for a specific (lesson, student) pair.
     * Used by the excuse approve cascade (D-16) to implement upsert semantics
     * without resorting to a raw Mongo update.
     */
    Optional<AttendanceDocument> findByLessonIdAndUserId(Long lessonId, Long userId);

    /**
     * M04 Группа 8 — gauge {@code attendance.students_in_red_zone}.
     *
     * <p>Упрощённое определение «красной зоны»: уникальные студенты у
     * которых за последние {@code since} дней набралось ≥ {@code minAbsences}
     * документов со статусом {@code absent}. Порог из QA4 (3 пропуска за
     * 30 дней) используется как baseline для dashboard, per-subject/
     * per-group thresholds (academic.threshold) не учитываются — они в
     * отдельном service-boundary и задача gauge просто дать operator'у
     * сигнал «массовые прогулы» без cross-service join'ов.
     *
     * @param sinceInclusive нижняя граница (lessonDate converted to Instant)
     * @param minAbsences    порог (≥ 3 по умолчанию)
     * @return количество уникальных студентов в красной зоне
     */
    @Aggregation(pipeline = {
            "{ $match: { status: 'absent', createdAt: { $gte: ?0 } } }",
            "{ $group: { _id: '$userId', c: { $sum: 1 } } }",
            "{ $match: { c: { $gte: ?1 } } }",
            "{ $count: 'count' }"
    })
    Optional<RedZoneCount> countStudentsInRedZone(Instant sinceInclusive, long minAbsences);

    /** Projection для {@link #countStudentsInRedZone(Instant, long)}. */
    interface RedZoneCount {
        long getCount();
    }
}
