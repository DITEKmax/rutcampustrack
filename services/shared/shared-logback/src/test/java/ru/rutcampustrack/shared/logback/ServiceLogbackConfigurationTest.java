package ru.rutcampustrack.shared.logback;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.joran.JoranConfigurator;
import ch.qos.logback.core.status.Status;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.ResourceLock;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;

@ResourceLock("SYSTEM_OUT")
class ServiceLogbackConfigurationTest {
    private static final List<String> SERVICES =
            List.of(
                    "api-gateway",
                    "auth-service/auth-app",
                    "academic-service/academic-app",
                    "schedule-service/schedule-app",
                    "attendance-service/attendance-app",
                    "mobile-bff/mobile-bff-app",
                    "notification-service/notification-app");

    private static boolean includesShared(Path config) throws Exception {
        var factory = DocumentBuilderFactory.newInstance();
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
        var builder = factory.newDocumentBuilder();
        builder.setErrorHandler(
                new org.xml.sax.helpers.DefaultHandler() {
                    @Override
                    public void error(org.xml.sax.SAXParseException error)
                            throws org.xml.sax.SAXException {
                        throw error;
                    }

                    @Override
                    public void fatalError(org.xml.sax.SAXParseException error)
                            throws org.xml.sax.SAXException {
                        throw error;
                    }
                });
        var document = builder.parse(config.toFile());
        var includes = document.getElementsByTagName("include");
        for (int index = 0; index < includes.getLength(); index++) {
            if ("shared/logback-base.xml"
                    .equals(
                            includes.item(index).getAttributes().getNamedItem("resource") == null
                                    ? null
                                    : includes.item(index)
                                            .getAttributes()
                                            .getNamedItem("resource")
                                            .getNodeValue())) {
                return true;
            }
        }
        return false;
    }

    @Test
    void everyServiceLoadsRealJsonStdoutAndMasksSyntheticMessage() throws Exception {
        Path root = Path.of(System.getProperty("rct.repository.root", "../../.."));
        PrintStream original = System.out;
        for (String service : SERVICES) {
            Path config =
                    root.resolve("services/" + service + "/src/main/resources/logback-spring.xml");
            assertThat(includesShared(config))
                    .as("Actual XML shared include: %s", service)
                    .isTrue();
            var context = new LoggerContext();
            context.setMDCAdapter(new ch.qos.logback.classic.util.LogbackMDCAdapter());
            var captured = new ByteArrayOutputStream();
            try (var output = new PrintStream(captured, true, StandardCharsets.UTF_8)) {
                System.setOut(output);
                var configurator = new JoranConfigurator();
                configurator.setContext(context);
                configurator.doConfigure(config.toFile());
                assertThat(
                                context.getStatusManager().getCopyOfStatusList().stream()
                                        .noneMatch(
                                                status ->
                                                        status.getEffectiveLevel() >= Status.ERROR))
                        .as("Effective Logback configuration has no errors: %s", service)
                        .isTrue();
                context.getLogger("ru.rutcampustrack.gate")
                        .info("synthetic Bearer eySynthetic.secret.signature");
                output.flush();
                String text = captured.toString(StandardCharsets.UTF_8);
                boolean jsonMasked = false;
                try {
                    var lines = text.lines().filter(line -> !line.isBlank()).toList();
                    jsonMasked =
                            lines.size() == 1
                                    && new ObjectMapper()
                                            .readTree(lines.getFirst())
                                            .path("msg")
                                            .asText()
                                            .equals("synthetic Bearer ***")
                                    && !text.contains("eySynthetic.secret.signature");
                } catch (Exception ignored) {
                    // Report a closed boolean; never attach a captured log body to an assertion.
                }
                assertThat(jsonMasked)
                        .as("Service emits one masked JSON stdout event: %s", service)
                        .isTrue();
            } finally {
                System.setOut(original);
                context.stop();
            }
        }
    }

    @Test
    void commentedIncludeAndExternalEntitiesCannotSatisfyXmlGate(@TempDir Path directory)
            throws Exception {
        Path config = directory.resolve("logback-spring.xml");
        Files.writeString(
                config,
                "<configuration><!-- <include resource=\"shared/logback-base.xml\"/>"
                    + " --></configuration>");
        assertThat(includesShared(config)).isFalse();
        Files.writeString(
                config,
                "<!DOCTYPE configuration [<!ENTITY fake SYSTEM"
                    + " \"file:///never-read-synthetic\">]><configuration>&fake;</configuration>");
        boolean rejected = false;
        try {
            includesShared(config);
        } catch (org.xml.sax.SAXException expected) {
            rejected = true;
        }
        assertThat(rejected)
                .as("External entity configuration is rejected before resource access")
                .isTrue();
    }
}
