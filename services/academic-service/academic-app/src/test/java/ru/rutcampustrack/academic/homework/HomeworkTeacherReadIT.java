package ru.rutcampustrack.academic.homework;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;
import ru.rutcampustrack.academic.integration.AbstractAcademicIntegrationTest;
import ru.rutcampustrack.shared.security.InternalJwtTestFactory;

import java.time.LocalDate;
import java.util.UUID;

import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Signed HTTP reads against real grants, assignments, lifecycle operations and date bounds. */
@AutoConfigureMockMvc
@TestPropertySource(properties = "rutcampustrack.security.internal-jwt.legacy-headers-enabled=true")
@Transactional
class HomeworkTeacherReadIT extends AbstractAcademicIntegrationTest {
    @Autowired private MockMvc mvc;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private InternalJwtTestFactory tokens;

    private long teacher, student, admin, group, subject, semester;
    private final LocalDate day = LocalDate.of(2041, 3, 10);

    @BeforeEach
    void fixture() {
        student = jdbc.queryForObject("SELECT id FROM users WHERE login='student'", Long.class);
        admin = jdbc.queryForObject("SELECT id FROM users WHERE login='admin'", Long.class);
        group = jdbc.queryForObject("SELECT id FROM groups WHERE name='ИВТ-211'", Long.class);
        teacher = jdbc.queryForObject("""
                INSERT INTO users(login,password_hash,last_name,first_name,role,status,created_at,updated_at)
                VALUES(?, '$2a$10$dummy', 'ДЗ', 'Преподаватель', 'teacher', 'active', NOW(), NOW()) RETURNING id
                """, Long.class, "hw_teacher_" + UUID.randomUUID().toString().substring(0, 16));
        jdbc.update("""
                INSERT INTO user_role_grants(user_id,role,status,created_at,updated_at)
                VALUES(?,'teacher','active',NOW(),NOW())
                """, teacher);
        subject = subject(group);
        semester = semester();
    }

    @ParameterizedTest
    @CsvSource({"LESSON,LECTURE", "DATE,LAB"})
    void ownExactAssignmentReadsContent(String mode, String type) throws Exception {
        assignment(teacher, group, subject, semester, type.toLowerCase(), day, null, "ACTIVE");
        long id = homework(group, subject, semester, mode, type, day, "ACTIVE");
        mvc.perform(get("/academic/homeworks/{id}", id).header("X-Internal-Token", teacherToken()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.title", is("Protected homework")));
    }

    @Test
    void openEndIncludesTheLastSemesterDay() throws Exception {
        LocalDate lastDay = LocalDate.of(2041, 6, 30);
        assignment(teacher, group, subject, semester, "lecture", day, null, "ACTIVE");
        long id = homework(group, subject, semester, "DATE", "LECTURE", lastDay, "ACTIVE");
        mvc.perform(get("/academic/homeworks/{id}", id).header("X-Internal-Token", teacherToken()))
                .andExpect(status().isOk());
    }

    @ParameterizedTest
    @CsvSource({"teacher", "group", "subject", "semester", "type", "before", "until", "afterSemester", "PREPARED", "CLOSED", "unknownType"})
    void foreignOrInactiveScopeIsDenied(String mismatch) throws Exception {
        long assignmentTeacher = mismatch.equals("teacher") ? admin : teacher;
        long assignmentGroup = mismatch.equals("group")
                ? jdbc.queryForObject("SELECT id FROM groups WHERE id<>? LIMIT 1", Long.class, group) : group;
        long assignmentSubject = mismatch.equals("subject") || mismatch.equals("group") ? subject(assignmentGroup) : subject;
        long assignmentSemester = mismatch.equals("semester") ? semester(2042) : semester;
        String type = mismatch.equals("type") ? "practice" : "lecture";
        LocalDate start = mismatch.equals("before") ? day.plusDays(1) : day.minusDays(1);
        if (mismatch.equals("semester")) start = day.plusYears(1);
        LocalDate end = mismatch.equals("until") ? day : null;
        String lifecycle = mismatch.equals("PREPARED") || mismatch.equals("CLOSED") ? mismatch : "ACTIVE";
        assignment(assignmentTeacher, assignmentGroup, assignmentSubject, assignmentSemester, type, start, end, lifecycle);
        long id = homework(group, subject, semester, "LESSON", mismatch.equals("unknownType") ? null : "LECTURE",
                mismatch.equals("afterSemester") ? LocalDate.of(2041, 7, 1) : day, "ACTIVE");
        mvc.perform(get("/academic/homeworks/{id}", id).header("X-Internal-Token", teacherToken()))
                .andExpect(status().isForbidden());
    }

    @ParameterizedTest
    @CsvSource({"PREPARED,403", "APPLIED,403", "COMMITTED,200"})
    void adoptionGrantsReadOnlyAfterCommit(String state, int expected) throws Exception {
        long target = assignment(teacher, group, subject, semester, "lecture", day, null, "ACTIVE");
        UUID operation = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO assignment_replacement_operations(operation_id,actor_id,request_key,payload_hash,
                    target_assignment_id,effective_from,source_valid_until,target_valid_until,state,operation_kind,requested_cutoff)
                VALUES(?,?,?,decode(repeat('11',32),'hex'),?,?,?,?,'PREPARED','ADOPT_UNASSIGNED',?)
                """, operation, admin, UUID.randomUUID(), target, day, LocalDate.of(2041, 7, 1),
                LocalDate.of(2041, 7, 1), day);
        if (!state.equals("PREPARED")) jdbc.update("""
                UPDATE assignment_replacement_operations SET state='APPLIED',schedule_receipt_state='APPLIED' WHERE operation_id=?
                """, operation);
        if (state.equals("COMMITTED")) jdbc.update("""
                UPDATE assignment_replacement_operations SET state='COMMITTED',schedule_receipt_state='COMMITTED' WHERE operation_id=?
                """, operation);
        long id = homework(group, subject, semester, "LESSON", "LECTURE", day, "ACTIVE");
        mvc.perform(get("/academic/homeworks/{id}", id).header("X-Internal-Token", teacherToken()))
                .andExpect(status().is(expected));
    }

    @Test
    void revokedGrantIsRecheckedOnEveryRead() throws Exception {
        assignment(teacher, group, subject, semester, "lecture", day, null, "ACTIVE");
        long id = homework(group, subject, semester, "DATE", "LECTURE", day, "ACTIVE");
        String token = teacherToken();
        mvc.perform(get("/academic/homeworks/{id}", id).header("X-Internal-Token", token)).andExpect(status().isOk());
        jdbc.update("UPDATE user_role_grants SET status='dismissed' WHERE user_id=? AND role='teacher'", teacher);
        mvc.perform(get("/academic/homeworks/{id}", id).header("X-Internal-Token", token)).andExpect(status().isForbidden());
    }

    @Test
    void inactiveSignedTeacherCannotReadWithActiveDatabaseGrant() throws Exception {
        assignment(teacher, group, subject, semester, "lecture", day, null, "ACTIVE");
        long id = homework(group, subject, semester, "LESSON", "LECTURE", day, "ACTIVE");
        mvc.perform(get("/academic/homeworks/{id}", id).header("X-Internal-Token",
                        tokens.validToken(teacher, UUID.randomUUID(), 1, 1, "TEACHER", "DISMISSED", null, false, true)))
                .andExpect(status().isForbidden());
    }

    @Test
    void legacyTeacherWithoutSignedMetadataCannotRead() throws Exception {
        assignment(teacher, group, subject, semester, "lecture", day, null, "ACTIVE");
        long id = homework(group, subject, semester, "LESSON", "LECTURE", day, "ACTIVE");
        mvc.perform(get("/academic/homeworks/{id}", id)
                        .header("X-User-Id", teacher).header("X-User-Role", "TEACHER"))
                .andExpect(status().isForbidden());
    }

    @ParameterizedTest
    @CsvSource({"PENDING", "ARCHIVED"})
    void publicationBoundsRemainProtected(String publication) throws Exception {
        assignment(teacher, group, subject, semester, "lecture", day, null, "ACTIVE");
        long id = homework(group, subject, semester, "LESSON", "LECTURE", day, publication);
        mvc.perform(get("/academic/homeworks/{id}", id).header("X-Internal-Token", teacherToken()))
                .andExpect(status().isNotFound());
        mvc.perform(get("/academic/homeworks/{id}", id).param("archived", "true")
                .header("X-Internal-Token", teacherToken())).andExpect(status().isForbidden());
    }

    @Test
    void teacherReadDoesNotGrantCompletionArchiveHistoryOrList() throws Exception {
        assignment(teacher, group, subject, semester, "lecture", day, null, "ACTIVE");
        long id = homework(group, subject, semester, "LESSON", "LECTURE", day, "ACTIVE");
        String token = teacherToken();
        mvc.perform(post("/academic/homeworks/{id}/complete", id).header("X-Internal-Token", token)).andExpect(status().isForbidden());
        mvc.perform(delete("/academic/homeworks/{id}", id).header("X-Internal-Token", token)).andExpect(status().isForbidden());
        mvc.perform(get("/academic/homeworks/{id}/history", id).header("X-Internal-Token", token)).andExpect(status().isForbidden());
        mvc.perform(get("/academic/homeworks").param("groupId", Long.toString(group)).param("semesterId", Long.toString(semester))
                .header("X-Internal-Token", token)).andExpect(status().isForbidden());
    }

    @Test
    void existingStudentHeadmanAndAdminReadBoundsRemain() throws Exception {
        long own = homework(group, subject, semester, "LESSON", null, day, "ACTIVE");
        long otherGroup = jdbc.queryForObject("SELECT id FROM groups WHERE id<>? LIMIT 1", Long.class, group);
        long foreign = homework(otherGroup, subject(otherGroup), semester, "LESSON", null, day, "ACTIVE");
        for (boolean headman : new boolean[]{false, true}) {
            String token = tokens.validToken(student, UUID.randomUUID(), 1, 1, "STUDENT", "ACTIVE", group, headman, false);
            mvc.perform(get("/academic/homeworks/{id}", own).header("X-Internal-Token", token)).andExpect(status().isOk());
            mvc.perform(get("/academic/homeworks/{id}", foreign).header("X-Internal-Token", token)).andExpect(status().isForbidden());
        }
        mvc.perform(get("/academic/homeworks/{id}", foreign).header("X-Internal-Token",
                        tokens.validToken(admin, UUID.randomUUID(), 1, 1, "ADMIN", "ACTIVE", null, false, false)))
                .andExpect(status().isOk());
    }

    private String teacherToken() {
        return tokens.validToken(teacher, UUID.randomUUID(), 1, 1, "TEACHER", "ACTIVE", null, false, false);
    }

    private long subject(long groupId) {
        long id = jdbc.queryForObject("INSERT INTO subjects(name,type,group_id) VALUES(?,'lecture',?) RETURNING id",
                Long.class, "HW protected " + UUID.randomUUID(), groupId);
        for (String type : new String[]{"lecture", "practice", "lab"}) jdbc.update(
                "INSERT INTO subject_lesson_types(subject_id,lesson_type) VALUES(?,CAST(? AS subject_type))", id, type);
        return id;
    }

    private long semester() {
        return semester(2041);
    }

    private long semester(int year) {
        return jdbc.queryForObject("""
                INSERT INTO semesters(name,date_from,date_to,is_active,created_at)
                VALUES(?,?,?,false,NOW()) RETURNING id
                """, Long.class, "HW protected " + UUID.randomUUID(), LocalDate.of(year, 2, 1), LocalDate.of(year, 6, 30));
    }

    private long assignment(long actor, long groupId, long subjectId, long semesterId, String type,
                            LocalDate from, LocalDate until, String lifecycle) {
        return jdbc.queryForObject("""
                INSERT INTO assignments(teacher_id,group_id,subject_id,semester_id,lesson_type,valid_from,valid_until_exclusive,lifecycle_state)
                VALUES(?,?,?,?,CAST(? AS subject_type),?,?,?) RETURNING id
                """, Long.class, actor, groupId, subjectId, semesterId, type, from, until, lifecycle);
    }

    private long homework(long groupId, long subjectId, long semesterId, String mode, String type, LocalDate date, String publication) {
        return jdbc.queryForObject("""
                INSERT INTO homeworks(group_id,subject_id,semester_id,title,published_by,lesson_date,lesson_number,
                    binding_mode,lesson_type,binding_id,actor_id,request_key,payload_hash,publication_state)
                VALUES(?,?,?,'Protected homework',?,?,?,?,?,?,?, ?,decode(repeat('11',32),'hex'),?) RETURNING id
                """, Long.class, groupId, subjectId, semesterId, student, date, mode.equals("DATE") ? null : 1,
                mode, type, subjectId, student, UUID.randomUUID(), publication);
    }
}
