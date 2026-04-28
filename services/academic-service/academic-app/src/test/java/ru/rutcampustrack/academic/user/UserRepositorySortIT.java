package ru.rutcampustrack.academic.user;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.transaction.annotation.Transactional;
import ru.rutcampustrack.academic.contract.enums.AccountStatus;
import ru.rutcampustrack.academic.contract.enums.UserRole;
import ru.rutcampustrack.academic.entity.Group;
import ru.rutcampustrack.academic.entity.User;
import ru.rutcampustrack.academic.integration.AbstractAcademicIntegrationTest;
import ru.rutcampustrack.academic.repository.GroupRepository;
import ru.rutcampustrack.academic.repository.UserRepository;

import java.time.OffsetDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies findByGroupId / findByRole return users in Cyrillic-alphabetical
 * order via the ru_icu collation (V21 + V22). Without ICU, default C-locale
 * sort puts «Ё» after «Я»; with ICU it lands between «Е» and «Ж».
 *
 * <p>Insertion order in this test is intentionally NOT alphabetical, so a
 * regression to heap-scan ordering would put «Янов» first.
 */
@Transactional
class UserRepositorySortIT extends AbstractAcademicIntegrationTest {

    @Autowired
    private UserRepository userRepository;
    @Autowired
    private GroupRepository groupRepository;

    @Test
    void findByGroupId_returnsCyrillicSortedOrder() {
        Long groupId = createGroup("СОРТ-021");

        // Insert deliberately out of order: Я → А → Ё → И. Heap scan would
        // preserve this; ICU ORDER BY must yield А, Ё, И, Я.
        insertStudent(groupId, "sort_yanov",   "Янов",    "Ян",    "Янович",   1001L);
        insertStudent(groupId, "sort_abramov", "Абрамов", "Антон", "Андреевич", 1002L);
        insertStudent(groupId, "sort_yozhik",  "Ёжиков",  "Ефим",  "Егорович",  1003L);
        insertStudent(groupId, "sort_ivanov",  "Иванов",  "Иван",  "Иванович",  1004L);

        List<User> students = userRepository.findByGroupId(groupId);

        assertThat(students)
                .extracting(User::getLastName)
                .containsExactly("Абрамов", "Ёжиков", "Иванов", "Янов");
    }

    @Test
    void findByGroupIdPageable_returnsCyrillicSortedOrder() {
        Long groupId = createGroup("СОРТ-022");

        insertStudent(groupId, "p_yanov",   "Янов",    "Ян",    "Янович",   2001L);
        insertStudent(groupId, "p_abramov", "Абрамов", "Антон", "Андреевич", 2002L);
        insertStudent(groupId, "p_yozhik",  "Ёжиков",  "Ефим",  "Егорович",  2003L);
        insertStudent(groupId, "p_ivanov",  "Иванов",  "Иван",  "Иванович",  2004L);

        Pageable pageable = PageRequest.of(0, 50);
        List<User> students = userRepository.findByGroupId(groupId, pageable).getContent();

        assertThat(students)
                .extracting(User::getLastName)
                .containsExactly("Абрамов", "Ёжиков", "Иванов", "Янов");
    }

    @Test
    void findByGroupId_excludesArchivedAndSorts() {
        Long groupId = createGroup("СОРТ-023");

        insertStudent(groupId, "arch_active1", "Богданов", "Борис", "Борисович", 3001L);
        Long archivedId = insertStudent(groupId, "arch_archived", "Абрамов", "Антон", "Андреевич", 3002L);
        insertStudent(groupId, "arch_active2", "Васильев", "Виктор", "Витальевич", 3003L);

        // Archive Абрамов — он не должен попасть в выдачу.
        userRepository.findById(archivedId).ifPresent(u -> {
            u.setStatus(AccountStatus.ARCHIVED);
            userRepository.save(u);
        });

        List<User> students = userRepository.findByGroupId(groupId);

        assertThat(students)
                .extracting(User::getLastName)
                .containsExactly("Богданов", "Васильев");
    }

    private Long createGroup(String name) {
        Group g = new Group();
        g.setName(name);
        g.setActive(true);
        g.setCreatedAt(OffsetDateTime.now());
        return groupRepository.save(g).getId();
    }

    private Long insertStudent(Long groupId, String login, String lastName, String firstName,
                               String middleName, Long telegramId) {
        User u = new User();
        u.setLogin(login);
        u.setPasswordHash("x");
        u.setLastName(lastName);
        u.setFirstName(firstName);
        u.setMiddleName(middleName);
        u.setRole(UserRole.STUDENT);
        u.setStatus(AccountStatus.ACTIVE);
        u.setTelegramId(telegramId);
        u.setGroupId(groupId);
        u.setHeadman(false);
        u.setPasswordChanged(false);
        OffsetDateTime now = OffsetDateTime.now();
        u.setCreatedAt(now);
        u.setUpdatedAt(now);
        return userRepository.save(u).getId();
    }
}
