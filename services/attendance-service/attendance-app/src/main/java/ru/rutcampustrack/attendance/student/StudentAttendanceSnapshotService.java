package ru.rutcampustrack.attendance.student;

import org.springframework.stereotype.Service;
import ru.rutcampustrack.attendance.checkin.AttendanceDocument;
import ru.rutcampustrack.attendance.checkin.AttendanceRepository;
import ru.rutcampustrack.attendance.contract.enums.AttendanceSource;
import ru.rutcampustrack.attendance.contract.enums.AttendanceStatus;
import ru.rutcampustrack.attendance.contract.enums.LateCheckinRequestOrigin;
import ru.rutcampustrack.attendance.contract.enums.LateCheckinRequestStatus;
import ru.rutcampustrack.attendance.latecheckin.LateCheckinRepository;
import ru.rutcampustrack.attendance.latecheckin.entity.LateCheckinRequest;
import ru.rutcampustrack.attendance.grpc.ScheduleGrpcClient;
import ru.rutcampustrack.attendance.student.StudentCheckinException.Code;
import ru.rutcampustrack.attendance.student.StudentCheckinModels.Identity;
import ru.rutcampustrack.schedule.grpc.LessonResponse;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

@Service
public class StudentAttendanceSnapshotService {

    public enum EligibilityReason {
        ELIGIBLE,
        ALREADY_PRESENT,
        LESSON_CANCELLED,
        TOO_EARLY,
        WINDOW_CLOSED,
        GEO_BLOCKED,
        PENDING_CONFIRMATION,
        COOLDOWN,
        HEADMAN_ABSENT_REQUIRES_APPEAL,
        HEADMAN_USES_JOURNAL
    }

    public record Eligibility(boolean allowed, EligibilityReason reason, Instant retryAt) {
    }

    public record Entry(
            long lessonId,
            AttendanceStatus attendanceStatus,
            AttendanceSource attendanceSource,
            Instant markedAt,
            LateCheckinRequest request,
            Instant retryAt,
            Eligibility eligibility
    ) {
    }

    public record Snapshot(List<Entry> entries, Instant serverNow) {
    }

    private static final Duration BUFFER = Duration.ofMinutes(5);

    private final ScheduleGrpcClient scheduleGrpcClient;
    private final AttendanceRepository attendanceRepository;
    private final LateCheckinRepository lateCheckinRepository;
    private final CheckinPairStateRepository pairRepository;
    private final Clock clock;

    public StudentAttendanceSnapshotService(
            ScheduleGrpcClient scheduleGrpcClient,
            AttendanceRepository attendanceRepository,
            LateCheckinRepository lateCheckinRepository,
            CheckinPairStateRepository pairRepository,
            Clock clock
    ) {
        this.scheduleGrpcClient = scheduleGrpcClient;
        this.attendanceRepository = attendanceRepository;
        this.lateCheckinRepository = lateCheckinRepository;
        this.pairRepository = pairRepository;
        this.clock = clock;
    }

    public Snapshot getSnapshot(Identity identity, List<Long> lessonIds) {
        validateIdentity(identity);
        if (lessonIds == null || lessonIds.stream().anyMatch(id -> id == null || id <= 0)) {
            throw new StudentCheckinException(Code.INVALID_REQUEST, "lesson_ids содержит некорректное значение");
        }
        Instant now = clock.instant();
        List<LessonResponse> lessons = new ArrayList<>(lessonIds.size());
        for (Long id : lessonIds) {
            LessonResponse lesson = scheduleGrpcClient.getLessonById(id);
            if (lesson.getGroupId() != identity.groupId()) {
                throw new StudentCheckinException(Code.OUT_OF_SCOPE,
                        "Запрошенная пара принадлежит другой группе");
            }
            lessons.add(lesson);
        }

        List<Entry> entries = new ArrayList<>(lessons.size());
        for (LessonResponse lesson : lessons) {
            AttendanceDocument attendance = attendanceRepository
                    .findByLessonIdAndUserId(lesson.getId(), identity.userId()).orElse(null);
            LateCheckinRequest request = lateCheckinRepository
                    .findFirstByStudentIdAndLessonIdAndOriginOrderByUpdatedAtDesc(
                            identity.userId(), lesson.getId(), LateCheckinRequestOrigin.AUTO_GEO_FAILURE)
                    .orElse(null);
            LateCheckinRequest pendingRequest = lateCheckinRepository.findFirstByStudentIdAndLessonIdAndStatus(
                    identity.userId(), lesson.getId(), LateCheckinRequestStatus.PENDING).orElse(null);
            CheckinPairStateDocument pair = pairRepository.findById(
                    PairWriteCoordinator.pairId(identity.userId(), lesson.getId())).orElse(null);
            Instant retryAt = pair == null ? null : pair.getRetryAt();
            entries.add(new Entry(
                    lesson.getId(),
                    attendance == null ? null : attendance.getStatus(),
                    attendance == null ? null : attendance.getSource(),
                    attendance == null ? null : attendance.getUpdatedAt(),
                    request,
                    retryAt,
                    eligibility(identity, lesson, attendance, pendingRequest, retryAt, now)
            ));
        }
        return new Snapshot(List.copyOf(entries), now);
    }

    private Eligibility eligibility(
            Identity identity,
            LessonResponse lesson,
            AttendanceDocument attendance,
            LateCheckinRequest pendingRequest,
            Instant retryAt,
            Instant now
    ) {
        if (identity.headman()) return disabled(EligibilityReason.HEADMAN_USES_JOURNAL, null);
        if (pendingRequest != null && pendingRequest.getOrigin() != LateCheckinRequestOrigin.AUTO_GEO_FAILURE) {
            return disabled(EligibilityReason.PENDING_CONFIRMATION, null);
        }
        if ("cancelled".equalsIgnoreCase(lesson.getStatus())) {
            return disabled(EligibilityReason.LESSON_CANCELLED, null);
        }
        if (attendance != null && attendance.getStatus() == AttendanceStatus.PRESENT) {
            return disabled(EligibilityReason.ALREADY_PRESENT, null);
        }
        if (attendance != null && attendance.getStatus() == AttendanceStatus.ABSENT
                && attendance.getSource() == AttendanceSource.HEADMAN) {
            return disabled(EligibilityReason.HEADMAN_ABSENT_REQUIRES_APPEAL, null);
        }
        if (lesson.getIsGeoBlocked() || lesson.getIsBlockedByHeadman()) {
            return disabled(EligibilityReason.GEO_BLOCKED, null);
        }
        LocalDate date = LocalDate.parse(lesson.getDate());
        LocalTime startsAt = LocalTime.parse(lesson.getStartTime());
        LocalTime endsAt = LocalTime.parse(lesson.getEndTime());
        Instant opensAt = date.atTime(startsAt).atZone(clock.getZone()).toInstant().minus(BUFFER);
        Instant closesAt = date.atTime(endsAt).atZone(clock.getZone()).toInstant().plus(BUFFER);
        if (now.isBefore(opensAt)) return disabled(EligibilityReason.TOO_EARLY, null);
        if (now.isAfter(closesAt)) return disabled(EligibilityReason.WINDOW_CLOSED, null);
        if (retryAt != null && now.isBefore(retryAt)) {
            return disabled(EligibilityReason.COOLDOWN, retryAt);
        }
        return new Eligibility(true, EligibilityReason.ELIGIBLE, null);
    }

    private static Eligibility disabled(EligibilityReason reason, Instant retryAt) {
        return new Eligibility(false, reason, retryAt);
    }

    private static void validateIdentity(Identity identity) {
        if (identity == null || identity.userId() <= 0 || identity.groupId() == null) {
            throw new StudentCheckinException(Code.INVALID_SESSION, "Не хватает student/group scope");
        }
        if (!"STUDENT".equalsIgnoreCase(identity.role())) {
            throw new StudentCheckinException(Code.WRONG_ROLE, "Снимок доступен роли STUDENT");
        }
    }
}
