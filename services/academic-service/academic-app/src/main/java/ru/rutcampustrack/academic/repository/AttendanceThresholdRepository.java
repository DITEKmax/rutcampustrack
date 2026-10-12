package ru.rutcampustrack.academic.repository;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import ru.rutcampustrack.academic.entity.AttendanceThreshold;

public interface AttendanceThresholdRepository extends JpaRepository<AttendanceThreshold, Long> {
    /** Subject-level threshold — most specific (THRSH-03) */
    Optional<AttendanceThreshold> findByGroupIdAndSubjectId(Long groupId, Long subjectId);

    /** Group-level threshold — mid specificity (THRSH-02) */
    Optional<AttendanceThreshold> findByGroupIdAndSubjectIdIsNull(Long groupId);

    /** Global threshold — least specific (THRSH-01) */
    Optional<AttendanceThreshold> findByGroupIdIsNullAndSubjectIdIsNull();
}
