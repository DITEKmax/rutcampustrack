package ru.rutcampustrack.academic.homework;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.rutcampustrack.academic.contract.enums.UserRole;
import ru.rutcampustrack.academic.contract.enums.AccountStatus;
import ru.rutcampustrack.academic.contract.exception.ResourceNotFoundException;
import ru.rutcampustrack.academic.entity.Homework;
import ru.rutcampustrack.academic.entity.HomeworkCompletion;
import ru.rutcampustrack.academic.entity.Semester;
import ru.rutcampustrack.academic.entity.User;
import ru.rutcampustrack.academic.exception.AccessDeniedException;
import ru.rutcampustrack.academic.repository.HomeworkCompletionRepository;
import ru.rutcampustrack.academic.repository.HomeworkRepository;
import ru.rutcampustrack.academic.repository.SemesterRepository;
import ru.rutcampustrack.academic.repository.UserRepository;
import ru.rutcampustrack.shared.security.InternalJwtClaims;

import java.time.OffsetDateTime;

/**
 * Student-only homework commands used by the trusted mobile BFF.
 *
 * <p>The request carries no actor or group identifier.  Both are resolved from
 * the validated internal JWT and checked against the current database user and
 * the homework row before the transaction changes completion state.</p>
 */
@Service
public class HomeworkStudentService {

    private final HomeworkRepository homeworkRepository;
    private final HomeworkCompletionRepository completionRepository;
    private final SemesterRepository semesterRepository;
    private final UserRepository userRepository;

    public HomeworkStudentService(HomeworkRepository homeworkRepository,
                                  HomeworkCompletionRepository completionRepository,
                                  SemesterRepository semesterRepository,
                                  UserRepository userRepository) {
        this.homeworkRepository = homeworkRepository;
        this.completionRepository = completionRepository;
        this.semesterRepository = semesterRepository;
        this.userRepository = userRepository;
    }

    /**
     * Applies the desired state atomically and returns the state observed in
     * this transaction.  The unique database constraint plus INSERT ... ON
     * CONFLICT makes repeated/concurrent complete commands safe.
     */
    @Transactional
    public CompletionState setCompletion(long homeworkId,
                                         long requestedSemesterId,
                                         InternalJwtClaims claims,
                                         boolean completed) {
        if (claims != null && claims.readOnly()) {
            throw new AccessDeniedException("Терминальная student-сессия доступна только для чтения");
        }
        Long studentId = requireStudent(claims);
        User student = userRepository.findById(studentId)
                .orElseThrow(() -> new AccessDeniedException("Студент недоступен"));
        if (student.getRole() != UserRole.STUDENT
                || student.getStatus() != AccountStatus.ACTIVE
                || student.getGroupId() == null
                || !student.getGroupId().equals(claims.groupId())) {
            throw new AccessDeniedException("JWT scope не совпадает с текущим студентом");
        }

        Semester active = semesterRepository.findByIsActiveTrue()
                .orElseThrow(() -> new ResourceNotFoundException("Semester", "isActive", true));
        if (requestedSemesterId != active.getId()) {
            throw new AccessDeniedException("Семестр не входит в текущий student scope");
        }

        Homework homework = homeworkRepository.findById(homeworkId)
                .orElseThrow(() -> new ResourceNotFoundException("Homework", "id", homeworkId));
        if (!student.getGroupId().equals(homework.getGroupId())) {
            throw new AccessDeniedException("ДЗ принадлежит другой группе");
        }
        if (!active.getId().equals(homework.getSemesterId())) {
            throw new AccessDeniedException("ДЗ принадлежит неактивному семестру");
        }

        if (completed) {
            completionRepository.insertIfAbsent(homework.getId(), studentId);
            HomeworkCompletion completion = completionRepository
                    .findByHomeworkIdAndStudentId(homework.getId(), studentId)
                    .orElseThrow(() -> new IllegalStateException("Completion row was not persisted"));
            return new CompletionState(true, completion.getCompletedAt());
        } else {
            completionRepository.deleteByHomeworkIdAndStudentId(homework.getId(), studentId);
            return new CompletionState(false, null);
        }
    }

    public record CompletionState(boolean completed, OffsetDateTime completedAt) {
        public CompletionState {
            if (completed != (completedAt != null)) {
                throw new IllegalStateException("Completion state and timestamp disagree");
            }
        }
    }

    private static Long requireStudent(InternalJwtClaims claims) {
        if (claims == null || !"STUDENT".equalsIgnoreCase(claims.role())
                || claims.userId() <= 0
                || claims.groupId() == null || claims.groupId() <= 0) {
            throw new AccessDeniedException("Нужен scope активного STUDENT");
        }
        return claims.userId();
    }
}
