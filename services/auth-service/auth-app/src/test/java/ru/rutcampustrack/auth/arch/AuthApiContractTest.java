package ru.rutcampustrack.auth.arch;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noMethods;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * M12 G4 / 01 P0-1 — Contract-first guardrail для auth-service.
 *
 * <p>Гарантирует что controllers в {@code ru.rutcampustrack.auth.controller}
 * не содержат HTTP mapping аннотаций ни на class-level, ни на method-level.
 * Все mappings должны быть ТОЛЬКО в {@code ru.rutcampustrack.auth.api.*Api}
 * interfaces из auth-api-contract модуля.</p>
 *
 * <p>Вторая проверка: каждый {@code @RestController} должен
 * {@code implements} хотя бы один interface — empty impl без контракта
 * означает структурный регрес.</p>
 */
@AnalyzeClasses(
        packages = "ru.rutcampustrack.auth.controller",
        importOptions = {ImportOption.DoNotIncludeTests.class})
class AuthApiContractTest {

    @ArchTest
    static final ArchRule controllersMustNotCarryClassLevelMapping =
            noClasses()
                    .that().resideInAPackage("..controller..")
                    .should().beAnnotatedWith(RequestMapping.class)
                    .because("M12: @RequestMapping принадлежит интерфейсу из auth-api-contract, не контроллеру");

    @ArchTest
    static final ArchRule controllerMethodsMustNotCarryMappings =
            noMethods()
                    .that().areDeclaredInClassesThat().resideInAPackage("..controller..")
                    .should().beAnnotatedWith(GetMapping.class)
                    .orShould().beAnnotatedWith(PostMapping.class)
                    .orShould().beAnnotatedWith(PutMapping.class)
                    .orShould().beAnnotatedWith(PatchMapping.class)
                    .orShould().beAnnotatedWith(DeleteMapping.class)
                    .orShould().beAnnotatedWith(RequestMapping.class)
                    .because("M12: method-level mappings принадлежат интерфейсу, не контроллеру");

    @ArchTest
    static final ArchRule restControllersMustImplementContractInterface =
            classes()
                    .that().areAnnotatedWith(RestController.class)
                    .and().resideInAPackage("..controller..")
                    .should().implement(ru.rutcampustrack.auth.api.AuthApi.class)
                    .orShould().implement(ru.rutcampustrack.auth.api.WsTicketApi.class)
                    .orShould().implement(ru.rutcampustrack.auth.api.InternalIssuerApi.class)
                    .orShould().implement(ru.rutcampustrack.auth.api.InternalWsTicketApi.class)
                    .because("M12 01 P0-1: каждый @RestController реализует interface из auth-api-contract");
}
