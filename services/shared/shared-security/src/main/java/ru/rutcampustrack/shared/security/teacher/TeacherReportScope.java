package ru.rutcampustrack.shared.security.teacher;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;

/** Private, versioned provenance for a generated teacher report. Never a grant by itself. */
public record TeacherReportScope(int version, List<Tuple> tuples) {
    public TeacherReportScope {
        if (version != 1
                || tuples == null
                || tuples.isEmpty()
                || tuples.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("Invalid teacher report scope");
        }
        tuples =
                tuples.stream()
                        .distinct()
                        .sorted(
                                Comparator.comparingLong(Tuple::groupId)
                                        .thenComparingLong(Tuple::subjectId))
                        .toList();
    }

    public static TeacherReportScope of(Collection<Tuple> tuples) {
        return new TeacherReportScope(1, List.copyOf(tuples));
    }

    public String sha256() {
        StringBuilder canonical = new StringBuilder("teacher-report-scope-v1\n");
        for (Tuple tuple : tuples) {
            canonical.append(tuple.groupId()).append(':').append(tuple.subjectId()).append('\n');
        }
        try {
            return HexFormat.of()
                    .formatHex(
                            MessageDigest.getInstance("SHA-256")
                                    .digest(canonical.toString().getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 unavailable", impossible);
        }
    }

    public boolean containsAll(TeacherReportScope captured) {
        return captured != null && tuples.containsAll(captured.tuples());
    }

    @Override
    public String toString() {
        return "TeacherReportScope[version=" + version + "]";
    }

    public record Tuple(long groupId, long subjectId) {
        public Tuple {
            if (groupId <= 0 || subjectId <= 0)
                throw new IllegalArgumentException("Invalid teacher tuple");
        }

        @Override
        public String toString() {
            return "TeacherReportTuple";
        }
    }
}
