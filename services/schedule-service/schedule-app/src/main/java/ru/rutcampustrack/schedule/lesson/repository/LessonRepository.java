package ru.rutcampustrack.schedule.lesson.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.rutcampustrack.schedule.contract.enums.LessonStatus;
import ru.rutcampustrack.schedule.lesson.entity.Lesson;

import java.time.LocalDate;
import java.util.List;

public interface LessonRepository extends JpaRepository<Lesson, Long> {

    List<Lesson> findByScheduleItemIdAndDateBetween(Long scheduleItemId, LocalDate from, LocalDate to);

    List<Lesson> findByStatusInAndDateBefore(List<LessonStatus> statuses, LocalDate date);
}
