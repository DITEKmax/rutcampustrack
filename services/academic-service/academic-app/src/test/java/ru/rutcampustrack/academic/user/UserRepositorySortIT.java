package ru.rutcampustrack.academic.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import ru.rutcampustrack.academic.contract.dto.group.CreateGroupRequest;
import ru.rutcampustrack.academic.contract.dto.user.CreateUserRequest;
import ru.rutcampustrack.academic.contract.dto.user.UserArchiveModels.ArchiveRequest;
import ru.rutcampustrack.academic.contract.dto.user.UserArchiveModels.Preview;
import ru.rutcampustrack.academic.contract.enums.UserRole;
import ru.rutcampustrack.academic.entity.User;
import ru.rutcampustrack.academic.group.GroupService;
import ru.rutcampustrack.academic.grpc.AttendanceUserImpactGrpcClient;
import ru.rutcampustrack.academic.integration.AbstractAcademicIntegrationTest;
import ru.rutcampustrack.academic.repository.UserRepository;
import ru.rutcampustrack.attendance.grpc.UserImpactSnapshot;
import ru.rutcampustrack.auth.session.model.AuthRole;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Verifies findByGroupId / findByRole return users in Cyrillic-alphabetical order via the ru_icu
 * collation (V21 + V22). Without ICU, default C-locale sort puts «Ё» after «Я»; with ICU it lands
 * between «Е» and «Ж».
 *
 * <p>Insertion order in this test is intentionally NOT alphabetical, so a regression to heap-scan
 * ordering would put «Янов» first. Actual Auth/Academic lifecycle backs archive exclusion; only
 * Attendance impact transport is doubled.
 */
class UserRepositorySortIT extends AbstractAcademicIntegrationTest {

    @Autowired private UserRepository userRepository;
    @Autowired private GroupService groups;
    @Autowired private UserArchiveService archives;
    @MockitoBean private AttendanceUserImpactGrpcClient attendance;

    @Test
    void findByGroupId_returnsCyrillicSortedOrder() {
        Long groupId = createGroup("СОРТ-111");

        // Insert deliberately out of order: Я → А → Ё → И. Heap scan would
        // preserve this; ICU ORDER BY must yield А, Ё, И, Я.
        insertStudent(groupId, "Янов", "Ян", "Янович", 1001L);
        insertStudent(groupId, "Абрамов", "Антон", "Андреевич", 1002L);
        insertStudent(groupId, "Ёжиков", "Ефим", "Егорович", 1003L);
        insertStudent(groupId, "Иванов", "Иван", "Иванович", 1004L);

        List<User> students = userRepository.findByGroupId(groupId);

        assertThat(students)
                .extracting(User::getLastName)
                .containsExactly("Абрамов", "Ёжиков", "Иванов", "Янов");
    }

    @Test
    void findByGroupIdPageable_returnsCyrillicSortedOrder() {
        Long groupId = createGroup("СОРТ-112");

        insertStudent(groupId, "Янов", "Ян", "Янович", 2001L);
        insertStudent(groupId, "Абрамов", "Антон", "Андреевич", 2002L);
        insertStudent(groupId, "Ёжиков", "Ефим", "Егорович", 2003L);
        insertStudent(groupId, "Иванов", "Иван", "Иванович", 2004L);

        Pageable pageable = PageRequest.of(0, 50);
        List<User> students = userRepository.findByGroupId(groupId, pageable).getContent();

        assertThat(students)
                .extracting(User::getLastName)
                .containsExactly("Абрамов", "Ёжиков", "Иванов", "Янов");
    }

    @Test
    void findByGroupId_excludesArchivedAndSorts() {
        Long groupId = createGroup("СОРТ-113");

        insertStudent(groupId, "Богданов", "Борис", "Борисович", 3001L);
        Long archivedId = insertStudent(groupId, "Абрамов", "Антон", "Андреевич", 3002L);
        insertStudent(groupId, "Васильев", "Виктор", "Витальевич", 3003L);

        // Archive Абрамов — он не должен попасть в выдачу.
        archive(archivedId);

        List<User> students = userRepository.findByGroupId(groupId);

        assertThat(students).extracting(User::getLastName).containsExactly("Богданов", "Васильев");
    }

    private Long createGroup(String name) {
        var actor = identityFixture.select(identityFixture.canonical().adminId(), AuthRole.ADMIN);
        try (var ignored = identityFixture.request(actor)) {
            return groups.createGroup(new CreateGroupRequest(name)).getId();
        }
    }

    private Long insertStudent(
            Long groupId, String lastName, String firstName, String middleName, Long telegramId) {
        var actor = identityFixture.select(identityFixture.canonical().adminId(), AuthRole.ADMIN);
        return identityFixture
                .seedUser(
                        new CreateUserRequest(
                                lastName,
                                firstName,
                                middleName,
                                UserRole.STUDENT,
                                groupId,
                                null,
                                telegramId),
                        actor)
                .userId();
    }

    private void archive(long user) {
        when(attendance.preview(user))
                .thenReturn(
                        UserImpactSnapshot.newBuilder()
                                .setUserId(user)
                                .setObservedAtEpochMs(Instant.now().toEpochMilli())
                                .setSnapshotDigest("b".repeat(64))
                                .build());
        var actor = identityFixture.select(identityFixture.canonical().adminId(), AuthRole.ADMIN);
        Preview preview;
        try (var ignored = identityFixture.request(actor)) {
            preview = archives.preview(user);
        }
        var request = new ArchiveRequest(UUID.randomUUID(), preview.previewDigest(), null);
        try (var ignored = identityFixture.request(actor, request.operationId())) {
            archives.archive(user, request);
        }
    }
}
