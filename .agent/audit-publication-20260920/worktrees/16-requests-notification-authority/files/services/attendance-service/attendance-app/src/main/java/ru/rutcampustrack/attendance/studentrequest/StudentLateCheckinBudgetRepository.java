package ru.rutcampustrack.attendance.studentrequest;

import org.springframework.data.mongodb.repository.MongoRepository;
import ru.rutcampustrack.attendance.studentrequest.entity.StudentLateCheckinBudgetDocument;

import java.util.Optional;

public interface StudentLateCheckinBudgetRepository
        extends MongoRepository<StudentLateCheckinBudgetDocument, String> {

    Optional<StudentLateCheckinBudgetDocument> findByStudentIdAndSemesterId(Long studentId, Long semesterId);
}
