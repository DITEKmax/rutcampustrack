package ru.rutcampustrack.schedule.item.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
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
}
