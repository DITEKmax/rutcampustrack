package ru.rutcampustrack.schedule.arch;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaField;
import com.tngtech.archunit.core.domain.properties.CanBeAnnotated;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;

import jakarta.persistence.Entity;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;

/**
 * M05 Группа 2 — NEW-143: preventive N+1 guard.
 *
 * <p>Правило срабатывает, как только кто-то добавит JPA relation ({@code @ManyToOne},
 * {@code @OneToMany}, {@code @OneToOne}, {@code @ManyToMany}) в entity. Текущий baseline v0.0.0 —
 * <b>все entity без relations</b>, FK как {@code Long} (convention из фаз 1.x–9.x). Правило при
 * этом passes — «all green» маркер архитектурного инварианта.
 *
 * <p><b>Зачем тест:</b> если завтра разработчик добавит {@code @ManyToOne Subject subject} в {@link
 * ru.rutcampustrack.schedule.item.entity.ScheduleItem}, build упадёт и направит к принятию
 * сознательного решения:
 *
 * <ul>
 *   <li>оставить FK как Long (vote +1 в существующую convention), или
 *   <li>добавить @EntityGraph / projection / JOIN FETCH для hot-path repo-методов.
 * </ul>
 *
 * <p>Reference-pattern для projection — {@code LessonDetailsProjection} в пакете {@code
 * lesson/projection/}.
 *
 * <p>Entity без JPA relations — действующий архитектурный инвариант.
 */
@AnalyzeClasses(
        packages = "ru.rutcampustrack.schedule",
        importOptions = {ImportOption.DoNotIncludeTests.class})
class RepositoryNPlusOneGuardTest {

    /**
     * Baseline invariant: все JPA entity используют FK как Long, без relations. Если при добавлении
     * relation разработчик осознанно снимает invariant — удалите этот тест и активируйте
     * полноценный guard по каждому методу.
     */
    @ArchTest
    static final ArchRule entitiesMustNotUseJpaRelations =
            classes()
                    .that()
                    .areAnnotatedWith(Entity.class)
                    .should(notHaveJpaRelationFields())
                    .because(
                            "v0.0.0 convention: FK как Long, без @ManyToOne/@OneToMany. "
                                    + "Причина — прозрачный SQL, нет lazy surprises, лёгкое "
                                    + "пересечение gRPC-границ (FK в soседний сервис). "
                                    + "Если необходима relation — см. NEW-143 runbook в "
                                    + "docs/architecture/architecture.md.");

    static ArchCondition<JavaClass> notHaveJpaRelationFields() {
        return new ArchCondition<>("не иметь @ManyToOne/@OneToMany/@OneToOne/@ManyToMany полей") {
            @Override
            public void check(JavaClass clazz, ConditionEvents events) {
                for (JavaField field : clazz.getFields()) {
                    if (hasAnyRelation(field)) {
                        events.add(
                                SimpleConditionEvent.violated(
                                        clazz,
                                        "Поле "
                                                + field.getFullName()
                                                + " помечено JPA relation"
                                                + " (@ManyToOne/OneToMany/OneToOne/ManyToMany)."
                                                + " Project-wide convention — FK как Long. См."
                                                + " NEW-143 + docs/architecture/architecture.md."));
                    }
                }
            }
        };
    }

    static boolean hasAnyRelation(CanBeAnnotated element) {
        return element.isAnnotatedWith(ManyToOne.class)
                || element.isAnnotatedWith(OneToMany.class)
                || element.isAnnotatedWith(ManyToMany.class)
                || element.isAnnotatedWith(OneToOne.class);
    }
}
