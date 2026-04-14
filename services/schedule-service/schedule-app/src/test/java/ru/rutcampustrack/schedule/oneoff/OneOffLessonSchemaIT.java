package ru.rutcampustrack.schedule.oneoff;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import ru.rutcampustrack.schedule.integration.AbstractScheduleIntegrationTest;
import ru.rutcampustrack.schedule.oneoff.entity.OneOffLesson;
import ru.rutcampustrack.schedule.oneoff.repository.OneOffLessonRepository;

import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Schema-level IT for schedule_one_off_lessons (Flyway V4, D-04/D-21/D-23).
 * Verifies:
 *  - UNIQUE(group_id, date, lesson_number) rejects duplicates.
 *  - semester_id NOT NULL constraint.
 *  - Round-trip persistence via the JPA repository.
 */
class OneOffLessonSchemaIT extends AbstractScheduleIntegrationTest {

    private static final Long GROUP_ID = 9001L;
    private static final Long SUBJECT_ID = 9100L;
    private static final Long SEMESTER_ID = 9200L;
    private static final Long CREATED_BY = 9300L;

    @Autowired
    OneOffLessonRepository repository;

    @BeforeEach
    void cleanUp() {
        repository.deleteAll();
    }

    private OneOffLesson newLesson(LocalDate date, short lessonNumber) {
        OneOffLesson o = new OneOffLesson();
        o.setGroupId(GROUP_ID);
        o.setSubjectId(SUBJECT_ID);
        o.setSemesterId(SEMESTER_ID);
        o.setDate(date);
        o.setLessonNumber(lessonNumber);
        o.setClassroom("A-101");
        o.setCreatedBy(CREATED_BY);
        return o;
    }

    @Test
    void uniqueConstraint_preventsDuplicate() {
        LocalDate day = LocalDate.of(2026, 4, 20);
        repository.saveAndFlush(newLesson(day, (short) 1));

        OneOffLesson dup = newLesson(day, (short) 1);
        assertThatThrownBy(() -> repository.saveAndFlush(dup))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void semesterIdNotNull_rejected() {
        OneOffLesson bad = newLesson(LocalDate.of(2026, 4, 21), (short) 2);
        bad.setSemesterId(null);
        assertThatThrownBy(() -> repository.saveAndFlush(bad))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void canInsertAndRetrieve() {
        LocalDate day = LocalDate.of(2026, 4, 22);
        OneOffLesson saved = repository.saveAndFlush(newLesson(day, (short) 3));

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getCreatedAt()).isNotNull();

        Optional<OneOffLesson> fetched =
                repository.findByGroupIdAndDateAndLessonNumber(GROUP_ID, day, (short) 3);
        assertThat(fetched).isPresent();
        assertThat(fetched.get().getClassroom()).isEqualTo("A-101");
        assertThat(fetched.get().getSemesterId()).isEqualTo(SEMESTER_ID);
    }
}
