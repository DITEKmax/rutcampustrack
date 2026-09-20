package ru.rutcampustrack.academic.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.rutcampustrack.academic.entity.HomeworkCompletion;
import java.util.List;
import java.util.Optional;

public interface HomeworkCompletionRepository extends JpaRepository<HomeworkCompletion, Long> {
    Optional<HomeworkCompletion> findByHomeworkIdAndStudentId(Long homeworkId, Long studentId);
    List<HomeworkCompletion> findByStudentId(Long studentId);
    List<HomeworkCompletion> findByHomeworkId(Long homeworkId);
    List<HomeworkCompletion> findByHomeworkIdInAndStudentId(List<Long> homeworkIds, Long studentId);
    boolean existsByHomeworkIdAndStudentId(Long homeworkId, Long studentId);

    /**
     * Desired-state completion write.  PostgreSQL's unique constraint makes
     * concurrent repeated complete commands a no-op instead of a duplicate
     * row/constraint error.
     */
    @Modifying
    @Query(value = "INSERT INTO homework_completions (homework_id, student_id, completed_at) "
            + "VALUES (:homeworkId, :studentId, CURRENT_TIMESTAMP) "
            + "ON CONFLICT (homework_id, student_id) DO NOTHING", nativeQuery = true)
    int insertIfAbsent(@Param("homeworkId") Long homeworkId, @Param("studentId") Long studentId);

    @Modifying
    @Query("DELETE FROM HomeworkCompletion c "
            + "WHERE c.homeworkId = :homeworkId AND c.studentId = :studentId")
    int deleteByHomeworkIdAndStudentId(@Param("homeworkId") Long homeworkId,
                                       @Param("studentId") Long studentId);
}
