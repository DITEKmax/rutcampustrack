package ru.rutcampustrack.auth.contract;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.Test;
import ru.rutcampustrack.auth.dto.AuthAdmissionRequest;
import ru.rutcampustrack.auth.dto.AuthAdmissionResponse;
import ru.rutcampustrack.auth.dto.AuthSessionSummary;
import ru.rutcampustrack.auth.dto.CurrentSessionResponse;
import ru.rutcampustrack.auth.dto.PasswordPolicyResponse;
import ru.rutcampustrack.auth.dto.RoleGrantResponse;
import ru.rutcampustrack.auth.dto.SelectActiveRoleResponse;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AuthTokenDtoRedactionTest {

    private static final String REQUEST_ACCESS_TOKEN = "request-synthetic-bearer";
    private static final String ADMISSION_INTERNAL_TOKEN = "admission-synthetic-bearer";
    private static final String ROLE_ACCESS_TOKEN = "role-synthetic-bearer";
    private static final ObjectMapper MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    @Test
    void authAdmissionRequestRedactsTokenButKeepsJsonWire() throws Exception {
        AuthAdmissionRequest dto = new AuthAdmissionRequest(REQUEST_ACCESS_TOKEN);

        assertRedacted(dto.toString(), "AuthAdmissionRequest[accessToken=<redacted>]", REQUEST_ACCESS_TOKEN);
        String json = assertJsonToken(dto, "accessToken", REQUEST_ACCESS_TOKEN);

        AuthAdmissionRequest roundTrip = MAPPER.readValue(json, AuthAdmissionRequest.class);
        assertThat(roundTrip.accessToken()).isEqualTo(REQUEST_ACCESS_TOKEN);
    }

    @Test
    void authAdmissionResponseRedactsTokenButKeepsJsonWire() throws Exception {
        AuthAdmissionResponse dto = new AuthAdmissionResponse(
                ADMISSION_INTERNAL_TOKEN,
                Instant.parse("2026-01-02T03:04:05Z"),
                "00000000-0000-0000-0000-000000000001",
                "42",
                "7",
                "8",
                "STUDENT",
                "ACTIVE",
                "42",
                false,
                false);

        assertRedacted(dto.toString(), "AuthAdmissionResponse[internalToken=<redacted>, expiresAt=", ADMISSION_INTERNAL_TOKEN);
        String json = assertJsonToken(dto, "internalToken", ADMISSION_INTERNAL_TOKEN);

        AuthAdmissionResponse roundTrip = MAPPER.readValue(json, AuthAdmissionResponse.class);
        assertThat(roundTrip.internalToken()).isEqualTo(ADMISSION_INTERNAL_TOKEN);
    }

    @Test
    void selectActiveRoleResponseRedactsTokenButKeepsJsonWire() throws Exception {
        SelectActiveRoleResponse dto = new SelectActiveRoleResponse(
                ROLE_ACCESS_TOKEN,
                900,
                validSession());

        assertRedacted(dto.toString(), "SelectActiveRoleResponse[accessToken=<redacted>, expiresIn=", ROLE_ACCESS_TOKEN);
        String json = assertJsonToken(dto, "accessToken", ROLE_ACCESS_TOKEN);

        SelectActiveRoleResponse roundTrip = MAPPER.readValue(json, SelectActiveRoleResponse.class);
        assertThat(roundTrip.accessToken()).isEqualTo(ROLE_ACCESS_TOKEN);
    }

    @Test
    void authContractsRejectNonCanonicalWireValues() {
        assertThatThrownBy(() -> currentSession(
                "00000000-0000-0000-0000-000000000001",
                "student",
                List.of(validRole("10"))))
                .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> currentSession(
                "00000000-0000-0000-0000-000000000001",
                "STUDENT",
                List.of(validRole("10"), validRole("11"))))
                .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> new RoleGrantResponse(
                "10", "STUDENT", "ACTIVE", "0", "Student", true, false))
                .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> currentSession(
                "1-1-1-1-1", null, List.of(validRole("10"))))
                .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> new AuthSessionSummary(
                "1-1-1-1-1",
                "PASSWORD",
                null,
                null,
                Instant.parse("2026-01-02T03:04:05Z"),
                Instant.parse("2026-01-02T03:04:05Z"),
                false))
                .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> new AuthAdmissionResponse(
                "synthetic-internal-token",
                Instant.parse("2026-01-02T03:04:05Z"),
                "1-1-1-1-1",
                "42", "7", "8", "STUDENT", "ACTIVE", "42", false, false))
                .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> currentSession(
                "AAAAAAAA-AAAA-AAAA-AAAA-AAAAAAAAAAAA",
                null,
                List.of(validRole("10"))))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static void assertRedacted(String representation, String expectedPrefix, String sentinel) {
        assertThat(representation).startsWith(expectedPrefix);
        assertThat(representation).contains("<redacted>");
        assertThat(representation).doesNotContain(sentinel);
    }

    private static String assertJsonToken(Object dto, String propertyName, String sentinel) throws Exception {
        String json = MAPPER.writeValueAsString(dto);
        assertThat(json).contains("\"" + propertyName + "\":\"" + sentinel + "\"");

        var tree = MAPPER.readTree(json);
        assertThat(tree.get(propertyName)).isNotNull();
        assertThat(tree.get(propertyName).asText()).isEqualTo(sentinel);
        return json;
    }

    private static CurrentSessionResponse currentSession(
            String sessionId,
            String activeRole,
            List<RoleGrantResponse> roles) {
        return new CurrentSessionResponse(
                sessionId,
                "42",
                "Student",
                null,
                "7",
                "8",
                activeRole,
                roles,
                false,
                new PasswordPolicyResponse(8, 64, true, List.of("P"), "NFC"));
    }

    private static RoleGrantResponse validRole(String grantId) {
        return new RoleGrantResponse(
                grantId, "STUDENT", "ACTIVE", "42", "Student", true, false);
    }

    private static CurrentSessionResponse validSession() {
        return new CurrentSessionResponse(
                "00000000-0000-0000-0000-000000000001",
                "42",
                "Student",
                null,
                "7",
                "8",
                "STUDENT",
                List.of(new RoleGrantResponse(
                        "10",
                        "STUDENT",
                        "ACTIVE",
                        "42",
                        "Student",
                        true,
                        false)),
                false,
                new PasswordPolicyResponse(8, 64, true, List.of("P"), "NFC"));
    }
}