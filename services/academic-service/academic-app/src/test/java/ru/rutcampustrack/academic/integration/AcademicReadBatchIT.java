package ru.rutcampustrack.academic.integration;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.persistence.EntityManagerFactory;
import java.util.ArrayList;
import java.util.List;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PagedResourcesAssembler;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.util.UriComponentsBuilder;
import ru.rutcampustrack.academic.assignment.AssignmentLifecycle;
import ru.rutcampustrack.academic.contract.dto.homework.HomeworkResponse;
import ru.rutcampustrack.academic.contract.enums.SubjectType;
import ru.rutcampustrack.academic.contract.enums.UserRole;
import ru.rutcampustrack.academic.entity.Assignment;
import ru.rutcampustrack.academic.homework.HomeworkAssembler;
import ru.rutcampustrack.academic.homework.HomeworkController;
import ru.rutcampustrack.academic.homework.HomeworkService;
import ru.rutcampustrack.academic.repository.AssignmentRepository;
import ru.rutcampustrack.academic.repository.SubjectRepository;
import ru.rutcampustrack.academic.security.RequestContext;
import ru.rutcampustrack.academic.subject.SubjectAssembler;

/** Real SQL deltas and response fields for the two current-page enrichment paths. */
class AcademicReadBatchIT extends AbstractAssignmentAuthorityIT {
    @Autowired EntityManagerFactory entityManagers;
    @Autowired SubjectRepository subjects;
    @Autowired AssignmentRepository assignments;
    @Autowired SubjectAssembler subjectAssembler;
    @Autowired HomeworkService homeworkService;
    @Autowired HomeworkAssembler homeworkAssembler;
    @Autowired RequestContext actor;
    private Statistics statistics;
    private Fixture fixture;

    @BeforeEach
    void fixture() {
        fixture = newFixture("batch-read");
        RequestContextHolder.setRequestAttributes(
                new ServletRequestAttributes(new MockHttpServletRequest()));
        actor.setUserId(fixture.headmanId());
        actor.setGroupId(fixture.groupId());
        actor.setRole(UserRole.STUDENT);
        statistics = entityManagers.unwrap(SessionFactory.class).getStatistics();
        statistics.setStatisticsEnabled(true);
    }

    @AfterEach
    void clearRequest() {
        statistics.setStatisticsEnabled(false);
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void subjectPageUsesTwoEnrichmentQueriesAndPreservesOrderScopeAndPublication() {
        List<Long> ids = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            long subject = insertSubject(fixture.groupId(), "Batch " + i, "practice", "lecture");
            ids.add(subject);
            assignments.saveAndFlush(
                    new Assignment(
                            fixture.teacher1Id(),
                            subject,
                            fixture.groupId(),
                            fixture.semesterId(),
                            SubjectType.LECTURE,
                            fixture.dateFrom(),
                            fixture.dateTo()));
            assignments.saveAndFlush(
                    new Assignment(
                            fixture.teacher2Id(),
                            subject,
                            fixture.groupId(),
                            fixture.semesterId(),
                            SubjectType.PRACTICE,
                            fixture.dateFrom(),
                            fixture.dateTo(),
                            AssignmentLifecycle.PREPARED));
        }
        long foreignGroup = insertGroup("BATCH-FOREIGN");
        insertSubject(foreignGroup, "Foreign subject", "lecture");
        for (int size : new int[] {1, 4}) {
            var page =
                    subjects.findByGroupId(
                            fixture.groupId(), PageRequest.of(1, size, Sort.by("id")));
            statistics.clear();
            var response = subjectAssembler.toResponsePage(page);
            assertThat(statistics.getPrepareStatementCount()).isEqualTo(2);
            assertThat(response.getTotalElements()).isEqualTo(6);
            assertThat(response.getNumber()).isEqualTo(1);
            assertThat(response.getContent())
                    .extracting(r -> r.getId())
                    .containsExactlyElementsOf(ids.subList(size, Math.min(size * 2, 6)));
            response.forEach(
                    r -> {
                        assertThat(r.getGroupId()).isEqualTo(fixture.groupId());
                        assertThat(r.getLessonTypes())
                                .containsExactly(SubjectType.LECTURE, SubjectType.PRACTICE);
                        assertThat(r.getTeacherIds()).containsExactly(fixture.teacher1Id());
                        assertThat(r.getAssignments()).hasSize(1);
                        assertThat(r.getCreatedAssignmentIds()).hasSize(1);
                    });
        }
        var empty = subjects.findByGroupId(fixture.groupId(), PageRequest.of(10, 4));
        statistics.clear();
        assertThat(subjectAssembler.toResponsePage(empty)).isEmpty();
        assertThat(statistics.getPrepareStatementCount()).isZero();
    }

    @Test
    void homeworkPageUsesOneCompletionQueryForCurrentActorIncludingEmptyPage() {
        long subject = insertSubject(fixture.groupId(), "Batch homework", "lecture");
        long otherStudent =
                jdbcTemplate.queryForObject(
                        """
                INSERT INTO users(login,password_hash,last_name,first_name,role,status,group_id)
                VALUES (?,'synthetic-hash','Batch','Other','student','active',?) RETURNING id
                """,
                        Long.class,
                        "batch-other-" + java.util.UUID.randomUUID().toString().substring(0, 16),
                        fixture.groupId());
        jdbcTemplate.update(
                "INSERT INTO user_role_grants(user_id,role,status,group_id,created_at,updated_at) VALUES (?,'student','active',?,NOW(),NOW())",
                otherStudent,
                fixture.groupId());
        List<Long> ids = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            ids.add(
                    jdbcTemplate.queryForObject(
                            """
INSERT INTO homeworks(group_id,subject_id,semester_id,title,published_by,
    lesson_date,lesson_number,binding_id,actor_id,request_key,payload_hash,publication_state)
VALUES (?,?,?, ?,?, ?,1,?, ?,gen_random_uuid(),decode(repeat('00',32),'hex'),'ACTIVE') RETURNING id
""",
                            Long.class,
                            fixture.groupId(),
                            subject,
                            fixture.semesterId(),
                            "Homework " + i,
                            fixture.headmanId(),
                            fixture.dateFrom(),
                            900000L + i,
                            fixture.headmanId()));
        }
        jdbcTemplate.update(
                "INSERT INTO homework_completions(homework_id,student_id) VALUES (?,?),(?,?),(?,?)",
                ids.get(0),
                fixture.headmanId(),
                ids.get(3),
                fixture.headmanId(),
                ids.get(1),
                otherStudent);
        HomeworkController controller = new HomeworkController(homeworkService, homeworkAssembler);
        var assembler =
                new PagedResourcesAssembler<HomeworkResponse>(
                        null,
                        UriComponentsBuilder.fromUriString(
                                        "https://example.test/academic/homeworks")
                                .build());
        for (int size : new int[] {1, 4}) {
            statistics.clear();
            var body =
                    controller
                            .listHomeworks(
                                    fixture.groupId(),
                                    fixture.semesterId(),
                                    false,
                                    PageRequest.of(0, size),
                                    assembler)
                            .getBody();
            assertThat(statistics.getPrepareStatementCount()).isEqualTo(2);
            assertThat(body.getMetadata().getTotalElements()).isEqualTo(6);
            var rows = body.getContent().stream().map(model -> model.getContent()).toList();
            // Preserve the underlying page order; only completion enrichment changes.
            var expected =
                    homeworkService
                            .listHomeworks(
                                    fixture.groupId(),
                                    fixture.semesterId(),
                                    PageRequest.of(0, size))
                            .stream()
                            .map(hw -> hw.getId())
                            .toList();
            assertThat(rows)
                    .extracting(HomeworkResponse::getId)
                    .containsExactlyElementsOf(expected);
            rows.forEach(
                    row ->
                            assertThat(row.isCompleted())
                                    .isEqualTo(
                                            row.getId().equals(ids.get(0))
                                                    || row.getId().equals(ids.get(3))));
        }
        statistics.clear();
        assertThat(
                        controller
                                .listHomeworks(
                                        fixture.groupId(),
                                        fixture.semesterId(),
                                        false,
                                        PageRequest.of(20, 4),
                                        assembler)
                                .getBody()
                                .getContent())
                .isEmpty();
        assertThat(statistics.getPrepareStatementCount()).isEqualTo(1);
        actor.setUserId(otherStudent);
        assertThat(homeworkService.completedHomeworkIds(ids)).containsExactly(ids.get(1));
        actor.setUserId(fixture.headmanId());
        assertThat(homeworkService.completedHomeworkIds(ids))
                .containsExactlyInAnyOrder(ids.get(0), ids.get(3));
    }

    @Test
    void assignmentLifecycleRoundTripsJpaAndNativeQueriesUsingExistingTextCodes() {
        for (AssignmentLifecycle state : AssignmentLifecycle.values()) {
            long subject =
                    insertSubject(fixture.groupId(), "Lifecycle " + state.getCode(), "lecture");
            var saved =
                    assignments.saveAndFlush(
                            new Assignment(
                                    fixture.teacher1Id(),
                                    subject,
                                    fixture.groupId(),
                                    fixture.semesterId(),
                                    SubjectType.LECTURE,
                                    fixture.dateFrom(),
                                    fixture.dateTo(),
                                    state));
            assertThat(
                            jdbcTemplate.queryForObject(
                                    "SELECT lifecycle_state FROM assignments WHERE id=?",
                                    String.class,
                                    saved.getId()))
                    .isEqualTo(state.getCode());
            assertThat(assignments.findById(saved.getId()).orElseThrow().getLifecycleState())
                    .isEqualTo(state);
            assertThat(assignments.findBySubjectId(subject))
                    .extracting(Assignment::getLifecycleState)
                    .contains(state);
            assertThat(
                            assignments.findPublishedActiveBySubjectIdAndGroupId(
                                    subject, fixture.groupId()))
                    .hasSize(state == AssignmentLifecycle.ACTIVE ? 1 : 0);
        }
    }
}
