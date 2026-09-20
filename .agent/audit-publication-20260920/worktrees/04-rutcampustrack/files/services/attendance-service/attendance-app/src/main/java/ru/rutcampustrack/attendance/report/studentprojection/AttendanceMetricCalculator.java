package ru.rutcampustrack.attendance.report.studentprojection;

import ru.rutcampustrack.attendance.contract.enums.AttendanceStatus;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

/**
 * Pure calculator for the numeric part of a student attendance projection.
 *
 * <p>Lifecycle and attendance mark are separate inputs. A planned or active
 * occurrence is future data, whereas a closed occurrence with no stored mark
 * is projected as absent and reported as an integrity diagnostic. The
 * calculator never writes the projected mark.</p>
 */
public final class AttendanceMetricCalculator {

    private static final BigDecimal ONE_HUNDRED = BigDecimal.valueOf(100);
    private static final int PERCENT_SCALE = 2;

    /** A schedule lifecycle supplied by the authoritative schedule adapter. */
    public enum OccurrenceState {
        PLANNED,
        ACTIVE,
        CLOSED,
        CANCELLED,
        TRANSFERRED_OUT
    }

    /**
     * One authoritative physical occurrence. The id is never inferred or
     * synthesized here. For CLOSED, a null mark means that persistence missed
     * the close-time ABSENT materialization and is therefore a diagnostic.
     */
    public record Occurrence(
            Long occurrenceId,
            OccurrenceState state,
            AttendanceStatus attendanceStatus) {

        public static Occurrence planned(long occurrenceId) {
            return new Occurrence(occurrenceId, OccurrenceState.PLANNED, null);
        }

        public static Occurrence active(long occurrenceId) {
            return new Occurrence(occurrenceId, OccurrenceState.ACTIVE, null);
        }

        public static Occurrence closed(long occurrenceId, AttendanceStatus status) {
            return new Occurrence(occurrenceId, OccurrenceState.CLOSED, status);
        }

        public static Occurrence closedWithoutMark(long occurrenceId) {
            return new Occurrence(occurrenceId, OccurrenceState.CLOSED, null);
        }

        public static Occurrence cancelled(long occurrenceId) {
            return new Occurrence(occurrenceId, OccurrenceState.CANCELLED, null);
        }

        public static Occurrence transferredOut(long occurrenceId) {
            return new Occurrence(occurrenceId, OccurrenceState.TRANSFERRED_OUT, null);
        }
    }

    /** A count and its public 0..100 percentage. */
    public static final class Metric {
        private final int count;
        private final BigDecimal percent;

        private Metric(int count, BigDecimal percent) {
            if (count < 0) {
                throw StudentProjectionException.invalidMetrics("negative count " + count);
            }
            if (percent != null
                    && (percent.signum() < 0 || percent.compareTo(ONE_HUNDRED) > 0)) {
                throw StudentProjectionException.invalidMetrics(
                        "percentage outside 0..100: " + percent);
            }
            this.count = count;
            this.percent = percent == null
                    ? null
                    : percent.setScale(PERCENT_SCALE, RoundingMode.HALF_UP);
        }

        public int count() {
            return count;
        }

        public BigDecimal percent() {
            return percent;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof Metric metric)) return false;
            return count == metric.count && java.util.Objects.equals(percent, metric.percent);
        }

        @Override
        public int hashCode() {
            return java.util.Objects.hash(count, percent);
        }

        @Override
        public String toString() {
            return "Metric[count=" + count + ", percent=" + percent + "]";
        }
    }

    /** Read-only diagnostics from projection repair of missing close marks. */
    public record Diagnostics(int missingClosedCount) {

        public Diagnostics {
            if (missingClosedCount < 0) {
                throw StudentProjectionException.invalidMetrics(
                        "negative missing-closed count " + missingClosedCount);
            }
        }

        public static Diagnostics none() {
            return new Diagnostics(0);
        }
    }

    /**
     * The four required measures. {@code plannedCount} is the dense number of
     * all included physical occurrences (future and held); {@link
     * #futureCount()} isolates the PLANNED/ACTIVE part. Percentages are
     * derived from these counts, so a caller cannot provide contradictory
     * count/percentage pairs.
     */
    public record Metrics(
            int plannedCount,
            int heldCount,
            int presentCount,
            int presentOrExcusedCount,
            int excusedCount,
            int absentCount,
            Diagnostics diagnostics) {

        public Metrics {
            if (plannedCount < 0) {
                throw StudentProjectionException.invalidMetrics("negative planned count");
            }
            if (heldCount < 0) {
                throw StudentProjectionException.invalidMetrics("negative held count");
            }
            if (heldCount > plannedCount) {
                throw StudentProjectionException.invalidMetrics(
                        "held count cannot exceed included planned count");
            }
            if (presentCount < 0 || presentOrExcusedCount < 0
                    || excusedCount < 0 || absentCount < 0) {
                throw StudentProjectionException.invalidMetrics("negative status count");
            }
            if (diagnostics == null) {
                throw StudentProjectionException.invalidMetrics("diagnostics is null");
            }
            int presentAndExcused = safeAdd(
                    presentCount, excusedCount, "present + excused");
            int allStatuses = safeAdd(
                    presentAndExcused, absentCount, "present + excused + absent");
            if (allStatuses != heldCount) {
                throw StudentProjectionException.invalidMetrics(
                        "status counts do not add up to held count: "
                                + allStatuses + " != " + heldCount);
            }
            if (presentOrExcusedCount != presentAndExcused) {
                throw StudentProjectionException.invalidMetrics(
                        "present-or-excused count does not equal present + excused");
            }
            if (diagnostics.missingClosedCount() > absentCount) {
                throw StudentProjectionException.invalidMetrics(
                        "missing-closed count cannot exceed absent count");
            }
        }

        /** Counts-only constructor for rank inputs and focused tests. */
        public static Metrics forRanking(int heldCount, int presentCount) {
            if (heldCount < 0 || presentCount < 0 || presentCount > heldCount) {
                throw StudentProjectionException.invalidMetrics(
                        "ranking counts must satisfy 0 <= present <= held");
            }
            int absentCount = heldCount - presentCount;
            return new Metrics(
                    heldCount,
                    heldCount,
                    presentCount,
                    presentCount,
                    0,
                    absentCount,
                    Diagnostics.none());
        }

        public static Metrics empty() {
            return new Metrics(0, 0, 0, 0, 0, 0, Diagnostics.none());
        }

        /** Number of included occurrences that are still PLANNED or ACTIVE. */
        public int futureCount() {
            return plannedCount - heldCount;
        }

        public Metric present() {
            return metric(presentCount);
        }

        public Metric presentOrExcused() {
            return metric(presentOrExcusedCount);
        }

        public Metric excused() {
            return metric(excusedCount);
        }

        public Metric absent() {
            return metric(absentCount);
        }

        public BigDecimal presentPercent() {
            return present().percent();
        }

        public BigDecimal presentOrExcusedPercent() {
            return presentOrExcused().percent();
        }

        public BigDecimal excusedPercent() {
            return excused().percent();
        }

        public BigDecimal absentPercent() {
            return absent().percent();
        }

        public int missingClosedCount() {
            return diagnostics.missingClosedCount();
        }

        public int totalIncludedOccurrences() {
            return plannedCount;
        }

        public boolean hasMissingClosedMarks() {
            return diagnostics.missingClosedCount() > 0;
        }

        private Metric metric(int count) {
            return new Metric(count, heldCount == 0 ? null : percentage(count, heldCount));
        }

        private static int safeAdd(int left, int right, String expression) {
            try {
                return Math.addExact(left, right);
            } catch (ArithmeticException exception) {
                throw StudentProjectionException.invalidMetrics(
                        "integer overflow in " + expression);
            }
        }
    }

    /**
     * Calculates dense included and held metrics. This static entry point has
     * no state and can be called by a future adapter without dependency setup.
     */
    public static Metrics calculate(Collection<Occurrence> occurrences) {
        if (occurrences == null) {
            throw StudentProjectionException.invalidOccurrence("occurrence collection is null");
        }

        int plannedCount = 0;
        int heldCount = 0;
        int presentCount = 0;
        int excusedCount = 0;
        int absentCount = 0;
        int missingClosedCount = 0;
        Set<Long> occurrenceIds = new HashSet<>();

        for (Occurrence occurrence : occurrences) {
            if (occurrence == null) {
                throw StudentProjectionException.invalidOccurrence("occurrence is null");
            }
            Long id = occurrence.occurrenceId();
            if (id == null || id <= 0) {
                throw StudentProjectionException.invalidOccurrence(
                        "occurrence id must be a positive authoritative Long");
            }
            if (!occurrenceIds.add(id)) {
                throw StudentProjectionException.duplicateOccurrence(id);
            }

            OccurrenceState state = occurrence.state();
            if (state == null) {
                throw StudentProjectionException.invalidOccurrence(
                        "occurrence " + id + " has null lifecycle state");
            }

            AttendanceStatus status = occurrence.attendanceStatus();
            switch (state) {
                case PLANNED, ACTIVE -> {
                    // Check-in can legitimately persist a mark before the
                    // schedule transition reaches CLOSED. It stays outside H.
                    rejectIncludedStatus(id, status);
                    plannedCount = addOne(plannedCount, "planned count");
                }
                case CLOSED -> {
                    plannedCount = addOne(plannedCount, "planned count");
                    heldCount = addOne(heldCount, "held count");
                    if (status == null) {
                        absentCount = addOne(absentCount, "absent count");
                        missingClosedCount = addOne(missingClosedCount, "missing closed count");
                    } else {
                        switch (status) {
                            case PRESENT -> presentCount = addOne(presentCount, "present count");
                            case ABSENT -> absentCount = addOne(absentCount, "absent count");
                            case EXCUSED -> excusedCount = addOne(excusedCount, "excused count");
                            case FREE_ATTENDANCE ->
                                    throw StudentProjectionException.unsupportedAttendanceStatus(id, status);
                            case CANCELLED -> throw StudentProjectionException.invalidOccurrence(
                                    "CLOSED occurrence " + id + " has CANCELLED attendance mark");
                        }
                    }
                }
                case CANCELLED, TRANSFERRED_OUT -> {
                    // Historical excluded rows may retain an invalidated mark.
                    // Lifecycle decides exclusion, but FREE_ATTENDANCE remains
                    // an explicit unsupported-data failure.
                    rejectUnsupportedStatusOnly(id, status);
                }
            }
        }

        int presentOrExcusedCount = addCounts(
                presentCount, excusedCount, "present + excused count");
        return new Metrics(
                plannedCount,
                heldCount,
                presentCount,
                presentOrExcusedCount,
                excusedCount,
                absentCount,
                new Diagnostics(missingClosedCount));
    }

    private static int addOne(int value, String name) {
        try {
            return Math.addExact(value, 1);
        } catch (ArithmeticException exception) {
            throw StudentProjectionException.invalidMetrics("integer overflow in " + name);
        }
    }

    private static int addCounts(int left, int right, String name) {
        try {
            return Math.addExact(left, right);
        } catch (ArithmeticException exception) {
            throw StudentProjectionException.invalidMetrics("integer overflow in " + name);
        }
    }

    private static void rejectUnsupportedStatusOnly(Long id, AttendanceStatus status) {
        if (status == AttendanceStatus.FREE_ATTENDANCE) {
            throw StudentProjectionException.unsupportedAttendanceStatus(id, status);
        }
    }

    private static void rejectIncludedStatus(Long id, AttendanceStatus status) {
        if (status == AttendanceStatus.FREE_ATTENDANCE) {
            throw StudentProjectionException.unsupportedAttendanceStatus(id, status);
        }
        if (status == AttendanceStatus.CANCELLED) {
            throw StudentProjectionException.invalidOccurrence(
                    "included " + id + " has CANCELLED attendance mark");
        }
    }

    private static BigDecimal percentage(int count, int heldCount) {
        return BigDecimal.valueOf(count)
                .multiply(ONE_HUNDRED)
                .divide(BigDecimal.valueOf(heldCount), PERCENT_SCALE, RoundingMode.HALF_UP)
                .setScale(PERCENT_SCALE, RoundingMode.HALF_UP);
    }
}
