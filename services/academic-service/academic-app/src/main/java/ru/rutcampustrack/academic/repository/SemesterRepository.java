package ru.rutcampustrack.academic.repository;

import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;
import ru.rutcampustrack.academic.entity.Semester;

public interface SemesterRepository extends JpaRepository<Semester, Long> {
    Optional<Semester> findByIsActiveTrue();

    @Query(
            value =
                    """
            SELECT id FROM semesters
             WHERE activation_scheduled = TRUE AND date_from <= :today
             ORDER BY date_from, id LIMIT 100
            """,
            nativeQuery = true)
    List<Long> findDueScheduledActivationIds(@Param("today") LocalDate today);

    @Query("select s from Semester s where s.id = :id")
    @QueryHints(@QueryHint(name = "jakarta.persistence.cache.retrieveMode", value = "BYPASS"))
    Optional<Semester> findByIdUncached(@Param("id") Long id);

    List<Semester> findAllByIsActiveTrueOrderByIdAsc();

    @Query(
            value =
                    """
            SELECT * FROM semesters
             WHERE semester_type = 'SPRING'
               AND COALESCE(promotion_cycle_date_to, date_to) < :today
             ORDER BY COALESCE(promotion_cycle_date_to, date_to) DESC, id DESC
             LIMIT 1
            """,
            nativeQuery = true)
    Optional<Semester> findLatestCompletedSpringPromotionCycle(@Param("today") LocalDate today);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from Semester s where s.id = :id")
    Optional<Semester> findByIdForUpdate(@Param("id") Long id);

    @Modifying
    @Query(
            "UPDATE Semester s SET s.isActive = false, s.stateVersion = s.stateVersion + 1 "
                    + "WHERE s.isActive = true")
    int deactivateAllActive();

    /**
     * Returns the first semester whose date range overlaps the supplied {@code [from, to]}
     * interval. Used by {@code SemesterService} for human-readable 409 conflict messages
     * (BUG-006-7). When {@code excludeId} is non-null, that semester is excluded from the check
     * (edit mode).
     *
     * <p>Native query leveraging the PostgreSQL {@code daterange(..., '[]')} inclusive-bound
     * operator and the {@code semesters_no_overlap} EXCLUDE gist index from V10 migration.
     */
    @Query(
            value =
                    """
            SELECT * FROM semesters
            WHERE daterange(date_from, date_to, '[]') && daterange(:from, :to, '[]')
              AND (:excludeId IS NULL OR id <> :excludeId)
            ORDER BY date_from
            LIMIT 1
            """,
            nativeQuery = true)
    Optional<Semester> findFirstOverlapping(
            @Param("from") LocalDate from,
            @Param("to") LocalDate to,
            @Param("excludeId") Long excludeId);
}
