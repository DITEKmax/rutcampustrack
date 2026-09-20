package ru.rutcampustrack.academic.assignment;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.stubbing.Answer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import ru.rutcampustrack.academic.integration.AbstractAssignmentAuthorityIT;
import ru.rutcampustrack.academic.repository.SemesterRepository;
import ru.rutcampustrack.academic.repository.SubjectRepository;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mockingDetails;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Real REST, PostgreSQL exclusion, and effective-date authority evidence. */
@AutoConfigureMockMvc
class AssignmentAuthorityIT extends AbstractAssignmentAuthorityIT {

    private static final ZoneId MOSCOW = ZoneId.of("Europe/Moscow");

    @Autowired
    private MockMvc mockMvc;

    @MockitoSpyBean
    private SemesterRepository semesterRepository;

    @MockitoSpyBean
    private SubjectRepository subjectRepository;

    private Fixture fixture;
    private List<Long> priorActiveSemesters;

    @BeforeEach
    void createFixture() {
        fixture = newFixture("assignment-authority");
    }

    @AfterEach
    void restoreActiveSemester() {
        if (priorActiveSemesters != null) {
            restoreActiveSemesterIds(priorActiveSemesters);
        }
    }

    @Test
    void addTeacherPersistsFullIdentityAndRestKeepsNullEffectiveEnd() throws Exception {
        long subjectId = insertSubject(fixture.groupId(), "L5A add teacher", "lecture");
        LocalDate from = fixture.dateFrom().plusDays(1);
        MvcResult result = mockMvc.perform(headman(MockMvcRequestBuilders.post(
                                "/academic/subjects/{subjectId}/teachers/{teacherId}",
                                subjectId, fixture.teacher1Id())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"semesterId":%d,"lessonType":"LECTURE","validFrom":"%s", "validUntilExclusive":null}
                                """.formatted(fixture.semesterId(), from)), fixture))
                .andExpect(status().isCreated())
                .andReturn();
        JsonNode response = objectMapper.readTree(result.getResponse().getContentAsString());
        long assignmentId = response.get("id").asLong();

        assertThat(response.get("teacherId").asLong()).isEqualTo(fixture.teacher1Id());
        assertThat(response.get("subjectId").asLong()).isEqualTo(subjectId);
        assertThat(response.get("groupId").asLong()).isEqualTo(fixture.groupId());
        assertThat(response.get("semesterId").asLong()).isEqualTo(fixture.semesterId());
        assertThat(response.get("lessonType").asText()).isEqualTo("LECTURE");
        assertThat(response.get("validFrom").asText()).isEqualTo(from.toString());
        assertThat(response.get("validUntilExclusive").isNull()).isTrue();

        AssignmentRow row = jdbcTemplate.queryForObject(
                "SELECT id, teacher_id, subject_id, group_id, semester_id, lesson_type::text, valid_from, "
                        + "valid_until_exclusive FROM assignments WHERE id = ?",
                (rs, rowNum) -> new AssignmentRow(rs.getLong(1), rs.getLong(2), rs.getLong(3),
                        rs.getLong(4), rs.getLong(5), rs.getString(6), rs.getObject(7, LocalDate.class),
                        rs.getObject(8, LocalDate.class)), assignmentId);
        assertThat(row).isEqualTo(new AssignmentRow(assignmentId, fixture.teacher1Id(), subjectId,
                fixture.groupId(), fixture.semesterId(), "lecture", from, null));
    }

    @Test
    void sameTeacherSameTypeOverlapRaceCommitsOneAndReturnsOne409() throws Exception {
        long subjectId = insertSubject(fixture.groupId(), "L5A overlap race", "lecture");
        LocalDate from = fixture.dateFrom().plusDays(1);
        String payload = assignPayload(fixture.employee1(), subjectId, fixture.semesterId(),
                fixture.groupId(), "LECTURE", from, from.plusDays(10));

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Integer>> futures = new ArrayList<>();
        Callable<Integer> command = () -> {
            ready.countDown();
            if (!start.await(10, TimeUnit.SECONDS)) {
                throw new IllegalStateException("overlap race barrier timed out");
            }
            return mockMvc.perform(headman(MockMvcRequestBuilders.post("/academic/assignments")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(payload), fixture))
                    .andReturn().getResponse().getStatus();
        };
        try {
            futures.add(executor.submit(command));
            futures.add(executor.submit(command));
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            List<Integer> statuses = List.of(futures.get(0).get(30, TimeUnit.SECONDS),
                    futures.get(1).get(30, TimeUnit.SECONDS));
            assertThat(statuses).containsExactlyInAnyOrder(201, 409);
        } finally {
            executor.shutdownNow();
            assertThat(executor.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
        }
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM assignments WHERE subject_id = ?", Integer.class, subjectId))
                .isEqualTo(1);
    }

    @Test
    void assignTeacherAndAddTeacherUseOrderedLocks() throws Throwable {
        long subjectId = insertSubject(fixture.groupId(), "L5A lock order", "lecture", "practice");
        LocalDate assignFrom = fixture.dateFrom().plusDays(1);
        LocalDate assignUntil = assignFrom.plusDays(5);
        LocalDate addFrom = assignUntil;
        LocalDate addUntil = addFrom.plusDays(5);

        CountDownLatch assignSemesterLocked = new CountDownLatch(1);
        CountDownLatch releaseAssignment = new CountDownLatch(1);
        CountDownLatch addSubjectLocked = new CountDownLatch(1);
        Map<Long, EndpointRole> endpointRoles = new ConcurrentHashMap<>();
        List<String> barrierEvents = Collections.synchronizedList(new ArrayList<>());
        Answer<Object> semesterRepositoryCall = springDataProxyDelegate(semesterRepository);
        Answer<Object> subjectRepositoryCall = springDataProxyDelegate(subjectRepository);

        doAnswer(invocation -> {
            EndpointRole role = endpointRoles.get(Thread.currentThread().getId());
            if (role == EndpointRole.ADD_TEACHER) {
                barrierEvents.add("add.semester.attempted");
                // In the corrected order, this is the second request's first
                // lock attempt. Let the already-held first semester lock owner
                // finish before the real call waits for that same row.
                releaseAssignment.countDown();
            }
            Object result = semesterRepositoryCall.answer(invocation);
            if (role == EndpointRole.ASSIGN_TEACHER) {
                // The real PESSIMISTIC_WRITE query returned, so the assignment
                // transaction owns the semester row before the other request starts.
                barrierEvents.add("assign.semester.locked");
                assignSemesterLocked.countDown();
                if (!releaseAssignment.await(20, TimeUnit.SECONDS)) {
                    throw new AssertionError("lock-order synchronization timed out");
                }
                barrierEvents.add("assign.released");
            }
            return result;
        }).when(semesterRepository).findByIdForUpdate(fixture.semesterId());

        doAnswer(invocation -> {
            Object result = subjectRepositoryCall.answer(invocation);
            if (endpointRoles.get(Thread.currentThread().getId()) == EndpointRole.ADD_TEACHER) {
                // The old order reaches this callback after acquiring the subject
                // lock; the corrected order reaches it after the semester wait.
                barrierEvents.add("add.subject.locked");
                addSubjectLocked.countDown();
                releaseAssignment.countDown();
            }
            return result;
        }).when(subjectRepository).findByIdForUpdate(subjectId);

        ExecutorService executor = Executors.newFixedThreadPool(2);
        Future<MvcResult> assignFuture = null;
        Future<MvcResult> addFuture = null;
        Throwable primaryFailure = null;
        try {
            assignFuture = executor.submit(() -> {
                long threadId = Thread.currentThread().getId();
                endpointRoles.put(threadId, EndpointRole.ASSIGN_TEACHER);
                try {
                    return mockMvc.perform(headman(MockMvcRequestBuilders.post("/academic/assignments")
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(assignPayload(fixture.employee1(), subjectId, fixture.semesterId(),
                                            fixture.groupId(), "LECTURE", assignFrom, assignUntil)), fixture))
                            .andReturn();
                } finally {
                    endpointRoles.remove(threadId);
                }
            });

            awaitBarrierOrRequestFailure(assignFuture, assignSemesterLocked,
                    "assignTeacher must acquire the semester row before addTeacher starts");

            addFuture = executor.submit(() -> {
                long threadId = Thread.currentThread().getId();
                endpointRoles.put(threadId, EndpointRole.ADD_TEACHER);
                try {
                    return mockMvc.perform(headman(MockMvcRequestBuilders.post(
                                    "/academic/subjects/{subjectId}/teachers/{teacherId}",
                                    subjectId, fixture.teacher2Id())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"semesterId":%d,"lessonType":"PRACTICE","validFrom":"%s",
                                     "validUntilExclusive":"%s"}
                                    """.formatted(fixture.semesterId(), addFrom, addUntil)), fixture))
                            .andReturn();
                } finally {
                    endpointRoles.remove(threadId);
                }
            });

            awaitBarrierOrRequestFailure(addFuture, addSubjectLocked,
                    "addTeacher must acquire the subject row on its real path");
            MvcResult assignResult = assignFuture.get(30, TimeUnit.SECONDS);
            MvcResult addResult = addFuture.get(30, TimeUnit.SECONDS);
            assertThat(barrierEvents)
                    .as("real lock barrier events")
                    .contains("assign.semester.locked", "assign.released",
                            "add.semester.attempted", "add.subject.locked");
            assertThat(assignResult.getResponse().getStatus()).isEqualTo(201);
            assertThat(addResult.getResponse().getStatus()).isEqualTo(201);

            JsonNode assignResponse = objectMapper.readTree(assignResult.getResponse().getContentAsString());
            JsonNode addResponse = objectMapper.readTree(addResult.getResponse().getContentAsString());
            long assignId = assignResponse.get("id").asLong();
            long addId = addResponse.get("id").asLong();
            assertThat(assignId).isPositive();
            assertThat(addId).isPositive().isNotEqualTo(assignId);
            assertThat(assignResponse.get("teacherId").asLong()).isEqualTo(fixture.teacher1Id());
            assertThat(addResponse.get("teacherId").asLong()).isEqualTo(fixture.teacher2Id());
        } catch (Throwable failure) {
            primaryFailure = failure;
        } finally {
            Throwable cleanupFailure = cleanupRequestExecutor(executor, releaseAssignment,
                    assignFuture, addFuture);
            if (primaryFailure != null) {
                if (cleanupFailure != null) {
                    primaryFailure.addSuppressed(cleanupFailure);
                }
            } else {
                primaryFailure = cleanupFailure;
            }
        }

        if (primaryFailure != null) {
            throw primaryFailure;
        }

        List<AssignmentRow> rows = jdbcTemplate.query(
                "SELECT id, teacher_id, subject_id, group_id, semester_id, lesson_type::text, valid_from, "
                        + "valid_until_exclusive FROM assignments WHERE subject_id = ? ORDER BY id",
                (rs, rowNum) -> new AssignmentRow(rs.getLong(1), rs.getLong(2), rs.getLong(3),
                        rs.getLong(4), rs.getLong(5), rs.getString(6), rs.getObject(7, LocalDate.class),
                        rs.getObject(8, LocalDate.class)), subjectId);
        assertThat(rows).hasSize(2);
        assertThat(rows).extracting(AssignmentRow::teacherId)
                .containsExactlyInAnyOrder(fixture.teacher1Id(), fixture.teacher2Id());
        assertThat(rows).extracting(AssignmentRow::lessonType)
                .containsExactlyInAnyOrder("lecture", "practice");
    }

    @Test
    void differentTeachersAndAdjacentPeriodsAreAllowed() throws Exception {
        long subjectId = insertSubject(fixture.groupId(), "L5A adjacent", "lecture");
        LocalDate from = fixture.dateFrom().plusDays(1);
        LocalDate boundary = from.plusDays(7);
        postAssignment(fixture.employee1(), subjectId, "LECTURE", from, boundary)
                .andExpect(status().isCreated());
        postAssignment(fixture.employee2(), subjectId, "LECTURE", from, boundary)
                .andExpect(status().isCreated());
        postAssignment(fixture.employee1(), subjectId, "LECTURE", boundary, boundary.plusDays(7))
                .andExpect(status().isCreated());

        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM assignments WHERE subject_id = ?", Integer.class, subjectId))
                .isEqualTo(3);
    }

    @Test
    void inactiveOrMissingTeacherGrantIsRejectedWithoutWrite() throws Exception {
        long subjectId = insertSubject(fixture.groupId(), "L5A grant gate", "lecture");
        jdbcTemplate.update("UPDATE user_role_grants SET status = 'suspended' "
                        + "WHERE user_id = ? AND role = 'teacher'", fixture.teacher2Id());
        LocalDate from = fixture.dateFrom().plusDays(1);

        postAssignment(fixture.employee2(), subjectId, "LECTURE", from, null)
                .andExpect(status().isForbidden());
        postAssignment(fixture.employeeWithoutGrant(), subjectId, "LECTURE", from.plusDays(1), null)
                .andExpect(status().isForbidden());

        assertThat(assignmentCount(subjectId)).isZero();
    }

    @Test
    void invalidAssignmentDateBoundsAreRejectedWithoutWrite() throws Exception {
        long subjectId = insertSubject(fixture.groupId(), "L5A date bounds", "lecture");
        LocalDate from = fixture.dateFrom().plusDays(1);

        postAssignment(fixture.employee1(), subjectId, "LECTURE", fixture.dateFrom().minusDays(1), null)
                .andExpect(status().isBadRequest());
        postAssignment(fixture.employee1(), subjectId, "LECTURE", from, from)
                .andExpect(status().isBadRequest());

        assertThat(assignmentCount(subjectId)).isZero();
    }

    @Test
    void myAssignmentsRouteExcludesFutureAndExpiredAssignmentsAndRequiresActiveGrant()
            throws Exception {
        priorActiveSemesters = activeSemesterIds();
        long semesterId = ensureCurrentActiveSemester();
        long subjectId = insertSubject(fixture.groupId(), "L5A current reads",
                "lecture", "practice", "lab");
        LocalDate today = LocalDate.now(MOSCOW);
        long activeStatus = postAssignment(fixture.employee1(), subjectId, semesterId, "LECTURE",
                today.minusDays(1), null).andReturn().getResponse().getStatus();
        assertThat(activeStatus).isEqualTo(201);
        long futureAssignmentId = readCreatedId(postAssignment(fixture.employee1(), subjectId, semesterId, "PRACTICE",
                today.plusDays(2), null).andReturn());
        long expiredAssignmentId = readCreatedId(postAssignment(fixture.employee1(), subjectId, semesterId, "LAB",
                today.minusDays(4), today.minusDays(1)).andReturn());
        long activeAssignmentId = latestAssignmentId(subjectId, "lecture");

        MvcResult result = mockMvc.perform(MockMvcRequestBuilders.get("/academic/assignments/my")
                        .header("X-User-Id", fixture.teacher1Id())
                        .header("X-User-Role", "TEACHER")
                        .header("X-Group-Id", "")
                        .header("X-Is-Headman", "false"))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        List<Long> ids = body.findValues("id").stream().map(JsonNode::asLong).toList();
        assertThat(ids).contains(activeAssignmentId)
                .doesNotContain(futureAssignmentId, expiredAssignmentId);

        jdbcTemplate.update("UPDATE user_role_grants SET status = 'suspended' "
                + "WHERE user_id = ? AND role = 'teacher'", fixture.teacher1Id());
        mockMvc.perform(MockMvcRequestBuilders.get("/academic/assignments/my")
                        .header("X-User-Id", fixture.teacher1Id())
                        .header("X-User-Role", "TEACHER")
                        .header("X-Group-Id", "")
                        .header("X-Is-Headman", "false"))
                .andExpect(status().isForbidden());
    }

    private ResultActions postAssignment(String employeeNumber,
                                         long subjectId,
                                         String lessonType,
                                         LocalDate from,
                                         LocalDate until) throws Exception {
        return postAssignment(employeeNumber, subjectId, fixture.semesterId(), lessonType, from, until);
    }

    private ResultActions postAssignment(String employeeNumber,
                                         long subjectId,
                                         long semesterId,
                                         String lessonType,
                                         LocalDate from,
                                         LocalDate until) throws Exception {
        return mockMvc.perform(headman(MockMvcRequestBuilders.post("/academic/assignments")
                .contentType(MediaType.APPLICATION_JSON)
                .content(assignPayload(employeeNumber, subjectId, semesterId, fixture.groupId(),
                        lessonType, from, until)), fixture));
    }

    private long readCreatedId(MvcResult result) throws Exception {
        assertThat(result.getResponse().getStatus()).isEqualTo(201);
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asLong();
    }

    private long latestAssignmentId(long subjectId, String lessonType) {
        return jdbcTemplate.queryForObject(
                "SELECT id FROM assignments WHERE subject_id = ? AND lesson_type = ?::subject_type "
                        + "ORDER BY id DESC LIMIT 1", Long.class, subjectId, lessonType);
    }

    private MockHttpServletRequestBuilder headman(MockHttpServletRequestBuilder request, Fixture value) {
        return request.header("X-User-Id", value.headmanId())
                .header("X-User-Role", "STUDENT")
                .header("X-Group-Id", value.groupId())
                .header("X-Is-Headman", "true");
    }

    private String assignPayload(String employeeNumber,
                                 long subjectId,
                                 long semesterId,
                                 long groupId,
                                 String lessonType,
                                 LocalDate validFrom,
                                 LocalDate validUntilExclusive) {
        String end = validUntilExclusive == null ? "null" : "\"" + validUntilExclusive + "\"";
        return "{\"employeeNumber\":\"%s\",\"subjectId\":%d,\"groupId\":%d,"
                .formatted(employeeNumber, subjectId, groupId)
                + "\"semesterId\":" + semesterId + ",\"lessonType\":\"" + lessonType
                + "\",\"validFrom\":\"" + validFrom + "\",\"validUntilExclusive\":" + end + "}";
    }

    private record AssignmentRow(long id,
                                 long teacherId,
                                 long subjectId,
                                 long groupId,
                                 long semesterId,
                                 String lessonType,
                                 LocalDate validFrom,
                                 LocalDate validUntilExclusive) {
    }

    private enum EndpointRole {
        ASSIGN_TEACHER,
        ADD_TEACHER
    }

    private static Answer<Object> springDataProxyDelegate(Object repositorySpy) {
        Answer<?> defaultAnswer = mockingDetails(repositorySpy)
                .getMockCreationSettings()
                .getDefaultAnswer();
        assertThat(defaultAnswer)
                .as("@MockitoSpyBean must retain Spring Test's delegate to the original Spring Data proxy")
                .isNotNull();
        return invocation -> defaultAnswer.answer(invocation);
    }

    private static void awaitBarrierOrRequestFailure(Future<MvcResult> requestFuture,
                                                       CountDownLatch barrier,
                                                       String description) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(20);
        while (!barrier.await(100, TimeUnit.MILLISECONDS)) {
            failIfRequestCompletedWithoutBarrier(requestFuture, description);
            if (System.nanoTime() >= deadline) {
                throw new AssertionError(description + ": barrier timed out");
            }
        }
    }

    private static void failIfRequestCompletedWithoutBarrier(Future<MvcResult> requestFuture,
                                                              String description) throws Exception {
        if (!requestFuture.isDone()) {
            return;
        }
        MvcResult result = requestFuture.get();
        Throwable resolvedException = result.getResolvedException();
        int status = result.getResponse().getStatus();
        if (resolvedException != null) {
            throw new AssertionError(description + ": request completed with HTTP " + status
                    + " and resolved exception " + resolvedException, resolvedException);
        }
        throw new AssertionError(description + ": request completed with HTTP " + status
                + " without barrier");
    }

    private static Throwable cleanupRequestExecutor(ExecutorService executor,
                                                     CountDownLatch releaseAssignment,
                                                     Future<?>... requestFutures) {
        Throwable cleanupFailure = null;
        try {
            releaseAssignment.countDown();
        } catch (Throwable failure) {
            cleanupFailure = failure;
        }

        boolean unfinishedRequest = false;
        for (Future<?> requestFuture : requestFutures) {
            if (requestFuture == null || requestFuture.isDone()) {
                continue;
            }
            unfinishedRequest = true;
            try {
                requestFuture.cancel(true);
            } catch (Throwable failure) {
                cleanupFailure = appendCleanupFailure(cleanupFailure, failure);
            }
        }

        try {
            if (unfinishedRequest) {
                // Interrupts are a bounded cleanup signal; they do not claim to
                // forcibly terminate arbitrary JDBC work. Termination is checked below.
                executor.shutdownNow();
            } else {
                executor.shutdown();
            }
        } catch (Throwable failure) {
            cleanupFailure = appendCleanupFailure(cleanupFailure, failure);
        }

        try {
            if (!executor.awaitTermination(20, TimeUnit.SECONDS)) {
                cleanupFailure = appendCleanupFailure(cleanupFailure,
                        new AssertionError("request executor did not terminate within 20 seconds"));
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            cleanupFailure = appendCleanupFailure(cleanupFailure,
                    new AssertionError("request executor cleanup interrupted", e));
        } catch (Throwable failure) {
            cleanupFailure = appendCleanupFailure(cleanupFailure, failure);
        }
        return cleanupFailure;
    }

    private static Throwable appendCleanupFailure(Throwable existing, Throwable additional) {
        if (existing == null) {
            return additional;
        }
        existing.addSuppressed(additional);
        return existing;
    }
}
