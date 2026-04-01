package ru.rutcampustrack.schedule.lesson.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.rutcampustrack.schedule.contract.enums.LessonStatus;
import ru.rutcampustrack.schedule.lesson.entity.Lesson;

import java.time.LocalDate;
import java.util.List;

public interface LessonRepository extends JpaRepository<Lesson, Long> {

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
}
