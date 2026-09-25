package ru.rutcampustrack.attendance.latecheckin;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import ru.rutcampustrack.attendance.contract.enums.LateCheckinRequestStatus;
import ru.rutcampustrack.attendance.contract.enums.LateCheckinRequestOrigin;
import ru.rutcampustrack.attendance.latecheckin.entity.LateCheckinRequest;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface LateCheckinRepository extends MongoRepository<LateCheckinRequest, String> {

    boolean existsByStudentIdAndLessonIdAndStatus(
            Long studentId, Long lessonId, LateCheckinRequestStatus status);

    Optional<LateCheckinRequest> findFirstByStudentIdAndLessonIdAndStatus(
            Long studentId, Long lessonId, LateCheckinRequestStatus status);

    Optional<LateCheckinRequest> findFirstByStudentIdAndLessonIdAndOriginOrderByUpdatedAtDesc(
            Long studentId, Long lessonId, LateCheckinRequestOrigin origin);

    List<LateCheckinRequest> findByGroupIdAndStatusOrderByCreatedAtAsc(
            Long groupId, LateCheckinRequestStatus status);

    List<LateCheckinRequest> findByGroupIdAndSemesterIdAndStatusIn(
            Long groupId, Long semesterId, List<LateCheckinRequestStatus> statuses);

    Page<LateCheckinRequest> findByGroupIdAndSemesterIdAndStudentIdAndLessonIdInAndStatusIn(
            Long groupId, Long semesterId, Long studentId, List<Long> lessonIds,
            List<LateCheckinRequestStatus> statuses, Pageable pageable);

    Page<LateCheckinRequest> findByGroupIdAndUpdatedAtGreaterThanEqual(
            Long groupId, Instant updatedAt, Pageable pageable);

    Page<LateCheckinRequest> findByGroupIdAndStatusAndUpdatedAtGreaterThanEqual(
            Long groupId, LateCheckinRequestStatus status, Instant updatedAt, Pageable pageable);
}
