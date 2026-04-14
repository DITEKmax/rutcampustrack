package ru.rutcampustrack.academic.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.rutcampustrack.academic.entity.Semester;

import java.time.LocalDate;
import java.util.Optional;

public interface SemesterRepository extends JpaRepository<Semester, Long> {
    Optional<Semester> findByIsActiveTrue();

    @Modifying
    @Query("UPDATE Semester s SET s.isActive = false WHERE s.isActive = true")
    int deactivateAllActive();

    /**
     * Returns the first semester whose date range overlaps the supplied
     * {@code [from, to]} interval. Used by {@code SemesterService} for
     * human-readable 409 conflict messages (BUG-006-7). When {@code excludeId}
     * is non-null, that semester is excluded from the check (edit mode).
     *
     * <p>Native query leveraging the PostgreSQL {@code daterange(..., '[]')}
     * inclusive-bound operator and the {@code semesters_no_overlap} EXCLUDE
     * gist index from V10 migration.
     */
    @Query(value = """
            SELECT * FROM semesters
            WHERE daterange(date_from, date_to, '[]') && daterange(:from, :to, '[]')
              AND (:excludeId IS NULL OR id <> :excludeId)
            ORDER BY date_from
            LIMIT 1
            """, nativeQuery = true)
    Optional<Semester> findFirstOverlapping(@Param("from") LocalDate from,
                                            @Param("to") LocalDate to,
                                            @Param("excludeId") Long excludeId);
}
