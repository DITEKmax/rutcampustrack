package ru.rutcampustrack.attendance.report;

import io.grpc.Context;
import io.grpc.Status;

import org.springframework.stereotype.Service;

import ru.rutcampustrack.attendance.grpc.*;
import ru.rutcampustrack.schedule.grpc.LessonInfo;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** ADMIN consumes the canonical group calculation without entering a Teacher authority path. */
@Service
public class AdminGroupStatsReadService {
    private static final int MAX_GROUPS = 10_000;
    private static final int MAX_LESSONS = 5_000;
    private static final Set<String> STATUSES =
            Set.of("planned", "active", "closed", "cancelled", "transferred");
    private final ScheduleGrpcClient schedule;
    private final ReportService reports;

    public AdminGroupStatsReadService(ScheduleGrpcClient schedule, ReportService reports) {
        this.schedule = schedule;
        this.reports = reports;
    }

    public AdminGroupStatsSnapshot read(AdminGroupStatsQuery query) {
        LocalDate from;
        LocalDate to;
        try {
            from = LocalDate.parse(query.getDateFrom());
            to = LocalDate.parse(query.getDateTo());
        } catch (RuntimeException invalid) {
            throw Status.INVALID_ARGUMENT
                    .withDescription("Invalid semester range")
                    .asRuntimeException();
        }
        Set<Long> groups = new java.util.LinkedHashSet<>(query.getGroupIdsList());
        if (query.getSemesterId() <= 0
                || to.isBefore(from)
                || groups.size() > MAX_GROUPS
                || groups.size() != query.getGroupIdsCount()
                || groups.stream().anyMatch(id -> id <= 0))
            throw Status.INVALID_ARGUMENT
                    .withDescription("Invalid group report scope")
                    .asRuntimeException();
        List<LessonInfo> lessons = new ArrayList<>();
        Set<Long> seen = new HashSet<>();
        for (Long group : groups) {
            if (Context.current().isCancelled())
                throw Status.DEADLINE_EXCEEDED.asRuntimeException();
            var response =
                    schedule.getLessonsByGroup(
                            group, query.getSemesterId(), from.toString(), to.toString());
            if (response == null) throw unavailable();
            for (var lesson : response.getLessonsList()) {
                LocalDate date;
                try {
                    date = LocalDate.parse(lesson.getDate());
                } catch (RuntimeException invalid) {
                    throw unavailable();
                }
                if (lesson.getId() <= 0
                        || !seen.add(lesson.getId())
                        || lesson.getGroupId() != group
                        || lesson.getSemesterId() != query.getSemesterId()
                        || date.isBefore(from)
                        || date.isAfter(to)
                        || !STATUSES.contains(lesson.getStatus().toLowerCase(Locale.ROOT)))
                    throw unavailable();
                if (seen.size() > MAX_LESSONS)
                    throw Status.RESOURCE_EXHAUSTED
                            .withDescription("Report scope exceeds bound")
                            .asRuntimeException();
                // Reuse the complete transfer-checked Schedule snapshot; avoid an N+1 refetch.
                LessonInfo.Builder value =
                        LessonInfo.newBuilder()
                                .setLessonId(lesson.getId())
                                .setGroupId(lesson.getGroupId())
                                .setSubjectId(lesson.getSubjectId())
                                .setSemesterId(lesson.getSemesterId())
                                .setDate(lesson.getDate())
                                .setStartsAt(lesson.getDate() + "T" + lesson.getStartTime())
                                .setLessonNumber(lesson.getLessonNumber())
                                .setOccurrenceId(lesson.getOccurrenceId())
                                .setLessonType(lesson.getLessonType())
                                .setGeneration(lesson.getGeneration())
                                .setRevision(lesson.getRevision())
                                .setStatus(lesson.getStatus());
                if (lesson.hasAssignmentId()) value.setAssignmentId(lesson.getAssignmentId());
                if (lesson.hasAssignedTeacherId())
                    value.setTeacherId(lesson.getAssignedTeacherId());
                lessons.add(value.build());
            }
        }
        var result =
                AdminGroupStatsSnapshot.newBuilder()
                        .setSemesterId(query.getSemesterId())
                        .setDateFrom(query.getDateFrom())
                        .setDateTo(query.getDateTo());
        for (var row : reports.aggregateAuthorizedGroups(query.getSemesterId(), groups, lessons)) {
            var value =
                    AdminGroupAttendanceTotals.newBuilder()
                            .setGroupId(row.groupId())
                            .setCountedLessons(row.lessonsCount())
                            .setPresent(row.present().numerator())
                            .setExcused(row.excused().numerator())
                            .setAbsent(row.absent().numerator())
                            .setOpportunities(row.present().denominator());
            if (row.present().denominator() > 0) {
                value.setPresentPercent(row.present().percent())
                        .setPresentOrExcusedPercent(row.presentOrExcused().percent());
            }
            result.addGroups(value);
        }
        return result.build();
    }

    private static RuntimeException unavailable() {
        return Status.UNAVAILABLE
                .withDescription("Incomplete lesson authority")
                .asRuntimeException();
    }
}
