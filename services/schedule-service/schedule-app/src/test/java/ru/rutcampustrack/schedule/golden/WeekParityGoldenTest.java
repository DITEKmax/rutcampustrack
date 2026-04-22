package ru.rutcampustrack.schedule.golden;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import ru.rutcampustrack.schedule.contract.enums.WeekType;

import java.io.InputStream;
import java.time.LocalDate;
import java.time.temporal.WeekFields;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * M08 Группа 4 / P2-8/4 — golden-tests для ISO week parity → WeekType маппинга.
 *
 * <p>Источник — {@code src/test/resources/golden/week-parity.json}. Кейсы
 * pre-computed в виде {@code (date, isoWeek, expectedWeekType)} с ручной
 * выверкой по календарю (semester boundaries, year transitions, ISO W53
 * long years, leap years).
 *
 * <p>Convention (см. LessonGenerationService.java:100 и memory
 * week_parity_convention):
 * <ul>
 *   <li>ISO-чётный (16, 18, ...) → {@link WeekType#ODD} → «1-я учебная неделя»</li>
 *   <li>ISO-нечётный (17, 19, ...) → {@link WeekType#EVEN} → «2-я учебная неделя»</li>
 * </ul>
 */
class WeekParityGoldenTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    static Stream<Arguments> goldenCases() throws Exception {
        List<Arguments> cases = new ArrayList<>();
        try (InputStream is = WeekParityGoldenTest.class.getResourceAsStream(
                "/golden/week-parity.json")) {
            JsonNode array = MAPPER.readTree(is);
            for (JsonNode n : array) {
                cases.add(Arguments.of(
                        n.get("name").asText(),
                        LocalDate.parse(n.get("date").asText()),
                        n.get("isoWeek").asInt(),
                        WeekType.valueOf(n.get("expectedWeekType").asText())));
            }
        }
        return cases.stream();
    }

    @ParameterizedTest(name = "[{index}] {0}")
    @MethodSource("goldenCases")
    @DisplayName("ISO week parity → WeekType mapping (golden fixture)")
    void mapsIsoWeekToExpectedWeekType(String name, LocalDate date, int expectedIsoWeek,
                                       WeekType expectedWeekType) {
        // First, verify the pre-computed isoWeek in fixture matches current JVM.
        int actualIsoWeek = date.get(WeekFields.ISO.weekOfWeekBasedYear());
        assertThat(actualIsoWeek)
                .as("Fixture date=%s должен иметь ISO week=%d, но JVM посчитал %d",
                        date, expectedIsoWeek, actualIsoWeek)
                .isEqualTo(expectedIsoWeek);

        // Second, apply the production convention and verify parity.
        WeekType actualWeekType = (actualIsoWeek % 2 == 0) ? WeekType.ODD : WeekType.EVEN;
        assertThat(actualWeekType)
                .as("ISO week %d должен маппиться на %s", actualIsoWeek, expectedWeekType)
                .isEqualTo(expectedWeekType);
    }
}
