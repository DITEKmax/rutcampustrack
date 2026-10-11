package ru.rutcampustrack.academic.integration;

import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.TestExecutionListeners;
import org.testcontainers.containers.PostgreSQLContainer;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Import(InternalJwtTestConfig.class)
@TestExecutionListeners(listeners = AcademicFixtureResetListener.class,
        mergeMode = TestExecutionListeners.MergeMode.MERGE_WITH_DEFAULTS)
public abstract class AbstractAcademicIntegrationTest {

    @org.springframework.beans.factory.annotation.Autowired
    private javax.sql.DataSource fixtureDataSource;
    @org.springframework.beans.factory.annotation.Autowired
    private ru.rutcampustrack.shared.security.InternalJwtTestFactory fixtureTokenFactory;

    protected org.springframework.test.web.servlet.request.RequestPostProcessor signedAdmin() {
        return AcademicTestAdminRequest.signed(new org.springframework.jdbc.core.JdbcTemplate(fixtureDataSource), fixtureTokenFactory);
    }

    @MockitoBean
    RabbitTemplate rabbitTemplate;

    static final PostgreSQLContainer<?> POSTGRES;

    static {
        // Only this fresh resource may be reset by AcademicFixtureResetListener.
        POSTGRES = new PostgreSQLContainer<>("postgres:16")
                .withDatabaseName("academic_ci_" + java.util.UUID.randomUUID().toString().replace("-", ""))
                .withUsername("rct_user")
                .withPassword("rct_dev_pass")
                .withReuse(false);
        POSTGRES.start();
    }

    @DynamicPropertySource
    static void overrideProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.autoconfigure.exclude",
                () -> "org.springframework.boot.autoconfigure.amqp.RabbitAutoConfiguration," +
                      "org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration," +
                      "org.springframework.boot.autoconfigure.data.redis.RedisRepositoriesAutoConfiguration");
    }
}
