package ru.rutcampustrack.academic.golden;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import ru.rutcampustrack.academic.entity.User;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * M08 Группа 4 / P2-8/4 — golden-tests для {@link User#getDisplayName()}.
 *
 * <p>Источник — {@code src/test/resources/golden/display-name.json}. Кейсы:
 * полное ФИО, без отчества, blank/whitespace middleName, double-barrel
 * lastName, латиница, кириллица с ё, апостроф, длинные имена.
 *
 * <p>Формат: {@code "lastName firstName [middleName]"} (middleName
 * опускается если null/пустой/whitespace — см. User.java:47-53).
 */
class DisplayNameGoldenTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    static Stream<Arguments> goldenCases() throws Exception {
        List<Arguments> cases = new ArrayList<>();
        try (InputStream is = DisplayNameGoldenTest.class.getResourceAsStream(
                "/golden/display-name.json")) {
            JsonNode array = MAPPER.readTree(is);
            for (JsonNode n : array) {
                JsonNode middleNode = n.get("middleName");
                String middleName = middleNode == null || middleNode.isNull()
                        ? null : middleNode.asText();
                cases.add(Arguments.of(
                        n.get("name").asText(),
                        n.get("firstName").asText(),
                        n.get("lastName").asText(),
                        middleName,
                        n.get("expectedDisplay").asText()));
            }
        }
        return cases.stream();
    }

    @ParameterizedTest(name = "[{index}] {0}")
    @MethodSource("goldenCases")
    @DisplayName("User.getDisplayName() → ФИО форматирование (golden fixture)")
    void computesExpectedDisplayName(String caseName, String firstName, String lastName,
                                     String middleName, String expected) {
        User user = new User();
        user.setFirstName(firstName);
        user.setLastName(lastName);
        user.setMiddleName(middleName);

        assertThat(user.getDisplayName()).isEqualTo(expected);
    }
}
