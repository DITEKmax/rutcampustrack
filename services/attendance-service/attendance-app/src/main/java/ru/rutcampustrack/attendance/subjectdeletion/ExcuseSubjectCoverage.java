package ru.rutcampustrack.attendance.subjectdeletion;

import ru.rutcampustrack.attendance.excuse.entity.ExcuseTicket;
import ru.rutcampustrack.attendance.studentrequest.entity.*;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Changes effective coverage only; original request and decision snapshots are retained. */
public final class ExcuseSubjectCoverage {
    private ExcuseSubjectCoverage() {}

    public static boolean affects(ExcuseTicket ticket, Set<Long> target) {
        return originals(ticket).stream().anyMatch(target::contains)
                || ticket.getLessonIds() != null
                        && ticket.getLessonIds().stream().anyMatch(target::contains);
    }

    public static List<Long> originals(ExcuseTicket ticket) {
        var ids =
                ticket.getOriginalLessonIds() == null
                        ? ticket.getLessonIds()
                        : ticket.getOriginalLessonIds();
        if (ids == null || ids.isEmpty() || ids.stream().anyMatch(id -> id == null || id <= 0))
            throw conflict();
        return ids.stream().distinct().sorted().toList();
    }

    public static boolean subtract(
            ExcuseTicket ticket,
            Set<Long> target,
            long subject,
            String operation,
            long actor,
            Instant now) {
        List<Long> before = originals(ticket);
        List<Long> remaining = before.stream().filter(id -> !target.contains(id)).toList();
        if (remaining.size() == before.size() && !affects(ticket, target)) return false;
        if (remaining.isEmpty())
            return false; // Caller deletes this sole-subject ticket and its owned attachments.
        var snapshots = ticket.getLessonSnapshots();
        if (snapshots == null || snapshots.isEmpty()) throw conflict();
        Set<Long> known = new HashSet<>();
        for (var snapshot : snapshots) {
            if (snapshot == null
                    || snapshot.getLessonId() == null
                    || snapshot.getSubjectId() == null
                    || snapshot.getSemesterId() == null
                    || !snapshot.getSemesterId().equals(ticket.getSemesterId())
                    || !java.util.Objects.equals(snapshot.getGroupId(), ticket.getGroupId()))
                throw conflict();
            if (target.contains(snapshot.getLessonId()) != (snapshot.getSubjectId() == subject))
                throw conflict();
            known.add(snapshot.getLessonId());
        }
        // Missing original-to-current transfer aliases are a safe conflict, not guessed ownership.
        if (!known.containsAll(remaining)) throw conflict();
        var history =
                ticket.getSubjectCoverageAdjustments() == null
                        ? new ArrayList<SubjectCoverageAdjustmentDocument>()
                        : new ArrayList<>(ticket.getSubjectCoverageAdjustments());
        if (history.stream().anyMatch(entry -> entry.operationId().equals(operation)))
            throw conflict();
        var originalSelection = ticket.getDecisionSelection();
        history.add(
                new SubjectCoverageAdjustmentDocument(
                        operation,
                        subject,
                        actor,
                        now,
                        java.util.stream.Stream.concat(
                                        before.stream(),
                                        snapshots.stream()
                                                .map(StudentLessonSnapshotDocument::getLessonId))
                                .filter(target::contains)
                                .distinct()
                                .sorted()
                                .toList(),
                        remaining,
                        List.copyOf(snapshots),
                        before,
                        originalSelection));
        ticket.setSubjectCoverageAdjustments(List.copyOf(history));
        ticket.setEffectiveOriginalLessonIds(remaining);
        ticket.setEffectiveLessonSnapshots(
                snapshots.stream().filter(s -> !target.contains(s.getLessonId())).toList());
        if (originalSelection != null) {
            ticket.setEffectiveDecisionSelection(
                    ExcuseDecisionSelectionDocument.builder()
                            .requestKey(originalSelection.getRequestKey())
                            .expectedRevision(originalSelection.getExpectedRevision())
                            .includedLessons(
                                    originalSelection.getIncludedLessons().stream()
                                            .filter(s -> !target.contains(s.getLessonId()))
                                            .toList())
                            .excludedLessons(
                                    originalSelection.getExcludedLessons().stream()
                                            .filter(s -> !target.contains(s.getLessonId()))
                                            .toList())
                            .build());
        }
        ticket.setLessonIds(
                ticket.getLessonIds().stream().filter(id -> !target.contains(id)).toList());
        ticket.setUpdatedAt(now);
        return true;
    }

    private static IllegalStateException conflict() {
        return new IllegalStateException("Mixed request coverage identity is unresolved");
    }
}
