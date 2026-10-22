package ru.rutcampustrack.academic.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import ru.rutcampustrack.academic.contract.enums.HomeworkPublicationState;
import ru.rutcampustrack.academic.entity.Homework;

public interface HomeworkRepository extends JpaRepository<Homework, Long> {
    @org.springframework.data.jpa.repository.Query(
            """
        SELECT h FROM Homework h WHERE h.groupId = :groupId AND h.semesterId = :semesterId
        AND (h.publicationState = ru.rutcampustrack.academic.contract.enums.HomeworkPublicationState.ARCHIVED
          OR (h.publicationState = ru.rutcampustrack.academic.contract.enums.HomeworkPublicationState.ACTIVE
              AND h.bindingMode = ru.rutcampustrack.academic.contract.enums.HomeworkBindingMode.DATE AND h.lessonDate < :today))
        ORDER BY h.lessonDate DESC, h.id DESC
        """)
    Page<Homework> findArchive(Long groupId, Long semesterId, LocalDate today, Pageable pageable);

    Optional<Homework> findByActorIdAndRequestKey(Long actorId, UUID requestKey);

    Optional<Homework> findByBindingId(Long bindingId);

    boolean existsByBindingId(Long bindingId);

    List<Homework> findByGroupIdAndSemesterId(Long groupId, Long semesterId);

    List<Homework> findByGroupIdAndSemesterIdAndPublicationState(
            Long groupId, Long semesterId, HomeworkPublicationState publicationState);

    Page<Homework> findByGroupIdAndSubjectIdAndSemesterId(
            Long groupId, Long subjectId, Long semesterId, Pageable pageable);

    List<Homework>
            findByGroupIdAndSemesterIdAndLessonDateBetweenOrderByLessonDateAscLessonNumberAscIdAsc(
                    Long groupId, Long semesterId, LocalDate from, LocalDate to);

    List<Homework>
            findByGroupIdAndSemesterIdAndPublicationStateAndLessonDateBetweenOrderByLessonDateAscLessonNumberAscIdAsc(
                    Long groupId,
                    Long semesterId,
                    HomeworkPublicationState publicationState,
                    LocalDate from,
                    LocalDate to);

    List<Homework>
            findBySemesterIdAndLessonDateAndDueReminderSentAtIsNullOrderByGroupIdAscLessonNumberAscIdAsc(
                    Long semesterId, LocalDate lessonDate);

    List<Homework> findBySemesterIdAndLessonDateOrderByGroupIdAscLessonNumberAscIdAsc(
            Long semesterId, LocalDate lessonDate);

    List<Homework>
            findBySemesterIdAndPublicationStateAndLessonDateAndDueReminderSentAtIsNullOrderByGroupIdAscLessonNumberAscIdAsc(
                    Long semesterId,
                    HomeworkPublicationState publicationState,
                    LocalDate lessonDate);
}
