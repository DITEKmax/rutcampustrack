package ru.rutcampustrack.academic.repository;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import ru.rutcampustrack.academic.entity.GroupPromotionCycleRecord;

public interface GroupPromotionCycleRecordRepository
        extends JpaRepository<GroupPromotionCycleRecord, Long> {
    List<GroupPromotionCycleRecord> findAllByCycleSemesterIdOrderByGroupId(Long cycleSemesterId);
}
