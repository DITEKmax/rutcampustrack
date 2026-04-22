package ru.rutcampustrack.academic.golden;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import ru.rutcampustrack.academic.entity.User;

import java.util.concurrent.ThreadLocalRandom;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * M08 Группа 4 (P2-8/4) — property-based tests для {@link User#getDisplayName()}.
 *
 * <p>Invariants:
 * <ol>
 *   <li><b>nameFormatterStable</b> — call getDisplayName() 100 раз на одном
 *       user'е даёт тот же результат (no side-effects, deterministic).</li>
 *   <li><b>lastNameAlwaysFirst</b> — формат строго «lastName firstName ...»,
 *       lastName — первый токен через split(' ').</li>
 *   <li><b>noMiddleNameIfBlank</b> — null/""/whitespace middleName → result НЕ
 *       содержит trailing space.</li>
 * </ol>
 */
class DisplayNamePropertyTest {

    private User userOf(String first, String last, String middle) {
        User u = new User();
        u.setFirstName(first);
        u.setLastName(last);
        u.setMiddleName(middle);
        return u;
    }

    @Test
    @DisplayName("nameFormatterStable — 1000 calls → same result")
    void nameFormatterStable() {
        User user = userOf("Иван", "Иванов", "Иванович");
        String first = user.getDisplayName();
        for (int i = 0; i < 1000; i++) {
            assertThat(user.getDisplayName()).isEqualTo(first);
        }
    }

    @RepeatedTest(50)
    @DisplayName("lastNameAlwaysFirst — первый токен = lastName (random input)")
    void lastNameAlwaysFirst() {
        String last = randomName("Фамил");
        String first = randomName("Им");
        String middle = ThreadLocalRandom.current().nextBoolean() ? randomName("Отч") : null;
        User user = userOf(first, last, middle);

        String display = user.getDisplayName();
        String[] tokens = display.split(" ");

        assertThat(tokens[0])
                .as("Первый токен displayName должен быть lastName")
                .isEqualTo(last);
        assertThat(tokens[1])
                .as("Второй токен displayName должен быть firstName")
                .isEqualTo(first);
    }

    @RepeatedTest(50)
    @DisplayName("noMiddleNameIfBlank — null/blank middleName → no trailing space")
    void noMiddleNameIfBlank() {
        String blank = switch (ThreadLocalRandom.current().nextInt(3)) {
            case 0 -> null;
            case 1 -> "";
            default -> "   ";
        };
        User user = userOf("Анна", "Петрова", blank);

        String display = user.getDisplayName();
        assertThat(display)
                .as("Displayname с blank middleName не должно иметь trailing spaces")
                .doesNotEndWith(" ")
                .isEqualTo("Петрова Анна");
    }

    private String randomName(String prefix) {
        int len = ThreadLocalRandom.current().nextInt(2, 12);
        StringBuilder sb = new StringBuilder(prefix);
        for (int i = 0; i < len; i++) {
            // Буквы кириллицы без пробелов
            sb.append((char) ('а' + ThreadLocalRandom.current().nextInt(32)));
        }
        return sb.toString();
    }
}
