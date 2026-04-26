# Golden tests — runbook (NEW-160)

Как писать golden-fixture-based тесты и property-based тесты в проекте.

## Зачем

**Golden tests** — метод, при котором входы и expected-выходы хранятся
в отдельном JSON-файле, а test-код — тонкий loop `@ParameterizedTest
@MethodSource`. Плюсы:

- Ручная выверка expected (см. pre-computed `isoWeek`, `expectedWeekType`
  в `week-parity.json`).
- Добавление нового кейса = +1 JSON entry, не +1 test-метод.
- Документация через fixture — reviewer видит все boundary-cases в
  одном файле.
- Regression-guard: если будущий refactor сменит convention (например
  `isoWeek % 2` → обратный mapping) — 22 теста упадут с читаемым
  diff'ом.

**Property-based tests** — дополняют golden'ы: invariants, которые
должны держаться на **любом** input'е. Для них используются simple
random loops + `@RepeatedTest(N)` (jqwik не подключаем для экономии
dep'ов).

## Артефакты M08 Группы 4

| Файл | Сервис | Кейсы |
|------|--------|-------|
| `services/schedule-service/.../resources/golden/week-parity.json` | schedule | 22 cases |
| `services/schedule-service/.../test/golden/WeekParityGoldenTest.java` | schedule | `@ParameterizedTest` через `@MethodSource` |
| `services/schedule-service/.../test/golden/WeekParityPropertyTest.java` | schedule | 2 property + 1 determinism test (200 iterations) |
| `services/academic-service/.../resources/golden/display-name.json` | academic | 12 cases |
| `services/academic-service/.../test/golden/DisplayNameGoldenTest.java` | academic | `@ParameterizedTest` |
| `services/academic-service/.../test/golden/DisplayNamePropertyTest.java` | academic | 3 properties (100+ iterations) |

## Паттерн: Golden fixture

### 1. JSON структура

```json
[
  {
    "name": "Short human-readable case description",
    "inputField1": "value",
    "inputField2": 42,
    "expectedOutput": "..."
  },
  ...
]
```

Минимум требований:
- `name` — для `@ParameterizedTest(name = "[{index}] {0}")` читаемости.
- Остальные поля — как удобно для test-кода.
- Pre-computed expected values через ручную выверку ИЛИ через
  reference implementation (если есть).

### 2. Test-код

```java
class MyFeatureGoldenTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    static Stream<Arguments> goldenCases() throws Exception {
        List<Arguments> cases = new ArrayList<>();
        try (InputStream is = MyFeatureGoldenTest.class
                .getResourceAsStream("/golden/my-feature.json")) {
            JsonNode array = MAPPER.readTree(is);
            for (JsonNode n : array) {
                cases.add(Arguments.of(
                        n.get("name").asText(),
                        n.get("input").asText(),
                        n.get("expected").asText()));
            }
        }
        return cases.stream();
    }

    @ParameterizedTest(name = "[{index}] {0}")
    @MethodSource("goldenCases")
    void caseProducesExpected(String name, String input, String expected) {
        assertThat(MyFeature.compute(input)).isEqualTo(expected);
    }
}
```

### 3. Когда обновлять golden fixture

- **Convention change** (намеренная): обновить JSON + commit message
  ясно указывает «breaking: mapping changed». Reviewer подтверждает.
- **Новый edge-case**: добавить entry в JSON, test-код не трогать.
- **Bug-fix** в production coде, который раньше давал неправильный
  expected: обновить JSON (было зафиксировано неверное поведение).
  Commit: «fix: <X>; update golden to match correct behavior».

## Паттерн: Property test без jqwik

```java
@RepeatedTest(100)
@DisplayName("invariantName — short description")
void invariantHolds() {
    // 1. Generate random input
    long minDay = LocalDate.of(2020, 1, 1).toEpochDay();
    long maxDay = LocalDate.of(2030, 12, 31).toEpochDay();
    LocalDate date = LocalDate.ofEpochDay(
            ThreadLocalRandom.current().nextLong(minDay, maxDay));

    // 2. Apply transformation
    WeekType result = MyFeature.compute(date);

    // 3. Assert invariant (НЕ expected — invariant!)
    assertThat(result).isIn(WeekType.ODD, WeekType.EVEN);
    // ИЛИ
    // assertThat(result).satisfies(parity -> { ... });
}
```

**Отличие от unit-test'а** — asserting **general property**, не конкретный
output. Примеры invariants:
- `parityFlipsEveryWeek` — для любой даты, `parity(d+7) != parity(d)`
  (кроме boundary ISO W53→W1).
- `parityStableWithinWeek` — 7 дней одной ISO-недели → одна parity.
- `nameFormatterStable` — 1000 calls → same result (no side-effects).
- `lastNameAlwaysFirst` — для любого user'а, первый токен split(' ')
  = lastName.

## Когда использовать что

| Situation | Tool |
|-----------|------|
| Конкретные входы/выходы, которые хочется зафиксировать навсегда | Golden fixture JSON |
| Math/boundary edge-cases (2020-12-31, ISO W53, leap years) | Golden fixture JSON (ручная выверка) |
| Invariant'ы на всём input-space (determinism, commutativity, associativity) | Property test + `@RepeatedTest` |
| Regression guard для решённого bug'а | Golden fixture JSON (один entry) |
| Regression guard для всего класса поведения | Property test |

## jqwik — почему не используется

jqwik (`net.jqwik:jqwik`) даёт: auto-shrinking counter-examples,
statistical distribution controls, generator composition. Для v0.0.0:

- **Scope M08 Группа 4** — 2 invariants per domain. Simple random
  + `@RepeatedTest` покрывает это без новой deps.
- **Shrinking** не нужен пока мы не находим failing counter-examples.
- **Integration с Spring Boot Test** — jqwik имеет известные конфликты
  с `@ParameterizedTest` resolver'ами, требует отдельного runner'а.

Если появится необходимость — добавить `testImplementation(libs.jqwik)`
+ migrate 2 property-test методов на `@Property` (jqwik tag).
Записано в `future-ideas.md` → v0.1.

## Clock-injection паттерн (04 P2-4)

Связанная тема — Clock injection для детерминизма времязависимых
тестов. 3 сервиса в attendance (`CheckinService`, `LateCheckinService`,
`ExcuseService`) + schedule (`LessonGenerationService`) принимают
`Clock` через constructor.

**Test override pattern:**

```java
@TestConfiguration
static class FixedClockConfig {
    @Bean
    @Primary
    Clock fixedClock() {
        return Clock.fixed(
                Instant.parse("2026-04-22T10:00:00Z"),
                ZoneId.of("Europe/Moscow"));
    }
}

@SpringBootTest
@Import(FixedClockConfig.class)
class CheckinWindowIT extends AbstractAttendanceIntegrationTest { ... }
```

ИЛИ через `@MockitoBean`:

```java
@MockitoBean
Clock clock;

@BeforeEach
void setupClock() {
    when(clock.instant()).thenReturn(Instant.parse("2026-04-22T10:00:00Z"));
    when(clock.getZone()).thenReturn(ZoneId.of("Europe/Moscow"));
}
```

## Источники

- M08 PLAN.md Группа 4 — `docs/milestones/M08-test-infrastructure/PLAN.md`
- OWNER-ANSWERS P2-8/4 — `docs/report-before-v0.0.0/OWNER-ANSWERS.md`
- Memory `project_week_parity_convention.md` — convention reference
