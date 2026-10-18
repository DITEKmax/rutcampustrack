package ru.rutcampustrack.academic.user;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import ru.rutcampustrack.academic.contract.dto.group.AssignHeadmanRequest;
import ru.rutcampustrack.academic.contract.dto.user.CreateUserRequest;
import ru.rutcampustrack.academic.contract.enums.UserRole;
import ru.rutcampustrack.academic.group.GroupHeadmanAssignmentService;
import ru.rutcampustrack.academic.integration.AbstractAcademicIntegrationTest;
import ru.rutcampustrack.academic.integration.AcademicIdentityIntegrationFixture.SelectedActor;
import ru.rutcampustrack.auth.session.model.AuthRole;

import java.util.concurrent.atomic.AtomicLong;

/**
 * GET /academic/users/by-ids — batch-резолв display-имён для STUDENT/TEACHER.
 *
 * <p>Покрывает:
 *
 * <ul>
 *   <li>STUDENT доступ (headman) → 200, поля только {id, fullName};
 *   <li>несуществующие ID молча отсутствуют в выдаче;
 *   <li>cap 100 ids → 400 при превышении;
 *   <li>пустой ids param → 400.
 * </ul>
 */
@AutoConfigureMockMvc
class UserByIdsApiIT extends AbstractAcademicIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired GroupHeadmanAssignmentService headmen;
    private static final AtomicLong TELEGRAM = new AtomicLong(610000009000L);

    private Long student1Id;
    private Long student2Id;
    private Long studentNoMiddleId;
    private SelectedActor caller;

    @BeforeEach
    void setUp() {
        var canonical = identityFixture.canonical();
        var admin = identityFixture.select(canonical.adminId(), AuthRole.ADMIN);
        student1Id =
                identityFixture
                        .seedUser(
                                new CreateUserRequest(
                                        "Алексеев",
                                        "Алексей",
                                        "Алексеевич",
                                        UserRole.STUDENT,
                                        canonical.groupId(),
                                        null,
                                        TELEGRAM.incrementAndGet()),
                                admin)
                        .userId();
        student2Id =
                identityFixture
                        .seedUser(
                                new CreateUserRequest(
                                        "Борисов",
                                        "Борис",
                                        "Борисович",
                                        UserRole.STUDENT,
                                        canonical.groupId(),
                                        null,
                                        TELEGRAM.incrementAndGet()),
                                admin)
                        .userId();
        studentNoMiddleId =
                identityFixture
                        .seedUser(
                                new CreateUserRequest(
                                        "Сидоров",
                                        "Семён",
                                        null,
                                        UserRole.STUDENT,
                                        canonical.groupId(),
                                        null,
                                        TELEGRAM.incrementAndGet()),
                                admin)
                        .userId();
        try (var ignored = identityFixture.request(admin)) {
            headmen.assign(
                    canonical.groupId(),
                    new AssignHeadmanRequest(student1Id, canonical.studentId()));
        }
        caller = identityFixture.select(student1Id, AuthRole.HEADMAN);
    }

    private org.springframework.test.web.servlet.ResultActions callAsStudent(String idsCsv)
            throws Exception {
        return mockMvc.perform(
                get("/academic/users/by-ids")
                        .with(identityFixture.signed(caller))
                        .param("ids", idsCsv));
    }

    @Test
    void byIds_student_returnsIdAndFullName_noPii() throws Exception {
        callAsStudent(student1Id + "," + student2Id)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$._embedded.userSummaryResponseList", hasSize(2)))
                .andExpect(
                        jsonPath(
                                "$._embedded.userSummaryResponseList[*].id",
                                containsInAnyOrder(student1Id.intValue(), student2Id.intValue())))
                .andExpect(
                        jsonPath(
                                "$._embedded.userSummaryResponseList[*].fullName",
                                hasItem("Алексеев Алексей Алексеевич")))
                .andExpect(
                        jsonPath(
                                "$._embedded.userSummaryResponseList[*].fullName",
                                hasItem("Борисов Борис Борисович")))
                // Security minimum: PII НЕ должны попасть в выдачу
                .andExpect(jsonPath("$._embedded.userSummaryResponseList[*].login").doesNotExist())
                .andExpect(jsonPath("$._embedded.userSummaryResponseList[*].email").doesNotExist())
                .andExpect(
                        jsonPath("$._embedded.userSummaryResponseList[*].telegramId")
                                .doesNotExist())
                .andExpect(jsonPath("$._embedded.userSummaryResponseList[*].role").doesNotExist());
    }

    @Test
    void byIds_userWithoutMiddleName_fullNameOmitsTrailingSpace() throws Exception {
        callAsStudent(String.valueOf(studentNoMiddleId))
                .andExpect(status().isOk())
                .andExpect(
                        jsonPath(
                                "$._embedded.userSummaryResponseList[0].fullName",
                                org.hamcrest.Matchers.is("Сидоров Семён")));
    }

    @Test
    void byIds_unknownIds_silentlyOmitted() throws Exception {
        callAsStudent(student1Id + ",9999999")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$._embedded.userSummaryResponseList", hasSize(1)))
                .andExpect(
                        jsonPath(
                                "$._embedded.userSummaryResponseList[0].id",
                                org.hamcrest.Matchers.is(student1Id.intValue())));
    }

    @Test
    void byIds_emptyIds_returns400() throws Exception {
        // Spring отвергнёт отсутствующий required @RequestParam как 400 раньше
        // того, как наш cap-валидатор сработает. Это всё ещё HTTP 400 — чего
        // достаточно с точки зрения contract'а.
        mockMvc.perform(get("/academic/users/by-ids").with(identityFixture.signed(caller)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void byIds_over100_returns400() throws Exception {
        StringBuilder sb = new StringBuilder();
        for (int i = 1; i <= 101; i++) {
            if (i > 1) sb.append(',');
            sb.append(i);
        }
        callAsStudent(sb.toString()).andExpect(status().isBadRequest());
    }
}
