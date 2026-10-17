package ru.rutcampustrack.attendance.studentrequest;

import java.util.Optional;
import org.springframework.data.mongodb.repository.MongoRepository;
import ru.rutcampustrack.attendance.studentrequest.entity.StudentLateCheckinBudgetDocument;

public interface StudentLateCheckinBudgetRepository
        extends MongoRepository<StudentLateCheckinBudgetDocument, String> {

    Optional<StudentLateCheckinBudgetDocument> findByStudentIdAndSemesterId(
            Long studentId, Long semesterId);
}
