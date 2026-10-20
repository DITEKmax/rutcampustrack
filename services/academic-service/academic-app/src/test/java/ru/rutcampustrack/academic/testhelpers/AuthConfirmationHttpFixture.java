package ru.rutcampustrack.academic.testhelpers;

import org.springframework.boot.web.embedded.tomcat.TomcatServletWebServerFactory;
import org.springframework.boot.web.server.WebServer;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.servlet.DispatcherServlet;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

import ru.rutcampustrack.academic.integration.AcademicIdentityIntegrationFixture;
import ru.rutcampustrack.auth.controller.InternalSemesterDeletionConfirmationController;
import ru.rutcampustrack.auth.exception.SemesterDeletionConfirmationExceptionHandler;
import ru.rutcampustrack.auth.security.InternalIssuerSecretFilter;

import java.net.InetAddress;

/**
 * Loopback servlet transport for the real Auth password handler and its actual Redis/JDBC service.
 */
public final class AuthConfirmationHttpFixture implements AutoCloseable {
    @Configuration(proxyBeanMethods = false)
    @EnableWebMvc
    static class Mvc {}

    private final WebServer server;

    public AuthConfirmationHttpFixture(AcademicIdentityIntegrationFixture identity) {
        var factory = new TomcatServletWebServerFactory(0);
        factory.setAddress(InetAddress.getLoopbackAddress());
        server =
                factory.getWebServer(
                        servlet -> {
                            var context = new AnnotationConfigWebApplicationContext();
                            context.register(
                                    Mvc.class,
                                    InternalSemesterDeletionConfirmationController.class,
                                    SemesterDeletionConfirmationExceptionHandler.class);
                            context.addBeanFactoryPostProcessor(
                                    beans ->
                                            beans.registerSingleton(
                                                    "semesterDeletionConfirmationService",
                                                    identity.semesterDeletionConfirmation()));
                            var dispatcher =
                                    servlet.addServlet(
                                            "auth-confirmation", new DispatcherServlet(context));
                            dispatcher.setLoadOnStartup(1);
                            dispatcher.addMapping("/");
                            servlet.addFilter(
                                            "internal-issuer",
                                            new InternalIssuerSecretFilter(
                                                    identity.internalIssuerProperties()))
                                    .addMappingForUrlPatterns(
                                            java.util.EnumSet.of(
                                                    jakarta.servlet.DispatcherType.REQUEST),
                                            false,
                                            "/internal/*");
                        });
        server.start();
    }

    public String url() {
        return "http://localhost:" + server.getPort();
    }

    @Override
    public void close() {
        server.stop();
    }
}
