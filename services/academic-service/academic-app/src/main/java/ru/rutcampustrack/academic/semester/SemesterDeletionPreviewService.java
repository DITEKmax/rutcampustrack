package ru.rutcampustrack.academic.semester;

import org.springframework.stereotype.Service;

import ru.rutcampustrack.academic.contract.dto.semester.SemesterDeletionCounts;
import ru.rutcampustrack.academic.contract.dto.semester.SemesterDeletionPreviewResponse;
import ru.rutcampustrack.academic.contract.enums.SemesterDeletionPriorState;
import ru.rutcampustrack.academic.contract.enums.SemesterTransition;
import ru.rutcampustrack.academic.entity.Semester;
import ru.rutcampustrack.academic.exception.ConflictException;
import ru.rutcampustrack.academic.exception.SemesterDeletionDependencyUnavailableException;
import ru.rutcampustrack.academic.grpc.AttendanceSemesterDeletionGrpcClient;
import ru.rutcampustrack.academic.grpc.ScheduleGrpcClient;
import ru.rutcampustrack.academic.repository.SemesterRepository;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/** Read-only three-domain preview shared by initial confirmation and sealed drift detection. */
@Service
public class SemesterDeletionPreviewService {
    private static final org.slf4j.Logger previewLog =
            org.slf4j.LoggerFactory.getLogger(SemesterDeletionPreviewService.class);

    private final SemesterRepository semesters;
    private final AcademicSemesterDeletionSnapshotReader academicReader;
    private final ScheduleGrpcClient scheduleClient;
    private final AttendanceSemesterDeletionGrpcClient attendanceClient;

    public SemesterDeletionPreviewService(
            SemesterRepository semesters,
            AcademicSemesterDeletionSnapshotReader academicReader,
            ScheduleGrpcClient scheduleClient,
            AttendanceSemesterDeletionGrpcClient attendanceClient) {
        this.semesters = semesters;
        this.academicReader = academicReader;
        this.scheduleClient = scheduleClient;
        this.attendanceClient = attendanceClient;
    }

    public Snapshot preview(long semesterId) {
        Semester semester =
                semesters
                        .findByIdUncached(semesterId)
                        .orElseThrow(
                                () ->
                                        new ru.rutcampustrack.academic.contract.exception
                                                .ResourceNotFoundException(
                                                "Semester", "id", semesterId));
        if (semester.getArchiveTransition() != SemesterTransition.NONE
                || semester.isReleasePending()) {
            throw new ConflictException(
                    "Семестр уже участвует в переходе и не может получить preview удаления");
        }
        return gather(
                semester.getId(),
                semester.getName(),
                semester.getStateVersion(),
                priorState(semester));
    }

    /**
     * Uses the user's original state version while intentionally excluding newly installed
     * control-plane epochs.
     */
    public Snapshot underFence(
            long semesterId,
            String semesterName,
            long originalStateVersion,
            SemesterDeletionPriorState priorState) {
        return gather(semesterId, semesterName, originalStateVersion, priorState);
    }

    private Snapshot gather(
            long semesterId,
            String semesterName,
            long stateVersion,
            SemesterDeletionPriorState priorState) {
        AcademicSemesterDeletionSnapshotReader.Snapshot academic = academicReader.read(semesterId);
        final ru.rutcampustrack.schedule.grpc.SemesterDeletionParticipantPreview schedule;
        final ru.rutcampustrack.attendance.grpc.SemesterDeletionParticipantPreview attendance;
        boolean scheduleRead = true;
        try {
            schedule = scheduleClient.previewSemesterDeletion(semesterId);
            scheduleRead = false;
            attendance = attendanceClient.preview(semesterId);
        } catch (SemesterDeletionDependencyUnavailableException unavailable) {
            if (!scheduleRead) previewAttendanceFailure(unavailable);
            throw unavailable;
        } catch (RuntimeException unavailable) {
            if (!scheduleRead) previewAttendanceFailure(unavailable);
            throw new SemesterDeletionDependencyUnavailableException(
                    "Не удалось получить согласованный preview всех доменов", unavailable);
        }
        if (schedule.getSemesterId() != semesterId
                || attendance.getSemesterId() != semesterId
                || schedule.getObservedFenceVersion() < 0
                || attendance.getObservedFenceVersion() < 0) {
            previewLog.warn(
                    "semester.preview failure source=ACADEMIC_PREVIEW stage=ECHO_FENCE status=NONE"
                        + " cause=ECHO_OR_FENCE_INVALID");
            throw new SemesterDeletionDependencyUnavailableException(
                    "Домен вернул preview для другой или неподтверждённой версии семестра");
        }
        requireDigest(academic.participantDigest(), "ACADEMIC_DIGEST");
        requireDigest(schedule.getParticipantDigest(), "SCHEDULE_DIGEST");
        requireDigest(attendance.getParticipantDigest(), "ATTENDANCE_DIGEST");
        if (academic.assignments() < 0
                || academic.homeworks() < 0
                || schedule.getScheduleTemplatesCount() < 0
                || schedule.getOneOffLessonsCount() < 0
                || schedule.getLessonsCount() < 0
                || attendance.getAttendanceMarksCount() < 0
                || attendance.getStudentRequestsCount() < 0) {
            previewLog.warn(
                    "semester.preview failure source=ACADEMIC_PREVIEW stage=COUNTS status=NONE"
                        + " cause=NEGATIVE_COUNT");
            throw new SemesterDeletionDependencyUnavailableException(
                    "Домен вернул отрицательный preview count");
        }

        SemesterDeletionCounts counts =
                new SemesterDeletionCounts(
                        schedule.getScheduleTemplatesCount(),
                        schedule.getOneOffLessonsCount(),
                        schedule.getLessonsCount(),
                        academic.assignments(),
                        academic.homeworks(),
                        attendance.getAttendanceMarksCount(),
                        attendance.getStudentRequestsCount());
        String digest =
                digest(
                        semesterId,
                        stateVersion,
                        priorState,
                        academic.participantDigest(),
                        schedule.getParticipantDigest(),
                        attendance.getParticipantDigest(),
                        counts);
        return new Snapshot(
                semesterId,
                semesterName,
                priorState,
                stateVersion,
                digest,
                counts,
                academic.participantDigest(),
                schedule.getParticipantDigest(),
                attendance.getParticipantDigest(),
                schedule.getObservedFenceVersion(),
                attendance.getObservedFenceVersion());
    }

    public static SemesterDeletionPriorState priorState(Semester semester) {
        if (semester.isArchived()) return SemesterDeletionPriorState.ARCHIVED;
        return semester.isActive()
                ? SemesterDeletionPriorState.ACTIVE
                : SemesterDeletionPriorState.INACTIVE;
    }

    private static String digest(
            long semesterId,
            long stateVersion,
            SemesterDeletionPriorState state,
            String academicDigest,
            String scheduleDigest,
            String attendanceDigest,
            SemesterDeletionCounts counts) {
        String canonical =
                semesterId
                        + "|"
                        + stateVersion
                        + "|"
                        + state.name()
                        + "|"
                        + academicDigest
                        + "|"
                        + scheduleDigest
                        + "|"
                        + attendanceDigest
                        + "|"
                        + counts.scheduleTemplates()
                        + "|"
                        + counts.oneOffLessons()
                        + "|"
                        + counts.lessons()
                        + "|"
                        + counts.assignments()
                        + "|"
                        + counts.homeworks()
                        + "|"
                        + counts.attendanceMarks()
                        + "|"
                        + counts.studentRequests();
        try {
            return HexFormat.of()
                    .formatHex(
                            MessageDigest.getInstance("SHA-256")
                                    .digest(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }

    private static void requireDigest(String digest, String stage) {
        if (digest == null || !digest.matches("[0-9a-fA-F]{64}")) {
            previewLog.warn(
                    "semester.preview failure source=ACADEMIC_PREVIEW stage={} status=NONE"
                        + " cause=DIGEST_INVALID",
                    stage);
            throw new SemesterDeletionDependencyUnavailableException(
                    "Домен вернул некорректный opaque digest");
        }
    }

    public record Snapshot(
            long semesterId,
            String semesterName,
            SemesterDeletionPriorState priorState,
            long stateVersion,
            String digest,
            SemesterDeletionCounts counts,
            String academicDigest,
            String scheduleDigest,
            String attendanceDigest,
            long scheduleFenceVersion,
            long attendanceFenceVersion) {
        public SemesterDeletionPreviewResponse response() {
            return new SemesterDeletionPreviewResponse(
                    semesterId, semesterName, priorState, stateVersion, digest, counts);
        }
    }

    private static void previewAttendanceFailure(RuntimeException failure) {
        String status = previewStatus(failure);
        previewLog.warn(
                "semester.preview failure source=ACADEMIC_PREVIEW stage=ATTENDANCE_PREVIEW"
                    + " status={} cause={}",
                status,
                previewCause(failure, !"NONE".equals(status)));
    }

    private static String previewStatus(Throwable failure) {
        try {
            for (int depth = 0;
                    failure != null && depth < 8;
                    depth++, failure = failure.getCause()) {
                if (failure instanceof io.grpc.StatusRuntimeException rpc)
                    return rpc.getStatus().getCode().name();
                if (failure instanceof io.grpc.StatusException rpc)
                    return rpc.getStatus().getCode().name();
            }
        } catch (RuntimeException ignored) {
            return "NONE";
        }
        return "NONE";
    }

    private static String previewCause(Throwable failure, boolean rpc) {
        boolean dns = false, tls = false, connect = false, timeout = false, io = false;
        try {
            for (int depth = 0;
                    failure != null && depth < 8;
                    depth++, failure = failure.getCause()) {
                dns |=
                        failure instanceof java.net.UnknownHostException
                                || failure instanceof java.nio.channels.UnresolvedAddressException;
                tls |= failure instanceof javax.net.ssl.SSLException;
                connect |= failure instanceof java.net.ConnectException;
                timeout |=
                        failure instanceof java.net.SocketTimeoutException
                                || failure instanceof java.net.http.HttpTimeoutException
                                || failure instanceof java.util.concurrent.TimeoutException;
                io |= failure instanceof java.io.IOException;
            }
        } catch (RuntimeException ignored) {
            return "UNKNOWN";
        }
        if (dns) return "DNS";
        if (tls) return "TLS";
        if (connect) return "CONNECT";
        if (timeout) return "TIMEOUT";
        if (io) return "TRANSPORT_IO";
        return rpc ? "RPC_STATUS" : "UNKNOWN";
    }
}
