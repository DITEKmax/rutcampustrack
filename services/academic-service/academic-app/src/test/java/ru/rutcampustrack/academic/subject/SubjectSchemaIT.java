package ru.rutcampustrack.academic.subject;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import ru.rutcampustrack.academic.entity.Subject;
import ru.rutcampustrack.academic.integration.AbstractAcademicIntegrationTest;
import ru.rutcampustrack.academic.repository.SubjectRepository;

/**
 * Phase 60-01 / D-01: подтверждает, что после миграции V12:
 *
 * <ul>
 *   <li>{@code subjects.group_id} — {@code NOT NULL};
 *   <li>{@code subjects.group_id} ссылается на {@code groups(id)} (FK);
 *   <li>{@link SubjectRepository#findByGroupId} фильтрует по группе.
 * </ul>
 */
class SubjectSchemaIT extends AbstractAcademicIntegrationTest {

    @Autowired private SubjectRepository subjectRepository;

    @Autowired private JdbcTemplate jdbcTemplate;

    private Long seedGroupId() {
        return jdbcTemplate.queryForObject(
                "SELECT id FROM groups WHERE name = 'ИВТ-211'", Long.class);
    }

    @Test
    void groupIdIsNotNull() {
        assertThatThrownBy(
                        () ->
                                jdbcTemplate.update(
                                        "INSERT INTO subjects(name,type,group_id,semester_id)"
                                            + " VALUES('null group','lecture',NULL,?)",
                                        identityFixture.canonical().semesterId()))
                .isInstanceOf(DataIntegrityViolationException.class)
                .satisfies(
                        error -> {
                            var sql = (org.postgresql.util.PSQLException) error.getCause();
                            assertThat(sql.getSQLState()).isEqualTo("23502");
                            assertThat(sql.getServerErrorMessage().getColumn())
                                    .isEqualTo("group_id");
                        });
    }

    @Test
    void groupIdHasForeignKey() {
        assertThatThrownBy(
                        () ->
                                jdbcTemplate.update(
                                        "INSERT INTO subjects(name,type,group_id,semester_id)"
                                            + " VALUES('missing group','lecture',?,?)",
                                        Long.MAX_VALUE,
                                        identityFixture.canonical().semesterId()))
                .isInstanceOf(DataIntegrityViolationException.class)
                .satisfies(
                        error -> {
                            var sql = (org.postgresql.util.PSQLException) error.getCause();
                            assertThat(sql.getSQLState()).isEqualTo("23503");
                            assertThat(sql.getServerErrorMessage().getConstraint())
                                    .isEqualTo("fk_subjects_group");
                        });
    }

    @Test
    @Transactional
    void findByGroupId_returnsOnlyThatGroupSubjects() {
        Long groupId =
                jdbcTemplate.queryForObject(
                        "INSERT INTO groups(name,is_active,created_at) VALUES('ТСТ-111',true,NOW())"
                            + " RETURNING id",
                        Long.class);
        Long foreignGroupId =
                jdbcTemplate.queryForObject(
                        "INSERT INTO groups(name,is_active,created_at) VALUES('ТСТ-112',true,NOW())"
                            + " RETURNING id",
                        Long.class);
        long first = subject(groupId, "Calculus");
        long second = subject(groupId, "Linear Algebra");
        long foreign = subject(foreignGroupId, "Foreign subject");
        Page<Subject> page = subjectRepository.findByGroupId(groupId, PageRequest.of(0, 20));
        assertThat(page.getTotalElements()).isEqualTo(2);
        assertThat(page.getContent())
                .extracting(Subject::getId)
                .containsExactlyInAnyOrder(first, second)
                .doesNotContain(foreign);
        assertThat(page.getContent()).allMatch(row -> row.getGroupId().equals(groupId));
    }

    private long subject(long groupId, String name) {
        return jdbcTemplate.queryForObject(
                "INSERT INTO subjects(name,type,group_id,semester_id) VALUES(?,'lecture',?,?)"
                    + " RETURNING id",
                Long.class,
                name,
                groupId,
                identityFixture.canonical().semesterId());
    }
}
