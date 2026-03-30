package ru.rutcampustrack.academic.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.rutcampustrack.academic.entity.HomeworkCompletion;
import java.util.List;
import java.util.Optional;

public interface HomeworkCompletionRepository extends JpaRepository<HomeworkCompletion, Long> {
    Optional<HomeworkCompletion> findByHomeworkIdAndStudentId(Long homeworkId, Long studentId);
    List<HomeworkCompletion> findByStudentId(Long studentId);
    boolean existsByHomeworkIdAndStudentId(Long homeworkId, Long studentId);
}
