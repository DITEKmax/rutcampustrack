package ru.rutcampustrack.mobilebff.contract;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.Test;
import ru.rutcampustrack.mobilebff.contract.model.StudentApiModels;
import ru.rutcampustrack.mobilebff.contract.model.StudentRequestApiModels;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class StudentRequestDetailJsonTest {

    private final ObjectMapper mapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    @Test
    void absentDetailPropertiesRemainPresentAsNull() throws Exception {
        JsonNode json = mapper.readTree(mapper.writeValueAsString(detail(null, null, null)));

        assertThat(json.has("summary")).isTrue();
        assertThat(json.has("attachments")).isTrue();
        assertThat(json.has("reason")).isTrue();
        assertThat(json.get("reason").isNull()).isTrue();
        assertThat(json.has("comment")).isTrue();
        assertThat(json.get("comment").isNull()).isTrue();
        assertThat(json.has("decision")).isTrue();
        assertThat(json.get("decision").isNull()).isTrue();
    }

    @Test
    void populatedDetailPropertiesAreSerializedUnchanged() throws Exception {
        StudentRequestApiModels.Decision decision = new StudentRequestApiModels.Decision(
                "Принято", Instant.parse("2026-09-08T12:34:56Z"));
        JsonNode json = mapper.readTree(mapper.writeValueAsString(
                detail(StudentRequestApiModels.ExcuseReason.ILLNESS, "Комментарий", decision)));

        assertThat(json.get("reason").asText()).isEqualTo("ILLNESS");
        assertThat(json.get("comment").asText()).isEqualTo("Комментарий");
        assertThat(json.get("decision").get("comment").asText()).isEqualTo("Принято");
        assertThat(json.get("decision").get("decidedAt").asText())
                .isEqualTo("2026-09-08T12:34:56Z");
        assertThat(json.get("attachments").isArray()).isTrue();
    }

    @Test
    void eligibilityReasonsHaveExactPublicWireNamesInCanonicalOrder() throws Exception {
        assertThat(mapper.writeValueAsString(StudentApiModels.EligibilityReason.values()))
                .isEqualTo("[\"ELIGIBLE\",\"ALREADY_PRESENT\",\"LESSON_CANCELLED\",\"TOO_EARLY\","
                        + "\"WINDOW_CLOSED\",\"GEO_BLOCKED\",\"PENDING_CONFIRMATION\",\"COOLDOWN\","
                        + "\"HEADMAN_ABSENT_REQUIRES_APPEAL\",\"HEADMAN_USES_JOURNAL\","
                        + "\"DEPENDENCY_UNAVAILABLE\"]");
    }

    private static StudentRequestApiModels.Detail detail(
            StudentRequestApiModels.ExcuseReason reason,
            String comment,
            StudentRequestApiModels.Decision decision) {
        StudentRequestApiModels.Summary summary = new StudentRequestApiModels.Summary(
                "request-1", StudentRequestApiModels.Kind.EXCUSE,
                StudentRequestApiModels.Status.PENDING, StudentRequestApiModels.Origin.MANUAL,
                List.of(), null, null);
        return new StudentRequestApiModels.Detail(summary, reason, comment, decision, null);
    }
}
