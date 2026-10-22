package ru.rutcampustrack.attendance.studentrequest;

import java.util.Optional;
import org.springframework.data.mongodb.repository.MongoRepository;
import ru.rutcampustrack.attendance.studentrequest.entity.StudentRequestReceiptDocument;

public interface StudentRequestReceiptRepository
        extends MongoRepository<StudentRequestReceiptDocument, String> {

    Optional<StudentRequestReceiptDocument> findByStudentIdAndCommandKindAndIdempotencyKey(
            Long studentId, String commandKind, String idempotencyKey);
}
