package ru.rutcampustrack.academic.arch;

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
 * M05 Группа 2 — NEW-143: preventive N+1 guard для academic-service. Идентичный правилам в
 * schedule-service — см. javadoc {@code schedule/arch/RepositoryNPlusOneGuardTest} для полного
 * описания.
 */
@AnalyzeClasses(
        packages = "ru.rutcampustrack.academic",
        importOptions = {ImportOption.DoNotIncludeTests.class})
class RepositoryNPlusOneGuardTest {

    @ArchTest
    static final ArchRule entitiesMustNotUseJpaRelations =
            classes()
                    .that()
                    .areAnnotatedWith(Entity.class)
                    .should(notHaveJpaRelationFields())
                    .because(
                            "v0.0.0 convention: FK как Long, без @ManyToOne/@OneToMany. См."
                                + " docs/architecture/architecture.md (NEW-143).");

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
                                                + " помечено JPA relation. Project-wide convention"
                                                + " — FK как Long. См. NEW-143 +"
                                                + " docs/architecture/architecture.md."));
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
