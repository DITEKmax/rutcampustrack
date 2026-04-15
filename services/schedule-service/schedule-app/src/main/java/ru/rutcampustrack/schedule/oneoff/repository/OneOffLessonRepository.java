package ru.rutcampustrack.schedule.oneoff.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.rutcampustrack.schedule.oneoff.entity.OneOffLesson;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface OneOffLessonRepository extends JpaRepository<OneOffLesson, Long> {

    List<OneOffLesson> findByGroupIdAndDateBetween(Long groupId, LocalDate dateFrom, LocalDate dateTo);

    boolean existsByGroupIdAndDateAndLessonNumber(Long groupId, LocalDate date, Short lessonNumber);

    Optional<OneOffLesson> findByGroupIdAndDateAndLessonNumber(Long groupId, LocalDate date, Short lessonNumber);

    /** Used by subject.deleted cascade — returns all one-off rows for subject. */
    List<OneOffLesson> findBySubjectId(Long subjectId);

    long countBySubjectId(Long subjectId);
}
