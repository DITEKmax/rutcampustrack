package ru.rutcampustrack.schedule.oneoff.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.rutcampustrack.schedule.oneoff.projection.OneOffCurrentLessonProjection;
import ru.rutcampustrack.schedule.oneoff.entity.OneOffLesson;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface OneOffLessonRepository extends JpaRepository<OneOffLesson, Long> {

    String CURRENT_SNAPSHOT = """
            SELECT origin.id AS id, physical.id AS physicalLessonId, origin.group_id AS groupId,
                   origin.subject_id AS subjectId, origin.semester_id AS semesterId,
                   physical.date AS date, physical.lesson_number AS lessonNumber,
                   physical.room_snapshot AS classroom, origin.created_by AS createdBy,
                   origin.created_at AS createdAt
              FROM schedule_one_off_lessons origin
              JOIN lesson_occurrences occurrence ON occurrence.one_off_lesson_id = origin.id
              JOIN lessons physical ON physical.id = occurrence.current_lesson_id
                   AND physical.occurrence_id = occurrence.id AND physical.one_off_lesson_id = origin.id
                   AND physical.id = origin.physical_lesson_id
            """;

    @Query(value = CURRENT_SNAPSHOT + " WHERE origin.id = :id", nativeQuery = true)
    Optional<OneOffCurrentLessonProjection> findCurrentSnapshot(@Param("id") Long id);

    @Query(value = CURRENT_SNAPSHOT + """
            WHERE origin.group_id = :groupId AND physical.date BETWEEN :dateFrom AND :dateTo
            ORDER BY physical.date, physical.start_time, physical.id
            """, nativeQuery = true)
    List<OneOffCurrentLessonProjection> findCurrentSnapshots(
            @Param("groupId") Long groupId, @Param("dateFrom") LocalDate dateFrom,
            @Param("dateTo") LocalDate dateTo);

    List<OneOffLesson> findByGroupIdAndDateBetween(Long groupId, LocalDate dateFrom, LocalDate dateTo);

    boolean existsByGroupIdAndDateAndLessonNumber(Long groupId, LocalDate date, Short lessonNumber);

    Optional<OneOffLesson> findByGroupIdAndDateAndLessonNumber(Long groupId, LocalDate date, Short lessonNumber);

    /** Used by subject.deleted cascade — returns all one-off rows for subject. */
    List<OneOffLesson> findBySubjectId(Long subjectId);

    long countBySubjectId(Long subjectId);
}
