package ru.rutcampustrack.schedule.item.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.rutcampustrack.schedule.item.entity.ScheduleItem;

import java.util.List;

public interface ScheduleItemRepository extends JpaRepository<ScheduleItem, Long> {

    List<ScheduleItem> findByGroupIdAndSemesterIdAndIsActiveTrue(Long groupId, Long semesterId);
}
