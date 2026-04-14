package ru.rutcampustrack.academic.group;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;
import ru.rutcampustrack.academic.entity.Group;
import ru.rutcampustrack.academic.event.GroupArchivedEvent;

import java.lang.reflect.Field;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * Unit tests для {@link GroupArchivalService} (BUG-006-6 / план 58-06).
 */
class GroupArchivalServiceTest {

    private static final Clock FIXED_CLOCK = Clock.fixed(
            Instant.parse("2026-06-01T00:00:00Z"), ZoneOffset.UTC);

    private Group group(long id, String name, boolean active) {
        Group g = new Group();
        try {
            Field idField = Group.class.getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(g, id);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
        g.setName(name);
        g.setActive(active);
        g.setCreatedAt(OffsetDateTime.parse("2022-09-01T00:00:00Z"));
        return g;
    }

    @Test
    void archive_setsSuffixAndFlagsAndTimestamp() {
        ApplicationEventPublisher publisher = mock(ApplicationEventPublisher.class);
        GroupArchivalService service = new GroupArchivalService(publisher, FIXED_CLOCK);

        Group g = group(10L, "УИТ-411", true);
        service.archive(g);

        assertThat(g.getName()).isEqualTo("УИТ-411 (выпуск 2026)");
        assertThat(g.isActive()).isFalse();
        assertThat(g.getArchivedAt()).isEqualTo(OffsetDateTime.parse("2026-06-01T00:00:00Z"));
    }

    @Test
    void archive_publishesGroupArchivedEvent() {
        ApplicationEventPublisher publisher = mock(ApplicationEventPublisher.class);
        GroupArchivalService service = new GroupArchivalService(publisher, FIXED_CLOCK);

        Group g = group(42L, "УИТ-411", true);
        service.archive(g);

        ArgumentCaptor<GroupArchivedEvent> captor = ArgumentCaptor.forClass(GroupArchivedEvent.class);
        verify(publisher).publishEvent(captor.capture());
        GroupArchivedEvent event = captor.getValue();
        assertThat(event.getEventType()).isEqualTo("group.archived");
        assertThat(((GroupArchivedEvent.Payload) event.getPayload()).groupId()).isEqualTo(42L);
    }

    @Test
    void archive_alreadyArchived_throwsIllegalState() {
        ApplicationEventPublisher publisher = mock(ApplicationEventPublisher.class);
        GroupArchivalService service = new GroupArchivalService(publisher, FIXED_CLOCK);

        Group g = group(7L, "УИТ-411 (выпуск 2025)", false);

        assertThatThrownBy(() -> service.archive(g))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("already archived");
    }

    @Test
    void buildArchivedName_composesSuffix() {
        GroupArchivalService service = new GroupArchivalService(
                mock(ApplicationEventPublisher.class), FIXED_CLOCK);
        assertThat(service.buildArchivedName("УИТ-411", 2026)).isEqualTo("УИТ-411 (выпуск 2026)");
    }
}
