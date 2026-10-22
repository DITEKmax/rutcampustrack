package ru.rutcampustrack.academic.groupavailability;

import ru.rutcampustrack.academic.contract.dto.group.*;

import java.time.LocalDate;
import java.util.List;

/** Fixed whole local lifecycle command. It contains no credentials, sessions or actor token. */
public record GroupAvailabilityCommand(
        Action action,
        long scopeGroupId,
        String requestDigest,
        UpdateGroupRequest update,
        Integer graduationYear,
        Plan cycle,
        List<GroupRevision> groups) {
    public GroupAvailabilityCommand {
        if (action == null
                || scopeGroupId < 0
                || requestDigest == null
                || !requestDigest.matches("[0-9a-f]{64}"))
            throw new IllegalArgumentException("fixed lifecycle intent required");
        groups = List.copyOf(groups);
        long previous = 0;
        for (var group : groups) {
            if (group.id() <= previous || group.revision() <= 0)
                throw new IllegalArgumentException("ordered complete group manifest required");
            previous = group.id();
        }
        if ((action == Action.DEACTIVATE || action == Action.RESTORE)
                        && (scopeGroupId <= 0
                                || groups.size() != 1
                                || groups.getFirst().id() != scopeGroupId
                                || cycle != null)
                || action == Action.DEACTIVATE && (update == null || update.active())
                || action != Action.DEACTIVATE && (update != null || graduationYear != null)
                || action == Action.DEACTIVATE
                        && (graduationYear == null
                                || graduationYear < 2000
                                || graduationYear > 9999)
                || (action == Action.MANUAL_CYCLE || action == Action.AUTOMATIC_CYCLE)
                        && cycle == null)
            throw new IllegalArgumentException("lifecycle action material differs");
        if (cycle != null) {
            var manifest = new java.util.TreeSet<Long>();
            for (var item :
                    java.util.stream.Stream.concat(
                                    cycle.promote().stream(), cycle.archive().stream())
                            .toList())
                if (item.id() <= 0 || !manifest.add(item.id()))
                    throw new IllegalArgumentException("cycle groups must be unique");
            if (!manifest.equals(
                    new java.util.TreeSet<>(groups.stream().map(GroupRevision::id).toList())))
                throw new IllegalArgumentException("cycle manifest must cover every local change");
        }
    }

    public enum Action {
        DEACTIVATE,
        RESTORE,
        MANUAL_CYCLE,
        AUTOMATIC_CYCLE
    }

    public record GroupRevision(long id, long revision) {}

    public record Item(
            long id, String from, String to, PromotionPreviewItem.Action action, long students) {
        PromotionPreviewItem dto() {
            return new PromotionPreviewItem(id, from, to, action, students);
        }

        static Item from(PromotionPreviewItem item) {
            return new Item(
                    item.getId(),
                    item.getFrom(),
                    item.getTo(),
                    item.getAction(),
                    item.getStudentCount());
        }
    }

    public record Skipped(
            long id,
            String name,
            PromotionSkippedItem.Reason reason,
            long students,
            PromotionPreviewItem.Action previousAction,
            String previousFrom,
            String previousTo) {}

    public record Conflict(String prefix, String reason, String message, List<Long> groupIds) {
        public Conflict {
            groupIds = List.copyOf(groupIds);
        }
    }

    public record Plan(
            long semester,
            LocalDate dateTo,
            Long scope,
            String previewVersion,
            List<Item> promote,
            List<Item> archive,
            List<Skipped> skipped,
            List<Conflict> conflicts) {
        public Plan {
            promote = List.copyOf(promote);
            archive = List.copyOf(archive);
            skipped = List.copyOf(skipped);
            conflicts = List.copyOf(conflicts);
            if (semester <= 0 || dateTo == null || previewVersion == null)
                throw new IllegalArgumentException("fixed cycle required");
        }

        public static Plan from(PromotionSummary plan) {
            return new Plan(
                    plan.getCycleSemesterId(),
                    plan.getCycleDateTo(),
                    plan.getGroupId(),
                    plan.getPreviewVersion(),
                    plan.getToPromote().stream().map(Item::from).toList(),
                    plan.getToArchive().stream().map(Item::from).toList(),
                    plan.getSkipped().stream()
                            .map(
                                    s ->
                                            new Skipped(
                                                    s.getId(),
                                                    s.getName(),
                                                    s.getReason(),
                                                    s.getStudentCount(),
                                                    s.getPreviousAction(),
                                                    s.getPreviousFrom(),
                                                    s.getPreviousTo()))
                            .toList(),
                    plan.getConflicts().stream()
                            .map(
                                    c ->
                                            new Conflict(
                                                    c.getPrefix(),
                                                    c.getReason(),
                                                    c.getMessage(),
                                                    c.getGroupIds()))
                            .toList());
        }

        public PromotionSummary result() {
            return new PromotionSummary(
                    promote.stream().map(Item::dto).toList(),
                    archive.stream().map(Item::dto).toList(),
                    skipped.stream()
                            .map(
                                    s ->
                                            new PromotionSkippedItem(
                                                    s.id(),
                                                    s.name(),
                                                    s.students(),
                                                    s.reason(),
                                                    s.previousAction(),
                                                    s.previousFrom(),
                                                    s.previousTo()))
                            .toList(),
                    conflicts.stream()
                            .map(
                                    c ->
                                            new PromotionSummary.PrefixConflict(
                                                    c.prefix(),
                                                    c.reason(),
                                                    c.message(),
                                                    c.groupIds()))
                            .toList(),
                    semester,
                    dateTo,
                    scope,
                    previewVersion,
                    false,
                    true);
        }
    }
}
