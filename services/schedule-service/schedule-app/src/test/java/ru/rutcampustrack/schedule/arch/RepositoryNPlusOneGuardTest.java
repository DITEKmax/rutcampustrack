package ru.rutcampustrack.schedule.arch;

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
 * M05 Группа 2 — NEW-143: preventive N+1 guard.
 *
 * <p>Правило срабатывает, как только кто-то добавит JPA relation
 * ({@code @ManyToOne}, {@code @OneToMany}, {@code @OneToOne},
 * {@code @ManyToMany}) в entity. Текущий baseline v0.0.0 — <b>все entity без
 * relations</b>, FK как {@code Long} (convention из фаз 1.x–9.x). Правило при
 * этом passes — «all green» маркер архитектурного инварианта.
 *
 * <p><b>Зачем тест:</b> если завтра разработчик добавит
 * {@code @ManyToOne Subject subject} в {@link ru.rutcampustrack.schedule.item.entity.ScheduleItem},
 * build упадёт и направит к принятию сознательного решения:
 * <ul>
 *   <li>оставить FK как Long (vote +1 в существующую convention), или</li>
 *   <li>добавить @EntityGraph / projection / JOIN FETCH для hot-path repo-методов.</li>
 * </ul>
 *
 * <p>Reference-pattern для projection — {@code LessonDetailsProjection} в
 * пакете {@code lesson/projection/}.
 *
 * <p>Состоит из двух подправил:
 * <ol>
 *   <li>{@link #entitiesMustNotUseJpaRelations} — baseline convention. Entity
 *       без relations — архитектурный инвариант v0.0.0.
 *   <li>{@link #repositoriesReturningCollectionsMustGuardNPlusOne} — если
 *       invariant когда-то будет нарушен (breaking-change в entity), то
 *       repository должен guard'ить.
 * </ol>
 */
@AnalyzeClasses(packages = "ru.rutcampustrack.schedule",
        importOptions = {ImportOption.DoNotIncludeTests.class})
class RepositoryNPlusOneGuardTest {

    /**
     * Baseline invariant: все JPA entity используют FK как Long, без relations.
     * Если при добавлении relation разработчик осознанно снимает invariant —
     * удалите этот тест и активируйте полноценный guard по каждому методу.
     */
    @ArchTest
    static final ArchRule entitiesMustNotUseJpaRelations =
            classes()
                    .that().areAnnotatedWith(Entity.class)
                    .should(notHaveJpaRelationFields())
                    .because(
                            "v0.0.0 convention: FK как Long, без @ManyToOne/@OneToMany. "
                                    + "Причина — прозрачный SQL, нет lazy surprises, лёгкое "
                                    + "пересечение gRPC-границ (FK в soседний сервис). "
                                    + "Если необходима relation — см. NEW-143 runbook в "
                                    + "docs/architecture.md.");

    /**
     * Guard для будущего: если в entity появится relation, repository-метод,
     * возвращающий коллекцию этой entity, должен защищаться.
     *
     * <p>Фильтр «return type is Collection» применяется прямо в condition —
     * DescribedPredicate не functional interface в ArchUnit 1.x, а hand-
     * written helper даёт то же самое без лишнего boilerplate'а.
     */
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
                                        + " помечено JPA relation (@ManyToOne/OneToMany/"
                                        + "OneToOne/ManyToMany). Project-wide convention — "
                                        + "FK как Long. См. NEW-143 + docs/architecture.md."));
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
                // Фильтр: только методы с return type Collection<*>.
                if (!method.getRawReturnType().isAssignableTo(Collection.class)) return;

                // Пока все entity без relations — этот метод гарантировано safe.
                // Когда появится первая relation — rule активируется.
                if (!anyEntityHasRelations(method.getOwner())) return;

                if (isGuarded(method)) return;
                events.add(SimpleConditionEvent.violated(method,
                        "Метод " + method.getFullName()
                                + " возвращает коллекцию, а в пакете появились entity с "
                                + "JPA relations. Добавьте Pageable / @EntityGraph / "
                                + "projection / JOIN FETCH. См. LessonDetailsProjection как пример."));
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
