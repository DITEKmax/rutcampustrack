package ru.rutcampustrack.schedule.item.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.rutcampustrack.schedule.contract.enums.WeekType;
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
    @Query("""
            SELECT COUNT(si) > 0 FROM ScheduleItem si
             WHERE si.groupId = :groupId
               AND si.lessonNumber = :lessonNumber
               AND si.dayOfWeek = :dayOfWeek
               AND si.semesterId = :semesterId
               AND si.isActive = true
               AND (si.weekType = ru.rutcampustrack.schedule.contract.enums.WeekType.ALL
                    OR si.weekType = :weekType)
            """)
    boolean existsActiveTemplateSlot(@Param("groupId") Long groupId,
                                     @Param("lessonNumber") Short lessonNumber,
                                     @Param("dayOfWeek") Short dayOfWeek,
                                     @Param("weekType") WeekType weekType,
                                     @Param("semesterId") Long semesterId);
}
