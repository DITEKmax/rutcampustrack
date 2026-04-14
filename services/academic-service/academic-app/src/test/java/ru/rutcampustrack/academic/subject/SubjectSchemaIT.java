package ru.rutcampustrack.academic.subject;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import ru.rutcampustrack.academic.contract.enums.SubjectType;
import ru.rutcampustrack.academic.entity.Subject;
import ru.rutcampustrack.academic.integration.AbstractAcademicIntegrationTest;
import ru.rutcampustrack.academic.repository.SubjectRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Phase 60-01 / D-01: подтверждает, что после миграции V12:
 * <ul>
 *   <li>{@code subjects.group_id} — {@code NOT NULL};</li>
 *   <li>{@code subjects.group_id} ссылается на {@code groups(id)} (FK);</li>
 *   <li>{@link SubjectRepository#findByGroupId} фильтрует по группе.</li>
 * </ul>
 */
class SubjectSchemaIT extends AbstractAcademicIntegrationTest {

    @Autowired
    private SubjectRepository subjectRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private Long seedGroupId() {
        return jdbcTemplate.queryForObject(
                "SELECT id FROM groups WHERE name = 'ИВТ-211'", Long.class);
    }

    @Test
    @Transactional
    void groupIdIsNotNull() {
        Subject subject = new Subject();
        subject.setName("Algorithms");
        subject.setType(SubjectType.LECTURE);
        // groupId = null — нарушение NOT NULL constraint
        assertThatThrownBy(() -> subjectRepository.saveAndFlush(subject))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @Transactional
    void groupIdHasForeignKey() {
        Subject subject = new Subject();
        subject.setName("Algorithms");
        subject.setType(SubjectType.LECTURE);
        subject.setGroupId(999_999_999L); // несуществующая группа

        assertThatThrownBy(() -> subjectRepository.saveAndFlush(subject))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @Transactional
    void findByGroupId_returnsOnlyThatGroupSubjects() {
        Long groupId = seedGroupId();

        Subject s1 = new Subject();
        s1.setName("Calculus");
        s1.setType(SubjectType.LECTURE);
        s1.setGroupId(groupId);
        subjectRepository.saveAndFlush(s1);

        Subject s2 = new Subject();
        s2.setName("Linear Algebra");
        s2.setType(SubjectType.PRACTICE);
        s2.setGroupId(groupId);
        subjectRepository.saveAndFlush(s2);

        Page<Subject> page = subjectRepository.findByGroupId(groupId, PageRequest.of(0, 20));
        assertThat(page.getContent())
                .extracting(Subject::getName)
                .contains("Calculus", "Linear Algebra");
        assertThat(page.getContent())
                .allMatch(s -> s.getGroupId().equals(groupId));
    }
}
