package ru.rutcampustrack.academic.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.rutcampustrack.academic.entity.HomeworkWeeklyDigestRun;

import java.time.LocalDate;

public interface HomeworkWeeklyDigestRunRepository extends JpaRepository<HomeworkWeeklyDigestRun, Long> {
    boolean existsByGroupIdAndWeekStart(Long groupId, LocalDate weekStart);
}
