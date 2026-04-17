package ru.rutcampustrack.academic.group;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.rutcampustrack.academic.contract.dto.group.PromotionPreviewItem;
import ru.rutcampustrack.academic.contract.dto.group.PromotionPreviewItem.Action;
import ru.rutcampustrack.academic.contract.dto.group.PromotionSummary;
import ru.rutcampustrack.academic.contract.dto.group.PromotionSummary.PrefixConflict;
import ru.rutcampustrack.academic.entity.Group;
import ru.rutcampustrack.academic.event.GroupRenamedEvent;
import ru.rutcampustrack.academic.repository.GroupRepository;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Промоушен групп на следующий курс (BUG-006-6 / план 58-06).
 *
 * <p>Разделён на {@link #preview()} (dry-run, без изменений БД) и
 * {@link #execute()} (применяет план). Обе операции строят единый план per-prefix:
 * <ul>
 *   <li>Группа с {@code course < maxCourse} — переименование ({@code course+1}).</li>
 *   <li>Группа с {@code course == maxCourse} — архивация через
 *       {@link GroupArchivalService#archive(Group)}.</li>
 *   <li>Группа с неизвестным типом программы — весь префикс в {@code conflicts[]}
 *       с {@code reason=unknown_type}.</li>
 *   <li>Коллизия имён в префиксе (новое имя занято активной группой того же префикса,
 *       которая не архивируется первой) — весь префикс в {@code conflicts[]} с
 *       {@code reason=name_conflict}.</li>
 *   <li>Имя не парсится — группа в {@code conflicts[]} с {@code reason=parse_error}.</li>
 * </ul>
 *
 * <p>Алгоритм применения внутри префикса: сначала архивируются группы maxCourse
 * (освобождая имена типа УИТ-4XX), затем переименования сортируются по курсу DESC
 * (УИТ-311→УИТ-411, затем УИТ-211→УИТ-311, затем УИТ-111→УИТ-211) — так исключается
 * UNIQUE-конфликт в середине транзакции.
 */
@Service
public class GroupPromotionService {

    private final GroupRepository groupRepository;
    private final GroupNameParser parser;
    private final GroupArchivalService archivalService;
    private final ApplicationEventPublisher publisher;

    public GroupPromotionService(GroupRepository groupRepository,
                                  GroupNameParser parser,
                                  GroupArchivalService archivalService,
                                  ApplicationEventPublisher publisher) {
        this.groupRepository = groupRepository;
        this.parser = parser;
        this.archivalService = archivalService;
        this.publisher = publisher;
    }

    @Transactional(readOnly = true)
    public PromotionSummary preview() {
        return compute(false);
    }

    @Transactional
    public PromotionSummary execute() {
        return compute(true);
    }

    private PromotionSummary compute(boolean apply) {
        List<Group> active = groupRepository.findAllByIsActiveTrue();

        List<PromotionPreviewItem> toPromote = new ArrayList<>();
        List<PromotionPreviewItem> toArchive = new ArrayList<>();
        List<PrefixConflict> conflicts = new ArrayList<>();

        // Группировка по префиксу + сбор parse_error-ов.
        Map<String, List<Group>> byPrefix = new HashMap<>();
        for (Group g : active) {
            try {
                GroupNameParser.ParsedName parsed = parser.parse(g.getName());
                byPrefix.computeIfAbsent(parsed.prefix(), k -> new ArrayList<>()).add(g);
            } catch (IllegalArgumentException e) {
                conflicts.add(new PrefixConflict(
                        "",
                        "parse_error",
                        "Не удалось распознать имя группы: " + g.getName(),
                        List.of(g.getId())
                ));
            }
        }

        for (Map.Entry<String, List<Group>> entry : byPrefix.entrySet()) {
            String prefix = entry.getKey();
            List<Group> groups = entry.getValue();
            List<PromotionPreviewItem> prefixPromote = new ArrayList<>();
            List<PromotionPreviewItem> prefixArchive = new ArrayList<>();

            PrefixConflict abort = planPrefix(prefix, groups, prefixPromote, prefixArchive);

            if (abort != null) {
                conflicts.add(abort);
                continue;
            }
            toPromote.addAll(prefixPromote);
            toArchive.addAll(prefixArchive);
            if (apply) {
                applyPrefix(groups, prefixPromote, prefixArchive);
            }
        }

        return new PromotionSummary(toPromote, toArchive, conflicts, !apply, apply);
    }

    /** Возвращает conflict или {@code null}, если префикс готов к применению. */
    private PrefixConflict planPrefix(String prefix,
                                       List<Group> groups,
                                       List<PromotionPreviewItem> prefixPromote,
                                       List<PromotionPreviewItem> prefixArchive) {
        // 1) Собрать имена, которые ОСВОБОДЯТСЯ в этом префиксе (архив + промоушен):
        //    имя считается "освобождающимся", если группа с этим именем либо архивируется,
        //    либо переименовывается (новое имя ≠ старое).
        java.util.Set<String> freeing = new java.util.HashSet<>();
        // 2) Сначала проверить unknown_type — он абортит префикс вне зависимости от имён.
        for (Group g : groups) {
            GroupNameParser.ParsedName parsed;
            try {
                parsed = parser.parse(g.getName());
            } catch (IllegalArgumentException e) {
                // Внутри prefix-ветки такого быть не должно (парсинг сделан выше),
                // но подстрахуемся.
                return new PrefixConflict(prefix, "parse_error",
                        "Не удалось распознать имя: " + g.getName(), List.of(g.getId()));
            }
            try {
                ProgramType.fromDigit(parsed.type());
            } catch (UnknownProgramTypeException e) {
                return new PrefixConflict(
                        prefix, "unknown_type",
                        "Неизвестный тип программы (цифра " + e.getDigit() + ") в префиксе " + prefix,
                        groups.stream().map(Group::getId).toList()
                );
            }
            freeing.add(g.getName());
        }

        // 3) Посчитать новый план: rename или archive.
        Map<String, Long> newNameOwners = new HashMap<>();
        for (Group g : groups) {
            GroupNameParser.ParsedName parsed = parser.parse(g.getName());
            ProgramType type = ProgramType.fromDigit(parsed.type());
            GroupNameParser.PromoteResult result = parser.promote(g.getName(), type);

            if (result instanceof GroupNameParser.PromoteResult.Renamed r) {
                // Конфликт: новое имя либо занято другой группой, которая НЕ освобождает его,
                // либо два разных источника дают одинаковое новое имя.
                if (newNameOwners.containsKey(r.newName())) {
                    return new PrefixConflict(
                            prefix, "name_conflict",
                            "Два источника претендуют на одно имя: " + r.newName(),
                            groups.stream().map(Group::getId).toList()
                    );
                }
                newNameOwners.put(r.newName(), g.getId());
                prefixPromote.add(new PromotionPreviewItem(
                        g.getId(), g.getName(), r.newName(), Action.PROMOTE));
            } else {
                prefixArchive.add(new PromotionPreviewItem(
                        g.getId(), g.getName(), null, Action.ARCHIVE));
            }
        }

        // 4) Проверить, что каждое НОВОЕ имя либо свободно в префиксе, либо
        //    принадлежит активной группе, которая тоже освобождается.
        for (Map.Entry<String, Long> e : newNameOwners.entrySet()) {
            String newName = e.getKey();
            for (Group g : groups) {
                if (!g.getId().equals(e.getValue())
                        && g.getName().equals(newName)
                        && !freeing.contains(g.getName())) {
                    return new PrefixConflict(
                            prefix, "name_conflict",
                            "Имя '" + newName + "' уже занято в префиксе " + prefix,
                            groups.stream().map(Group::getId).toList()
                    );
                }
            }
        }

        return null;
    }

    private void applyPrefix(List<Group> groups,
                              List<PromotionPreviewItem> promote,
                              List<PromotionPreviewItem> archive) {
        Map<Long, Group> byId = new HashMap<>();
        for (Group g : groups) byId.put(g.getId(), g);

        // Сначала архивация — освобождает старшие имена (УИТ-4XX).
        for (PromotionPreviewItem item : archive) {
            archivalService.archive(byId.get(item.getId()));
        }
        // Затем переименования по курсу DESC (from 3→4, потом 2→3, потом 1→2),
        // чтобы имя-назначение всегда было свободным на момент save.
        promote.sort(Comparator.comparing(PromotionPreviewItem::getFrom).reversed());
        for (PromotionPreviewItem item : promote) {
            Group g = byId.get(item.getId());
            g.setName(item.getTo());
            publisher.publishEvent(new GroupRenamedEvent(this, g.getId(), g.getName()));
        }
    }
}
