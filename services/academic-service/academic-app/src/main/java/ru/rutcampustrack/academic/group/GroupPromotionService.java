package ru.rutcampustrack.academic.group;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import ru.rutcampustrack.academic.contract.dto.group.PromotionExecuteRequest;
import ru.rutcampustrack.academic.contract.dto.group.PromotionPreviewItem;
import ru.rutcampustrack.academic.contract.dto.group.PromotionPreviewItem.Action;
import ru.rutcampustrack.academic.contract.dto.group.PromotionSkippedItem;
import ru.rutcampustrack.academic.contract.dto.group.PromotionSkippedItem.Reason;
import ru.rutcampustrack.academic.contract.dto.group.PromotionSummary;
import ru.rutcampustrack.academic.contract.dto.group.PromotionSummary.PrefixConflict;
import ru.rutcampustrack.academic.contract.enums.SemesterType;
import ru.rutcampustrack.academic.entity.Group;
import ru.rutcampustrack.academic.entity.GroupPromotionCycleRecord;
import ru.rutcampustrack.academic.entity.Semester;
import ru.rutcampustrack.academic.event.GroupRenamedEvent;
import ru.rutcampustrack.academic.repository.GroupPromotionCycleRecordRepository;
import ru.rutcampustrack.academic.repository.GroupRegistryReadRepository;
import ru.rutcampustrack.academic.repository.GroupRepository;
import ru.rutcampustrack.academic.repository.SemesterRepository;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;

/**
 * Single-group and mass promotion share the same completed-spring-cycle guard.
 * Execute locks the cycle and active rows, then rebuilds and compares the exact
 * server plan before applying any change.
 */
@Service
public class GroupPromotionService {
    private final GroupRepository groupRepository;
    private final SemesterRepository semesterRepository;
    private final GroupPromotionCycleRecordRepository cycleRecordRepository;
    private final GroupRegistryReadRepository registryReadRepository;
    private final GroupNameParser parser;
    private final GroupArchivalService archivalService;
    private final ApplicationEventPublisher publisher;
    private final Clock clock;

    public GroupPromotionService(GroupRepository groupRepository,
                                 SemesterRepository semesterRepository,
                                 GroupPromotionCycleRecordRepository cycleRecordRepository,
                                 GroupRegistryReadRepository registryReadRepository,
                                 GroupNameParser parser,
                                 GroupArchivalService archivalService,
                                 ApplicationEventPublisher publisher,
                                 Clock clock) {
        this.groupRepository = groupRepository;
        this.semesterRepository = semesterRepository;
        this.cycleRecordRepository = cycleRecordRepository;
        this.registryReadRepository = registryReadRepository;
        this.parser = parser;
        this.archivalService = archivalService;
        this.publisher = publisher;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public PromotionSummary preview(Long groupId) {
        Semester cycle = findLatestCompletedSpring();
        List<Group> active = groupRepository.findAllByIsActiveTrue();
        List<GroupPromotionCycleRecord> processed =
                cycleRecordRepository.findAllByCycleSemesterIdOrderByGroupId(cycle.getId());
        return buildSummary(cycle, active, processed, groupId, true);
    }

    @Transactional
    public PromotionSummary execute(PromotionExecuteRequest request) {
        if (request == null || request.getCycleSemesterId() == null
                || request.getPreviewVersion() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Нужно подтвердить полученный предпросмотр.");
        }

        Semester cycle = requireCurrentCycleForUpdate(request.getCycleSemesterId());
        List<Group> active = groupRepository.findAllActiveForPromotionUpdate();
        List<GroupPromotionCycleRecord> processed =
                cycleRecordRepository.findAllByCycleSemesterIdOrderByGroupId(cycle.getId());
        PromotionSummary plan = buildSummary(cycle, active, processed, request.getGroupId(), true);
        if (!Objects.equals(plan.getPreviewVersion(), request.getPreviewVersion())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Предпросмотр устарел. Обнови реестр и подготовь новый план.");
        }

        apply(plan, active, cycle);
        return new PromotionSummary(
                plan.getToPromote(),
                plan.getToArchive(),
                plan.getSkipped(),
                plan.getConflicts(),
                plan.getCycleSemesterId(),
                plan.getCycleDateTo(),
                plan.getGroupId(),
                plan.getPreviewVersion(),
                false,
                true);
    }

    private Semester findLatestCompletedSpring() {
        LocalDate today = LocalDate.now(clock);
        return semesterRepository.findFirstBySemesterTypeAndDateToBeforeOrderByDateToDescIdDesc(
                        SemesterType.SPRING, today)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT,
                        "Нет завершённого весеннего семестра для цикла перевода групп."));
    }

    private Semester requireCurrentCycleForUpdate(Long cycleSemesterId) {
        Semester requested = semesterRepository.findByIdForUpdate(cycleSemesterId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Весенний семестр не найден."));
        if (requested.getSemesterType() != SemesterType.SPRING
                || !requested.getDateTo().isBefore(LocalDate.now(clock))) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Весенний семестр ещё не завершён или больше не подходит для перевода.");
        }
        Semester latest = findLatestCompletedSpring();
        if (!latest.getId().equals(requested.getId())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Цикл перевода изменился. Подготовь новый предпросмотр.");
        }
        return requested;
    }

    private PromotionSummary buildSummary(Semester cycle,
                                          List<Group> active,
                                          List<GroupPromotionCycleRecord> processed,
                                          Long groupId,
                                          boolean dryRun) {
        active = new ArrayList<>(active);
        active.sort(Comparator.comparing(Group::getId));
        Map<Long, Group> activeById = new HashMap<>();
        for (Group group : active) activeById.put(group.getId(), group);
        if (groupId != null && !activeById.containsKey(groupId)) {
            boolean exists = groupRepository.findById(groupId).isPresent();
            throw new ResponseStatusException(
                    exists ? HttpStatus.CONFLICT : HttpStatus.NOT_FOUND,
                    exists ? "Группа уже не активна и не может быть переведена." : "Группа не найдена.");
        }

        Map<Long, Long> studentCounts =
                registryReadRepository.countActiveStudentsByGroupIds(activeById.keySet());
        Map<Long, GroupPromotionCycleRecord> processedByGroup = new HashMap<>();
        for (GroupPromotionCycleRecord record : processed) {
            processedByGroup.put(record.getGroupId(), record);
        }

        List<Group> scoped = groupId == null
                ? active
                : List.of(activeById.get(groupId));
        List<Group> eligible = new ArrayList<>();
        List<PromotionSkippedItem> skipped = new ArrayList<>();
        for (Group group : scoped) {
            GroupPromotionCycleRecord record = processedByGroup.get(group.getId());
            if (record != null) {
                skipped.add(new PromotionSkippedItem(
                        group.getId(), group.getName(), record.getStudentCount(),
                        Reason.ALREADY_PROCESSED, record.getAction(),
                        record.getFromName(), record.getToName()));
            } else if (createdAfterCycle(group, cycle)) {
                skipped.add(new PromotionSkippedItem(
                        group.getId(), group.getName(), studentCount(studentCounts, group.getId()),
                        Reason.CREATED_AFTER_CYCLE_END, null, null, null));
            } else {
                eligible.add(group);
            }
        }

        Map<String, List<Group>> blockersByPrefix = new TreeMap<>();
        for (Group group : active) {
            try {
                String prefix = parser.parse(group.getName()).prefix();
                blockersByPrefix.computeIfAbsent(prefix, ignored -> new ArrayList<>()).add(group);
            } catch (IllegalArgumentException ignored) {
                // A malformed group blocks only its own requested operation below.
            }
        }

        Map<String, List<Group>> eligibleByPrefix = new TreeMap<>();
        List<PrefixConflict> conflicts = new ArrayList<>();
        for (Group group : eligible) {
            try {
                String prefix = parser.parse(group.getName()).prefix();
                eligibleByPrefix.computeIfAbsent(prefix, ignored -> new ArrayList<>()).add(group);
            } catch (IllegalArgumentException e) {
                conflicts.add(new PrefixConflict(
                        "", "parse_error", "Не удалось распознать имя группы: " + group.getName(),
                        List.of(group.getId())));
            }
        }

        List<PromotionPreviewItem> toPromote = new ArrayList<>();
        List<PromotionPreviewItem> toArchive = new ArrayList<>();
        for (Map.Entry<String, List<Group>> entry : eligibleByPrefix.entrySet()) {
            List<PromotionPreviewItem> prefixPromote = new ArrayList<>();
            List<PromotionPreviewItem> prefixArchive = new ArrayList<>();
            PrefixConflict conflict = planPrefix(
                    entry.getKey(), entry.getValue(),
                    blockersByPrefix.getOrDefault(entry.getKey(), List.of()),
                    studentCounts, prefixPromote, prefixArchive);
            if (conflict != null) {
                conflicts.add(conflict);
                continue;
            }
            toPromote.addAll(prefixPromote);
            toArchive.addAll(prefixArchive);
        }

        toPromote.sort(Comparator.comparing(PromotionPreviewItem::getId));
        toArchive.sort(Comparator.comparing(PromotionPreviewItem::getId));
        skipped.sort(Comparator.comparing(PromotionSkippedItem::getId));
        conflicts.sort(Comparator.comparing(PrefixConflict::getPrefix)
                .thenComparing(PrefixConflict::getReason));

        String previewVersion = previewVersion(
                cycle, groupId, toPromote, toArchive, skipped, conflicts);
        return new PromotionSummary(toPromote, toArchive, skipped, conflicts,
                cycle.getId(), cycle.getDateTo(), groupId, previewVersion, dryRun, !dryRun);
    }

    private boolean createdAfterCycle(Group group, Semester cycle) {
        LocalDate createdOn = group.getCreatedAt().atZoneSameInstant(clock.getZone()).toLocalDate();
        return createdOn.isAfter(cycle.getDateTo());
    }

    private PrefixConflict planPrefix(String prefix,
                                      List<Group> eligible,
                                      List<Group> activeBlockers,
                                      Map<Long, Long> studentCounts,
                                      List<PromotionPreviewItem> prefixPromote,
                                      List<PromotionPreviewItem> prefixArchive) {
        Set<Long> eligibleIds = new HashSet<>();
        for (Group group : eligible) {
            eligibleIds.add(group.getId());
            try {
                GroupCodeRules.fromName(group.getName(), group.getTrainingDurationYears());
            } catch (UnknownProgramTypeException e) {
                return conflict(prefix, "unknown_type",
                        "Неизвестный тип программы (цифра " + e.getDigit() + ") в префиксе " + prefix,
                        eligible, List.of());
            } catch (GroupCodeRules.InvalidCodeException e) {
                return conflict(prefix, "invalid_code",
                        "Некорректный код группы '" + group.getName() + "': " + e.getMessage(),
                        eligible, List.of());
            }
        }

        Map<String, Long> newNameOwners = new HashMap<>();
        for (Group group : eligible) {
            GroupCodeRules.CanonicalCode current =
                    GroupCodeRules.fromName(group.getName(), group.getTrainingDurationYears());
            GroupCodeRules.CanonicalCode next = current.next();
            long students = studentCount(studentCounts, group.getId());
            if (next == null) {
                prefixArchive.add(new PromotionPreviewItem(
                        group.getId(), group.getName(), null, Action.ARCHIVE, students));
                continue;
            }

            Long previousOwner = newNameOwners.putIfAbsent(next.name(), group.getId());
            if (previousOwner != null) {
                return conflict(prefix, "name_conflict",
                        "Два источника претендуют на одно имя: " + next.name(),
                        eligible, List.of(previousOwner, group.getId()));
            }
            prefixPromote.add(new PromotionPreviewItem(
                    group.getId(), group.getName(), next.name(), Action.PROMOTE, students));
        }

        for (Map.Entry<String, Long> entry : newNameOwners.entrySet()) {
            for (Group blocker : activeBlockers) {
                if (!blocker.getId().equals(entry.getValue())
                        && blocker.getName().equals(entry.getKey())
                        && !eligibleIds.contains(blocker.getId())) {
                    return conflict(prefix, "name_conflict",
                            "Имя '" + entry.getKey() + "' занято активной группой " + blocker.getId() + ".",
                            eligible, List.of(blocker.getId()));
                }
            }
        }
        return null;
    }

    private PrefixConflict conflict(String prefix, String reason, String message,
                                    List<Group> eligible, List<Long> additionalIds) {
        Set<Long> ids = new java.util.TreeSet<>(additionalIds);
        eligible.stream().map(Group::getId).forEach(ids::add);
        return new PrefixConflict(prefix, reason, message, new ArrayList<>(ids));
    }

    private void apply(PromotionSummary plan, List<Group> active, Semester cycle) {
        Map<Long, Group> byId = new HashMap<>();
        for (Group group : active) byId.put(group.getId(), group);
        OffsetDateTime processedAt = OffsetDateTime.now(clock);
        List<GroupPromotionCycleRecord> records = new ArrayList<>();

        for (PromotionPreviewItem item : plan.getToArchive()) {
            Group group = byId.get(item.getId());
            GroupCodeRules.apply(group,
                    GroupCodeRules.fromName(group.getName(), group.getTrainingDurationYears()));
            archivalService.archive(group, cycle.getDateTo().getYear());
            records.add(new GroupPromotionCycleRecord(
                    cycle.getId(), group.getId(), Action.ARCHIVE,
                    item.getFrom(), group.getName(), item.getStudentCount(), processedAt));
        }

        List<PromotionPreviewItem> orderedPromotions = new ArrayList<>(plan.getToPromote());
        orderedPromotions.sort(Comparator.comparing(PromotionPreviewItem::getFrom).reversed());
        for (PromotionPreviewItem item : orderedPromotions) {
            Group group = byId.get(item.getId());
            GroupCodeRules.apply(group,
                    GroupCodeRules.fromName(item.getTo(), group.getTrainingDurationYears()));
            publisher.publishEvent(new GroupRenamedEvent(this, group.getId(), group.getName()));
            records.add(new GroupPromotionCycleRecord(
                    cycle.getId(), group.getId(), Action.PROMOTE,
                    item.getFrom(), item.getTo(), item.getStudentCount(), processedAt));
        }
        cycleRecordRepository.saveAll(records);
        cycleRecordRepository.flush();
    }

    private long studentCount(Map<Long, Long> studentCounts, Long groupId) {
        return studentCounts.getOrDefault(groupId, 0L);
    }

    private String previewVersion(Semester cycle,
                                  Long groupId,
                                  List<PromotionPreviewItem> toPromote,
                                  List<PromotionPreviewItem> toArchive,
                                  List<PromotionSkippedItem> skipped,
                                  List<PrefixConflict> conflicts) {
        StringBuilder value = new StringBuilder("group-promotion-v1|")
                .append(cycle.getId()).append('|')
                .append(cycle.getDateTo()).append('|')
                .append(groupId == null ? "mass" : "group:" + groupId).append('|');
        appendItems(value, "promote", toPromote);
        appendItems(value, "archive", toArchive);
        skipped.forEach(item -> value.append("skip|").append(item.getId()).append('|')
                .append(item.getName()).append('|').append(item.getStudentCount()).append('|')
                .append(item.getReason()).append('|').append(item.getPreviousAction()).append('|')
                .append(item.getPreviousFrom()).append('|').append(item.getPreviousTo()).append('|'));
        conflicts.forEach(conflict -> {
            value.append("conflict|").append(conflict.getPrefix()).append('|')
                    .append(conflict.getReason()).append('|').append(conflict.getMessage()).append('|');
            conflict.getGroupIds().stream().sorted().forEach(id -> value.append(id).append(','));
            value.append('|');
        });
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.toString().getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }

    private void appendItems(StringBuilder value, String label, List<PromotionPreviewItem> items) {
        items.forEach(item -> value.append(label).append('|').append(item.getId()).append('|')
                .append(item.getFrom()).append('|').append(item.getTo()).append('|')
                .append(item.getAction()).append('|').append(item.getStudentCount()).append('|'));
    }
}
