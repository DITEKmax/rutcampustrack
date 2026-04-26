package ru.rutcampustrack.academic.arch;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaField;
import com.tngtech.archunit.core.domain.JavaMethod;
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
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;

import java.util.Collection;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;

/**
 * M05 Группа 2 — NEW-143: preventive N+1 guard для academic-service.
 * Идентичный правилам в schedule-service — см. javadoc
 * {@code schedule/arch/RepositoryNPlusOneGuardTest} для полного описания.
 */
@AnalyzeClasses(packages = "ru.rutcampustrack.academic",
        importOptions = {ImportOption.DoNotIncludeTests.class})
class RepositoryNPlusOneGuardTest {

    @ArchTest
    static final ArchRule entitiesMustNotUseJpaRelations =
            classes()
                    .that().areAnnotatedWith(Entity.class)
                    .should(notHaveJpaRelationFields())
                    .because("v0.0.0 convention: FK как Long, без "
                            + "@ManyToOne/@OneToMany. См. docs/architecture/architecture.md (NEW-143).");

    @ArchTest
    static final ArchRule repositoriesReturningCollectionsMustGuardNPlusOne =
            methods()
                    .that().areDeclaredInClassesThat().areAssignableTo(Repository.class)
                    .should(beGuardedIfReturnsCollectionAndElementHasRelations())
                    .because("NEW-143: repository-метод, возвращающий коллекцию entity с "
                            + "JPA relations, должен иметь Pageable / @EntityGraph / "
                            + "*Projection return type / JOIN FETCH.");

    static ArchCondition<JavaClass> notHaveJpaRelationFields() {
        return new ArchCondition<>("не иметь @ManyToOne/@OneToMany/@OneToOne/@ManyToMany полей") {
            @Override
            public void check(JavaClass clazz, ConditionEvents events) {
                for (JavaField field : clazz.getFields()) {
                    if (hasAnyRelation(field)) {
                        events.add(SimpleConditionEvent.violated(clazz,
                                "Поле " + field.getFullName()
                                        + " помечено JPA relation. Project-wide convention — "
                                        + "FK как Long. См. NEW-143 + docs/architecture/architecture.md."));
                    }
                }
            }
        };
    }

    static ArchCondition<JavaMethod> beGuardedIfReturnsCollectionAndElementHasRelations() {
        return new ArchCondition<>("be guarded (Pageable | @EntityGraph | *Projection | JOIN FETCH) "
                + "если return type — коллекция и в домене есть entity с relations") {
            @Override
            public void check(JavaMethod method, ConditionEvents events) {
                if (!method.getRawReturnType().isAssignableTo(Collection.class)) return;
                if (!anyEntityHasRelations(method.getOwner())) return;
                if (isGuarded(method)) return;
                events.add(SimpleConditionEvent.violated(method,
                        "Метод " + method.getFullName()
                                + " возвращает коллекцию, а в пакете появились entity с "
                                + "JPA relations. Добавьте Pageable / @EntityGraph / "
                                + "projection / JOIN FETCH."));
            }
        };
    }

    static boolean anyEntityHasRelations(JavaClass contextClass) {
        return contextClass.getPackage().getClasses().stream()
                .filter(c -> c.isAnnotatedWith(Entity.class))
                .flatMap(c -> c.getFields().stream())
                .anyMatch(RepositoryNPlusOneGuardTest::hasAnyRelation);
    }

    static boolean hasAnyRelation(CanBeAnnotated element) {
        return element.isAnnotatedWith(ManyToOne.class)
                || element.isAnnotatedWith(OneToMany.class)
                || element.isAnnotatedWith(ManyToMany.class)
                || element.isAnnotatedWith(OneToOne.class);
    }

    static boolean isGuarded(JavaMethod method) {
        for (JavaClass paramType : method.getRawParameterTypes()) {
            if (paramType.isAssignableTo(Pageable.class)) return true;
        }
        if (method.isAnnotatedWith(EntityGraph.class)) return true;
        if (method.getRawReturnType().getSimpleName().endsWith("Projection")) return true;
        var queryAnn = method.tryGetAnnotationOfType(Query.class.getName());
        if (queryAnn.isPresent()) {
            Object val = queryAnn.get().get("value").orElse("");
            String q = val == null ? "" : val.toString().toLowerCase();
            if (q.contains("join fetch")) return true;
            Object nativeFlag = queryAnn.get().get("nativeQuery").orElse(Boolean.FALSE);
            if (Boolean.TRUE.equals(nativeFlag) && q.contains(" join ")) return true;
        }
        return false;
    }
}
