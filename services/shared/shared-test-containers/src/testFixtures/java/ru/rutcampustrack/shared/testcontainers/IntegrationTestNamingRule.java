package ru.rutcampustrack.shared.testcontainers;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaModifier;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import org.springframework.boot.test.context.SpringBootTest;
import org.testcontainers.junit.jupiter.Testcontainers;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;

/**
 * M08 Группа 1 / P2-8/1 — ArchUnit правило, гарантирующее что все integration-тесты
 * (Spring context, Testcontainers) именуются с суффиксом "IT", чтобы Gradle
 * split тасок ({@code test} vs {@code integrationTest} в root build.gradle.kts)
 * работал корректно.
 *
 * <p>Критерий «integration-тест» — класс, отмеченный одной из:
 * <ul>
 *   <li>{@link SpringBootTest}</li>
 *   <li>{@link Testcontainers}</li>
 *   <li>extends {@code ContainerTestBase} (из shared-test-containers testFixtures)</li>
 *   <li>extends любой abstract класс в пакете {@code ...integration.*}
 *       с именем {@code Abstract*IntegrationTest} (meta-annotated через inheritance)</li>
 * </ul>
 *
 * <p>Abstract классы (modifiers содержат {@code ABSTRACT}) исключаются — они не
 * запускаются JUnit Platform'ой напрямую, имя {@code Abstract*IntegrationTest}
 * keep-as-is конвенция проекта v0.0.0 (см. NOTES.md Группа 1 audit).
 *
 * <p>Unit-тесты (чистый JUnit + Mockito, без Spring context) — не покрываются
 * правилом, остаются с суффиксом {@code Test}.
 *
 * <p><b>Usage:</b>
 * <pre>{@code
 * @AnalyzeClasses(packages = "ru.rutcampustrack.<service>",
 *         importOptions = ImportOption.OnlyIncludeTests.class)
 * class IntegrationTestNamingConventionIT {
 *     @ArchTest
 *     static final ArchRule rule = IntegrationTestNamingRule.integrationTestsMustEndWithIT();
 * }
 * }</pre>
 */
public final class IntegrationTestNamingRule {

    private IntegrationTestNamingRule() {
    }

    public static ArchRule integrationTestsMustEndWithIT() {
        return classes()
                .that(new com.tngtech.archunit.base.DescribedPredicate<JavaClass>(
                        "являются integration-тестом и не abstract") {
                    @Override
                    public boolean test(JavaClass clazz) {
                        if (clazz.getModifiers().contains(JavaModifier.ABSTRACT)) {
                            return false;
                        }
                        return looksLikeIntegrationTest(clazz);
                    }
                })
                .should(endWithIT())
                .because(
                        "M08 Группа 1 (P2-8/1): класс с @SpringBootTest / @Testcontainers "
                                + "/ extends ContainerTestBase должен оканчиваться на \"IT\", "
                                + "иначе Gradle integrationTest task не увидит его.");
    }

    private static boolean looksLikeIntegrationTest(JavaClass clazz) {
        if (clazz.isAnnotatedWith(SpringBootTest.class)) return true;
        if (clazz.isAnnotatedWith(Testcontainers.class)) return true;

        JavaClass parent = clazz.getRawSuperclass().orElse(null);
        while (parent != null && !parent.getName().equals("java.lang.Object")) {
            if (parent.isAnnotatedWith(SpringBootTest.class)) return true;
            if (parent.isAnnotatedWith(Testcontainers.class)) return true;
            String simpleName = parent.getSimpleName();
            if ("ContainerTestBase".equals(simpleName)) return true;
            if (simpleName.startsWith("Abstract") && simpleName.endsWith("IntegrationTest")) {
                return true;
            }
            parent = parent.getRawSuperclass().orElse(null);
        }
        return false;
    }

    private static ArchCondition<JavaClass> endWithIT() {
        return new ArchCondition<>("иметь имя, оканчивающееся на \"IT\"") {
            @Override
            public void check(JavaClass item, ConditionEvents events) {
                if (!item.getSimpleName().endsWith("IT")) {
                    events.add(SimpleConditionEvent.violated(
                            item,
                            "Класс " + item.getFullName() + " — integration-тест "
                                    + "(Spring context / Testcontainers), должен оканчиваться "
                                    + "на \"IT\" (сейчас суффикс не соответствует)."));
                }
            }
        };
    }
}
