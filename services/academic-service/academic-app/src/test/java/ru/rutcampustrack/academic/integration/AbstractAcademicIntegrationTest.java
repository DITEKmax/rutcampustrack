package ru.rutcampustrack.academic.integration;

import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestExecutionListeners;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.PostgreSQLContainer;

import ru.rutcampustrack.shared.outbox.OutboxEventSender;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Import(InternalJwtTestConfig.class)
@TestExecutionListeners(
        listeners = AcademicFixtureResetListener.class,
        mergeMode = TestExecutionListeners.MergeMode.MERGE_WITH_DEFAULTS)
public abstract class AbstractAcademicIntegrationTest {

    @org.springframework.beans.factory.annotation.Autowired
    protected AcademicIdentityIntegrationFixture identityFixture;

    protected org.springframework.test.web.servlet.request.RequestPostProcessor signedAdmin() {
        return AcademicTestAdminRequest.signed(identityFixture);
    }

    @MockitoBean RabbitTemplate rabbitTemplate;

    @MockitoBean OutboxEventSender outboxEventSender;

    static final AcademicIdentityIntegrationFixture IDENTITY_FIXTURE =
            AcademicIdentityIntegrationFixture.start();
    static final PostgreSQLContainer<?> POSTGRES = IDENTITY_FIXTURE.postgres();

    @DynamicPropertySource
    static void overrideProperties(DynamicPropertyRegistry registry) {
        IDENTITY_FIXTURE.properties(registry, "ordinary");
        registry.add(
                "spring.autoconfigure.exclude",
                () ->
                        "org.springframework.boot.autoconfigure.amqp.RabbitAutoConfiguration,"
                            + "org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration,"
                            + "org.springframework.boot.autoconfigure.data.redis.RedisRepositoriesAutoConfiguration");
    }
}
