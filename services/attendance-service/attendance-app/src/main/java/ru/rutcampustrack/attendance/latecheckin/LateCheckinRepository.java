package ru.rutcampustrack.attendance.latecheckin;

import org.springframework.data.mongodb.repository.MongoRepository;
import ru.rutcampustrack.attendance.contract.enums.LateCheckinRequestStatus;
import ru.rutcampustrack.attendance.latecheckin.entity.LateCheckinRequest;

import java.util.List;

public interface LateCheckinRepository extends MongoRepository<LateCheckinRequest, String> {

    boolean existsByStudentIdAndLessonIdAndStatus(
            Long studentId, Long lessonId, LateCheckinRequestStatus status);

    List<LateCheckinRequest> findByGroupIdAndStatusOrderByCreatedAtAsc(
            Long groupId, LateCheckinRequestStatus status);
}
