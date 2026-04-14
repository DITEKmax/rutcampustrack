package ru.rutcampustrack.academic.group;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;
import ru.rutcampustrack.academic.contract.dto.group.PromotionPreviewItem;
import ru.rutcampustrack.academic.contract.dto.group.PromotionPreviewItem.Action;
import ru.rutcampustrack.academic.contract.dto.group.PromotionSummary;
import ru.rutcampustrack.academic.entity.Group;
import ru.rutcampustrack.academic.event.GroupArchivedEvent;
import ru.rutcampustrack.academic.event.GroupRenamedEvent;
import ru.rutcampustrack.academic.repository.GroupRepository;

import java.lang.reflect.Field;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests для {@link GroupPromotionService} (BUG-006-6 / план 58-06).
 *
 * <p>Используется fixed clock (2026-09-01) — 14 дней до осеннего семестра. Это
 * типовой trigger-момент scheduled промоушена.
 */
class GroupPromotionServiceTest {

    private static final Clock FIXED_CLOCK = Clock.fixed(
            Instant.parse("2026-09-01T00:00:00Z"), ZoneOffset.UTC);

    private GroupRepository groupRepository;
    private ApplicationEventPublisher publisher;
    private GroupArchivalService archivalService;
    private GroupPromotionService service;

    private final AtomicLong idSeq = new AtomicLong(1);

    @BeforeEach
    void setUp() {
        groupRepository = mock(GroupRepository.class);
        publisher = mock(ApplicationEventPublisher.class);
        archivalService = new GroupArchivalService(publisher, FIXED_CLOCK);
        service = new GroupPromotionService(
                groupRepository, new GroupNameParser(), archivalService, publisher);
    }

    private Group group(String name) {
        Group g = new Group();
        try {
            Field idField = Group.class.getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(g, idSeq.getAndIncrement());
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
        g.setName(name);
        g.setActive(true);
        g.setCreatedAt(OffsetDateTime.parse("2022-09-01T00:00:00Z"));
        return g;
    }

    private void seed(Group... groups) {
        when(groupRepository.findAllByIsActiveTrue()).thenReturn(new ArrayList<>(Arrays.asList(groups)));
    }

    // ---- preview --------------------------------------------------------

    @Test
    void preview_emptyDb_returnsEmptySummary() {
        seed();
        PromotionSummary s = service.preview();
        assertThat(s.getToPromote()).isEmpty();
        assertThat(s.getToArchive()).isEmpty();
        assertThat(s.getConflicts()).isEmpty();
        assertThat(s.isDryRun()).isTrue();
        assertThat(s.isExecuted()).isFalse();
    }

    @Test
    void preview_bachelor_course1AndCourse4() {
        Group g1 = group("УИТ-111");
        Group g4 = group("УИТ-411");
        seed(g1, g4);

        PromotionSummary s = service.preview();

        assertThat(s.getToPromote())
                .extracting(PromotionPreviewItem::getFrom, PromotionPreviewItem::getTo, PromotionPreviewItem::getAction)
                .containsExactly(org.assertj.core.api.Assertions.tuple("УИТ-111", "УИТ-211", Action.PROMOTE));
        assertThat(s.getToArchive())
                .extracting(PromotionPreviewItem::getFrom, PromotionPreviewItem::getAction)
                .containsExactly(org.assertj.core.api.Assertions.tuple("УИТ-411", Action.ARCHIVE));
        assertThat(s.getConflicts()).isEmpty();
    }

    @Test
    void preview_nameConflictDueToUnknownBlockingFreeing_abortsPrefix() {
        // Сценарий реального name_conflict:
        // - УИТ-111 (bachelor, promotes to УИТ-211)
        // - УИТ-211 (bachelor, promotes to УИТ-311) — freeing, но тип у неё неизвестен
        //   делает её abort для всего префикса через unknown_type ДО проверки name_conflict.
        // Значит «name_conflict в одном префиксе без unknown_type» не достижим в валидных
        // данных (цепочки курсов всегда разрешаются сортировкой). Проверяем, что логика
        // при этом остаётся корректной: при наличии группы с unknown type — всё падает в unknown_type.
        Group g1 = group("УИТ-111");
        Group g2 = group("УИТ-251"); // type=5, неизвестен
        seed(g1, g2);

        PromotionSummary s = service.preview();

        assertThat(s.getToPromote()).isEmpty();
        assertThat(s.getConflicts()).hasSize(1);
        assertThat(s.getConflicts().get(0).getPrefix()).isEqualTo("УИТ");
        assertThat(s.getConflicts().get(0).getReason()).isEqualTo("unknown_type");
    }

    @Test
    void preview_courseChain_resolvesWithoutConflict() {
        // УИТ-111 (→УИТ-211) + УИТ-211 (→УИТ-311): цепочка разрешается на apply-стадии
        // сортировкой по курсу DESC. Preview должен показать обе промоушена без конфликта.
        Group g1 = group("УИТ-111");
        Group g2 = group("УИТ-211");
        seed(g1, g2);

        PromotionSummary s = service.preview();

        assertThat(s.getConflicts()).isEmpty();
        assertThat(s.getToPromote()).hasSize(2);
        assertThat(s.getToPromote())
                .extracting(PromotionPreviewItem::getFrom)
                .containsExactlyInAnyOrder("УИТ-111", "УИТ-211");
    }

    @Test
    void preview_unknownType_putsPrefixInConflicts() {
        // type=5 не зарегистрирован
        Group g = group("УИТ-351");
        seed(g);

        PromotionSummary s = service.preview();

        assertThat(s.getToPromote()).isEmpty();
        assertThat(s.getConflicts()).hasSize(1);
        assertThat(s.getConflicts().get(0).getReason()).isEqualTo("unknown_type");
        assertThat(s.getConflicts().get(0).getPrefix()).isEqualTo("УИТ");
    }

    @Test
    void preview_prefixesAreIndependent() {
        // УИТ-префикс ok, УВП-префикс — unknown_type (digit=5) → независимо отбракован.
        Group ok = group("УИТ-111");
        Group badUvp = group("УВП-151"); // type=5 → unknown
        seed(ok, badUvp);

        PromotionSummary s = service.preview();

        assertThat(s.getToPromote()).hasSize(1);
        assertThat(s.getToPromote().get(0).getFrom()).isEqualTo("УИТ-111");
        assertThat(s.getConflicts()).hasSize(1);
        assertThat(s.getConflicts().get(0).getPrefix()).isEqualTo("УВП");
        assertThat(s.getConflicts().get(0).getReason()).isEqualTo("unknown_type");
    }

    // ---- execute --------------------------------------------------------

    @Test
    void execute_renamesGroupAndPublishesGroupRenamedEvent() {
        Group g = group("УИТ-111");
        seed(g);

        service.execute();

        assertThat(g.getName()).isEqualTo("УИТ-211");
        assertThat(g.isActive()).isTrue();

        ArgumentCaptor<org.springframework.context.ApplicationEvent> events =
                ArgumentCaptor.forClass(org.springframework.context.ApplicationEvent.class);
        org.mockito.Mockito.verify(publisher, org.mockito.Mockito.atLeastOnce())
                .publishEvent(events.capture());
        assertThat(events.getAllValues())
                .anyMatch(e -> e instanceof GroupRenamedEvent);
    }

    @Test
    void execute_archivesMaxCourseAndPublishesGroupArchivedEvent() {
        Group g = group("УИТ-411");
        seed(g);

        service.execute();

        assertThat(g.getName()).isEqualTo("УИТ-411 (выпуск 2026)");
        assertThat(g.isActive()).isFalse();
        assertThat(g.getArchivedAt()).isNotNull();

        ArgumentCaptor<org.springframework.context.ApplicationEvent> events =
                ArgumentCaptor.forClass(org.springframework.context.ApplicationEvent.class);
        org.mockito.Mockito.verify(publisher, org.mockito.Mockito.atLeastOnce())
                .publishEvent(events.capture());
        assertThat(events.getAllValues())
                .anyMatch(e -> e instanceof GroupArchivedEvent);
    }

    @Test
    void execute_perPrefixSavepoint_conflictedPrefixRolledBack() {
        // УИТ-префикс ok, УВП с unknown_type (digit=5) — префикс skip целиком.
        Group ok = group("УИТ-111");
        Group badUvp = group("УВП-151");
        seed(ok, badUvp);

        service.execute();

        // УИТ-префикс применён:
        assertThat(ok.getName()).isEqualTo("УИТ-211");
        // УВП-префикс — не тронут:
        assertThat(badUvp.getName()).isEqualTo("УВП-151");
        assertThat(badUvp.isActive()).isTrue();
    }

    @Test
    void execute_twiceAdvancesCourseByOne() {
        Group g = group("УИТ-111");
        seed(g);

        service.execute(); // УИТ-111 → УИТ-211
        assertThat(g.getName()).isEqualTo("УИТ-211");

        // Re-seed, second run
        seed(g);
        service.execute(); // УИТ-211 → УИТ-311
        assertThat(g.getName()).isEqualTo("УИТ-311");
    }

    @Test
    void execute_renameChain_bachelor1to4_resolvesOrderingNoCollision() {
        // УИТ-111, УИТ-211, УИТ-311, УИТ-411 — все промоутятся (последняя архивируется).
        // Проверяем что нет UNIQUE-коллизии на промежуточных save (архив сначала).
        Group g1 = group("УИТ-111");
        Group g2 = group("УИТ-211");
        Group g3 = group("УИТ-311");
        Group g4 = group("УИТ-411");
        seed(g1, g2, g3, g4);

        PromotionSummary s = service.execute();

        assertThat(s.getConflicts()).isEmpty();
        assertThat(g1.getName()).isEqualTo("УИТ-211");
        assertThat(g2.getName()).isEqualTo("УИТ-311");
        assertThat(g3.getName()).isEqualTo("УИТ-411");
        assertThat(g4.isActive()).isFalse();
        assertThat(g4.getName()).isEqualTo("УИТ-411 (выпуск 2026)");
    }

    @Test
    void preview_parseError_putInConflicts() {
        Group garbage = group("УИТ-111");
        // подменяем имя уже после создания на то, что пройдёт entity-pattern (архивное),
        // но не пройдёт parser.parse (active-only).
        garbage.setName("УИТ-411 (выпуск 2026)");
        seed(garbage);

        PromotionSummary s = service.preview();

        assertThat(s.getToPromote()).isEmpty();
        assertThat(s.getToArchive()).isEmpty();
        assertThat(s.getConflicts()).hasSize(1);
        assertThat(s.getConflicts().get(0).getReason()).isEqualTo("parse_error");
    }
}
