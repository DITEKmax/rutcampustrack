package ru.rutcampustrack.mobilebff.contract;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import ru.rutcampustrack.mobilebff.contract.model.StudentRequestApiModels;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class StudentRequestOptionsJsonTest {
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void serializesRequiredCommentRequiredForBothReasonValues() throws Exception {
        StudentRequestApiModels.Options options = new StudentRequestApiModels.Options(
                List.of(
                        new StudentRequestApiModels.ReasonOption(
                                StudentRequestApiModels.ExcuseReason.OTHER, "Другое", true),
                        new StudentRequestApiModels.ReasonOption(
                                StudentRequestApiModels.ExcuseReason.ILLNESS, "Болезнь", false)),
                new StudentRequestApiModels.FileLimits(2, 10L, 20L, List.of(), List.of()),
                new StudentRequestApiModels.Budget("30", 5, 0, 5), List.of());

        JsonNode reasons = mapper.readTree(mapper.writeValueAsString(options)).get("reasons");

        assertThat(reasons).hasSize(2);
        assertThat(reasons.get(0).has("commentRequired")).isTrue();
        assertThat(reasons.get(0).get("commentRequired").isBoolean()).isTrue();
        assertThat(reasons.get(0).get("commentRequired").asBoolean()).isTrue();
        assertThat(reasons.get(1).get("commentRequired").asBoolean()).isFalse();
    }
}