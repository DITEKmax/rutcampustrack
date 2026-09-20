package ru.rutcampustrack.mobilebff.contractexport;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import ru.rutcampustrack.mobilebff.MobileBffApplication;
import ru.rutcampustrack.mobilebff.contract.model.StudentApiModels.ProblemCode;
import ru.rutcampustrack.mobilebff.error.MobileBffException;
import ru.rutcampustrack.mobilebff.student.StudentCheckinFacade;
import ru.rutcampustrack.shared.security.InternalJwtTestFactory;
import ru.rutcampustrack.shared.security.PublicKeyProvider;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.http.MediaType.APPLICATION_PROBLEM_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(
        classes = MobileBffApplication.class,
        properties = {
                "springdoc.api-docs.path=/api-docs",
                "springdoc.enable-hateoas=false",
                "spring.autoconfigure.exclude="
                        + "ru.rutcampustrack.shared.web.autoconfigure.SharedWebAutoConfiguration"
        }
)
@AutoConfigureMockMvc
class OpenApiSnapshotIT {

    private static final Path SNAPSHOT_PATH = Path.of("../../..", "docs", "openapi", "mobile-bff.json")
            .toAbsolutePath().normalize();
    private static final InternalJwtTestFactory JWT = new InternalJwtTestFactory();

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PublicKeyProvider publicKeyProvider;

    @MockitoBean
    private StudentCheckinFacade checkins;

    @BeforeEach
    void setUpRuntimeSeam() {
        when(publicKeyProvider.getPublicKey()).thenReturn(JWT.publicKey());
        when(checkins.checkin(anyLong(), anyString(), any())).thenThrow(new MobileBffException(
                HttpStatus.NOT_FOUND, ProblemCode.LESSON_NOT_FOUND, "Пара не найдена"));
    }

    @Test
    void apiDocsMatchCanonicalSnapshot() throws Exception {
        String body = mockMvc.perform(get("/api-docs"))
                .andReturn()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8);
        assertThat(body).isNotBlank();

        ObjectMapper mapper = new ObjectMapper();
        ObjectNode root = (ObjectNode) mapper.readTree(body);
        root.remove("servers");
        assertThat(root.at("/openapi").asText()).isEqualTo("3.0.1");
        assertThat(root.at("/info/version").asText()).isEqualTo("JS-STUDENT-01-r1");
        assertThat(root.at("/paths/~1api~1v1~1student~1today/get")).isNotEmpty();
        assertThat(root.at("/paths/~1api~1v1~1student~1schedule/get")).isNotEmpty();
        assertThat(root.at("/paths/~1api~1v1~1student~1lessons~1{lessonId}~1checkin/post")).isNotEmpty();
        assertThat(root.at("/components/schemas/GeoInput/oneOf")).hasSize(2);
        assertThat(root.at("/components/schemas/CoordinatesGeo/type").asText()).isEqualTo("object");
        assertThat(root.at("/components/schemas/CoordinatesGeo/required").toString())
                .contains("latitude", "longitude");
        assertThat(root.at("/components/schemas/CoordinatesGeo/properties/kind/enum").toString())
                .isEqualTo("[\"COORDINATES\"]");
        assertThat(root.at("/components/schemas/UnavailableGeo/properties/kind/enum").toString())
                .isEqualTo("[\"UNAVAILABLE\"]");
        assertThat(root.at("/components/schemas/TodayLesson/properties/attendance/nullable").asBoolean())
                .isTrue();
        assertThat(root.at("/components/schemas/StudentCheckinAck/properties/request/nullable").asBoolean())
                .isTrue();
        assertThat(root.at("/components/schemas/Room/properties/previous/nullable").asBoolean())
                .isTrue();
        assertThat(root.at("/components/schemas/MobileProblemDetails/properties/extras/additionalProperties/type")
                .asText()).isEqualTo("string");

        ObjectWriter writer = mapper.writer()
                .with(SerializationFeature.INDENT_OUTPUT)
                .with(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS);
        String actual = writer.writeValueAsString(root) + "\n";

        if (Boolean.getBoolean("openapi.snapshot.update")) {
            Files.createDirectories(SNAPSHOT_PATH.getParent());
            Files.writeString(SNAPSHOT_PATH, actual, StandardCharsets.UTF_8);
            return;
        }

        assertThat(SNAPSHOT_PATH).exists();
        assertThat(actual).isEqualTo(Files.readString(SNAPSHOT_PATH, StandardCharsets.UTF_8));
    }

    @Test
    void bothValidGeoKindsReachTheContractSeam() throws Exception {
        mockMvc.perform(post("/api/v1/student/lessons/77/checkin")
                        .header("X-Internal-Token", studentToken())
                        .header("Idempotency-Key", "contract-key-0001")
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"geo":{"kind":"COORDINATES","latitude":55.751244,"longitude":37.618423}}
                                """))
                .andExpect(status().isNotFound());

        mockMvc.perform(post("/api/v1/student/lessons/77/checkin")
                        .header("X-Internal-Token", studentToken())
                        .header("Idempotency-Key", "contract-key-0002")
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"geo":{"kind":"UNAVAILABLE","reason":"TIMEOUT"}}
                                """))
                .andExpect(status().isNotFound());
    }

    @Test
    void missingOrNullCoordinateIsRejectedBeforeTheSeam() throws Exception {
        mockMvc.perform(post("/api/v1/student/lessons/77/checkin")
                        .header("X-Internal-Token", studentToken())
                        .header("Idempotency-Key", "contract-key-0003")
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"geo":{"kind":"COORDINATES","longitude":37.618423}}
                                """))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post("/api/v1/student/lessons/77/checkin")
                        .header("X-Internal-Token", studentToken())
                        .header("Idempotency-Key", "contract-key-0004")
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"geo":{"kind":"COORDINATES","latitude":null,"longitude":37.618423}}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void clientInputFailuresUseTypedProblemDetails() throws Exception {
        expectProblem(post("/api/v1/student/lessons/77/checkin")
                        .header("X-Internal-Token", studentToken())
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"geo":{"kind":"COORDINATES","latitude":55.751244,"longitude":37.618423}}
                                """),
                HttpStatus.BAD_REQUEST, ProblemCode.INVALID_IDEMPOTENCY_KEY);

        expectProblem(post("/api/v1/student/lessons/77/checkin")
                        .header("X-Internal-Token", studentToken())
                        .header("Idempotency-Key", "short-key")
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"geo":{"kind":"COORDINATES","latitude":55.751244,"longitude":37.618423}}
                                """),
                HttpStatus.BAD_REQUEST, ProblemCode.INVALID_IDEMPOTENCY_KEY);

        expectProblem(post("/api/v1/student/lessons/77/checkin")
                        .header("X-Internal-Token", studentToken())
                        .header("Idempotency-Key", "contract-key-0005")
                        .contentType(APPLICATION_JSON)
                        .content("{\"geo\":"),
                HttpStatus.BAD_REQUEST, ProblemCode.INVALID_REQUEST);

        expectProblem(post("/api/v1/student/lessons/77/checkin")
                        .header("X-Internal-Token", studentToken())
                        .header("Idempotency-Key", "contract-key-0006")
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"geo":{"kind":"UNEXPECTED"}}
                                """),
                HttpStatus.BAD_REQUEST, ProblemCode.INVALID_REQUEST);

        expectProblem(post("/api/v1/student/lessons/77/checkin")
                        .header("X-Internal-Token", studentToken())
                        .header("Idempotency-Key", "contract-key-0007")
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"geo":{"kind":"COORDINATES","latitude":99,"longitude":37.618423}}
                                """),
                HttpStatus.BAD_REQUEST, ProblemCode.INVALID_REQUEST);

        expectProblem(post("/api/v1/student/lessons/77/checkin")
                        .header("X-Internal-Token", studentToken())
                        .header("Idempotency-Key", "contract-key-0008")
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"geo":{"kind":"COORDINATES","latitude":55.751244,"longitude":37.618423}}
                                """),
                HttpStatus.NOT_FOUND, ProblemCode.LESSON_NOT_FOUND);
    }

    private void expectProblem(org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request,
                               HttpStatus expectedStatus, ProblemCode code) throws Exception {
        mockMvc.perform(request)
                .andExpect(status().is(expectedStatus.value()))
                .andExpect(content().contentTypeCompatibleWith(APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value(code.name()));
    }

    private static String studentToken() {
        return JWT.validToken(100L, "STUDENT", 10L, false);
    }
}
