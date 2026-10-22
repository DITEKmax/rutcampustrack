package ru.rutcampustrack.attendance.student;

import java.util.Optional;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface StudentCheckinReceiptRepository
        extends MongoRepository<StudentCheckinReceiptDocument, String> {
    Optional<StudentCheckinReceiptDocument> findByStudentIdAndLessonIdAndIdempotencyKey(
            Long studentId, Long lessonId, String idempotencyKey);
}
