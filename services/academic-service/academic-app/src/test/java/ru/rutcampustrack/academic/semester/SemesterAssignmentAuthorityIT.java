package ru.rutcampustrack.academic.semester;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import ru.rutcampustrack.academic.integration.AbstractAssignmentAuthorityIT;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Real route evidence for semester immutability and lock ordering. */
@AutoConfigureMockMvc
class SemesterAssignmentAuthorityIT extends AbstractAssignmentAuthorityIT {

    private static final ZoneId MOSCOW = ZoneId.of("Europe/Moscow");

    @Autowired
    private MockMvc mockMvc;

    private Fixture fixture;

    @BeforeEach
    void createFixture() {
        fixture = newFixture("semester-authority");
    }

    @Test
    void referencedSemesterDateChangeIsRejectedButNameOnlyUpdateKeepsEligibility() throws Exception {
        long subjectId = insertSubject(fixture.groupId(), "L5A semester guard", "lecture");
        String originalName = jdbcTemplate.queryForObject(
                "SELECT name FROM semesters WHERE id = ?", String.class, fixture.semesterId());
        postAssignment(subjectId, fixture.dateFrom().plusDays(1), null)
                .andExpect(status().isCreated());

        mockMvc.perform(admin(MockMvcRequestBuilders.put("/academic/semesters/{id}", fixture.semesterId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateBody("L5A changed dates", fixture.dateFrom().plusDays(1), fixture.dateTo())), fixture))
                .andExpect(status().isConflict());

        assertThat(jdbcTemplate.queryForObject(
                "SELECT date_from FROM semesters WHERE id = ?", LocalDate.class, fixture.semesterId()))
                .isEqualTo(fixture.dateFrom());
        assertThat(jdbcTemplate.queryForObject(
                "SELECT name FROM semesters WHERE id = ?", String.class, fixture.semesterId()))
                .isEqualTo(originalName);

        mockMvc.perform(admin(MockMvcRequestBuilders.put("/academic/semesters/{id}", fixture.semesterId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateBody("L5A renamed only", fixture.dateFrom(), fixture.dateTo())), fixture))
                .andExpect(status().isOk());
        assertThat(jdbcTemplate.queryForObject(
                "SELECT name FROM semesters WHERE id = ?", String.class, fixture.semesterId()))
                .isEqualTo("L5A renamed only");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM assignments WHERE semester_id = ?", Integer.class, fixture.semesterId()))
                .isEqualTo(1);
    }

    @Test
    void semesterDateMutationAndAssignmentCreateRaceIsSerialized() throws Exception {
        long subjectId = insertSubject(fixture.groupId(), "L5A semester race", "lecture");
        LocalDate assignmentStart = fixture.dateFrom().plusDays(20);
        String assignmentBody = """
                {
                  "employeeNumber":"%s",
                  "subjectId":%d,
                  "groupId":%d,
                  "semesterId":%d,
                  "lessonType":"LECTURE",
                  "validFrom":"%s",
                  "validUntilExclusive":null
                }
                """.formatted(fixture.employee1(), subjectId, fixture.groupId(), fixture.semesterId(), assignmentStart);
        String updateBody = updateBody("L5A narrowed semester", fixture.dateFrom(), fixture.dateFrom().plusDays(10));

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Integer>> futures = new ArrayList<>();
        Callable<Integer> assignment = () -> {
            ready.countDown();
            start.await(10, TimeUnit.SECONDS);
            return mockMvc.perform(headman(MockMvcRequestBuilders.post("/academic/assignments")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(assignmentBody), fixture))
                    .andReturn().getResponse().getStatus();
        };
        Callable<Integer> semesterUpdate = () -> {
            ready.countDown();
            start.await(10, TimeUnit.SECONDS);
            return mockMvc.perform(admin(MockMvcRequestBuilders.put(
                            "/academic/semesters/{id}", fixture.semesterId())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(updateBody), fixture))
                    .andReturn().getResponse().getStatus();
        };
        try {
            futures.add(executor.submit(assignment));
            futures.add(executor.submit(semesterUpdate));
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            int assignmentStatus = futures.get(0).get(30, TimeUnit.SECONDS);
            int updateStatus = futures.get(1).get(30, TimeUnit.SECONDS);
            assertThat((assignmentStatus == 400 && updateStatus == 200)
                    || (assignmentStatus == 201 && updateStatus == 409)).isTrue();

            int assignmentCount = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM assignments WHERE subject_id = ?", Integer.class, subjectId);
            if (updateStatus == 200) {
                assertThat(assignmentStatus).isEqualTo(400);
                assertThat(assignmentCount).isZero();
                assertThat(jdbcTemplate.queryForObject(
                        "SELECT date_to FROM semesters WHERE id = ?", LocalDate.class, fixture.semesterId()))
                        .isEqualTo(fixture.dateFrom().plusDays(10));
            } else {
                assertThat(updateStatus).isEqualTo(409);
                assertThat(assignmentStatus).isEqualTo(201);
                assertThat(assignmentCount).isEqualTo(1);
                assertThat(jdbcTemplate.queryForObject(
                        "SELECT date_to FROM semesters WHERE id = ?", LocalDate.class, fixture.semesterId()))
                        .isEqualTo(fixture.dateTo());
            }
        } finally {
            executor.shutdownNow();
            assertThat(executor.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
        }
    }

    @Test
    void completedSemesterRejectsNameOnlyUpdate() throws Exception {
        LocalDate dateFrom = LocalDate.now(MOSCOW).minusYears(10);
        LocalDate dateTo = dateFrom.plusDays(29);
        String originalName = "L5A completed " + UUID.randomUUID();
        long semesterId = jdbcTemplate.queryForObject(
                "INSERT INTO semesters (name, date_from, date_to, is_active, created_at) "
                        + "VALUES (?, ?, ?, false, NOW()) RETURNING id",
                Long.class, originalName, dateFrom, dateTo);

        mockMvc.perform(admin(MockMvcRequestBuilders.put("/academic/semesters/{id}", semesterId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateBody("L5A completed renamed", dateFrom, dateTo)), fixture))
                .andExpect(status().isConflict());
        assertThat(jdbcTemplate.queryForObject(
                "SELECT name FROM semesters WHERE id = ?", String.class, semesterId))
                .isEqualTo(originalName);
    }

    private org.springframework.test.web.servlet.ResultActions postAssignment(long subjectId,
                                                                                LocalDate from,
                                                                                LocalDate until)
            throws Exception {
        String end = until == null ? "null" : "\"" + until + "\"";
        return mockMvc.perform(headman(MockMvcRequestBuilders.post("/academic/assignments")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                          "employeeNumber":"%s",
                          "subjectId":%d,
                          "groupId":%d,
                          "semesterId":%d,
                          "lessonType":"LECTURE",
                          "validFrom":"%s",
                          "validUntilExclusive":%s
                        }
                        """.formatted(fixture.employee1(), subjectId, fixture.groupId(),
                        fixture.semesterId(), from, end)), fixture));
    }

    private String updateBody(String name, LocalDate from, LocalDate to) {
        return "{\"name\":\"" + name + "\",\"dateFrom\":\"" + from
                + "\",\"dateTo\":\"" + to + "\"}";
    }

    private MockHttpServletRequestBuilder headman(MockHttpServletRequestBuilder request, Fixture value) {
        return request.header("X-User-Id", value.headmanId())
                .header("X-User-Role", "STUDENT")
                .header("X-Group-Id", value.groupId())
                .header("X-Is-Headman", "true");
    }

    private MockHttpServletRequestBuilder admin(MockHttpServletRequestBuilder request, Fixture value) {
        return request.header("X-User-Id", value.headmanId())
                .header("X-User-Role", "ADMIN")
                .header("X-Group-Id", "")
                .header("X-Is-Headman", "false");
    }
}
