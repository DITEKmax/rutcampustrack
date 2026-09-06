package ru.rutcampustrack.attendance.student;

import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;

public interface StudentCheckinReceiptRepository extends MongoRepository<StudentCheckinReceiptDocument, String> {
    Optional<StudentCheckinReceiptDocument> findByStudentIdAndLessonIdAndIdempotencyKey(
            Long studentId, Long lessonId, String idempotencyKey);
}
