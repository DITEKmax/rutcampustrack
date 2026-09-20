package ru.rutcampustrack.academic.studentprojection;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

/** Immutable result of the student projection resolver. */
public record StudentProjectionScope(
        long studentId,
        long semesterId,
        LocalDate dateFrom,
        LocalDate dateTo,
        boolean terminalReadOnly,
        List<Long> activeRosterUserIds,
        List<Subject> subjects,
        List<MembershipSegment> ownMembershipSegments,
        RankVisibility rankVisibility,
        Long rankGroupId,
        boolean rankEligible,
        LocalDate serverDate) {

    public StudentProjectionScope {
        activeRosterUserIds = List.copyOf(Objects.requireNonNull(activeRosterUserIds,
                "activeRosterUserIds"));
        subjects = List.copyOf(Objects.requireNonNull(subjects, "subjects"));
        ownMembershipSegments = List.copyOf(Objects.requireNonNull(ownMembershipSegments,
                "ownMembershipSegments"));
        rankVisibility = Objects.requireNonNull(rankVisibility, "rankVisibility");
        Objects.requireNonNull(dateFrom, "dateFrom");
        Objects.requireNonNull(dateTo, "dateTo");
        Objects.requireNonNull(serverDate, "serverDate");
        if (dateTo.isBefore(dateFrom)) {
            throw new IllegalArgumentException("date_to must not precede date_from");
        }
    }

    public record MembershipSegment(
            long groupId,
            LocalDate dateFrom,
            LocalDate dateUntilExclusive,
            List<Long> subjectIds) {
        public MembershipSegment {
            subjectIds = List.copyOf(Objects.requireNonNull(subjectIds, "subjectIds"));
            Objects.requireNonNull(dateFrom, "dateFrom");
            Objects.requireNonNull(dateUntilExclusive, "dateUntilExclusive");
            if (groupId <= 0 || !dateUntilExclusive.isAfter(dateFrom)) {
                throw new IllegalArgumentException("membership segment must be positive and non-empty");
            }
        }
    }

    public record Subject(
            long subjectId,
            String name,
            String type,
            long groupId,
            List<String> lessonTypes) {
        public Subject {
            lessonTypes = List.copyOf(Objects.requireNonNull(lessonTypes, "lessonTypes"));
            if (subjectId <= 0 || groupId <= 0) {
                throw new IllegalArgumentException("subject identifiers must be positive");
            }
            if (name == null || name.isBlank() || type == null || type.isBlank()) {
                throw new IllegalArgumentException("subject name and type are required");
            }
        }
    }

    public enum RankVisibility {
        VISIBLE,
        HIDDEN
    }
}
