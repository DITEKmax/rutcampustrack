package ru.rutcampustrack.schedule.item.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.rutcampustrack.schedule.item.entity.ScheduleItem;

import java.util.List;

public interface ScheduleItemRepository extends JpaRepository<ScheduleItem, Long> {

    List<ScheduleItem> findByGroupIdAndSemesterIdAndIsActiveTrue(Long groupId, Long semesterId);

    Page<ScheduleItem> findByGroupIdAndSemesterIdAndIsActiveTrue(Long groupId, Long semesterId, Pageable pageable);

    /**
     * Returns ALL schedule items for a group regardless of active status.
     * Used by the view endpoint which must show all templates for lesson JOIN.
     */
    List<ScheduleItem> findByGroupId(Long groupId);

    /**
     * Phase 60-03 D-09 conflict-check helper.
     * Returns true if an active template slot exists that would collide with a one-off
     * lesson on the given (dayOfWeek, lessonNumber, weekType, semester).
     * weekType match: ALL template always collides; ODD/EVEN collides only with its own parity.
     *
     * @param groupId       target group
     * @param lessonNumber  slot number (1..8)
     * @param dayOfWeek     0=Mon..5=Sat (schedule_items convention)
     * @param weekType      computed parity of the target date (ODD or EVEN — ALL is invalid here)
     * @param semesterId    semester id the one-off will be anchored to
     */
    /**
     * Native query — `week_type` column is a PostgreSQL custom enum, so JPA-sent
     * varchar must be CAST explicitly (project V5 decision).
     */
    @Query(value = """
            SELECT EXISTS (
                SELECT 1 FROM schedule_items
                 WHERE group_id = :groupId
                   AND lesson_number = :lessonNumber
                   AND day_of_week = :dayOfWeek
                   AND semester_id = :semesterId
                   AND is_active = true
                   AND (week_type = 'all'::week_type
                        OR week_type = CAST(:weekType AS week_type))
            )
            """, nativeQuery = true)
    boolean existsActiveTemplateSlot(@Param("groupId") Long groupId,
                                     @Param("lessonNumber") Short lessonNumber,
                                     @Param("dayOfWeek") Short dayOfWeek,
                                     @Param("weekType") String weekType,
                                     @Param("semesterId") Long semesterId);

    /**
     * All schedule items referencing the given subject, regardless of is_active.
     * Used by the subject.deleted cascade to collect schedule_item ids for
     * child-lesson cleanup and physical removal.
     */
    List<ScheduleItem> findBySubjectId(Long subjectId);

    long countBySubjectIdAndIsActiveTrue(Long subjectId);
}
