package ru.rutcampustrack.schedule.migration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import org.hibernate.boot.MetadataSources;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;
import org.hibernate.tool.schema.spi.SchemaManagementException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.testcontainers.containers.PostgreSQLContainer;
import ru.rutcampustrack.schedule.event.ScheduleEventConsumerProcessedEntity;

/** Regression for production's restricted runtime role resolving current_schema() to pg_catalog. */
class RuntimeSchemaValidationIT {

    @Test
    void validatesPublicTablesWithRuntimeSearchPathBeforeHibernateStarts() throws Exception {
        try (var postgres =
                new PostgreSQLContainer<>("postgres:16")
                        .withDatabaseName("schedule_runtime_schema")
                        .withUsername("schema_owner")
                        .withPassword("synthetic-owner-password")) {
            postgres.start();
            var owner =
                    new DriverManagerDataSource(
                            postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
            try (Connection connection = owner.getConnection()) {
                ScriptUtils.executeSqlScript(
                        connection,
                        new ClassPathResource("db/migration/V14__event_consumer_processed.sql"));
            }
            var ownerJdbc = new JdbcTemplate(owner);
            ownerJdbc.execute(
                    "CREATE ROLE rct_runtime LOGIN PASSWORD 'synthetic-runtime-password'"
                            + " NOSUPERUSER NOCREATEDB NOCREATEROLE NOINHERIT");
            ownerJdbc.execute("ALTER ROLE rct_runtime SET search_path = pg_catalog, public");
            ownerJdbc.execute("REVOKE CREATE ON SCHEMA public FROM PUBLIC");
            ownerJdbc.execute("GRANT USAGE ON SCHEMA public TO rct_runtime");
            ownerJdbc.execute(
                    "GRANT SELECT, INSERT, UPDATE, DELETE ON public.event_consumer_processed"
                            + " TO rct_runtime");

            // Authenticate as runtime before any Hibernate registry/metadata/session factory
            // exists.
            var runtime =
                    new DriverManagerDataSource(
                            postgres.getJdbcUrl(), "rct_runtime", "synthetic-runtime-password");
            assertRuntimeNamespaceAndPrivileges(runtime);

            var yaml = new YamlPropertiesFactoryBean();
            yaml.setResources(new ClassPathResource("application.yml"));
            var delivered = yaml.getObject();
            assertThat(delivered).isNotNull();
            Map<String, Object> settings = new HashMap<>();
            String prefix = "spring.jpa.properties.";
            delivered.forEach(
                    (key, value) -> {
                        if (key.toString().startsWith(prefix)) {
                            settings.put(key.toString().substring(prefix.length()), value);
                        }
                    });
            assertThat(delivered.getProperty("spring.jpa.hibernate.ddl-auto"))
                    .isEqualTo("validate");
            settings.put(
                    "hibernate.hbm2ddl.auto",
                    delivered.getProperty("spring.jpa.hibernate.ddl-auto"));
            settings.put("hibernate.connection.datasource", runtime);

            var original = new HashMap<>(settings);
            original.remove("hibernate.default_schema");
            assertThatThrownBy(() -> validate(original))
                    .isInstanceOf(SchemaManagementException.class)
                    .hasMessageContaining("missing table [event_consumer_processed]");
            System.out.println("ORIGINAL_GROUPED_VALIDATION=EXPECTED_MISSING_TABLE");

            // This must depend on delivered application.yml, not a test-supplied public override.
            validate(settings);
            System.out.println("DELIVERED_CONFIG_VALIDATION=PASS");
            assertRuntimeNamespaceAndPrivileges(runtime);
        }
    }

    private static void validate(Map<String, Object> settings) {
        var registry = new StandardServiceRegistryBuilder().applySettings(settings).build();
        try {
            try (var sessionFactory =
                    new MetadataSources(registry)
                            .addAnnotatedClass(ScheduleEventConsumerProcessedEntity.class)
                            .buildMetadata()
                            .buildSessionFactory()) {
                assertThat(sessionFactory.isOpen()).isTrue();
            }
        } finally {
            StandardServiceRegistryBuilder.destroy(registry);
        }
    }

    private static void assertRuntimeNamespaceAndPrivileges(DriverManagerDataSource runtime)
            throws SQLException {
        try (var connection = runtime.getConnection();
                var statement = connection.createStatement()) {
            assertThat(connection.getSchema()).isEqualTo("pg_catalog");
            try (var result =
                    statement.executeQuery(
                            "SELECT current_user, current_setting('search_path'),"
                                    + " to_regclass('public.event_consumer_processed') IS NOT NULL,"
                                    + " has_table_privilege(current_user,"
                                    + " 'public.event_consumer_processed', 'SELECT'),"
                                    + " has_schema_privilege(current_user, 'public', 'CREATE'),"
                                    + " rolsuper, rolcreatedb, rolcreaterole FROM pg_roles"
                                    + " WHERE rolname = current_user")) {
                assertThat(result.next()).isTrue();
                assertThat(result.getString(1)).isEqualTo("rct_runtime");
                assertThat(result.getString(2)).isEqualTo("pg_catalog, public");
                assertThat(result.getBoolean(3)).isTrue();
                assertThat(result.getBoolean(4)).isTrue();
                for (int column = 5; column <= 8; column++) {
                    assertThat(result.getBoolean(column)).isFalse();
                }
            }
            try (var result =
                    statement.executeQuery("SELECT count(*) FROM event_consumer_processed")) {
                assertThat(result.next()).isTrue();
                assertThat(result.getLong(1)).isZero();
            }
            assertThatThrownBy(
                            () ->
                                    statement.execute(
                                            "CREATE TABLE public.runtime_ddl_probe(id int)"))
                    .isInstanceOf(SQLException.class)
                    .satisfies(
                            error ->
                                    assertThat(((SQLException) error).getSQLState())
                                            .isEqualTo("42501"));
            System.out.println(
                    "RUNTIME_ROLE_PREFLIGHT=PASS current_schema=pg_catalog table_readable=true ddl_denied=true");
        }
    }
}
