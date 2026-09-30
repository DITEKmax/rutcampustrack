package ru.rutcampustrack.attendance.grpc;

import io.grpc.stub.StreamObserver;
import net.devh.boot.grpc.server.service.GrpcService;
import org.springframework.beans.factory.annotation.Autowired;
import ru.rutcampustrack.attendance.contract.enums.ExcuseType;
import ru.rutcampustrack.attendance.contract.enums.UserRole;
import ru.rutcampustrack.attendance.contract.enums.AttendanceSource;
import ru.rutcampustrack.attendance.contract.enums.AttendanceStatus;
import ru.rutcampustrack.attendance.contract.enums.LateCheckinResolutionReason;
import ru.rutcampustrack.attendance.exception.AcademicServiceUnavailableException;
import ru.rutcampustrack.attendance.exception.BadRequestException;
import ru.rutcampustrack.attendance.exception.ScheduleServiceUnavailableException;
import ru.rutcampustrack.attendance.semester.SemesterCacheService;
import ru.rutcampustrack.attendance.student.StudentAttendanceSnapshotService;
import ru.rutcampustrack.attendance.student.StudentCheckinException;
import ru.rutcampustrack.attendance.student.StudentCheckinModels;
import ru.rutcampustrack.attendance.student.StudentCheckinService;
import ru.rutcampustrack.attendance.report.studentprojection.AttendanceMetricCalculator;
import ru.rutcampustrack.attendance.report.studentprojection.StudentAttendanceProjectionService;
import ru.rutcampustrack.attendance.report.studentprojection.StudentProjectionException;
import ru.rutcampustrack.attendance.studentrequest.RequestBucket;
import ru.rutcampustrack.attendance.studentrequest.StudentRequestModels;
import ru.rutcampustrack.attendance.studentrequest.StudentRequestService;
import ru.rutcampustrack.schedule.grpc.LessonResponse;
import ru.rutcampustrack.shared.security.InternalJwtClaims;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@GrpcService
public class AttendanceStudentGrpcServiceImpl
        extends AttendanceStudentGrpcServiceGrpc.AttendanceStudentGrpcServiceImplBase {

    private final StudentCheckinService checkinService;
    private final StudentAttendanceSnapshotService snapshotService;
    private final ScheduleGrpcClient scheduleGrpcClient;
    private final AcademicGrpcClient academicGrpcClient;
    private final SemesterCacheService semesterCacheService;
    private final StudentRequestService requestService;
    private final StudentAttendanceProjectionService projectionService;

    public AttendanceStudentGrpcServiceImpl(
            StudentCheckinService checkinService,
            StudentAttendanceSnapshotService snapshotService,
            ScheduleGrpcClient scheduleGrpcClient,
            AcademicGrpcClient academicGrpcClient,
            SemesterCacheService semesterCacheService
    ) {
        this(checkinService, snapshotService, scheduleGrpcClient, academicGrpcClient,
                semesterCacheService, null, null);
    }

    public AttendanceStudentGrpcServiceImpl(
            StudentCheckinService checkinService,
            StudentAttendanceSnapshotService snapshotService,
            ScheduleGrpcClient scheduleGrpcClient,
            AcademicGrpcClient academicGrpcClient,
            SemesterCacheService semesterCacheService,
            StudentRequestService requestService
    ) {
        this(checkinService, snapshotService, scheduleGrpcClient, academicGrpcClient,
                semesterCacheService, requestService, null);
    }

    @Autowired
    public AttendanceStudentGrpcServiceImpl(
            StudentCheckinService checkinService,
            StudentAttendanceSnapshotService snapshotService,
            ScheduleGrpcClient scheduleGrpcClient,
            AcademicGrpcClient academicGrpcClient,
            SemesterCacheService semesterCacheService,
            StudentRequestService requestService,
            StudentAttendanceProjectionService projectionService
    ) {
        this.checkinService = checkinService;
        this.snapshotService = snapshotService;
        this.scheduleGrpcClient = scheduleGrpcClient;
        this.academicGrpcClient = academicGrpcClient;
        this.semesterCacheService = semesterCacheService;
        this.requestService = requestService;
        this.projectionService = projectionService;
    }

    @Override
    public void getStudentAttendanceProjection(
            StudentAttendanceProjectionRequest request,
            StreamObserver<StudentAttendanceProjectionResponse> observer) {
        try {
            if (projectionService == null) {
                throw new AcademicServiceUnavailableException("Student attendance projection is not configured");
            }
            InternalJwtClaims claims = requireClaims();
            var projection = projectionService.project(
                    claims,
                    request.getSemesterId(),
                    request.hasSubjectId() ? request.getSubjectId() : null,
                    request.getRange(),
                    request.getLessonTypesList());
            observer.onNext(toProto(projection, projectionRequestOptions(claims, request, projection)));
            observer.onCompleted();
        } catch (RuntimeException error) {
            observer.onError(mapError(error));
        }
    }

    @Override
    public void getStudentAttendanceRanking(
            StudentAttendanceRankingRequest request,
            StreamObserver<StudentAttendanceRankingResponse> observer) {
        try {
            if (projectionService == null) {
                throw new AcademicServiceUnavailableException("Student attendance projection is not configured");
            }
            InternalJwtClaims claims = requireClaims();
            StudentAttendanceProjectionService.RankingPage ranking = projectionService.ranking(
                    claims,
                    request.getSemesterId(),
                    request.hasPage() ? request.getPage() : null,
                    request.getSize());
            observer.onNext(toProto(ranking));
            observer.onCompleted();
        } catch (RuntimeException error) {
            observer.onError(mapError(error));
        }
    }

    @Override
    public void getStudentAttendanceSnapshot(
            StudentAttendanceSnapshotRequest request,
            StreamObserver<StudentAttendanceSnapshotResponse> observer
    ) {
        try {
            InternalJwtClaims claims = requireClaims();
            var snapshot = snapshotService.getSnapshot(identity(claims, null), request.getLessonIdsList());
            StudentAttendanceSnapshotResponse.Builder response = StudentAttendanceSnapshotResponse.newBuilder()
                    .setServerNow(snapshot.serverNow().toString());
            snapshot.entries().forEach(entry -> response.addEntries(toProto(entry)));
            observer.onNext(response.build());
            observer.onCompleted();
        } catch (RuntimeException error) {
            observer.onError(mapError(error));
        }
    }

    @Override
    public void checkin(StudentCheckinCommand command, StreamObserver<StudentCheckinResult> observer) {
        try {
            InternalJwtClaims claims = requireClaims();
            if (claims.readOnly()) {
                throw new StudentCheckinException(StudentCheckinException.Code.OUT_OF_SCOPE,
                        "Терминальная student-сессия доступна только для чтения");
            }
            StudentCheckinModels.Geo geo = parseGeo(command);
            StudentCheckinModels.Identity identity = identity(claims, null);
            var replay = checkinService.replay(
                    identity, command.getLessonId(), command.getIdempotencyKey(), geo);
            if (replay != null) {
                observer.onNext(toProto(replay));
                observer.onCompleted();
                return;
            }
            LessonResponse lesson = scheduleGrpcClient.getLessonById(command.getLessonId());
            if (lesson == null || lesson.getSemesterId() <= 0) {
                throw new StudentCheckinException(StudentCheckinException.Code.LESSON_NOT_FOUND,
                        "Пара не найдена");
            }
            String displayName = academicGrpcClient.getUserDisplayName(claims.userId());
            AcademicGrpcClient.SubjectDetails subject = lesson.getSubjectId() > 0
                    ? academicGrpcClient.getSubjectDetailsByIds(List.of(lesson.getSubjectId()))
                            .get(lesson.getSubjectId())
                    : null;
            StudentCheckinModels.Lesson domainLesson = new StudentCheckinModels.Lesson(
                    lesson.getId(), lesson.getGroupId(), lesson.getSubjectId(),
                    subject == null ? null : subject.name(), lesson.getLessonType(),
                    lesson.getSemesterId(), lesson.getLessonNumber(),
                    LocalDate.parse(lesson.getDate()), LocalTime.parse(lesson.getStartTime()),
                    LocalTime.parse(lesson.getEndTime()), lesson.getStatus(),
                    lesson.getIsGeoBlocked() || lesson.getIsBlockedByHeadman());
            var ack = checkinService.checkin(
                    identity(claims, displayName), domainLesson, command.getIdempotencyKey(), geo);
            observer.onNext(toProto(ack));
            observer.onCompleted();
        } catch (RuntimeException error) {
            observer.onError(mapError(error));
        }
    }

    @Override
    public void listStudentRequests(StudentRequestListQuery query,
                                    StreamObserver<StudentRequestPage> observer) {
        try {
            StudentRequestModels.Identity identity = requestIdentity(requireClaims());
            RequestBucket bucket = query.getBucket() == StudentRequestBucket.STUDENT_REQUEST_BUCKET_ARCHIVE
                    ? RequestBucket.ARCHIVE : RequestBucket.OPEN;
            Integer page = query.hasPage() ? query.getPage() : null;
            Integer size = query.hasSize() ? query.getSize() : null;
            observer.onNext(StudentRequestGrpcMapper.page(requireRequestService()
                    .list(identity, bucket, page, size)));
            observer.onCompleted();
        } catch (RuntimeException error) {
            observer.onError(StudentRequestGrpcErrors.toStatus(error));
        }
    }

    @Override
    public void getStudentRequest(StudentRequestId request,
                                  StreamObserver<StudentRequestDetail> observer) {
        try {
            StudentRequestModels.Identity identity = requestIdentity(requireClaims());
            validateObjectId(request.getRequestId(), "request_id");
            observer.onNext(StudentRequestGrpcMapper.detail(
                    requireRequestService().get(identity, request.getRequestId())));
            observer.onCompleted();
        } catch (RuntimeException error) {
            observer.onError(StudentRequestGrpcErrors.toStatus(error));
        }
    }

    @Override
    public void getStudentRequestOptions(StudentRequestOptionsQuery query,
                                         StreamObserver<StudentRequestOptions> observer) {
        try {
            StudentRequestModels.Identity identity = requestIdentity(requireClaims());
            observer.onNext(StudentRequestGrpcMapper.options(requireRequestService().options(identity)));
            observer.onCompleted();
        } catch (RuntimeException error) {
            observer.onError(StudentRequestGrpcErrors.toStatus(error));
        }
    }

    @Override
    public void submitStudentExcuse(SubmitStudentExcuseCommand command,
                                    StreamObserver<StudentRequestDetail> observer) {
        try {
            InternalJwtClaims claims = requireClaims();
            requireMutableRequestClaims(claims);
            StudentRequestModels.Identity identity = requestIdentity(claims);
            if (command.getIdempotencyKey().isBlank()) {
                throw new ru.rutcampustrack.attendance.exception.InvalidIdempotencyKeyException();
            }
            List<StudentRequestModels.AttachmentInput> attachments = command.getAttachmentsList().stream()
                    .map(file -> new StudentRequestModels.AttachmentInput(
                            file.getName(), file.getDeclaredContentType(), file.getData().toByteArray()))
                    .toList();
            StudentRequestModels.ExcuseSubmission submission = new StudentRequestModels.ExcuseSubmission(
                    command.getLessonIdsList(), parseReason(command.getReason()),
                    command.hasComment() ? command.getComment() : null,
                    attachments, command.getIdempotencyKey());
            observer.onNext(StudentRequestGrpcMapper.detail(
                    requireRequestService().submitExcuse(identity, submission)));
            observer.onCompleted();
        } catch (RuntimeException error) {
            observer.onError(StudentRequestGrpcErrors.toStatus(error));
        }
    }

    @Override
    public void submitStudentLateCheckin(SubmitStudentLateCheckinCommand command,
                                         StreamObserver<StudentRequestDetail> observer) {
        try {
            InternalJwtClaims claims = requireClaims();
            requireMutableRequestClaims(claims);
            StudentRequestModels.Identity identity = requestIdentity(claims);
            if (command.getIdempotencyKey().isBlank()) {
                throw new ru.rutcampustrack.attendance.exception.InvalidIdempotencyKeyException();
            }
            observer.onNext(StudentRequestGrpcMapper.detail(requireRequestService().submitLateCheckin(
                    identity, new StudentRequestModels.LateCheckinSubmission(
                            command.getLessonId(), command.getIdempotencyKey()))));
            observer.onCompleted();
        } catch (RuntimeException error) {
            observer.onError(StudentRequestGrpcErrors.toStatus(error));
        }
    }

    @Override
    public void cancelStudentRequest(StudentRequestId request,
                                     StreamObserver<StudentRequestDetail> observer) {
        try {
            InternalJwtClaims claims = requireClaims();
            requireMutableRequestClaims(claims);
            StudentRequestModels.Identity identity = requestIdentity(claims);
            validateObjectId(request.getRequestId(), "request_id");
            observer.onNext(StudentRequestGrpcMapper.detail(
                    requireRequestService().cancel(identity, request.getRequestId())));
            observer.onCompleted();
        } catch (RuntimeException error) {
            observer.onError(StudentRequestGrpcErrors.toStatus(error));
        }
    }

    @Override
    public void downloadStudentRequestAttachment(StudentRequestAttachmentId request,
                                                  StreamObserver<StudentRequestAttachmentDownload> observer) {
        try {
            StudentRequestModels.Identity identity = requestIdentity(requireClaims());
            validateObjectId(request.getRequestId(), "request_id");
            validateObjectId(request.getAttachmentId(), "attachment_id");
            observer.onNext(StudentRequestGrpcMapper.download(requireRequestService().download(
                    identity, request.getRequestId(), request.getAttachmentId())));
            observer.onCompleted();
        } catch (RuntimeException error) {
            observer.onError(StudentRequestGrpcErrors.toStatus(error));
        }
    }

    private StudentRequestService requireRequestService() {
        if (requestService == null) {
            throw new AcademicServiceUnavailableException("Student request transport is not configured");
        }
        return requestService;
    }

    private static StudentRequestModels.Identity requestIdentity(InternalJwtClaims claims) {
        if (claims == null || claims.userId() <= 0
                || claims.groupId() == null || claims.groupId() <= 0) {
            throw new StudentRequestTransportException(
                    StudentRequestErrorCode.STUDENT_REQUEST_ERROR_CODE_OUT_OF_SCOPE,
                    "Authenticated student scope is missing");
        }
        if (claims.domainRole() == null || !"STUDENT".equalsIgnoreCase(claims.domainRole())) {
            throw new StudentRequestTransportException(
                    StudentRequestErrorCode.STUDENT_REQUEST_ERROR_CODE_WRONG_ROLE,
                    "Student request API requires STUDENT role");
        }
        return new StudentRequestModels.Identity(claims.userId(), UserRole.STUDENT,
                claims.groupId(), claims.isHeadman());
    }

    private static void requireMutableRequestClaims(InternalJwtClaims claims) {
        if (claims.readOnly()) {
            throw new StudentRequestTransportException(
                    StudentRequestErrorCode.STUDENT_REQUEST_ERROR_CODE_OUT_OF_SCOPE,
                    "Терминальная student-сессия доступна только для чтения");
        }
    }

    private static ExcuseType parseReason(StudentExcuseReason reason) {
        if (reason == null || reason == StudentExcuseReason.STUDENT_EXCUSE_REASON_UNSPECIFIED
                || reason == StudentExcuseReason.UNRECOGNIZED) {
            throw new BadRequestException("Excuse reason is required");
        }
        try {
            return ExcuseType.valueOf(reason.name().replace("STUDENT_EXCUSE_REASON_", ""));
        } catch (IllegalArgumentException error) {
            throw new BadRequestException("Excuse reason is not supported");
        }
    }

    private static void validateObjectId(String value, String field) {
        if (value == null || !value.matches("[0-9a-fA-F]{24}")) {
            throw new BadRequestException(field + " must be a 24-character hexadecimal id");
        }
    }

    private static InternalJwtClaims requireClaims() {
        InternalJwtClaims claims = StudentGrpcIdentity.CLAIMS.get();
        if (claims == null) {
            throw new StudentCheckinException(StudentCheckinException.Code.INVALID_SESSION,
                    "Internal identity is missing");
        }
        return claims;
    }

    private static StudentCheckinModels.Identity identity(InternalJwtClaims claims, String displayName) {
        return new StudentCheckinModels.Identity(
                claims.userId(), claims.domainRole(), claims.groupId(), claims.isHeadman(), displayName,
                claims.readOnly());
    }

    private static StudentCheckinModels.Geo parseGeo(StudentCheckinCommand command) {
        if (command.getLessonId() <= 0) {
            throw new StudentCheckinException(StudentCheckinException.Code.INVALID_REQUEST,
                    "lesson_id должен быть положительным");
        }
        return switch (command.getGeoCase()) {
            case COORDINATES -> {
                Coordinates coordinates = command.getCoordinates();
                if (!coordinates.hasLatitude() || !coordinates.hasLongitude()) {
                    throw new StudentCheckinException(StudentCheckinException.Code.INVALID_REQUEST,
                            "Обе координаты обязательны");
                }
                yield new StudentCheckinModels.Coordinates(
                        coordinates.getLatitude(), coordinates.getLongitude());
            }
            case UNAVAILABLE -> {
                GeoUnavailableReason reason = command.getUnavailable().getReason();
                if (reason == GeoUnavailableReason.GEO_UNAVAILABLE_REASON_UNSPECIFIED
                        || reason == GeoUnavailableReason.UNRECOGNIZED) {
                    throw new StudentCheckinException(StudentCheckinException.Code.INVALID_REQUEST,
                            "Причина недоступной геолокации обязательна");
                }
                yield new StudentCheckinModels.Unavailable(reason.name());
            }
            case GEO_NOT_SET -> throw new StudentCheckinException(
                    StudentCheckinException.Code.INVALID_REQUEST, "geo обязателен");
        };
    }

    private static StudentAttendanceEntry toProto(StudentAttendanceSnapshotService.Entry entry) {
        StudentAttendanceEntry.Builder result = StudentAttendanceEntry.newBuilder()
                .setLessonId(entry.lessonId())
                .setStatus(toProtoStatus(entry.attendanceStatus()))
                .setSource(toProtoSource(entry.attendanceSource()))
                .setEligibility(toProto(entry.eligibility()));
        if (entry.markedAt() != null) result.setMarkedAt(entry.markedAt().toString());
        if (entry.request() != null) result.setRequest(toProto(entry.request()));
        if (entry.retryAt() != null) result.setRetryAt(entry.retryAt().toString());
        return result.build();
    }

    private static StudentCheckinResult toProto(StudentCheckinModels.Ack ack) {
        StudentCheckinResult.Builder result = StudentCheckinResult.newBuilder()
                .setOutcome(ack.outcome() == StudentCheckinModels.Outcome.PRESENT
                        ? CheckinOutcome.CHECKIN_OUTCOME_PRESENT
                        : CheckinOutcome.CHECKIN_OUTCOME_PENDING_CONFIRMATION)
                .setLessonId(ack.lessonId())
                .setServerNow(ack.serverNow().toString());
        if (ack.attendance() != null) {
            result.setAttendance(StudentAttendanceEntry.newBuilder()
                    .setLessonId(ack.lessonId())
                    .setStatus(toProtoStatus(ack.attendance().status()))
                    .setSource(toProtoSource(ack.attendance().source()))
                    .setMarkedAt(ack.attendance().markedAt().toString())
                    .build());
        }
        if (ack.request() != null) {
            result.setRequest(AutomaticCheckinRequest.newBuilder()
                    .setId(ack.request().id())
                    .setStatus(toProtoRequestStatus(ack.request().status()))
                    .setOrigin(AutomaticCheckinRequestOrigin.AUTOMATIC_CHECKIN_REQUEST_ORIGIN_AUTO_GEO_FAILURE)
                    .build());
        }
        if (ack.retryAt() != null) result.setRetryAt(ack.retryAt().toString());
        return result.build();
    }

    private static StudentAttendanceProjectionResponse toProto(
            StudentAttendanceProjectionService.Projection projection) {
        return toProto(projection, Map.of());
    }

    private static StudentAttendanceProjectionResponse toProto(
            StudentAttendanceProjectionService.Projection projection,
            Map<Long, List<StudentAttendanceRequestOption>> requestOptions) {
        StudentAttendanceProjectionResponse.Builder result = StudentAttendanceProjectionResponse.newBuilder()
                .setStudentId(projection.studentId())
                .setSemesterId(projection.semesterId())
                .setDateFrom(projection.dateFrom().toString())
                .setDateTo(projection.dateTo().toString())
                .setServerNow(projection.serverNow().toString())
                .setTerminalReadOnly(projection.terminalReadOnly())
                .setMetrics(toProto(projection.metrics()))
                .setGraph(toProto(projection.graph()))
                .setOwnRank(toProto(projection.ownRank()));
        projection.days().forEach(day -> result.addDays(toProto(day, requestOptions)));
        projection.subjects().forEach(subject -> result.addSubjects(toProto(subject)));
        return result.build();
    }

    private static StudentAttendanceDay toProto(
            StudentAttendanceProjectionService.Day day,
            Map<Long, List<StudentAttendanceRequestOption>> requestOptions) {
        StudentAttendanceDay.Builder result = StudentAttendanceDay.newBuilder()
                .setDate(day.date().toString())
                .setWeekday(day.weekday())
                .setDayNumber(day.dayNumber())
                .setState(day.state());
        day.lessons().forEach(lesson -> result.addLessons(toProto(lesson, requestOptions)));
        return result.build();
    }

    private static StudentAttendanceLesson toProto(
            StudentAttendanceProjectionService.Lesson lesson,
            Map<Long, List<StudentAttendanceRequestOption>> requestOptions) {
        StudentAttendanceLesson.Builder result = StudentAttendanceLesson.newBuilder()
                .setLessonId(lesson.lessonId())
                .setDate(lesson.date().toString())
                .setLessonNumber(lesson.lessonNumber())
                .setSubjectId(lesson.subjectId())
                .setSubjectName(lesson.subjectName())
                .setLessonType(lesson.lessonType())
                .setStartsAt(lesson.startsAt().toString())
                .setEndsAt(lesson.endsAt().toString())
                .setStatus(lesson.uiStatus())
                .addAllRequestOptions(requestOptions.getOrDefault(lesson.lessonId(), List.of()));
        if (lesson.room() != null) result.setRoom(lesson.room());
        return result.build();
    }

    private Map<Long, List<StudentAttendanceRequestOption>> projectionRequestOptions(
            InternalJwtClaims claims,
            StudentAttendanceProjectionRequest request,
            StudentAttendanceProjectionService.Projection projection) {
        if (requestService == null || claims.readOnly() || projection.terminalReadOnly()
                || !java.util.Objects.equals(semesterCacheService.getActiveSemesterId(), request.getSemesterId())) {
            return Map.of();
        }
        try {
            StudentRequestModels.RequestOptions options = requestService.options(requestIdentity(claims));
            Map<Long, List<StudentAttendanceRequestOption>> result = new HashMap<>();
            for (StudentRequestModels.LessonOption option : options.lessons()) {
                List<StudentAttendanceRequestOption> mapped = List.of(
                        StudentAttendanceRequestOption.newBuilder()
                                .setId("EXCUSE")
                                .setKind("EXCUSE")
                                .setLabel("Уважительная причина")
                                .setEnabled(option.excuseEligible())
                                .build(),
                        StudentAttendanceRequestOption.newBuilder()
                                .setId("LATE_CHECKIN")
                                .setKind("LATE_CHECKIN")
                                .setLabel("Поздняя отметка")
                                .setEnabled(option.lateCheckinEligible())
                                .build());
                result.put(option.lesson().lessonId(), mapped);
            }
            return result;
        } catch (RuntimeException unavailable) {
            // Request actions are optional decoration of the read projection;
            // an unavailable options dependency fails closed to no actions.
            return Map.of();
        }
    }

    private static StudentAttendanceSubject toProto(StudentAttendanceProjectionService.Subject subject) {
        StudentAttendanceSubject.Builder result = StudentAttendanceSubject.newBuilder()
                .setSubjectId(subject.subjectId())
                .setName(subject.name())
                .setMetrics(toProto(subject.metrics()))
                .addAllAvailableTypes(subject.availableTypes())
                .addAllSelectedTypes(subject.selectedTypes())
                .setSelectedAggregate(toProto(subject.selectedAggregate()))
                .addAllTypeCards(subject.typeCards().stream()
                        .map(AttendanceStudentGrpcServiceImpl::toProto)
                        .toList())
                .addAllSeries(subject.series().stream()
                        .map(AttendanceStudentGrpcServiceImpl::toProto)
                        .toList());
        return result.build();
    }

    private static StudentAttendanceTypeCard toProto(
            StudentAttendanceProjectionService.TypeCard card) {
        return StudentAttendanceTypeCard.newBuilder()
                .setLessonType(card.lessonType())
                .setMetrics(toProto(card.metrics()))
                .addAllHistory(card.history().stream()
                        .map(history -> StudentAttendanceHistorySegment.newBuilder()
                                .setId(history.id()).setStatus(history.status()).build())
                        .toList())
                .build();
    }

    private static StudentAttendanceGraph toProto(StudentAttendanceProjectionService.Graph graph) {
        return StudentAttendanceGraph.newBuilder()
                .addAllDays(graph.days().stream()
                        .map(AttendanceStudentGrpcServiceImpl::toProto)
                        .toList())
                .addAllWeeks(graph.weeks().stream()
                        .map(AttendanceStudentGrpcServiceImpl::toProto)
                        .toList())
                .build();
    }

    private static StudentAttendanceSeriesPoint toProto(
            StudentAttendanceProjectionService.SeriesPoint point) {
        return StudentAttendanceSeriesPoint.newBuilder()
                .setId(point.id())
                .setLabel(point.label())
                .setDateFrom(point.dateFrom().toString())
                .setDateTo(point.dateTo().toString())
                .setState(point.state())
                .setMetrics(toProto(point.metrics()))
                .build();
    }

    private static StudentAttendanceOwnRank toProto(StudentAttendanceProjectionService.Rank rank) {
        StudentAttendanceOwnRank.Builder result = StudentAttendanceOwnRank.newBuilder()
                .setParticipantCount(rank.participantCount())
                .setAvailable(rank.available());
        if (rank.position() != null) result.setPosition(rank.position());
        return result.build();
    }

    private static StudentAttendanceRankingResponse toProto(
            StudentAttendanceProjectionService.RankingPage ranking) {
        StudentAttendanceRankingResponse.Builder result = StudentAttendanceRankingResponse.newBuilder()
                .setAvailable(ranking.available())
                .setPage(ranking.page())
                .setSize(ranking.size())
                .setTotal(ranking.total());
        if (ranking.ownPosition() != null) result.setOwnPosition(ranking.ownPosition());
        ranking.rows().forEach(row -> {
            StudentAttendanceRankingRow.Builder item = StudentAttendanceRankingRow.newBuilder()
                    .setStudentId(row.studentId())
                    .setDisplayName(row.name())
                    .setIsSelf(row.isSelf());
            if (row.position() != null) item.setPosition(row.position());
            if (row.percentage() != null) item.setPercentage(row.percentage().doubleValue());
            result.addRows(item);
        });
        return result.build();
    }

    private static StudentAttendanceMetricSet toProto(AttendanceMetricCalculator.Metrics metrics) {
        return StudentAttendanceMetricSet.newBuilder()
                .setPresent(toProto(metrics.present()))
                .setPresentOrExcused(toProto(metrics.presentOrExcused()))
                .setExcused(toProto(metrics.excused()))
                .setAbsent(toProto(metrics.absent()))
                .setHeld(metrics.heldCount())
                .setPlanned(metrics.plannedCount())
                .setMissingClosed(metrics.missingClosedCount())
                .build();
    }

    private static StudentAttendanceMetric toProto(AttendanceMetricCalculator.Metric metric) {
        StudentAttendanceMetric.Builder result = StudentAttendanceMetric.newBuilder()
                .setCount(metric.count());
        if (metric.percent() != null) result.setPercent(metric.percent().doubleValue());
        return result.build();
    }

    private static AutomaticCheckinRequest toProto(
            ru.rutcampustrack.attendance.latecheckin.entity.LateCheckinRequest request) {
        AutomaticCheckinRequest.Builder result = AutomaticCheckinRequest.newBuilder()
                .setId(request.getId())
                .setStatus(toProtoRequestStatus(request.getStatus()))
                .setOrigin(AutomaticCheckinRequestOrigin.AUTOMATIC_CHECKIN_REQUEST_ORIGIN_AUTO_GEO_FAILURE);
        if (request.getResolutionReason() != null) {
            result.setResolutionReason(switch (request.getResolutionReason()) {
                case GEO_CONFIRMED -> AutomaticCheckinResolutionReason.AUTOMATIC_CHECKIN_RESOLUTION_REASON_GEO_CONFIRMED;
                case HEADMAN_APPROVED -> AutomaticCheckinResolutionReason.AUTOMATIC_CHECKIN_RESOLUTION_REASON_HEADMAN_APPROVED;
                case HEADMAN_REJECTED -> AutomaticCheckinResolutionReason.AUTOMATIC_CHECKIN_RESOLUTION_REASON_HEADMAN_REJECTED;
                case CANCELLED_BY_STUDENT -> AutomaticCheckinResolutionReason.AUTOMATIC_CHECKIN_RESOLUTION_REASON_STUDENT_CANCELLED;
                case PRESENT_PRIORITY -> AutomaticCheckinResolutionReason.AUTOMATIC_CHECKIN_RESOLUTION_REASON_PRESENT_PRIORITY;
            });
        }
        return result.build();
    }

    private static AutomaticCheckinRequestStatus toProtoRequestStatus(
            ru.rutcampustrack.attendance.contract.enums.LateCheckinRequestStatus status) {
        return switch (status) {
            case PENDING -> AutomaticCheckinRequestStatus.AUTOMATIC_CHECKIN_REQUEST_STATUS_PENDING;
            case APPROVED -> AutomaticCheckinRequestStatus.AUTOMATIC_CHECKIN_REQUEST_STATUS_APPROVED;
            case REJECTED -> AutomaticCheckinRequestStatus.AUTOMATIC_CHECKIN_REQUEST_STATUS_REJECTED;
            case CANCELLED -> AutomaticCheckinRequestStatus.AUTOMATIC_CHECKIN_REQUEST_STATUS_CANCELLED;
        };
    }

    private static StudentCheckinEligibility toProto(StudentAttendanceSnapshotService.Eligibility eligibility) {
        StudentCheckinEligibility.Builder result = StudentCheckinEligibility.newBuilder()
                .setAllowed(eligibility.allowed())
                .setReason(StudentCheckinEligibilityReason.valueOf(
                        "STUDENT_CHECKIN_ELIGIBILITY_REASON_" + eligibility.reason().name()));
        if (eligibility.retryAt() != null) result.setRetryAt(eligibility.retryAt().toString());
        return result.build();
    }

    private static ru.rutcampustrack.attendance.grpc.AttendanceStatus toProtoStatus(AttendanceStatus status) {
        if (status == null) return ru.rutcampustrack.attendance.grpc.AttendanceStatus.ATTENDANCE_STATUS_UNSPECIFIED;
        return switch (status) {
            case PRESENT -> ru.rutcampustrack.attendance.grpc.AttendanceStatus.ATTENDANCE_STATUS_PRESENT;
            case ABSENT -> ru.rutcampustrack.attendance.grpc.AttendanceStatus.ATTENDANCE_STATUS_ABSENT;
            case EXCUSED, FREE_ATTENDANCE -> ru.rutcampustrack.attendance.grpc.AttendanceStatus.ATTENDANCE_STATUS_EXCUSED;
            case CANCELLED -> ru.rutcampustrack.attendance.grpc.AttendanceStatus.ATTENDANCE_STATUS_UNSPECIFIED;
        };
    }

    private static ru.rutcampustrack.attendance.grpc.AttendanceSource toProtoSource(AttendanceSource source) {
        if (source == null) return ru.rutcampustrack.attendance.grpc.AttendanceSource.ATTENDANCE_SOURCE_UNSPECIFIED;
        return switch (source) {
            case STUDENT_GEO -> ru.rutcampustrack.attendance.grpc.AttendanceSource.ATTENDANCE_SOURCE_STUDENT_GEO;
            case LATE_CHECKIN -> ru.rutcampustrack.attendance.grpc.AttendanceSource.ATTENDANCE_SOURCE_LATE_CHECKIN;
            case HEADMAN, HEADMAN_EXCUSE -> ru.rutcampustrack.attendance.grpc.AttendanceSource.ATTENDANCE_SOURCE_HEADMAN;
            case AUTO_SCHEDULER -> ru.rutcampustrack.attendance.grpc.AttendanceSource.ATTENDANCE_SOURCE_SYSTEM;
        };
    }

    private static RuntimeException mapError(RuntimeException error) {
        if (error instanceof StudentCheckinException checkin) {
            return StudentCheckinGrpcErrors.toStatus(checkin);
        }
        if (error instanceof ru.rutcampustrack.attendance.contract.exception.ResourceNotFoundException) {
            return StudentCheckinGrpcErrors.toStatus(new StudentCheckinException(
                    StudentCheckinException.Code.LESSON_NOT_FOUND, "Пара не найдена"));
        }
        if (error instanceof ScheduleServiceUnavailableException
                || error instanceof AcademicServiceUnavailableException) {
            return StudentCheckinGrpcErrors.toStatus(new StudentCheckinException(
                    StudentCheckinException.Code.DEPENDENCY_UNAVAILABLE,
                    "Обязательный сервис временно недоступен"));
        }
        if (error instanceof StudentProjectionException) {
            return StudentCheckinGrpcErrors.toStatus(new StudentCheckinException(
                    StudentCheckinException.Code.DEPENDENCY_UNAVAILABLE,
                    "Attendance projection data is inconsistent or unavailable"));
        }
        return io.grpc.Status.INTERNAL.withDescription("Student attendance command failed")
                .withCause(error).asRuntimeException();
    }
}
