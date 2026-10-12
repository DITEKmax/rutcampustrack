package ru.rutcampustrack.schedule.week;

import static org.hamcrest.Matchers.nullValue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.SerializationFeature;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.aop.aspectj.annotation.AspectJProxyFactory;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import ru.rutcampustrack.schedule.exception.GlobalExceptionHandler;
import ru.rutcampustrack.schedule.grpc.AcademicAdminPermissionClient;
import ru.rutcampustrack.schedule.security.RequestContext;
import ru.rutcampustrack.schedule.security.RoleCheckAspect;
import ru.rutcampustrack.schedule.security.ScheduleUserContextFilter;
import ru.rutcampustrack.shared.security.InternalJwtProperties;
import ru.rutcampustrack.shared.security.InternalJwtTestFactory;
import ru.rutcampustrack.shared.security.InternalJwtValidator;
import ru.rutcampustrack.shared.security.PublicKeyProvider;

/** Real MVC, role aspect and signed HTTP filter; no application/DB infrastructure. */
class AcademicWeekPolicyControllerTest {
    private static final String PATH = "/schedule/academic-week-policy";
    private static final UUID SESSION = UUID.fromString("dddddddd-dddd-4ddd-8ddd-dddddddddddd");
    private final InternalJwtTestFactory signer = new InternalJwtTestFactory();
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        RequestContext context = new RequestContext();
        AspectJProxyFactory proxy = new AspectJProxyFactory(new AcademicWeekPolicyController());
        proxy.setProxyTargetClass(true);
        RoleCheckAspect roleChecks = new RoleCheckAspect(context);
        ReflectionTestUtils.setField(
                roleChecks, "adminPermissionClient", mock(AcademicAdminPermissionClient.class));
        proxy.addAspect(roleChecks);
        InternalJwtProperties properties =
                new InternalJwtProperties(
                        null,
                        60,
                        0,
                        false,
                        InternalJwtTestFactory.ISSUER,
                        InternalJwtTestFactory.AUDIENCE,
                        "X-Internal-Token");
        PublicKeyProvider keys = mock(PublicKeyProvider.class);
        when(keys.getPublicKey()).thenReturn(signer.publicKey());
        ScheduleUserContextFilter filter =
                new ScheduleUserContextFilter(
                        new InternalJwtValidator(keys, properties), properties, context, null);
        mvc =
                MockMvcBuilders.standaloneSetup((AcademicWeekPolicyController) proxy.getProxy())
                        .setMessageConverters(
                                new MappingJackson2HttpMessageConverter(
                                        Jackson2ObjectMapperBuilder.json()
                                                .featuresToDisable(
                                                        SerializationFeature
                                                                .WRITE_DATES_AS_TIMESTAMPS)
                                                .build()))
                        .setControllerAdvice(new GlobalExceptionHandler())
                        .addFilters(filter)
                        .build();
    }

    @Test
    void isoYearBoundaryAndWeek53ReturnConsistentSelectedWeekAndCatalog() throws Exception {
        mvc.perform(
                        get(PATH)
                                .param("date", "2021-01-01")
                                .header("X-Internal-Token", token("STUDENT")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.weekStart").value("2020-12-28"))
                .andExpect(jsonPath("$.weekEnd").value("2021-01-03"))
                .andExpect(jsonPath("$.isoWeek").value(53))
                .andExpect(jsonPath("$.weekType").value("EVEN"))
                .andExpect(jsonPath("$.parityNumber").value(2))
                .andExpect(jsonPath("$.label").value("2"))
                .andExpect(jsonPath("$.weekTypes.length()").value(3))
                .andExpect(jsonPath("$.weekTypes[0].weekType").value("ALL"))
                .andExpect(jsonPath("$.weekTypes[0].parityNumber").value(nullValue()))
                .andExpect(jsonPath("$.weekTypes[0].label").value("Каждую неделю"))
                .andExpect(jsonPath("$.weekTypes[1].weekType").value("ODD"))
                .andExpect(jsonPath("$.weekTypes[1].parityNumber").value(1))
                .andExpect(jsonPath("$.weekTypes[1].label").value("1"))
                .andExpect(jsonPath("$.weekTypes[2].weekType").value("EVEN"))
                .andExpect(jsonPath("$.weekTypes[2].parityNumber").value(2))
                .andExpect(jsonPath("$.weekTypes[2].label").value("2"));
        // ISO week 53 -> week 1 are both odd: parity must not alternate by elapsed weeks.
        mvc.perform(
                        get(PATH)
                                .param("date", "2021-01-04")
                                .header("X-Internal-Token", token("STUDENT")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isoWeek").value(1))
                .andExpect(jsonPath("$.weekType").value("EVEN"));
        mvc.perform(
                        get(PATH)
                                .param("date", "2019-12-30")
                                .header("X-Internal-Token", token("STUDENT")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isoWeek").value(1))
                .andExpect(jsonPath("$.weekType").value("EVEN"));
        mvc.perform(
                        get(PATH)
                                .param("date", "2021-01-11")
                                .header("X-Internal-Token", token("STUDENT")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isoWeek").value(2))
                .andExpect(jsonPath("$.weekType").value("ODD"))
                .andExpect(jsonPath("$.parityNumber").value(1))
                .andExpect(jsonPath("$.label").value("1"));
    }

    @Test
    void dateIsRequiredAndInvalidDatesAreBadRequests() throws Exception {
        mvc.perform(get(PATH).header("X-Internal-Token", token("STUDENT")))
                .andExpect(status().isBadRequest());
        for (String invalid : new String[] {"", "not-a-date", "2026-02-30", "06.10.2026"}) {
            mvc.perform(
                            get(PATH)
                                    .param("date", invalid)
                                    .header("X-Internal-Token", token("STUDENT")))
                    .andExpect(status().isBadRequest());
        }
    }

    @Test
    void signedAdminTeacherStudentAndHeadmanCanReadMetadataWithoutGroupScope() throws Exception {
        for (String role : new String[] {"ADMIN", "TEACHER", "STUDENT", "HEADMAN"}) {
            mvc.perform(
                            get(PATH)
                                    .param("date", "2026-10-06")
                                    .header("X-Internal-Token", token(role)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.weekStart").value("2026-10-05"));
        }
    }

    @Test
    void unsignedLegacyAndWrongSignerCannotReadMetadata() throws Exception {
        mvc.perform(get(PATH).param("date", "2026-10-06")).andExpect(status().isUnauthorized());
        mvc.perform(
                        get(PATH)
                                .param("date", "2026-10-06")
                                .header("X-User-Id", "1")
                                .header("X-User-Role", "ADMIN"))
                .andExpect(status().isUnauthorized());
        String wrongSigner =
                new InternalJwtTestFactory()
                        .validToken(1, SESSION, 1, 1, "ADMIN", "ACTIVE", null, false, false);
        mvc.perform(get(PATH).param("date", "2026-10-06").header("X-Internal-Token", wrongSigner))
                .andExpect(status().isUnauthorized());
    }

    private String token(String role) {
        boolean student = "STUDENT".equals(role) || "HEADMAN".equals(role);
        return signer.validToken(
                1,
                SESSION,
                1,
                1,
                role,
                "ACTIVE",
                student ? 10L : null,
                "HEADMAN".equals(role),
                false);
    }
}
