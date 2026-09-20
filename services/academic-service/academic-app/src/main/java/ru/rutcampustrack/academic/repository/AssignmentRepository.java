package ru.rutcampustrack.academic.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.rutcampustrack.academic.contract.enums.SubjectType;
import ru.rutcampustrack.academic.entity.Assignment;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface AssignmentRepository extends JpaRepository<Assignment, Long> {

    List<Assignment> findByGroupIdAndSemesterId(Long groupId, Long semesterId);

    List<Assignment> findByTeacherIdAndSemesterId(Long teacherId, Long semesterId);

    List<Assignment> findByTeacherIdAndSemesterIdAndValidFromLessThanEqual(
            Long teacherId, Long semesterId, LocalDate today);

    List<Assignment> findBySubjectId(Long subjectId);

    List<Assignment> findBySubjectIdAndLessonType(Long subjectId, SubjectType lessonType);

    List<Assignment> findBySemesterIdOrderByIdAsc(Long semesterId);

    Optional<Assignment> findByIdAndSubjectIdAndGroupIdAndTeacherId(
            Long id, Long subjectId, Long groupId, Long teacherId);

    boolean existsBySemesterId(Long semesterId);

    boolean existsBySubjectId(Long subjectId);

    boolean existsBySubjectIdAndLessonType(Long subjectId, SubjectType lessonType);

    @Query(value = """
            SELECT a.*
            FROM assignments a
            JOIN semesters existing_semester ON existing_semester.id = a.semester_id
            WHERE a.teacher_id = :teacherId
              AND a.subject_id = :subjectId
              AND a.group_id = :groupId
              AND a.semester_id = :semesterId
              AND a.lesson_type = CAST(:lessonType AS subject_type)
              AND daterange(
                    a.valid_from,
                    COALESCE(a.valid_until_exclusive, existing_semester.date_to + 1),
                    '[)'
                  ) && daterange(
                    CAST(:validFrom AS date),
                    COALESCE(CAST(:validUntilExclusive AS date), existing_semester.date_to + 1),
                    '[)'
                  )
            LIMIT 1
            """, nativeQuery = true)
    Optional<Assignment> findOverlapping(@Param("teacherId") Long teacherId,
                                         @Param("subjectId") Long subjectId,
                                         @Param("groupId") Long groupId,
                                         @Param("semesterId") Long semesterId,
                                         @Param("lessonType") String lessonType,
                                         @Param("validFrom") LocalDate validFrom,
                                         @Param("validUntilExclusive") LocalDate validUntilExclusive);
}
