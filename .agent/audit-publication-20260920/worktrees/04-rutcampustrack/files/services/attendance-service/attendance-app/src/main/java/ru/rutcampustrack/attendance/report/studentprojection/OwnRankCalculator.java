package ru.rutcampustrack.attendance.report.studentprojection;

import java.math.BigInteger;
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

    /**
     * Calculates competition rank: one plus the number of peers with a
     * strictly greater exact present/held fraction. The roster is never
     * amended with an implicit self entry.
     */
    public static Rank calculate(
            Long ownParticipantId,
            Collection<Participant> authoritativeActiveRoster) {
        validateOwnId(ownParticipantId);
        if (authoritativeActiveRoster == null) {
            throw StudentProjectionException.invalidRoster("roster is null");
        }

        List<Participant> participants = new ArrayList<>(authoritativeActiveRoster.size());
        java.util.Set<Long> participantIds = new java.util.HashSet<>();
        Participant own = null;
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
            if (id.equals(ownParticipantId)) {
                own = participant;
            }
        }

        int participantCount = participants.size();
        if (own == null || own.metrics() == null || own.metrics().heldCount() == 0) {
            return Rank.unavailable(participantCount);
        }

        int ownPresent = own.metrics().presentCount();
        int ownHeld = own.metrics().heldCount();
        BigInteger ownPresentBig = BigInteger.valueOf(ownPresent);
        BigInteger ownHeldBig = BigInteger.valueOf(ownHeld);
        int strictlyBetter = 0;

        for (Participant participant : participants) {
            if (participant.participantId().equals(ownParticipantId)) {
                continue;
            }
            AttendanceMetricCalculator.Metrics metrics = participant.metrics();
            if (metrics == null || metrics.heldCount() == 0) {
                continue;
            }
            BigInteger peerPresentTimesOwnHeld = BigInteger.valueOf(metrics.presentCount())
                    .multiply(ownHeldBig);
            BigInteger ownPresentTimesPeerHeld = ownPresentBig
                    .multiply(BigInteger.valueOf(metrics.heldCount()));
            if (peerPresentTimesOwnHeld.compareTo(ownPresentTimesPeerHeld) > 0) {
                strictlyBetter++;
            }
        }

        return new Rank(Math.addExact(strictlyBetter, 1), participantCount, true);
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
