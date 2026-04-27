package ru.rutcampustrack.academic.user;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import ru.rutcampustrack.academic.integration.AbstractAcademicIntegrationTest;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * GET /academic/users/by-ids — batch-резолв display-имён для STUDENT/TEACHER.
 *
 * <p>Покрывает:
 * <ul>
 *     <li>STUDENT доступ (headman) → 200, поля только {id, fullName};
 *     <li>несуществующие ID молча отсутствуют в выдаче;
 *     <li>cap 100 ids → 400 при превышении;
 *     <li>пустой ids param → 400.
 * </ul>
 */
@AutoConfigureMockMvc
@Transactional
class UserByIdsApiIT extends AbstractAcademicIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired JdbcTemplate jdbcTemplate;

    private Long student1Id;
    private Long student2Id;
    private Long studentNoMiddleId;
    private Long callerStudentId;

    @BeforeEach
    void setUp() {
        jdbcTemplate.update(
                "INSERT INTO users (login, password_hash, last_name, first_name, middle_name, " +
                        "role, status, is_headman, password_changed, created_at, updated_at) VALUES " +
                        "('byids_a', 'x', 'Алексеев','Алексей',  'Алексеевич', 'student', 'active', true,  false, NOW(), NOW())," +
                        "('byids_b', 'x', 'Борисов', 'Борис',    'Борисович',  'student', 'active', false, false, NOW(), NOW())," +
                        "('byids_c', 'x', 'Сидоров', 'Семён',    NULL,         'student', 'active', false, false, NOW(), NOW())"
        );
        student1Id = jdbcTemplate.queryForObject(
                "SELECT id FROM users WHERE login = 'byids_a'", Long.class);
        student2Id = jdbcTemplate.queryForObject(
                "SELECT id FROM users WHERE login = 'byids_b'", Long.class);
        studentNoMiddleId = jdbcTemplate.queryForObject(
                "SELECT id FROM users WHERE login = 'byids_c'", Long.class);
        callerStudentId = student1Id; // headman caller
    }

    private org.springframework.test.web.servlet.ResultActions callAsStudent(String idsCsv) throws Exception {
        return mockMvc.perform(get("/academic/users/by-ids")
                .header("X-User-Id", callerStudentId)
                .header("X-User-Role", "STUDENT")
                .header("X-Group-Id", "")
                .header("X-Is-Headman", "true")
                .param("ids", idsCsv));
    }

    @Test
    void byIds_student_returnsIdAndFullName_noPii() throws Exception {
        callAsStudent(student1Id + "," + student2Id)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$._embedded.userSummaryResponseList", hasSize(2)))
                .andExpect(jsonPath("$._embedded.userSummaryResponseList[*].id",
                        containsInAnyOrder(student1Id.intValue(), student2Id.intValue())))
                .andExpect(jsonPath("$._embedded.userSummaryResponseList[*].fullName",
                        hasItem("Алексеев Алексей Алексеевич")))
                .andExpect(jsonPath("$._embedded.userSummaryResponseList[*].fullName",
                        hasItem("Борисов Борис Борисович")))
                // Security minimum: PII НЕ должны попасть в выдачу
                .andExpect(jsonPath("$._embedded.userSummaryResponseList[*].login").doesNotExist())
                .andExpect(jsonPath("$._embedded.userSummaryResponseList[*].email").doesNotExist())
                .andExpect(jsonPath("$._embedded.userSummaryResponseList[*].telegramId").doesNotExist())
                .andExpect(jsonPath("$._embedded.userSummaryResponseList[*].role").doesNotExist());
    }

    @Test
    void byIds_userWithoutMiddleName_fullNameOmitsTrailingSpace() throws Exception {
        callAsStudent(String.valueOf(studentNoMiddleId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$._embedded.userSummaryResponseList[0].fullName",
                        org.hamcrest.Matchers.is("Сидоров Семён")));
    }

    @Test
    void byIds_unknownIds_silentlyOmitted() throws Exception {
        callAsStudent(student1Id + ",9999999")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$._embedded.userSummaryResponseList", hasSize(1)))
                .andExpect(jsonPath("$._embedded.userSummaryResponseList[0].id",
                        org.hamcrest.Matchers.is(student1Id.intValue())));
    }

    @Test
    void byIds_emptyIds_returns400() throws Exception {
        // Spring отвергнёт отсутствующий required @RequestParam как 400 раньше
        // того, как наш cap-валидатор сработает. Это всё ещё HTTP 400 — чего
        // достаточно с точки зрения contract'а.
        mockMvc.perform(get("/academic/users/by-ids")
                .header("X-User-Id", callerStudentId)
                .header("X-User-Role", "STUDENT")
                .header("X-Group-Id", "")
                .header("X-Is-Headman", "true"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void byIds_over100_returns400() throws Exception {
        StringBuilder sb = new StringBuilder();
        for (int i = 1; i <= 101; i++) {
            if (i > 1) sb.append(',');
            sb.append(i);
        }
        callAsStudent(sb.toString())
                .andExpect(status().isBadRequest());
    }
}
