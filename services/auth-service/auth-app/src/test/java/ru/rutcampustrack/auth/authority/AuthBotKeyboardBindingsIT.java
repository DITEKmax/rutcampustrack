package ru.rutcampustrack.auth.authority;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.grpc.Status;
import io.grpc.StatusRuntimeException;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;

import ru.rutcampustrack.auth.support.AuthDatabaseFixture;
import ru.rutcampustrack.authauthority.grpc.ListBotKeyboardBindingsRequest;

import java.time.Instant;
import java.util.ArrayList;

/** Narrow catalogue discovery uses real Auth tables and excludes all profile/secret fields. */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AuthBotKeyboardBindingsIT {
    private final PostgreSQLContainer<?> database =
            new PostgreSQLContainer<>("postgres:16")
                    .withDatabaseName("auth_keyboard_1109")
                    .withUsername("keyboard")
                    .withPassword("synthetic-keyboard-1109")
                    .withReuse(false);
    private JdbcTemplate jdbc;
    private AuthBotAuthorityQuery query;

    @BeforeAll
    void start() {
        String nativeUrl = System.getenv("RCT_BOT_KEYBOARD_NATIVE_JDBC_URL");
        String url;
        String user;
        String password;
        if (nativeUrl != null) {
            if (!nativeUrl.matches(
                    "jdbc:postgresql://127\\.0\\.0\\.1:15439/rct_bot_auth_1109_[a-z0-9_]+"))
                throw new IllegalArgumentException("Dedicated loopback Auth fixture required");
            url = nativeUrl;
            user = "rct_bot_auth_1109";
            password = ""; // Dedicated local trust-only fixture; no production credentials.
        } else {
            database.start();
            url = database.getJdbcUrl();
            user = database.getUsername();
            password = database.getPassword();
        }
        Flyway.configure()
                .dataSource(url, user, password)
                .locations("filesystem:" + AuthDatabaseFixture.migrations())
                .load()
                .migrate();
        jdbc = new JdbcTemplate(new DriverManagerDataSource(url, user, password));
        query = new AuthBotAuthorityQuery(jdbc);
    }

    @AfterAll
    void stop() {
        if (database.isRunning()) database.stop();
    }

    @Test
    void paginatedSweepCoversAllBoundIncludingFencedAndNewBindingsOnNextSweep() {
        var expected = new ArrayList<Long>();
        for (int i = 0; i < 203; i++) {
            long id =
                    AuthDatabaseFixture.identity(
                            jdbc, "keyboard-" + i, Instant.parse("2026-11-09T00:00:00Z"));
            if (i == 2) continue; // identity has no Telegram binding
            jdbc.update("UPDATE auth_identities SET telegram_id=? WHERE id=?", 100000 + id, id);
            expected.add(id);
            if (i == 3) {
                jdbc.update(
                        "INSERT INTO user_role_grants(user_id,role,status,group_id) VALUES"
                            + " (?,'student','active',9981)",
                        id);
                assertThat(query.find(100000 + id, 100000 + id)).isNotNull();
                AuthDatabaseFixture.fence(jdbc, id); // discovery != menu authority
            }
        }
        var first = query.listKeyboardBindings(request(0, 0, 200));
        assertThat(first.getBindingsCount()).isEqualTo(200);
        assertThat(first.getNextAfterUserId()).isEqualTo(expected.get(199));
        long late =
                AuthDatabaseFixture.identity(
                        jdbc, "keyboard-late", Instant.parse("2026-11-09T00:00:00Z"));
        jdbc.update("UPDATE auth_identities SET telegram_id=? WHERE id=?", 100000 + late, late);
        var second =
                query.listKeyboardBindings(
                        request(first.getNextAfterUserId(), first.getThroughUserId(), 200));
        assertThat(second.getBindingsCount()).isEqualTo(2);
        assertThat(second.getNextAfterUserId()).isZero();
        var found = new ArrayList<Long>();
        first.getBindingsList().forEach(b -> found.add(b.getUserId()));
        second.getBindingsList().forEach(b -> found.add(b.getUserId()));
        assertThat(found).containsExactlyElementsOf(expected);
        assertThat(first.getBindings(0).getTelegramId()).isEqualTo(100000 + expected.getFirst());
        assertThat(first.getBindings(0).getTelegramBindingVersion()).isEqualTo(2);
        assertThat(first.getBindings(0).getRolesVersion()).isPositive();
        assertThat(first.getBindingsList())
                .anyMatch(b -> b.getUserId() == expected.get(2) && b.getRolesVersion() == 2);
        // Fenced or no-active-grant menu reads remain unavailable, not a missing binding.
        assertThat(query.find(100000 + expected.get(2), 100000 + expected.get(2))).isNull();
        var refresh = query.listKeyboardBindings(request(first.getNextAfterUserId(), late, 200));
        assertThat(refresh.getBindingsList()).anyMatch(b -> b.getUserId() == late);
        long rebound = expected.getLast();
        jdbc.update(
                "UPDATE auth_identities SET telegram_id=? WHERE id=?", 200000 + rebound, rebound);
        var reboundPage = query.listKeyboardBindings(request(rebound - 1, late, 200));
        assertThat(reboundPage.getBindingsList())
                .anyMatch(
                        b ->
                                b.getUserId() == rebound
                                        && b.getTelegramId() == 200000 + rebound
                                        && b.getTelegramBindingVersion() == 3);
        jdbc.update("UPDATE auth_identities SET telegram_id=NULL WHERE id=?", expected.getFirst());
        var current = query.listKeyboardBindings(request(0, 0, 200));
        assertThat(current.getBindingsList()).noneMatch(b -> b.getUserId() == expected.getFirst());
    }

    @Test
    void rejectsUnboundedOrInvalidCursorRequests() {
        for (var request :
                new ListBotKeyboardBindingsRequest[] {
                    request(0, 0, 0), request(0, 0, 201), request(-1, 0, 10), request(20, 10, 10)
                }) {
            assertThatThrownBy(() -> query.listKeyboardBindings(request))
                    .isInstanceOfSatisfying(
                            StatusRuntimeException.class,
                            e ->
                                    assertThat(e.getStatus().getCode())
                                            .isEqualTo(Status.Code.INVALID_ARGUMENT));
        }
    }

    private static ListBotKeyboardBindingsRequest request(long after, long upper, int limit) {
        return ListBotKeyboardBindingsRequest.newBuilder()
                .setAfterUserId(after)
                .setThroughUserId(upper)
                .setPageSize(limit)
                .build();
    }
}
