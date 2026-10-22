package ru.rutcampustrack.academic.events;

import static org.assertj.core.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ObjectNode;

import org.junit.jupiter.api.Test;

import ru.rutcampustrack.academic.exportjobs.ExportStateEvent;
import ru.rutcampustrack.auth.dto.ExportJobModels.State;

import java.time.OffsetDateTime;
import java.util.UUID;

class ExportStateContractTest {
    @Test
    void actualProducerCodesRoundTripAndSchemaExcludesUnknownStatesAndFileContents()
            throws Exception {
        var mapper =
                new ObjectMapper()
                        .findAndRegisterModules()
                        .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        for (State state : State.values()) {
            var event =
                    new ExportStateEvent(
                            this, UUID.randomUUID(), 7, UUID.randomUUID(), state.code());
            event.setSourceService("academic-service");
            event.setEventVersion(1);
            event.setTraceId(UUID.randomUUID().toString());
            event.setOccurredAt(OffsetDateTime.now());
            String json = mapper.writeValueAsString(event);
            assertThat(EventSchemaValidator.validate("export.state.changed.json", json)).isEmpty();
            assertThat(mapper.readValue(mapper.writeValueAsString(state), State.class))
                    .isEqualTo(state);
            ObjectNode bad = (ObjectNode) mapper.readTree(json);
            ((ObjectNode) bad.path("payload")).put("state", "READY");
            assertThat(
                            EventSchemaValidator.validate(
                                    "export.state.changed.json", mapper.writeValueAsString(bad)))
                    .isNotEmpty();
            ((ObjectNode) bad.path("payload"))
                    .put("state", state.code())
                    .put("contents", "synthetic");
            assertThat(
                            EventSchemaValidator.validate(
                                    "export.state.changed.json", mapper.writeValueAsString(bad)))
                    .isNotEmpty();
        }
    }
}
