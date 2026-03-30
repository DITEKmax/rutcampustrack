package ru.rutcampustrack.academic.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.rutcampustrack.academic.entity.StudentGroupHistory;
import java.util.List;
import java.util.Optional;

public interface StudentGroupHistoryRepository extends JpaRepository<StudentGroupHistory, Long> {
    List<StudentGroupHistory> findByUserIdOrderByJoinedAtDesc(Long userId);
    Optional<StudentGroupHistory> findByUserIdAndLeftAtIsNull(Long userId);
}
