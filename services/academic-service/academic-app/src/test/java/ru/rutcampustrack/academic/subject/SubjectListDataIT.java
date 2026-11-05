package ru.rutcampustrack.academic.subject;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;

import jakarta.persistence.EntityManagerFactory;

import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.MockMvcPrint;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.util.AopTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import ru.rutcampustrack.academic.assignment.AssignmentClosureService;
import ru.rutcampustrack.academic.exception.ScheduleServiceUnavailableException;
import ru.rutcampustrack.academic.grpc.ScheduleGrpcClient;
import ru.rutcampustrack.academic.integration.AbstractAssignmentAuthorityIT;
import ru.rutcampustrack.schedule.grpc.AssignmentCloseReceipt;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Actual subject HTTP/JPA data oracles; Schedule adoption receipts are a controlled seam. */
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
@TestPropertySource(properties = "spring.jpa.properties.hibernate.generate_statistics=true")
class SubjectListDataIT extends AbstractAssignmentAuthorityIT {

    @Autowired private MockMvc mockMvc;
    @Autowired private EntityManagerFactory entityManagerFactory;
    @MockitoBean private ScheduleGrpcClient schedule;
    @MockitoSpyBean private AssignmentClosureService closureService;

    private Fixture fixture;
    private List<Long> priorActiveSemesters;

    @BeforeEach
    void ownedFixtures() {
        fixture = newFixture("lecture-list");
        priorActiveSemesters = activeSemesterIds();
        successfulAdoptionReceipts(schedule);
    }

    @AfterEach
    void preserveActiveSemesterFixture() {
        restoreActiveSemesterIds(priorActiveSemesters);
    }

    @Test
    void legalPageSizesPreserveExactOwnRowsTypesAssignmentsAndTotals() throws Exception {
        List<Long> expectedIds = new ArrayList<>();
        for (int index = 0; index < 51; index++) {
            expectedIds.add(
                    insertSubject(
                            fixture.groupId(), "L05 subject " + index, "lecture", "practice"));
        }
        Fixture other = newFixture("lecture-other-group");
        insertSubject(other.groupId(), "L05 subject 0", "lecture");
        long assignedSubject = expectedIds.getFirst();
        JsonNode assignment =
                createAssignment(
                        assignedSubject,
                        fixture.semesterId(),
                        fixture.dateFrom().plusDays(1),
                        false);
        long assignmentId = assignment.get("id").asLong();
        Statistics statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();

        for (int size : List.of(1, 10, 50)) {
            List<Long> seen = new ArrayList<>();
            int totalPages = (expectedIds.size() + size - 1) / size;
            for (int pageNumber = 0; pageNumber < totalPages; pageNumber++) {
                MockHttpServletRequestBuilder request =
                        own(
                                get("/academic/subjects")
                                        .param("size", Integer.toString(size))
                                        .param("page", Integer.toString(pageNumber))
                                        .param("sort", "id,asc"));
                statistics.clear();
                long started = System.nanoTime();
                JsonNode page =
                        response(
                                mockMvc.perform(request)
                                        .andExpect(status().isOk())
                                        .andReturn()
                                        .getResponse()
                                        .getContentAsString());
                long elapsedMicros = (System.nanoTime() - started) / 1_000;
                long statements = statistics.getPrepareStatementCount();
                // Hibernate measures JPA statements only; independent JDBC calls are outside this
                // diagnostic.
                System.out.printf(
                        "L05 list diagnostics: size=%d page=%d statements=%d elapsedMicros=%d%n",
                        size, pageNumber, statements, elapsedMicros);
                assertThat(page.path("page").path("totalElements").asLong()).isEqualTo(51);
                assertThat(page.path("page").path("totalPages").asInt()).isEqualTo(totalPages);
                JsonNode rows = page.path("_embedded").path("subjectResponseList");
                assertThat(rows).hasSize(Math.min(size, 51 - pageNumber * size));
                for (JsonNode row : rows) {
                    long id = row.get("id").asLong();
                    seen.add(id);
                    assertThat(row.get("groupId").asLong()).isEqualTo(fixture.groupId());
                    assertThat(textValues(row.get("lessonTypes")))
                            .containsExactly("LECTURE", "PRACTICE");
                    if (id == assignedSubject) {
                        assertThat(longValues(row.get("teacherIds")))
                                .containsExactly(fixture.teacher1Id());
                        assertThat(longValues(row.get("createdAssignmentIds")))
                                .containsExactly(assignmentId);
                        assertThat(row.get("assignments")).hasSize(1);
                        assertThat(row.get("assignments").get(0).get("id").asLong())
                                .isEqualTo(assignmentId);
                    } else {
                        assertThat(row.get("teacherIds")).isEmpty();
                        assertThat(row.get("assignments")).isEmpty();
                    }
                }
            }
            assertThat(seen).containsExactlyElementsOf(expectedIds);
        }
    }

    @Test
    void unassignedSubjectsKeepTheirOriginalSemesterAcrossActiveSemesterChanges() throws Exception {
        Fixture other = newFixture("subject-scope-other-group");
        long firstSemester = ensureCurrentActiveSemester();
        JsonNode first = createUnassignedSubject("Scoped unassigned A");
        assertThat(first.path("semesterId").asLong()).isEqualTo(firstSemester);

        long secondSemester = fixture.semesterId();
        restoreActiveSemesterIds(List.of(secondSemester));
        JsonNode second = createUnassignedSubject("Scoped unassigned B");
        assertThat(second.path("semesterId").asLong()).isEqualTo(secondSemester);
        long legacy = insertSubject(fixture.groupId(), "Legacy unassigned", "lecture");

        JsonNode page =
                response(
                        mockMvc.perform(own(get("/academic/subjects").param("sort", "id,asc")))
                                .andExpect(status().isOk())
                                .andReturn()
                                .getResponse()
                                .getContentAsString());
        JsonNode rows = page.path("_embedded").path("subjectResponseList");
        assertThat(rows).hasSize(3);
        assertThat(rows.get(0).path("id").asLong()).isEqualTo(first.path("id").asLong());
        assertThat(rows.get(0).path("semesterId").asLong()).isEqualTo(firstSemester);
        assertThat(rows.get(1).path("id").asLong()).isEqualTo(second.path("id").asLong());
        assertThat(rows.get(1).path("semesterId").asLong()).isEqualTo(secondSemester);
        assertThat(rows.get(2).path("id").asLong()).isEqualTo(legacy);
        assertThat(rows.get(2).path("semesterId").isNull()).isTrue();
        for (JsonNode row : rows) {
            long id = row.path("id").asLong();
            assertThat(row.path("assignments")).isEmpty();
            JsonNode detail =
                    response(
                            mockMvc.perform(own(get("/academic/subjects/{id}", id)))
                                    .andExpect(status().isOk())
                                    .andReturn()
                                    .getResponse()
                                    .getContentAsString());
            assertThat(detail.path("semesterId")).isEqualTo(row.path("semesterId"));
        }
        mockMvc.perform(
                        signedHeadman(
                                get("/academic/subjects/{id}", first.path("id").asLong()),
                                other.headmanId(),
                                other.groupId()))
                .andExpect(status().isForbidden());
    }

    @Test
    void teacherListDoesNotPublishPendingAdoptionBeforeExactResume() throws Exception {
        long semesterId = ensureCurrentActiveSemester();
        long subjectId = insertSubject(fixture.groupId(), "L05 pending teacher subject", "lecture");
        doThrow(new ScheduleServiceUnavailableException("isolated Schedule commit outage"))
                .when(schedule)
                .commitAssignmentClose(any(UUID.class), any());
        JsonNode pending =
                createAssignment(
                        subjectId,
                        semesterId,
                        LocalDate.now(ZoneId.of("Europe/Moscow")).minusDays(1),
                        true);
        JsonNode handle = pending.get("extras").get("pendingAdoptions").get(0);
        long assignmentId = handle.get("targetAssignmentId").asLong();
        String operationId = handle.get("adoptionOperationId").asText();
        JsonNode ownPending =
                response(
                        mockMvc.perform(own(get("/academic/subjects/{id}", subjectId)))
                                .andExpect(status().isOk())
                                .andReturn()
                                .getResponse()
                                .getContentAsString());
        assertThat(ownPending.get("assignments")).isEmpty();
        assertThat(ownPending.get("teacherIds")).isEmpty();
        assertThat(ownPending.get("pendingAdoptions").get(0).get("targetAssignmentId").asLong())
                .isEqualTo(assignmentId);

        AssignmentClosureService closureSpy = AopTestUtils.getUltimateTargetObject(closureService);
        doAnswer(
                        call -> {
                            assertThat(jdbcTemplate.queryForObject("SHOW transaction_isolation", String.class))
                                    .isEqualTo("repeatable read");
                            assertThat(jdbcTemplate.queryForObject("SHOW transaction_read_only", String.class))
                                    .isEqualTo("on");
                            return call.callRealMethod();
                        })
                .when(closureSpy)
                .pendingSemesterIds(List.of(subjectId));
        JsonNode pendingListRow = ownSubjectListRow(subjectId);
        assertThat(pendingListRow.path("semesterId").isNull()).isTrue();
        assertThat(longValues(pendingListRow.path("pendingSemesterIds"))).containsExactly(semesterId);
        assertThat(pendingListRow.path("assignments")).isEmpty();
        assertThat(pendingListRow.path("teacherIds")).isEmpty();
        assertThat(pendingListRow.hasNonNull("pendingAdoptions")).isFalse();
        assertThat(pendingListRow.toString()).doesNotContain(operationId);
        assertThat(teacherListIds())
                .as("uncommitted adoption must not publish teacher subject authority")
                .doesNotContain(subjectId);

        doReturn(
                        AssignmentCloseReceipt.newBuilder()
                                .setOperationId(operationId)
                                .setState("COMMITTED")
                                .build())
                .when(schedule)
                .commitAssignmentClose(any(UUID.class), any());
        mockMvc.perform(
                        own(
                                post("/academic/assignments/{id}/resume-adoption", assignmentId)
                                        .param("operationId", operationId)))
                .andExpect(status().isOk());
        assertThat(teacherListIds()).contains(subjectId);
        assertThat(ownSubjectListRow(subjectId).has("pendingSemesterIds")).isFalse();
        assertThat(
                        jdbcTemplate.queryForObject(
                                "SELECT COUNT(*) FROM assignments WHERE subject_id=?",
                                Integer.class,
                                subjectId))
                .isEqualTo(1);
        assertThat(
                        jdbcTemplate.queryForObject(
                                "SELECT state FROM assignment_replacement_operations WHERE"
                                        + " operation_id=?::uuid",
                                String.class,
                                operationId))
                .isEqualTo("COMMITTED");
    }

    private JsonNode createUnassignedSubject(String name) throws Exception {
        String body =
                """
                {"name":"%s","type":"LECTURE","lessonTypes":["LECTURE"],"initialAssignments":[]}
                """
                        .formatted(name);
        return response(
                mockMvc.perform(
                                own(
                                        post("/academic/subjects")
                                                .contentType(MediaType.APPLICATION_JSON)
                                                .content(body)))
                        .andExpect(status().isCreated())
                        .andReturn()
                        .getResponse()
                        .getContentAsString());
    }

    private JsonNode createAssignment(
            long subjectId, long semesterId, LocalDate from, boolean expectPending)
            throws Exception {
        String body =
                """
                {"employeeNumber":"%s","subjectId":%d,"groupId":%d,"semesterId":%d,
                 "lessonType":"LECTURE","validFrom":"%s","validUntilExclusive":null}
                """
                        .formatted(
                                fixture.employee1(),
                                subjectId,
                                fixture.groupId(),
                                semesterId,
                                from);
        return response(
                mockMvc.perform(
                                own(
                                        post("/academic/assignments")
                                                .contentType(MediaType.APPLICATION_JSON)
                                                .content(body)))
                        .andExpect(
                                expectPending
                                        ? status().isServiceUnavailable()
                                        : status().isCreated())
                        .andReturn()
                        .getResponse()
                        .getContentAsString());
    }

    private JsonNode ownSubjectListRow(long subjectId) throws Exception {
        JsonNode page =
                response(
                        mockMvc.perform(own(get("/academic/subjects").param("size", "50")))
                                .andExpect(status().isOk())
                                .andReturn()
                                .getResponse()
                                .getContentAsString());
        for (JsonNode row : page.path("_embedded").path("subjectResponseList")) {
            if (row.path("id").asLong() == subjectId) return row;
        }
        throw new AssertionError("Missing subject from own list: " + subjectId);
    }

    private List<Long> teacherListIds() throws Exception {
        JsonNode page =
                response(
                        mockMvc.perform(
                                        signedTeacher(
                                                get("/academic/subjects").param("size", "50"),
                                                fixture.teacher1Id()))
                                .andExpect(status().isOk())
                                .andReturn()
                                .getResponse()
                                .getContentAsString());
        List<Long> ids = new ArrayList<>();
        page.path("_embedded")
                .path("subjectResponseList")
                .forEach(
                        row -> {
                            assertThat(row.has("pendingSemesterIds")).isFalse();
                            ids.add(row.get("id").asLong());
                        });
        return ids;
    }

    private MockHttpServletRequestBuilder own(MockHttpServletRequestBuilder request) {
        return signedHeadman(request, fixture.headmanId(), fixture.groupId());
    }

    private JsonNode response(String body) throws Exception {
        return objectMapper.readTree(body);
    }

    private static List<String> textValues(JsonNode node) {
        List<String> values = new ArrayList<>();
        node.forEach(value -> values.add(value.asText()));
        return values;
    }

    private static List<Long> longValues(JsonNode node) {
        List<Long> values = new ArrayList<>();
        node.forEach(value -> values.add(value.asLong()));
        return values;
    }
}
