package ru.rutcampustrack.schedule.lesson.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.rutcampustrack.schedule.contract.enums.LessonStatus;
import ru.rutcampustrack.schedule.lesson.entity.Lesson;
import ru.rutcampustrack.schedule.lesson.projection.LessonDetailsProjection;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface LessonRepository extends JpaRepository<Lesson, Long> {

    /**
     * M05 Группа 2 — reference-pattern для NEW-143 (Spring Data projection).
     *
     * <p>Один JOIN + SELECT subset полей вместо {@code findById(lessonId)}
     * + {@code findById(scheduleItemId)} (2 roundtrips). Alias columns
     * совпадают с getters в {@link LessonDetailsProjection} (case-insensitive).
     *
     * <p>Boolean {@code is_blocked_by_headman} выставляется с алиасом
     * {@code isBlockedByHeadman} — Spring распознаёт {@code getIsBlockedByHeadman()}.
     */
    @Query(value = """
        SELECT l.id              AS id,
               l.date            AS date,
               l.status::text    AS status,
               l.is_blocked_by_headman AS isBlockedByHeadman,
               si.group_id       AS groupId,
               si.subject_id     AS subjectId,
               si.lesson_number  AS lessonNumber,
               si.start_time     AS startTime,
               si.end_time       AS endTime,
               si.room           AS room
          FROM lessons l
          JOIN schedule_items si ON si.id = l.schedule_item_id
         WHERE l.id = :lessonId
        """, nativeQuery = true)
    Optional<LessonDetailsProjection> findLessonDetails(@Param("lessonId") Long lessonId);

    List<Lesson> findByScheduleItemIdAndDateBetween(Long scheduleItemId, LocalDate from, LocalDate to);

    /**
     * Returns lessons whose status is in the given list and date is before the threshold.
     * Uses native query with explicit cast to avoid PostgreSQL enum vs varchar operator error.
     */
    @Query(value = "SELECT * FROM lessons WHERE status::text IN :statuses AND date < :date",
            nativeQuery = true)
    List<Lesson> findByStatusInAndDateBefore(
            @Param("statuses") List<String> statuses,
            @Param("date") LocalDate date);

    /**
     * Returns lessons filtered by multiple schedule item IDs, date range, and statuses.
     * Uses native query with explicit cast to avoid PostgreSQL enum vs varchar operator error.
     * Used by both the group view endpoint and mass-cancel operation.
     */
    @Query(value = "SELECT * FROM lessons WHERE schedule_item_id IN :itemIds AND date BETWEEN :from AND :to AND status::text IN :statuses",
            nativeQuery = true)
    List<Lesson> findByScheduleItemIdInAndDateBetweenAndStatusIn(
            @Param("itemIds") List<Long> scheduleItemIds,
            @Param("from") LocalDate from,
            @Param("to") LocalDate to,
            @Param("statuses") List<String> statuses);

    /**
     * M05 D9 / P2-10/5: SQL-pagination вариант {@link
     * #findByScheduleItemIdInAndDateBetweenAndStatusIn(List, LocalDate, LocalDate,
     * List)}. Устраняет OOM-risk при загрузке всех lessons группы за семестр
     * (500-2000+ rows) c последующей in-memory пагинацией — вместо этого
     * PostgreSQL применяет LIMIT/OFFSET в плане запроса.
     *
     * <p>Native query + Pageable требует явного {@code countQuery} —
     * Hibernate не может derive его из {@code SELECT *}.
     *
     * <p>Использует composite index {@code idx_lessons_item_date} (M05 Группа 1).
     */
    @Query(value = "SELECT * FROM lessons WHERE schedule_item_id IN :itemIds "
            + "AND date BETWEEN :from AND :to AND status::text IN :statuses "
            + "ORDER BY date ASC, id ASC",
           countQuery = "SELECT COUNT(*) FROM lessons WHERE schedule_item_id IN :itemIds "
                   + "AND date BETWEEN :from AND :to AND status::text IN :statuses",
           nativeQuery = true)
    Page<Lesson> pageByScheduleItemIdInAndDateBetweenAndStatusIn(
            @Param("itemIds") List<Long> scheduleItemIds,
            @Param("from") LocalDate from,
            @Param("to") LocalDate to,
            @Param("statuses") List<String> statuses,
            Pageable pageable);

    /**
     * Deletes all PLANNED lessons for a given schedule item starting from a given date.
     * Used by LessonGenerationService.regenerateFromDate() to clear stale planned lessons
     * before re-generating them after a ScheduleItem update.
     * Uses native query with status::text cast (same pattern as other queries in this repo).
     */
    @Modifying
    @Query(value = "DELETE FROM lessons WHERE schedule_item_id = :itemId AND status::text = 'planned' AND date >= :fromDate",
           nativeQuery = true)
    void deletePlannedFromDate(@Param("itemId") Long scheduleItemId,
                               @Param("fromDate") LocalDate fromDate);

    /**
     * Reconciliation-only: deletes every PLANNED and CANCELLED lesson for a
     * schedule item on or after the given date. Used by
     * {@link ru.rutcampustrack.schedule.lesson.IsoParityReconciler} to fully
     * wipe the regeneration target window before reinsert — otherwise a
     * previously-CANCELLED future lesson would collide with the freshly
     * generated PLANNED one on the {@code UNIQUE (schedule_item_id, date)}
     * constraint. ACTIVE/CLOSED are preserved because they imply real
     * attendance data and cannot legitimately exist in a future date anyway.
     */
    @Modifying
    @Query(value = "DELETE FROM lessons WHERE schedule_item_id = :itemId "
            + "AND status::text IN ('planned','cancelled') AND date >= :fromDate",
           nativeQuery = true)
    void deletePlannedOrCancelledFromDate(@Param("itemId") Long scheduleItemId,
                                          @Param("fromDate") LocalDate fromDate);

    /**
     * Returns the IDs of PLANNED and CANCELLED lessons for a schedule item on
     * or after the given date. Companion to {@link #deletePlannedOrCancelledFromDate}
     * so the caller can publish {@code lesson.deleted} events before the physical
     * delete (attendance-service drops dependent records).
     */
    @Query(value = "SELECT id FROM lessons WHERE schedule_item_id = :itemId "
            + "AND status::text IN ('planned','cancelled') AND date >= :fromDate",
           nativeQuery = true)
    List<Long> findPlannedOrCancelledIdsFromDate(@Param("itemId") Long scheduleItemId,
                                                 @Param("fromDate") LocalDate fromDate);

    /**
     * Returns the IDs of PLANNED lessons for a schedule item on or after the given date.
     * Used by callers who must publish a cascade event (e.g. lesson.deleted) BEFORE
     * calling {@link #deletePlannedFromDate} so downstream services (attendance-service)
     * can drop dependent records keyed to the about-to-be-deleted lesson ids.
     */
    @Query(value = "SELECT id FROM lessons WHERE schedule_item_id = :itemId AND status::text = 'planned' AND date >= :fromDate",
           nativeQuery = true)
    List<Long> findPlannedIdsFromDate(@Param("itemId") Long scheduleItemId,
                                      @Param("fromDate") LocalDate fromDate);

    /**
     * Finds the active lesson for a group on a given date, ordered by lesson_number ASC.
     * Returns at most one result (LIMIT 1). Used by gRPC GetActiveLesson (GRPC-01).
     * When multiple active lessons exist for the same group (rare scheduling overlap),
     * returns the first by lesson_number ASC per D-02.
     */
    @Query(value = """
        SELECT l.* FROM lessons l
        JOIN schedule_items si ON si.id = l.schedule_item_id
        WHERE l.status::text = 'active'
          AND si.group_id = :groupId
          AND l.date = CAST(:date AS date)
        ORDER BY si.lesson_number ASC
        LIMIT 1
        """, nativeQuery = true)
    Optional<Lesson> findActiveLessonForGroup(
            @Param("groupId") Long groupId,
            @Param("date") LocalDate date);

    /**
     * Phase 61 D-04: Резолв пары по natural key (group_id, date, lesson_number).
     * Возвращает lesson только если статус planned/active/closed (cancelled отфильтровывается).
     * Используется academic-service для валидации существования пары перед созданием/апдейтом ДЗ.
     */
    @Query(value = """
        SELECT l.* FROM lessons l
        JOIN schedule_items si ON si.id = l.schedule_item_id
        WHERE si.group_id = :groupId
          AND si.lesson_number = :lessonNumber
          AND l.date = CAST(:date AS date)
          AND l.status::text IN ('planned','active','closed')
        ORDER BY l.date
        LIMIT 1
        """, nativeQuery = true)
    Optional<Lesson> findByGroupDateAndLessonNumber(
            @Param("groupId") Long groupId,
            @Param("date") LocalDate date,
            @Param("lessonNumber") Integer lessonNumber);

    /**
     * Finds PLANNED lessons whose (date + start_time) <= nowMoscow (CRON-01).
     * JOIN schedule_items to compare start_time. Uses native query with status::text cast
     * (same pattern as all other queries in this repo).
     * The :now parameter is LocalDateTime in Moscow wall-clock time.
     */
    @Query(value = """
        SELECT l.* FROM lessons l
        JOIN schedule_items si ON si.id = l.schedule_item_id
        WHERE l.status::text = 'planned'
          AND (l.date + si.start_time) <= CAST(:now AS timestamp)
        ORDER BY l.date, si.start_time
        """, nativeQuery = true)
    List<Lesson> findPlannedDueForActivation(@Param("now") LocalDateTime now);

    /**
     * Finds ACTIVE lessons whose (date + end_time + 5 minutes) <= nowMoscow (CRON-02).
     */
    @Query(value = """
        SELECT l.* FROM lessons l
        JOIN schedule_items si ON si.id = l.schedule_item_id
        WHERE l.status::text = 'active'
          AND (l.date + si.end_time + INTERVAL '5 minutes') <= CAST(:now AS timestamp)
        ORDER BY l.date, si.end_time
        """, nativeQuery = true)
    List<Lesson> findActiveDueForClosure(@Param("now") LocalDateTime now);

    /**
     * Counts lessons whose status is NOT 'planned' (i.e. active/closed/cancelled)
     * across all schedule_items referencing the given subject_id. Used by
     * CountSubjectReferences gRPC to decide whether deleting the subject risks
     * losing attendance data.
     */
    @Query(value = """
        SELECT COUNT(l.id) FROM lessons l
        JOIN schedule_items si ON si.id = l.schedule_item_id
        WHERE si.subject_id = :subjectId
          AND l.status::text <> 'planned'
        """, nativeQuery = true)
    long countNonPlannedBySubjectId(@Param("subjectId") Long subjectId);

    /**
     * Counts all lessons across schedule_items of a subject — used purely for
     * informing the headman how many rows will be wiped.
     */
    @Query(value = """
        SELECT COUNT(l.id) FROM lessons l
        JOIN schedule_items si ON si.id = l.schedule_item_id
        WHERE si.subject_id = :subjectId
        """, nativeQuery = true)
    long countAllBySubjectId(@Param("subjectId") Long subjectId);

    /**
     * Returns the ids of all lessons attached to any schedule_item of the subject.
     * Used by the subject.deleted cascade to publish lesson.deleted BEFORE
     * the physical delete (so attendance-service can drop docs via existing path).
     */
    @Query(value = """
        SELECT l.id FROM lessons l
        JOIN schedule_items si ON si.id = l.schedule_item_id
        WHERE si.subject_id = :subjectId
        """, nativeQuery = true)
    List<Long> findIdsBySubjectId(@Param("subjectId") Long subjectId);
}
