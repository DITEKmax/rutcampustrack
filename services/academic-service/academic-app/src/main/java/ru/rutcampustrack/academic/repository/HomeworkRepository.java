package ru.rutcampustrack.academic.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import ru.rutcampustrack.academic.entity.Homework;
import ru.rutcampustrack.academic.contract.enums.HomeworkPublicationState;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface HomeworkRepository extends JpaRepository<Homework, Long> {
    Optional<Homework> findByActorIdAndRequestKey(Long actorId, UUID requestKey);

    List<Homework> findByGroupIdAndSemesterId(Long groupId, Long semesterId);
    List<Homework> findByGroupIdAndSemesterIdAndPublicationState(
            Long groupId, Long semesterId, HomeworkPublicationState publicationState);
    Page<Homework> findByGroupIdAndSubjectIdAndSemesterId(
        Long groupId, Long subjectId, Long semesterId, Pageable pageable);
    List<Homework> findByGroupIdAndSemesterIdAndLessonDateBetweenOrderByLessonDateAscLessonNumberAscIdAsc(
            Long groupId, Long semesterId, LocalDate from, LocalDate to);
    List<Homework> findByGroupIdAndSemesterIdAndPublicationStateAndLessonDateBetweenOrderByLessonDateAscLessonNumberAscIdAsc(
            Long groupId, Long semesterId, HomeworkPublicationState publicationState,
            LocalDate from, LocalDate to);
    List<Homework> findBySemesterIdAndLessonDateAndDueReminderSentAtIsNullOrderByGroupIdAscLessonNumberAscIdAsc(
            Long semesterId, LocalDate lessonDate);
    List<Homework> findBySemesterIdAndPublicationStateAndLessonDateAndDueReminderSentAtIsNullOrderByGroupIdAscLessonNumberAscIdAsc(
            Long semesterId, HomeworkPublicationState publicationState, LocalDate lessonDate);
}
