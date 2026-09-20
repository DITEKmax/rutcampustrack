package ru.rutcampustrack.academic.grpc;

import com.fasterxml.jackson.databind.JsonNode;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import net.devh.boot.grpc.client.inject.GrpcClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import ru.rutcampustrack.academic.integration.AbstractAssignmentAuthorityIT;
import ru.rutcampustrack.academic.repository.AssignmentRepository;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Real producer-to-in-process-gRPC assignment identity and read evidence. */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "grpc.server.in-process-name=academic-assignment-grpc-it",
                "grpc.server.port=-1",
                "grpc.client.assignmentInProcess.address=in-process:academic-assignment-grpc-it",
                "grpc.client.assignmentInProcess.negotiationType=plaintext"
        }
)
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class AcademicAssignmentGrpcIT extends AbstractAssignmentAuthorityIT {

    private static final ZoneId MOSCOW = ZoneId.of("Europe/Moscow");

    @GrpcClient("assignmentInProcess")
    private AcademicGrpcServiceGrpc.AcademicGrpcServiceBlockingStub stub;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AssignmentRepository assignmentRepository;

    private Fixture fixture;
    private List<Long> priorActiveSemesters;

    @BeforeEach
    void createFixture() {
        fixture = newFixture("grpc-authority");
    }

    @AfterEach
    void restoreActiveSemester() {
        if (priorActiveSemesters != null) {
            restoreActiveSemesterIds(priorActiveSemesters);
        }
    }

    @Test
    void assignmentsByIdsReturnRetainedIdentityAndConcreteEffectiveEnd() throws Exception {
        JsonNode created = createSubject("L5A grpc identity", List.of("LECTURE"), List.of(
                assignment(fixture.teacher1Id(), fixture.semesterId(), "LECTURE",
                        fixture.dateFrom().plusDays(1), null)));
        long assignmentId = created.get("createdAssignmentIds").get(0).asLong();

        AssignmentsByIdsResponse response = stub.getAssignmentsByIds(AssignmentsByIdsRequest.newBuilder()
                .addAssignmentIds(assignmentId)
                .build());
        assertThat(response.getAssignmentsList()).hasSize(1);
        AssignmentInfo info = response.getAssignments(0);
        assertThat(info.getId()).isEqualTo(assignmentId);
        assertThat(info.getTeacherId()).isEqualTo(fixture.teacher1Id());
        assertThat(info.getSubjectId()).isEqualTo(created.get("id").asLong());
        assertThat(info.getGroupId()).isEqualTo(fixture.groupId());
        assertThat(info.getSemesterId()).isEqualTo(fixture.semesterId());
        assertThat(info.getLessonType()).isEqualTo("lecture");
        assertThat(info.getValidFrom()).isEqualTo(fixture.dateFrom().plusDays(1).toString());
        assertThat(info.getValidUntilExclusive()).isEqualTo(fixture.dateTo().plusDays(1).toString());
    }

    @Test
    void assignmentsByIdsRejectPositiveMissingIdsAtomically() throws Exception {
        JsonNode created = createSubject("L5A grpc batch", List.of("LECTURE", "PRACTICE"), List.of(
                assignment(fixture.teacher1Id(), fixture.semesterId(), "LECTURE",
                        fixture.dateFrom().plusDays(1), fixture.dateFrom().plusDays(8)),
                assignment(fixture.teacher2Id(), fixture.semesterId(), "PRACTICE",
                        fixture.dateFrom().plusDays(1), null)));
        long existingId = created.get("createdAssignmentIds").get(0).asLong();
        long missingId = 8_000_000_000L;

        assertThatThrownBy(() -> stub.getAssignmentsByIds(AssignmentsByIdsRequest.newBuilder()
                .addAssignmentIds(existingId)
                .addAssignmentIds(missingId)
                .build()))
                .isInstanceOf(StatusRuntimeException.class)
                .satisfies(error -> assertThat(((StatusRuntimeException) error).getStatus().getCode())
                        .isEqualTo(Status.Code.NOT_FOUND));
    }

    @Test
    void assignmentsByIdsRejectNonPositiveIdsBeforeReadingAnyRow() throws Exception {
        JsonNode created = createSubject("L5A grpc nonpositive", List.of("LECTURE"), List.of(
                assignment(fixture.teacher1Id(), fixture.semesterId(), "LECTURE",
                        fixture.dateFrom().plusDays(1), null)));
        long existingId = created.get("createdAssignmentIds").get(0).asLong();

        assertThatThrownBy(() -> stub.getAssignmentsByIds(AssignmentsByIdsRequest.newBuilder()
                .addAssignmentIds(existingId)
                .addAssignmentIds(0L)
                .build()))
                .isInstanceOf(StatusRuntimeException.class)
                .satisfies(error -> assertThat(((StatusRuntimeException) error).getStatus().getCode())
                        .isEqualTo(Status.Code.INVALID_ARGUMENT));

        assertThatThrownBy(() -> stub.getAssignmentsByIds(AssignmentsByIdsRequest.newBuilder()
                .addAssignmentIds(existingId)
                .addAssignmentIds(-1L)
                .build()))
                .isInstanceOf(StatusRuntimeException.class)
                .satisfies(error -> assertThat(((StatusRuntimeException) error).getStatus().getCode())
                        .isEqualTo(Status.Code.INVALID_ARGUMENT));
    }

    @Test
    void assignmentsByIdsEmptyBatchReturnsEmptyResponse() {
        AssignmentsByIdsResponse response = stub.getAssignmentsByIds(
                AssignmentsByIdsRequest.getDefaultInstance());

        assertThat(response.getAssignmentsList()).isEmpty();
    }

    @Test
    void assignmentRepositoryFindByIdLoadsCompositeLessonTypeIdentity() throws Exception {
        JsonNode created = createSubject("L5A grpc repository identity", List.of("LECTURE"), List.of(
                assignment(fixture.teacher1Id(), fixture.semesterId(), "LECTURE",
                        fixture.dateFrom().plusDays(1), null)));
        long assignmentId = created.get("createdAssignmentIds").get(0).asLong();

        var assignment = assignmentRepository.findById(assignmentId).orElseThrow();

        assertThat(assignment.getId()).isEqualTo(assignmentId);
        assertThat(assignment.getSubjectId()).isEqualTo(created.get("id").asLong());
        assertThat(assignment.getLessonType().name()).isEqualTo("LECTURE");
    }

    @Test
    void teacherSubjectsExcludeFutureAndExpiredAssignmentsAndRequireActiveGrant() throws Exception {
        priorActiveSemesters = activeSemesterIds();
        long semesterId = ensureCurrentActiveSemester();
        LocalDate today = LocalDate.now(MOSCOW);
        JsonNode created = createSubject("L5A grpc current", List.of("LECTURE", "PRACTICE", "LAB"), List.of(
                assignment(fixture.teacher1Id(), semesterId, "LECTURE", today.minusDays(1), null),
                assignment(fixture.teacher1Id(), semesterId, "PRACTICE", today.plusDays(2), null),
                assignment(fixture.teacher1Id(), semesterId, "LAB", today.minusDays(4), today.minusDays(1))));
        long activeAssignmentId = jdbcTemplate.queryForObject(
                "SELECT id FROM assignments WHERE subject_id = ? AND lesson_type = 'lecture'::subject_type",
                Long.class, created.get("id").asLong());

        TeacherSubjectsResponse response = stub.getTeacherSubjects(TeacherSubjectsRequest.newBuilder()
                .setTeacherId(fixture.teacher1Id())
                .setSemesterId(semesterId)
                .build());
        assertThat(response.getSubjectsList()).hasSize(1);
        TeacherSubjectInfo info = response.getSubjects(0);
        assertThat(info.getAssignmentId()).isEqualTo(activeAssignmentId);
        assertThat(info.getSubjectId()).isEqualTo(created.get("id").asLong());
        assertThat(info.getLessonType()).isEqualTo("lecture");
        assertThat(info.getValidFrom()).isEqualTo(today.minusDays(1).toString());
        assertThat(info.getValidUntilExclusive()).isEqualTo(
                jdbcTemplate.queryForObject("SELECT date_to FROM semesters WHERE id = ?",
                        LocalDate.class, semesterId).plusDays(1).toString());

        jdbcTemplate.update("UPDATE user_role_grants SET status = 'suspended' "
                + "WHERE user_id = ? AND role = 'teacher'", fixture.teacher1Id());
        assertThatThrownBy(() -> stub.getTeacherSubjects(TeacherSubjectsRequest.newBuilder()
                .setTeacherId(fixture.teacher1Id())
                .setSemesterId(semesterId)
                .build()))
                .isInstanceOf(StatusRuntimeException.class)
                .satisfies(error -> assertThat(((StatusRuntimeException) error).getStatus().getCode())
                        .isEqualTo(Status.Code.PERMISSION_DENIED));
    }

    private JsonNode createSubject(String name,
                                   List<String> lessonTypes,
                                   List<String> assignments) throws Exception {
        String body = """
                {
                  "name":"%s",
                  "type":"%s",
                  "lessonTypes":%s,
                  "initialAssignments":[%s]
                }
                """.formatted(name, lessonTypes.get(0), quotedArray(lessonTypes), String.join(",", assignments));
        MvcResult result = mockMvc.perform(headman(MockMvcRequestBuilders.post("/academic/subjects")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body), fixture))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private String assignment(long teacherId,
                              long semesterId,
                              String lessonType,
                              LocalDate validFrom,
                              LocalDate validUntilExclusive) {
        String end = validUntilExclusive == null ? "null" : "\"" + validUntilExclusive + "\"";
        return "{\"teacherId\":%d,\"semesterId\":%d,\"lessonType\":\"%s\","
                .formatted(teacherId, semesterId, lessonType)
                + "\"validFrom\":\"" + validFrom + "\",\"validUntilExclusive\":" + end + "}";
    }

    private String quotedArray(List<String> values) {
        return "[" + values.stream().map(value -> "\"" + value + "\"").reduce((left, right) -> left + "," + right)
                .orElse("") + "]";
    }

    private MockHttpServletRequestBuilder headman(MockHttpServletRequestBuilder request, Fixture value) {
        return request.header("X-User-Id", value.headmanId())
                .header("X-User-Role", "STUDENT")
                .header("X-Group-Id", value.groupId())
                .header("X-Is-Headman", "true");
    }
}
