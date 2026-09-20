package ru.rutcampustrack.academic.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.rutcampustrack.academic.entity.StudentGroupHistory;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;

public interface StudentGroupHistoryRepository extends JpaRepository<StudentGroupHistory, Long> {
    List<StudentGroupHistory> findByUserIdOrderByJoinedAtDesc(Long userId);
    List<StudentGroupHistory> findByUserIdOrderByJoinedAtAscIdAsc(Long userId);
    Optional<StudentGroupHistory> findByUserIdAndLeftAtIsNull(Long userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select h from StudentGroupHistory h where h.userId = :userId and h.leftAt is null "
            + "order by h.joinedAt, h.id")
    List<StudentGroupHistory> findOpenByUserIdForUpdate(@Param("userId") Long userId);

    @Query("select h from StudentGroupHistory h where h.groupId = :groupId "
            + "order by h.userId, h.joinedAt, h.id")
    List<StudentGroupHistory> findByGroupIdOrderByUserIdAscJoinedAtAscIdAsc(@Param("groupId") Long groupId);
}
