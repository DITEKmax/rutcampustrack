package ru.rutcampustrack.attendance.grpc;

import com.google.protobuf.ByteString;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import net.devh.boot.grpc.server.service.GrpcService;
import org.springframework.beans.factory.annotation.Autowired;
import ru.rutcampustrack.attendance.contract.dto.report.LessonAttendanceResponse;
import ru.rutcampustrack.attendance.contract.dto.report.StudentAttendanceEntry;
import ru.rutcampustrack.attendance.contract.enums.ExcuseTicketStatus;
import ru.rutcampustrack.attendance.exception.AccessDeniedException;
import ru.rutcampustrack.attendance.exception.AcademicServiceUnavailableException;
import ru.rutcampustrack.attendance.exception.BadRequestException;
import ru.rutcampustrack.attendance.exception.ScheduleServiceUnavailableException;
import ru.rutcampustrack.attendance.exception.ReportExportUnavailableException;
import ru.rutcampustrack.attendance.excuse.ExcuseRepository;
import ru.rutcampustrack.attendance.excuse.entity.ExcuseTicket;
import ru.rutcampustrack.attendance.report.ReportService;
import ru.rutcampustrack.attendance.report.teacher.TeacherAttendanceExportService;
import ru.rutcampustrack.attendance.report.teacher.TeacherStatsExportService;
import ru.rutcampustrack.attendance.studentrequest.AttachmentState;
import ru.rutcampustrack.attendance.studentrequest.RequestAttachmentRepository;
import ru.rutcampustrack.attendance.studentrequest.entity.RequestAttachmentDescriptorDocument;
import ru.rutcampustrack.attendance.studentrequest.entity.RequestAttachmentDocument;
import ru.rutcampustrack.schedule.grpc.LessonResponse;
import ru.rutcampustrack.shared.security.InternalJwtClaims;
import ru.rutcampustrack.teacher.grpc.TeacherAttachmentDownload;
import ru.rutcampustrack.teacher.grpc.TeacherAttendanceExportRequest;
import ru.rutcampustrack.teacher.grpc.TeacherAttendanceExportResponse;
import ru.rutcampustrack.teacher.grpc.TeacherAttendanceReadServiceGrpc;
import ru.rutcampustrack.teacher.grpc.TeacherExcuseAttachment;
import ru.rutcampustrack.teacher.grpc.TeacherExcuseAttachmentRequest;
import ru.rutcampustrack.teacher.grpc.TeacherExcuseRequest;
import ru.rutcampustrack.teacher.grpc.TeacherExcuseResponse;
import ru.rutcampustrack.teacher.grpc.TeacherJournalCell;
import ru.rutcampustrack.teacher.grpc.TeacherJournalRequest;
import ru.rutcampustrack.teacher.grpc.TeacherJournalResponse;
import ru.rutcampustrack.teacher.grpc.TeacherJournalStudent;
import ru.rutcampustrack.teacher.grpc.TeacherLessonRequest;
import ru.rutcampustrack.teacher.grpc.TeacherLessonResponse;
import ru.rutcampustrack.teacher.grpc.TeacherLessonSummary;
import ru.rutcampustrack.teacher.grpc.TeacherRosterEntry;
import ru.rutcampustrack.teacher.grpc.TeacherStatsFilter;
import ru.rutcampustrack.teacher.grpc.TeacherStatsExportRequest;
import ru.rutcampustrack.teacher.grpc.TeacherStatsExportResponse;
import ru.rutcampustrack.teacher.grpc.TeacherStatsMetric;
import ru.rutcampustrack.teacher.grpc.TeacherStatsRequest;
import ru.rutcampustrack.teacher.grpc.TeacherStatsResponse;
import ru.rutcampustrack.teacher.grpc.TeacherStatsScope;
import ru.rutcampustrack.teacher.grpc.TeacherStatsSort;
import ru.rutcampustrack.teacher.grpc.TeacherStatsSubjectOption;
import ru.rutcampustrack.teacher.grpc.TeacherStudentStats;
import ru.rutcampustrack.teacher.grpc.TeacherGroupStats;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/** Server-scoped, read-only teacher attendance and excuse boundary. */
@GrpcService
public final class TeacherAttendanceReadGrpcService
        extends TeacherAttendanceReadServiceGrpc.TeacherAttendanceReadServiceImplBase {

    private static final List<ExcuseTicketStatus> VISIBLE_TICKET_STATUSES =
            List.of(ExcuseTicketStatus.SUBMITTED, ExcuseTicketStatus.APPROVED);

    private final ReportService reportService;
    private final ScheduleGrpcClient scheduleGrpcClient;
    private final AcademicGrpcClient academicGrpcClient;
    private final ExcuseRepository excuseRepository;
    private final RequestAttachmentRepository attachmentRepository;
    private final Clock clock;
    private final TeacherAttendanceExportService exportService;
    private final TeacherStatsExportService statsExportService;

    public TeacherAttendanceReadGrpcService(ReportService reportService,
                                            ScheduleGrpcClient scheduleGrpcClient,
                                            AcademicGrpcClient academicGrpcClient,
                                            ExcuseRepository excuseRepository,
                                            RequestAttachmentRepository attachmentRepository,
                                            Clock clock) {
        this(reportService, scheduleGrpcClient, academicGrpcClient, excuseRepository,
                attachmentRepository, clock, null, null);
    }

    @Autowired
    public TeacherAttendanceReadGrpcService(ReportService reportService,
                                            ScheduleGrpcClient scheduleGrpcClient,
                                            AcademicGrpcClient academicGrpcClient,
                                            ExcuseRepository excuseRepository,
                                            RequestAttachmentRepository attachmentRepository,
                                            Clock clock,
                                            TeacherAcademicGrpcClient teacherAcademicGrpcClient,
                                            DocumentRendererGrpcClient documentRendererGrpcClient) {
        this.reportService = reportService;
        this.scheduleGrpcClient = scheduleGrpcClient;
        this.academicGrpcClient = academicGrpcClient;
        this.excuseRepository = excuseRepository;
        this.attachmentRepository = attachmentRepository;
        this.clock = clock;
        this.exportService = new TeacherAttendanceExportService(reportService, scheduleGrpcClient,
                academicGrpcClient, teacherAcademicGrpcClient, documentRendererGrpcClient,
                this::readTeacherJournal);
        this.statsExportService = new TeacherStatsExportService(reportService, academicGrpcClient,
                teacherAcademicGrpcClient, documentRendererGrpcClient);
    }

    @Override
    public void getTeacherLesson(TeacherLessonRequest request,
                                 StreamObserver<TeacherLessonResponse> responseObserver) {
        try {
            InternalJwtClaims claims = requireTeacher();
            long lessonId = positive(request.getLessonId(), "lesson_id");
            LessonAttendanceResponse response = reportService
                    .getTeacherLessonAttendance(lessonId, claims.userId());
            LessonResponse lesson = scheduleGrpcClient.getLessonById(lessonId);
            Map<Long, TicketMarker> markers = ticketMarkers(lessonId);
            TeacherLessonResponse.Builder result = TeacherLessonResponse.newBuilder()
                    .setLesson(toSummary(lesson))
                    .setServerNow(clock.instant().toString());
            for (StudentAttendanceEntry entry : response.getEntries()) {
                result.addRoster(toRosterEntry(entry, markers.get(entry.getUserId())));
            }
            responseObserver.onNext(result.build());
            responseObserver.onCompleted();
        } catch (RuntimeException error) {
            responseObserver.onError(toStatus(error));
        }
    }

    @Override
    public void getTeacherJournal(TeacherJournalRequest request,
                                   StreamObserver<TeacherJournalResponse> responseObserver) {
        try {
            InternalJwtClaims claims = requireTeacher();
            responseObserver.onNext(readTeacherJournal(claims.userId(), request.getLessonIdsList()));
            responseObserver.onCompleted();
        } catch (RuntimeException error) {
            responseObserver.onError(toStatus(error));
        }
    }

    @Override
    public void exportTeacherAttendance(TeacherAttendanceExportRequest request,
                                       StreamObserver<TeacherAttendanceExportResponse> responseObserver) {
        try {
            InternalJwtClaims claims = requireTeacher();
            responseObserver.onNext(exportService.export(request, claims.userId()));
            responseObserver.onCompleted();
        } catch (RuntimeException error) {
            responseObserver.onError(toStatus(error));
        }
    }

    private TeacherJournalResponse readTeacherJournal(long teacherId, List<Long> requestedIds) {
        List<Long> lessonIds = requestedIds.stream().filter(id -> id > 0).distinct().toList();
        if (lessonIds.isEmpty() || lessonIds.size() > 100 || lessonIds.size() != requestedIds.size()) {
            throw Status.INVALID_ARGUMENT.withDescription("lesson_ids must contain 1..100 positive ids")
                    .asRuntimeException();
        }
        List<LessonRoster> rosters = new ArrayList<>();
        for (Long lessonId : lessonIds) {
            LessonAttendanceResponse attendance = reportService
                    .getTeacherLessonAttendance(lessonId, teacherId);
            LessonResponse lesson = scheduleGrpcClient.getLessonById(lessonId);
            Map<Long, TicketMarker> markers = ticketMarkers(lessonId);
            List<TeacherRosterEntry> entries = attendance.getEntries().stream()
                    .map(entry -> toRosterEntry(entry, markers.get(entry.getUserId())))
                    .toList();
            rosters.add(new LessonRoster(lesson, entries));
        }
        JournalScope scope = null;
        for (LessonRoster roster : rosters) {
            JournalScope lessonScope = JournalScope.from(roster.lesson());
            if (scope == null) {
                scope = lessonScope;
            } else if (!scope.equals(lessonScope)) {
                throw Status.INVALID_ARGUMENT.withDescription(
                                "lesson_ids must share group, subject, lesson type, and semester")
                        .asRuntimeException();
            }
        }
        rosters.sort(Comparator.comparing((LessonRoster value) -> value.lesson().getDate())
                .thenComparing(value -> value.lesson().getLessonNumber())
                .thenComparing(value -> value.lesson().getId()));

        Map<Long, StudentJournalAccumulator> students = new HashMap<>();
        List<TeacherLessonSummary> lessons = new ArrayList<>();
        for (LessonRoster roster : rosters) {
            lessons.add(toSummary(roster.lesson()));
            for (TeacherRosterEntry entry : roster.entries()) {
                StudentJournalAccumulator student = students.computeIfAbsent(
                        entry.getStudentId(), id -> new StudentJournalAccumulator(
                                entry.getStudentId(), entry.getDisplayName()));
                student.cells().put(roster.lesson().getId(), TeacherJournalCell.newBuilder()
                        .setLessonId(roster.lesson().getId())
                        .setStatus(entry.getStatus())
                        .setSymbol(entry.getSymbol())
                        .setSource(entry.getSource())
                        .setRecordPresent(entry.getRecordPresent())
                        .setPendingTicket(entry.getPendingTicket())
                        .setAutoAbsent(entry.getAutoAbsent())
                        .setTicketId(entry.getTicketId())
                        .setExcuseType(entry.getExcuseType())
                        .setExcuseReason(entry.getExcuseReason())
                        .build());
            }
        }
        List<TeacherJournalStudent> resultStudents = students.values().stream()
                .sorted(Comparator.comparing(StudentJournalAccumulator::displayName,
                        Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER))
                        .thenComparingLong(StudentJournalAccumulator::studentId))
                .map(student -> TeacherJournalStudent.newBuilder()
                        .setStudentId(student.studentId())
                        .setDisplayName(student.displayName())
                        .addAllCells(student.cells().entrySet().stream()
                                .sorted(Map.Entry.comparingByKey())
                                .map(Map.Entry::getValue)
                                .toList())
                        .build())
                .toList();
        return TeacherJournalResponse.newBuilder()
                .addAllLessons(lessons)
                .addAllStudents(resultStudents)
                .setServerNow(clock.instant().toString())
                .build();
    }

    @Override
    public void getTeacherStats(TeacherStatsRequest request,
                                StreamObserver<TeacherStatsResponse> responseObserver) {
        try {
            InternalJwtClaims claims = requireTeacher();
            TeacherStatsScope scope = request.getScope();
            ReportService.TeacherStatsQuery query = statsQuery(request);
            ReportService.TeacherStatsResult result = reportService.getTeacherStats(query, claims.userId());
            TeacherStatsResponse.Builder response = TeacherStatsResponse.newBuilder()
                    .setScope(scope)
                    .setSemesterId(result.semesterId())
                    .setLessonsCount(result.lessonsCount())
                    .setServerNow(result.serverNow().toString());
            if (result.periodFrom() != null) response.setPeriodFrom(result.periodFrom().toString());
            if (result.periodTo() != null) response.setPeriodTo(result.periodTo().toString());
            result.students().stream().map(this::toStudentStats).forEach(response::addStudents);
            result.groups().stream().map(this::toGroupStats).forEach(response::addGroups);
            result.subjectOptions().stream().map(this::toSubjectOption).forEach(response::addSubjectOptions);
            responseObserver.onNext(response.build());
            responseObserver.onCompleted();
        } catch (RuntimeException error) {
            responseObserver.onError(toStatus(error));
        }
    }

    @Override
    public void exportTeacherStats(TeacherStatsExportRequest request,
                                   StreamObserver<TeacherStatsExportResponse> responseObserver) {
        try {
            InternalJwtClaims claims = requireTeacher();
            if (!request.hasStatsRequest()) {
                throw Status.INVALID_ARGUMENT.withDescription("stats_request is required").asRuntimeException();
            }
            if (statsExportService == null) {
                throw Status.UNAVAILABLE.withDescription("Teacher statistics export is not configured")
                        .asRuntimeException();
            }
            responseObserver.onNext(statsExportService.export(
                    statsQuery(request.getStatsRequest()), claims.userId(), request.getFormat()));
            responseObserver.onCompleted();
        } catch (RuntimeException error) {
            responseObserver.onError(toStatus(error));
        }
    }

    private static ReportService.TeacherStatsQuery statsQuery(TeacherStatsRequest request) {
        TeacherStatsScope scope = request.getScope();
        if (scope != TeacherStatsScope.TEACHER_STATS_STUDENTS
                && scope != TeacherStatsScope.TEACHER_STATS_GROUPS) {
            throw Status.INVALID_ARGUMENT.withDescription("stats scope is required").asRuntimeException();
        }
        List<Long> lessonIds = request.getLessonIdsList().stream()
                .filter(id -> id > 0)
                .distinct()
                .toList();
        if (lessonIds.size() != request.getLessonIdsCount() || lessonIds.size() > 5_000) {
            throw Status.INVALID_ARGUMENT.withDescription("lesson_ids must contain unique positive ids")
                    .asRuntimeException();
        }
        ReportService.TeacherStatsScope domainScope = scope == TeacherStatsScope.TEACHER_STATS_STUDENTS
                ? ReportService.TeacherStatsScope.STUDENTS : ReportService.TeacherStatsScope.GROUPS;
        return new ReportService.TeacherStatsQuery(
                lessonIds,
                domainScope,
                request.getGroupId(),
                request.getSubjectId(),
                request.getLessonTypesList(),
                request.getSortsList().stream()
                        .map(sort -> new ReportService.TeacherStatsSort(sort.getColumn(), sort.getDescending()))
                        .toList(),
                request.getFiltersList().stream()
                        .map(filter -> new ReportService.TeacherStatsFilter(
                                filter.getColumn(),
                                filter.getContains(),
                                filter.hasMinPercent() ? filter.getMinPercent() : null,
                                filter.hasMaxPercent() ? filter.getMaxPercent() : null,
                                filter.hasMinValue() ? filter.getMinValue() : null,
                                filter.hasMaxValue() ? filter.getMaxValue() : null))
                        .toList(),
                positive(request.getSemesterId(), "semester_id"));
    }

    private TeacherStudentStats toStudentStats(ReportService.TeacherStudentStats row) {
        return TeacherStudentStats.newBuilder()
                .setStudentId(row.studentId())
                .setDisplayName(value(row.displayName()))
                .setPresent(toMetric(row.present()))
                .setPresentOrExcused(toMetric(row.presentOrExcused()))
                .setExcused(toMetric(row.excused()))
                .setAbsent(toMetric(row.absent()))
                .build();
    }

    private TeacherGroupStats toGroupStats(ReportService.TeacherGroupStats row) {
        return TeacherGroupStats.newBuilder()
                .setGroupId(row.groupId())
                .setGroupName(value(row.groupName()))
                .setLessonsCount(row.lessonsCount())
                .setPresent(toMetric(row.present()))
                .setPresentOrExcused(toMetric(row.presentOrExcused()))
                .setExcused(toMetric(row.excused()))
                .setAbsent(toMetric(row.absent()))
                .build();
    }

    private TeacherStatsSubjectOption toSubjectOption(ReportService.TeacherStatsSubjectOption option) {
        return TeacherStatsSubjectOption.newBuilder()
                .setGroupId(option.groupId())
                .setSubjectId(option.subjectId())
                .setSubjectName(value(option.subjectName()))
                .addAllLessonTypes(option.lessonTypes())
                .build();
    }

    private static TeacherStatsMetric toMetric(ReportService.TeacherMetric metric) {
        return TeacherStatsMetric.newBuilder()
                .setNumerator(metric.numerator())
                .setDenominator(metric.denominator())
                .setPercent(metric.percent())
                .build();
    }

    @Override
    public void getTeacherExcuse(TeacherExcuseRequest request,
                                 StreamObserver<TeacherExcuseResponse> responseObserver) {
        try {
            InternalJwtClaims claims = requireTeacher();
            String requestId = required(request.getRequestId(), "request_id");
            ExcuseTicket ticket = excuseRepository.findById(requestId)
                    .orElseThrow(() -> Status.NOT_FOUND.withDescription("request not found")
                            .asRuntimeException());
            List<LessonResponse> lessons = authorizedTicketLessons(ticket, claims.userId());
            if (lessons.isEmpty()) {
                throw Status.NOT_FOUND.withDescription("request not found")
                        .asRuntimeException();
            }
            TeacherExcuseResponse.Builder result = TeacherExcuseResponse.newBuilder()
                    .setRequestId(ticket.getId())
                    .setRequestKind("EXCUSE")
                    .setStatus(ticket.getStatus() == null ? "" : ticket.getStatus().name().toLowerCase(Locale.ROOT))
                    .setStudentId(value(ticket.getStudentId()))
                    .setStudentName(value(ticket.getStudentName()))
                    .setGroupId(value(ticket.getGroupId()))
                    .setExcuseType(ticket.getExcuseType() == null ? "" : ticket.getExcuseType().name().toLowerCase(Locale.ROOT))
                    .setComment(value(ticket.getComment()))
                    .setCreatedAt(ticket.getCreatedAt() == null ? "" : ticket.getCreatedAt().toString())
                    .setDecisionBy(value(ticket.getDecisionBy()))
                    .setDecisionAt(ticket.getDecisionAt() == null ? "" : ticket.getDecisionAt().toString())
                    .setDecisionComment(value(ticket.getDecisionComment()))
                    .setServerNow(clock.instant().toString());
            for (LessonResponse lesson : lessons) {
                result.addLessons(toSummary(lesson));
            }
            if (ticket.getAttachmentDescriptors() != null) {
                for (RequestAttachmentDescriptorDocument descriptor : ticket.getAttachmentDescriptors()) {
                    if (descriptor == null || descriptor.getId() == null
                            || !AttachmentState.ACTIVE.equals(descriptor.getState())
                            || expired(descriptor.getExpiresAt())) {
                        continue;
                    }
                    result.addAttachments(TeacherExcuseAttachment.newBuilder()
                            .setAttachmentId(descriptor.getId())
                            .setFileName(value(descriptor.getName()))
                            .setContentType(value(descriptor.getContentType()))
                            .setSize(value(descriptor.getSize()))
                            .setUploadedAt(descriptor.getUploadedAt() == null ? "" : descriptor.getUploadedAt().toString())
                            .setExpiresAt(descriptor.getExpiresAt() == null ? "" : descriptor.getExpiresAt().toString())
                            .build());
                }
            }
            responseObserver.onNext(result.build());
            responseObserver.onCompleted();
        } catch (RuntimeException error) {
            responseObserver.onError(toStatus(error));
        }
    }

    @Override
    public void downloadTeacherExcuseAttachment(TeacherExcuseAttachmentRequest request,
                                                 StreamObserver<TeacherAttachmentDownload> responseObserver) {
        try {
            InternalJwtClaims claims = requireTeacher();
            String requestId = required(request.getRequestId(), "request_id");
            String attachmentId = required(request.getAttachmentId(), "attachment_id");
            ExcuseTicket ticket = excuseRepository.findById(requestId)
                    .orElseThrow(() -> Status.NOT_FOUND.withDescription("request not found")
                            .asRuntimeException());
            if (authorizedTicketLessons(ticket, claims.userId()).isEmpty()) {
                throw Status.NOT_FOUND.withDescription("request not found")
                        .asRuntimeException();
            }
            boolean described = ticket.getAttachmentDescriptors() != null
                    && ticket.getAttachmentDescriptors().stream()
                    .anyMatch(descriptor -> descriptor != null
                            && attachmentId.equals(descriptor.getId())
                            && AttachmentState.ACTIVE.equals(descriptor.getState())
                            && !expired(descriptor.getExpiresAt()));
            if (!described) {
                throw Status.NOT_FOUND.withDescription("attachment not found")
                        .asRuntimeException();
            }
            RequestAttachmentDocument document = attachmentRepository.findById(attachmentId)
                    .filter(value -> requestId.equals(value.getRequestId()))
                    .filter(value -> Objects.equals(ticket.getStudentId(), value.getOwnerStudentId()))
                    .filter(value -> AttachmentState.ACTIVE.equals(value.getState()))
                    .filter(value -> !expired(value.getExpiresAt()))
                    .orElseThrow(() -> Status.NOT_FOUND.withDescription("attachment not found")
                            .asRuntimeException());
            byte[] data = document.getData() == null ? new byte[0] : document.getData().getData();
            responseObserver.onNext(TeacherAttachmentDownload.newBuilder()
                    .setFileName(value(document.getName()))
                    .setContentType(value(document.getContentType()))
                    .setSize(data.length)
                    .setData(ByteString.copyFrom(data))
                    .build());
            responseObserver.onCompleted();
        } catch (RuntimeException error) {
            responseObserver.onError(toStatus(error));
        }
    }

    private InternalJwtClaims requireTeacher() {
        InternalJwtClaims claims = TeacherAttendanceGrpcIdentity.requireClaims();
        if (!"TEACHER".equals(claims.domainRole())
                || !"ACTIVE".equals(claims.status())
                || claims.userId() <= 0) {
            throw Status.PERMISSION_DENIED.withDescription("teacher role is required")
                    .asRuntimeException();
        }
        return claims;
    }

    private Map<Long, TicketMarker> ticketMarkers(long lessonId) {
        Map<Long, TicketMarker> result = new HashMap<>();
        for (ExcuseTicket ticket : excuseRepository.findByLessonIdsInAndStatusIn(
                List.of(lessonId), VISIBLE_TICKET_STATUSES)) {
            if (ticket == null || ticket.getStudentId() == null || ticket.getLessonIds() == null
                    || !ticket.getLessonIds().contains(lessonId)) {
                continue;
            }
            boolean pending = ticket.getStatus() == ExcuseTicketStatus.SUBMITTED;
            TicketMarker previous = result.get(ticket.getStudentId());
            if (previous == null || (pending && !previous.pending())) {
                result.put(ticket.getStudentId(), new TicketMarker(ticket.getId(), pending));
            }
        }
        return result;
    }

    private TeacherRosterEntry toRosterEntry(StudentAttendanceEntry entry, TicketMarker marker) {
        boolean noTicketExcuse = marker == null
                && "excused".equalsIgnoreCase(entry.getStatus());
        boolean automaticAbsent = entry.getStatus() != null
                && "absent".equalsIgnoreCase(entry.getStatus())
                && "auto_scheduler".equalsIgnoreCase(entry.getSource());
        TeacherRosterEntry.Builder result = TeacherRosterEntry.newBuilder()
                .setStudentId(value(entry.getUserId()))
                .setDisplayName(value(entry.getDisplayName()))
                .setStatus(value(entry.getStatus()))
                .setSymbol(value(entry.getSymbol()))
                // Preserve a machine-readable distinction for a manual EXCUSED
                // record that has no persisted student ticket.  The UI must not
                // invent a request card for this case.
                .setSource(noTicketExcuse ? "NO_TICKET" : value(entry.getSource()))
                .setRecordPresent(entry.getStatus() != null)
                .setPendingTicket(marker != null && marker.pending())
                .setAutoAbsent(automaticAbsent)
                .setTicketId(marker == null ? "" : value(marker.ticketId()))
                .setExcuseType(value(entry.getExcuseType()))
                .setExcuseReason(value(entry.getExcuseReason()))
                .setExcuseComment(value(entry.getComment()))
                .setAttachmentId(value(entry.getAttachmentId()))
                .setAttachmentName(value(entry.getAttachmentName()))
                .setAttachmentContentType(value(entry.getAttachmentContentType()))
                .setAttachmentSize(value(entry.getAttachmentSize()));
        return result.build();
    }

    private List<LessonResponse> authorizedTicketLessons(ExcuseTicket ticket, long teacherId) {
        List<Long> lessonIds = ticket.getLessonIds() == null ? List.of() : ticket.getLessonIds();
        List<LessonResponse> result = new ArrayList<>();
        for (Long lessonId : lessonIds.stream().filter(Objects::nonNull).distinct().toList()) {
            try {
                LessonResponse lesson = scheduleGrpcClient.getLessonById(lessonId);
                reportService.authorizeTeacherOwnLesson(lesson, teacherId);
                result.add(lesson);
            } catch (AccessDeniedException | ru.rutcampustrack.attendance.contract.exception.ResourceNotFoundException ignored) {
                // A mixed ticket is projected only over the teacher's concrete lessons.
            }
        }
        return result;
    }

    private TeacherLessonSummary toSummary(LessonResponse lesson) {
        AcademicGrpcClient.SubjectDetails subject = academicGrpcClient
                .getSubjectDetailsByIds(List.of(lesson.getSubjectId()))
                .get(lesson.getSubjectId());
        String groupName = academicGrpcClient.getGroup(lesson.getGroupId()).getName();
        return TeacherLessonSummary.newBuilder()
                .setLessonId(lesson.getId())
                .setScheduleItemId(lesson.getScheduleItemId())
                .setGroupId(lesson.getGroupId())
                .setGroupName(value(groupName))
                .setSubjectId(lesson.getSubjectId())
                .setSubjectName(subject == null ? "" : value(subject.name()))
                .setSemesterId(lesson.getSemesterId())
                .setAssignmentId(lesson.getAssignmentId())
                .setAssignedTeacherId(lesson.getAssignedTeacherId())
                .setLessonType(value(lesson.getLessonType()))
                .setLessonDate(value(lesson.getDate()))
                .setLessonNumber(lesson.getLessonNumber())
                .setStartTime(value(lesson.getStartTime()))
                .setEndTime(value(lesson.getEndTime()))
                .setRoom(value(lesson.getRoom()))
                .setStatus(value(lesson.getStatus()))
                .setOccurrenceId(Long.toString(lesson.getOccurrenceId()))
                .setGeneration(safeInt(lesson.getGeneration()))
                .setRevision(safeInt(lesson.getRevision()))
                .setOneOff(lesson.getScheduleItemId() <= 0)
                .setMoved(lesson.getRevision() > 1 || lesson.getGeneration() > 1)
                .setCancelled("cancelled".equalsIgnoreCase(lesson.getStatus()))
                .build();
    }

    private boolean expired(Instant expiresAt) {
        return expiresAt != null && !expiresAt.isAfter(clock.instant());
    }

    private static int safeInt(long value) {
        return (int) Math.max(Integer.MIN_VALUE, Math.min(Integer.MAX_VALUE, value));
    }

    private static long positive(long value, String field) {
        if (value <= 0) {
            throw Status.INVALID_ARGUMENT.withDescription(field + " must be positive")
                    .asRuntimeException();
        }
        return value;
    }

    private static String required(String value, String field) {
        if (value == null || value.isBlank()) {
            throw Status.INVALID_ARGUMENT.withDescription(field + " is required")
                    .asRuntimeException();
        }
        return value;
    }

    private static String value(String value) {
        return value == null ? "" : value;
    }

    private static long value(Long value) {
        return value == null ? 0L : value;
    }

    private static io.grpc.StatusRuntimeException toStatus(Throwable error) {
        if (error instanceof io.grpc.StatusRuntimeException grpc) {
            return grpc;
        }
        if (error instanceof AccessDeniedException) {
            return Status.PERMISSION_DENIED.withDescription("teacher lesson is out of scope")
                    .withCause(error).asRuntimeException();
        }
        if (error instanceof BadRequestException) {
            return Status.INVALID_ARGUMENT.withDescription(error.getMessage())
                    .withCause(error).asRuntimeException();
        }
        if (error instanceof AcademicServiceUnavailableException
                || error instanceof ScheduleServiceUnavailableException
                || error instanceof ReportExportUnavailableException) {
            return Status.UNAVAILABLE.withDescription(error.getMessage())
                    .withCause(error).asRuntimeException();
        }
        if (error instanceof ru.rutcampustrack.attendance.contract.exception.ResourceNotFoundException) {
            return Status.NOT_FOUND.withDescription(error.getMessage()).withCause(error).asRuntimeException();
        }
        return Status.INTERNAL.withDescription("Teacher read failed").withCause(error).asRuntimeException();
    }

    private record TicketMarker(String ticketId, boolean pending) {
    }

    private record LessonRoster(LessonResponse lesson, List<TeacherRosterEntry> entries) {
    }

    private record JournalScope(long groupId, long subjectId, long semesterId, String lessonType) {
        private static JournalScope from(LessonResponse lesson) {
            return new JournalScope(
                    lesson.getGroupId(),
                    lesson.getSubjectId(),
                    lesson.getSemesterId(),
                    value(lesson.getLessonType()).toLowerCase(Locale.ROOT));
        }
    }

    private static final class StudentJournalAccumulator {
        private final long studentId;
        private final String displayName;
        private final Map<Long, TeacherJournalCell> cells = new LinkedHashMap<>();

        private StudentJournalAccumulator(long studentId, String displayName) {
            this.studentId = studentId;
            this.displayName = displayName;
        }

        private long studentId() {
            return studentId;
        }

        private String displayName() {
            return displayName;
        }

        private Map<Long, TeacherJournalCell> cells() {
            return cells;
        }
    }
}
