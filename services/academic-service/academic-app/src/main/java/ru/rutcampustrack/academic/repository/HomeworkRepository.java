package ru.rutcampustrack.academic.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import ru.rutcampustrack.academic.entity.Homework;
import java.time.LocalDate;
import java.util.List;

public interface HomeworkRepository extends JpaRepository<Homework, Long> {
    List<Homework> findByGroupIdAndSemesterId(Long groupId, Long semesterId);
    Page<Homework> findByGroupIdAndSubjectIdAndSemesterId(
        Long groupId, Long subjectId, Long semesterId, Pageable pageable);
    List<Homework> findByGroupIdAndSemesterIdAndLessonDateBetweenOrderByLessonDateAscLessonNumberAscIdAsc(
            Long groupId, Long semesterId, LocalDate from, LocalDate to);
    List<Homework> findBySemesterIdAndLessonDateAndDueReminderSentAtIsNullOrderByGroupIdAscLessonNumberAscIdAsc(
            Long semesterId, LocalDate lessonDate);
}
