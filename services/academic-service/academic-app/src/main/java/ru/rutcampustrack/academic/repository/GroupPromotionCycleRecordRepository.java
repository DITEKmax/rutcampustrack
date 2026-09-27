package ru.rutcampustrack.academic.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.rutcampustrack.academic.entity.GroupPromotionCycleRecord;

import java.util.List;

public interface GroupPromotionCycleRecordRepository
        extends JpaRepository<GroupPromotionCycleRecord, Long> {
    List<GroupPromotionCycleRecord> findAllByCycleSemesterIdOrderByGroupId(Long cycleSemesterId);
}
