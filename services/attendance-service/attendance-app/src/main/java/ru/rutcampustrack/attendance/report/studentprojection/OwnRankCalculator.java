package ru.rutcampustrack.attendance.report.studentprojection;

import java.math.BigInteger;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Pure exact rank calculator for an authoritative active participant roster.
 * Only the current participant's rank summary is returned.
 */
public final class OwnRankCalculator {

    /** One roster member and their already-calculated read metrics. */
    public record Participant(
            Long participantId,
            AttendanceMetricCalculator.Metrics metrics) {

        public Participant(Long participantId, int heldCount, int presentCount) {
            this(participantId,
                    AttendanceMetricCalculator.Metrics.forRanking(heldCount, presentCount));
        }

        public static Participant withCounts(long participantId, int heldCount, int presentCount) {
            return new Participant(participantId,
                    AttendanceMetricCalculator.Metrics.forRanking(heldCount, presentCount));
        }
    }

    /** Public rank summary; peer details intentionally have no representation. */
    public record Rank(Integer position, int participantCount, boolean available) {

        public Rank {
            if (participantCount < 0) {
                throw StudentProjectionException.invalidRoster("negative participant count");
            }
            if (available && (position == null || position < 1)) {
                throw StudentProjectionException.invalidRoster(
                        "available rank must have a positive position");
            }
            if (!available && position != null) {
                throw StudentProjectionException.invalidRoster(
                        "unavailable rank must have null position");
            }
        }

        public static Rank unavailable(int participantCount) {
            return new Rank(null, participantCount, false);
        }
    }

    /** One row in the authorized cohort ordering; unavailable metrics remain unranked. */
    public record RankedParticipant(long participantId, Integer position, BigDecimal percentage) {
    }

    /**
     * Calculates competition rank: one plus the number of peers with a
     * strictly greater exact present/held fraction. The roster is never
     * amended with an implicit self entry.
     */
    public static Rank calculate(
            Long ownParticipantId,
            Collection<Participant> authoritativeActiveRoster) {
        validateOwnId(ownParticipantId);
        return summarize(ownParticipantId, rankAll(authoritativeActiveRoster));
    }

    /**
     * Orders the same cohort used by {@link #calculate(Long, Collection)}.
     * Ratios use exact cross multiplication; rounded percentages are only for
     * display. Ties keep the same competition position and stable id ordering.
     */
    public static List<RankedParticipant> rankAll(Collection<Participant> authoritativeActiveRoster) {
        if (authoritativeActiveRoster == null) {
            throw StudentProjectionException.invalidRoster("roster is null");
        }

        List<Participant> participants = new ArrayList<>(authoritativeActiveRoster.size());
        java.util.Set<Long> participantIds = new java.util.HashSet<>();
        for (Participant participant : authoritativeActiveRoster) {
            if (participant == null) {
                throw StudentProjectionException.invalidRoster("roster contains null participant");
            }
            Long id = participant.participantId();
            validateParticipantId(id);
            if (!participantIds.add(id)) {
                throw StudentProjectionException.invalidRoster("duplicate participant id " + id);
            }
            validateMetrics(id, participant.metrics());
            participants.add(participant);
        }

        participants.sort((left, right) -> {
            boolean leftAvailable = hasRatio(left.metrics());
            boolean rightAvailable = hasRatio(right.metrics());
            if (leftAvailable != rightAvailable) {
                return leftAvailable ? -1 : 1;
            }
            if (leftAvailable) {
                int byRatio = compareRatio(right.metrics(), left.metrics());
                if (byRatio != 0) return byRatio;
            }
            return Long.compare(left.participantId(), right.participantId());
        });

        List<RankedParticipant> result = new ArrayList<>(participants.size());
        int rankPosition = 0;
        int availableCount = 0;
        Participant previousRanked = null;
        for (Participant participant : participants) {
            AttendanceMetricCalculator.Metrics metrics = participant.metrics();
            if (!hasRatio(metrics)) {
                result.add(new RankedParticipant(participant.participantId(), null, null));
                continue;
            }
            availableCount++;
            if (previousRanked == null || compareRatio(previousRanked.metrics(), metrics) != 0) {
                rankPosition = availableCount;
            }
            result.add(new RankedParticipant(
                    participant.participantId(), rankPosition, metrics.presentPercent()));
            previousRanked = participant;
        }
        return List.copyOf(result);
    }

    /** Converts the complete ordered cohort back to the existing own-only summary. */
    public static Rank summarize(Long ownParticipantId, List<RankedParticipant> rankedRoster) {
        validateOwnId(ownParticipantId);
        if (rankedRoster == null) {
            throw StudentProjectionException.invalidRoster("ranked roster is null");
        }
        RankedParticipant own = rankedRoster.stream()
                .filter(participant -> participant.participantId() == ownParticipantId)
                .findFirst()
                .orElse(null);
        if (own == null || own.position() == null) {
            return Rank.unavailable(rankedRoster.size());
        }
        return new Rank(own.position(), rankedRoster.size(), true);
    }

    private static boolean hasRatio(AttendanceMetricCalculator.Metrics metrics) {
        return metrics != null && metrics.heldCount() > 0;
    }

    /** Positive means left has the higher exact present/held fraction. */
    private static int compareRatio(
            AttendanceMetricCalculator.Metrics left,
            AttendanceMetricCalculator.Metrics right) {
        BigInteger leftCross = BigInteger.valueOf(left.presentCount())
                .multiply(BigInteger.valueOf(right.heldCount()));
        BigInteger rightCross = BigInteger.valueOf(right.presentCount())
                .multiply(BigInteger.valueOf(left.heldCount()));
        return leftCross.compareTo(rightCross);
    }

    private static void validateOwnId(Long ownParticipantId) {
        if (ownParticipantId == null || ownParticipantId <= 0) {
            throw StudentProjectionException.invalidRoster(
                    "own participant id must be a positive authoritative Long");
        }
    }

    private static void validateParticipantId(Long participantId) {
        if (participantId == null || participantId <= 0) {
            throw StudentProjectionException.invalidRoster(
                    "participant id must be a positive authoritative Long");
        }
    }

    private static void validateMetrics(
            Long participantId,
            AttendanceMetricCalculator.Metrics metrics) {
        if (metrics == null) {
            return;
        }
        try {
            // Re-read all public values so a future metrics implementation
            // cannot accidentally bypass the count invariants at this
            // boundary. The record constructor already performs the same
            // checks for the current implementation.
            int held = metrics.heldCount();
            int present = metrics.presentCount();
            int presentOrExcused = metrics.presentOrExcusedCount();
            int excused = metrics.excusedCount();
            int absent = metrics.absentCount();
            if (held < 0 || present < 0 || presentOrExcused < 0
                    || excused < 0 || absent < 0
                    || present > held
                    || presentOrExcused > held
                    || excused > held
                    || absent > held
                    || Math.addExact(Math.addExact(present, excused), absent) != held
                    || presentOrExcused != Math.addExact(present, excused)) {
                throw StudentProjectionException.invalidMetrics(
                        "participant " + participantId + " has inconsistent counts");
            }
        } catch (ArithmeticException exception) {
            throw StudentProjectionException.invalidMetrics(
                    "participant " + participantId + " metrics overflow");
        }
    }
}
