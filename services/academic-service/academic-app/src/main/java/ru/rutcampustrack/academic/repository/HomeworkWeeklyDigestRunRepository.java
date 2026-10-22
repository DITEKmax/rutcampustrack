package ru.rutcampustrack.academic.repository;

import java.time.LocalDate;
import org.springframework.data.jpa.repository.JpaRepository;
import ru.rutcampustrack.academic.entity.HomeworkWeeklyDigestRun;

public interface HomeworkWeeklyDigestRunRepository
        extends JpaRepository<HomeworkWeeklyDigestRun, Long> {
    boolean existsByGroupIdAndWeekStart(Long groupId, LocalDate weekStart);
}
