package ru.rutcampustrack.schedule.grpc;

import io.grpc.stub.StreamObserver;
import net.devh.boot.grpc.server.service.GrpcService;
import org.springframework.beans.factory.annotation.Autowired;
import ru.rutcampustrack.schedule.exception.ResourceNotFoundException;
import ru.rutcampustrack.schedule.homework.HomeworkBindingService;
import ru.rutcampustrack.schedule.item.entity.ScheduleItem;
import ru.rutcampustrack.schedule.item.repository.ScheduleItemRepository;
import ru.rutcampustrack.schedule.lesson.entity.Lesson;
import ru.rutcampustrack.schedule.lesson.LessonTransferWriter;
import ru.rutcampustrack.schedule.lesson.repository.LessonRepository;
import ru.rutcampustrack.schedule.oneoff.repository.OneOffLessonRepository;
import ru.rutcampustrack.schedule.replacement.AssignmentReplacementService;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * gRPC service implementation for Schedule Service.
 * Exposes schedule data to Attendance Service via three RPCs:
 * - GetActiveLesson (GRPC-01): find the currently active lesson for a group
 * - GetLessonById (GRPC-02): get full lesson details by ID
 * - GetLessonsByGroup (GRPC-03): get all lessons for a group over a date range
 *
 * Queries repositories directly (no caching needed — Attendance Service
 * calls are infrequent and real-time sensitive).
 */
@GrpcService
public class ScheduleGrpcServiceImpl extends ScheduleGrpcServiceGrpc.ScheduleGrpcServiceImplBase {

    private final LessonRepository lessonRepository;
    private final ScheduleItemRepository scheduleItemRepository;
    private final OneOffLessonRepository oneOffLessonRepository;
    private final HomeworkBindingService homeworkBindingService;
    private final AssignmentReplacementService assignmentReplacementService;
    private final LessonTransferWriter lessonTransferWriter;

    /** Legacy constructor kept for focused tests of the pre-V17 read RPCs. */
    public ScheduleGrpcServiceImpl(LessonRepository lessonRepository,
                                   ScheduleItemRepository scheduleItemRepository,
                                   OneOffLessonRepository oneOffLessonRepository) {
        this(lessonRepository, scheduleItemRepository, oneOffLessonRepository, null, null, null);
    }

    /** Compatibility constructor retained for focused pre-replacement tests. */
    public ScheduleGrpcServiceImpl(LessonRepository lessonRepository,
                                   ScheduleItemRepository scheduleItemRepository,
                                   OneOffLessonRepository oneOffLessonRepository,
                                   HomeworkBindingService homeworkBindingService) {
        this(lessonRepository, scheduleItemRepository, oneOffLessonRepository,
                homeworkBindingService, null, null);
    }

    /** Compatibility constructor retained for focused pre-transfer tests. */
    public ScheduleGrpcServiceImpl(LessonRepository lessonRepository,
                                   ScheduleItemRepository scheduleItemRepository,
                                   OneOffLessonRepository oneOffLessonRepository,
                                   HomeworkBindingService homeworkBindingService,
                                   AssignmentReplacementService assignmentReplacementService) {
        this(lessonRepository, scheduleItemRepository, oneOffLessonRepository,
                homeworkBindingService, assignmentReplacementService, null);
    }

    @Autowired
    public ScheduleGrpcServiceImpl(LessonRepository lessonRepository,
                                   ScheduleItemRepository scheduleItemRepository,
                                   OneOffLessonRepository oneOffLessonRepository,
                                   HomeworkBindingService homeworkBindingService,
                                   AssignmentReplacementService assignmentReplacementService,
                                   LessonTransferWriter lessonTransferWriter) {
        this.lessonRepository = lessonRepository;
        this.scheduleItemRepository = scheduleItemRepository;
        this.oneOffLessonRepository = oneOffLessonRepository;
        this.homeworkBindingService = homeworkBindingService;
        this.assignmentReplacementService = assignmentReplacementService;
        this.lessonTransferWriter = lessonTransferWriter;
    }

    /**
     * GRPC-01: Get the active lesson for a group at a given timestamp.
     * Returns NOT_FOUND if no active lesson exists for the group on that date.
     */
    @Override
    public void getActiveLesson(ActiveLessonRequest request,
                                StreamObserver<LessonResponse> responseObserver) {
        LocalDate date = LocalDateTime.parse(request.getTimestamp()).toLocalDate();

        Lesson lesson = lessonRepository.findActiveLessonForGroup(request.getGroupId(), date)
                .orElseThrow(() -> new ResourceNotFoundException("Lesson", "group_id", request.getGroupId()));

        ScheduleItem item = findTemplateForRead(lesson);

        responseObserver.onNext(buildResponse(lesson, item));
        responseObserver.onCompleted();
    }

    /**
     * GRPC-02: Get a lesson by ID with full ScheduleItem details.
     * Returns NOT_FOUND if no lesson exists with the given ID.
     */
    @Override
    public void getLessonById(LessonByIdRequest request,
                              StreamObserver<LessonResponse> responseObserver) {
        Lesson lesson = lessonRepository.findById(request.getLessonId())
                .orElseThrow(() -> new ResourceNotFoundException("Lesson", "id", request.getLessonId()));

        ScheduleItem item = findTemplateForRead(lesson);

        responseObserver.onNext(buildResponse(lesson, item));
        responseObserver.onCompleted();
    }

    /**
     * GRPC-03: Get all lessons for a group within a semester over a date range.
     * Returns INVALID_ARGUMENT if date_from is after date_to (D-04).
     * Returns empty LessonsResponse if no schedule items exist for the group/semester.
     */
    @Override
    public void getLessonsByGroup(LessonsByGroupRequest request,
                                  StreamObserver<LessonsResponse> responseObserver) {
        LocalDate from = LocalDate.parse(request.getDateFrom());
        LocalDate to = LocalDate.parse(request.getDateTo());

        if (from.isAfter(to)) {
            throw new IllegalArgumentException("date_from must not be after date_to");
        }

        List<Lesson> lessons = lessonRepository.findByGroupIdAndSemesterIdAndDateBetweenAndStatusIn(
                request.getGroupId(), request.getSemesterId(), from, to,
                List.of("planned", "active", "closed", "cancelled"));
        if (lessons.isEmpty()) {
            responseObserver.onNext(LessonsResponse.newBuilder().build());
            responseObserver.onCompleted();
            return;
        }
        Map<Long, ScheduleItem> itemById = scheduleItemRepository.findAllById(lessons.stream()
                        .map(Lesson::getScheduleItemId)
                        .filter(java.util.Objects::nonNull)
                        .distinct().toList()).stream()
                .collect(Collectors.toMap(ScheduleItem::getId, i -> i));

        List<Long> lessonIds = lessons.stream().map(Lesson::getId).toList();
        Map<Long, LessonTransferWriter.TransferState> transferStates = transferStates(lessonIds);
        Map<Long, Long> occurrenceRevisions = occurrenceRevisions(lessonIds);
        List<LessonResponse> responses = lessons.stream()
                .map(l -> buildResponse(l, itemById.get(l.getScheduleItemId()),
                        transferStates.get(l.getId()), occurrenceRevisions.get(l.getId())))
                .toList();

        OffsetDateTime updatedAt = itemById.values().stream()
                .map(ScheduleItem::getCreatedAt)
                .filter(java.util.Objects::nonNull)
                .max(OffsetDateTime::compareTo)
                .orElse(null);
        for (Lesson lesson : lessons) {
            for (OffsetDateTime candidate : new OffsetDateTime[] {
                    lesson.getCreatedAt(),
                    lesson.getClosedAt(),
                    lesson.getCancelledAt()}) {
                if (candidate != null && (updatedAt == null || candidate.isAfter(updatedAt))) {
                    updatedAt = candidate;
                }
            }
        }
        LessonsResponse.Builder result = LessonsResponse.newBuilder().addAllLessons(responses);
        if (updatedAt != null) result.setUpdatedAt(updatedAt.toInstant().toString());
        responseObserver.onNext(result.build());
        responseObserver.onCompleted();
    }

    /**
     * GRPC-04: Get compact lesson info by a list of lesson IDs.
     * Used by Attendance Service (excuse flow) to validate that lessonIds
     * belong to the student's group (D-25). Returns empty list for an empty
     * request or when no lessons are found. Lessons whose ScheduleItem is
     * missing are skipped silently.
     */
    @Override
    public void getLessonsByIds(LessonsByIdsRequest request,
                                StreamObserver<LessonsByIdsResponse> responseObserver) {
        List<Long> ids = request.getLessonIdsList();

        if (ids.isEmpty()) {
            responseObserver.onNext(LessonsByIdsResponse.newBuilder().build());
            responseObserver.onCompleted();
            return;
        }

        List<Lesson> lessons = lessonRepository.findAllById(ids);

        if (lessons.isEmpty()) {
            responseObserver.onNext(LessonsByIdsResponse.newBuilder().build());
            responseObserver.onCompleted();
            return;
        }

        Map<Long, LessonTransferWriter.TransferState> transferStates = transferStates(
                lessons.stream().map(Lesson::getId).toList());
        if (transferStates.values().stream().anyMatch(state -> !"COMPLETED".equals(state.state()))) {
            throw io.grpc.Status.FAILED_PRECONDITION
                    .withDescription("lesson transfer is not complete")
                    .asRuntimeException();
        }

        List<Long> scheduleItemIds = lessons.stream()
                .map(Lesson::getScheduleItemId)
                .filter(java.util.Objects::nonNull)
                .distinct()
                .toList();

        Map<Long, ScheduleItem> itemById = scheduleItemRepository.findAllById(scheduleItemIds).stream()
                .collect(Collectors.toMap(ScheduleItem::getId, i -> i));

        List<LessonInfo> infos = lessons.stream()
                .map(l -> lessonInfo(l, itemById.get(l.getScheduleItemId())))
                .toList();

        responseObserver.onNext(LessonsByIdsResponse.newBuilder().addAllLessons(infos).build());
        responseObserver.onCompleted();
    }

    /**
     * Phase 61 D-04: Резолв пары по natural key (group_id, date, lesson_number).
     * Используется academic-service для валидации существования пары перед созданием/апдейтом ДЗ.
     * Возвращает NOT_FOUND, если пара не найдена в статусах planned/active/closed на эту дату.
     */
    @Override
    public void resolveLesson(ResolveLessonRequest request,
                              StreamObserver<LessonResponse> responseObserver) {
        // The V17 resolver is the authoritative source for immutable occurrence
        // identity and current physical snapshots used by HomeworkService.
        // Focused legacy read tests still use the three-argument constructor.
        if (homeworkBindingService != null) {
            responseObserver.onNext(homeworkBindingService.resolveLesson(request));
            responseObserver.onCompleted();
            return;
        }
        LocalDate date = LocalDate.parse(request.getDate());

        Lesson lesson = lessonRepository
                .findByGroupDateAndLessonNumber(request.getGroupId(), date, request.getLessonNumber())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Lesson",
                        "group_id/date/lesson_number",
                        request.getGroupId() + "/" + date + "/" + request.getLessonNumber()));

        ScheduleItem item = findTemplateForRead(lesson);

        responseObserver.onNext(buildResponse(lesson, item));
        responseObserver.onCompleted();
    }

    @Override
    public void reserveHomeworkBinding(ReserveHomeworkBindingRequest request,
                                       StreamObserver<HomeworkBindingResponse> responseObserver) {
        responseObserver.onNext(requireHomeworkBindingService().reserve(request));
        responseObserver.onCompleted();
    }

    @Override
    public void confirmHomeworkBinding(ConfirmHomeworkBindingRequest request,
                                       StreamObserver<HomeworkBindingResponse> responseObserver) {
        responseObserver.onNext(requireHomeworkBindingService().confirm(request));
        responseObserver.onCompleted();
    }

    @Override
    public void getHomeworkBindings(HomeworkBindingsRequest request,
                                    StreamObserver<HomeworkBindingsResponse> responseObserver) {
        responseObserver.onNext(HomeworkBindingsResponse.newBuilder()
                .addAllBindings(requireHomeworkBindingService().getBindings(request))
                .build());
        responseObserver.onCompleted();
    }

    @Override
    public void archiveHomeworkBinding(ArchiveHomeworkBindingRequest request,
                                       StreamObserver<HomeworkBindingResponse> responseObserver) {
        responseObserver.onNext(requireHomeworkBindingService().archive(request));
        responseObserver.onCompleted();
    }

    private HomeworkBindingService requireHomeworkBindingService() {
        if (homeworkBindingService == null) {
            throw new IllegalStateException("homework binding store is not configured");
        }
        return homeworkBindingService;
    }

    /**
     * Pre-check удаления Subject: academic-service зовёт этот метод, чтобы понять,
     * потеряются ли реальные данные посещаемости при каскадном удалении.
     * Возвращает счётчики — решение о 409 принимает academic.
     */
    @Override
    public void countSubjectReferences(CountSubjectReferencesRequest request,
                                       StreamObserver<CountSubjectReferencesResponse> responseObserver) {
        long subjectId = request.getSubjectId();
        long scheduleItems = scheduleItemRepository.countBySubjectIdAndIsActiveTrue(subjectId);
        long oneOff = oneOffLessonRepository.countBySubjectId(subjectId);
        long nonPlanned = lessonRepository.countNonPlannedBySubjectId(subjectId);
        long total = lessonRepository.countAllBySubjectId(subjectId);

        responseObserver.onNext(CountSubjectReferencesResponse.newBuilder()
                .setScheduleItemsCount(scheduleItems)
                .setOneOffLessonsCount(oneOff)
                .setNonPlannedLessonsCount(nonPlanned)
                .setTotalLessonsCount(total)
                .build());
        responseObserver.onCompleted();
    }

    @Override
    public void installAssignmentCloseCap(InstallAssignmentCloseCapRequest request,
                                          StreamObserver<AssignmentCloseReceipt> responseObserver) {
        responseObserver.onNext(requireAssignmentReplacementService().install(request));
        responseObserver.onCompleted();
    }

    @Override
    public void commitAssignmentClose(CommitAssignmentCloseRequest request,
                                      StreamObserver<AssignmentCloseReceipt> responseObserver) {
        responseObserver.onNext(requireAssignmentReplacementService().commit(request));
        responseObserver.onCompleted();
    }

    private AssignmentReplacementService requireAssignmentReplacementService() {
        if (assignmentReplacementService == null) {
            throw new IllegalStateException("assignment replacement store is not configured");
        }
        return assignmentReplacementService;
    }

    private LessonResponse buildResponse(Lesson lesson, ScheduleItem item) {
        return buildResponse(lesson, item, transferStates(List.of(lesson.getId())).get(lesson.getId()));
    }

    private LessonResponse buildResponse(Lesson lesson, ScheduleItem item,
                                         LessonTransferWriter.TransferState transferState) {
        Long occurrenceRevision = occurrenceRevisions(List.of(lesson.getId())).get(lesson.getId());
        return buildResponse(lesson, item, transferState, occurrenceRevision);
    }

    private LessonResponse buildResponse(Lesson lesson, ScheduleItem item,
                                         LessonTransferWriter.TransferState transferState,
                                         Long occurrenceRevision) {
        Long groupId = lesson.getGroupId() != null ? lesson.getGroupId() : item.getGroupId();
        Long subjectId = lesson.getSubjectId() != null ? lesson.getSubjectId() : item.getSubjectId();
        Short lessonNumber = lesson.getLessonNumber() != null ? lesson.getLessonNumber() : item.getLessonNumber();
        java.time.LocalTime start = lesson.getStartTime() != null ? lesson.getStartTime() : item.getStartTime();
        java.time.LocalTime end = lesson.getEndTime() != null ? lesson.getEndTime() : item.getEndTime();
        String room = lesson.getRoomSnapshot() != null ? lesson.getRoomSnapshot() : item.getRoom();
        LessonResponse.Builder response = LessonResponse.newBuilder()
                .setId(lesson.getId())
                .setScheduleItemId(lesson.getScheduleItemId() == null ? 0 : lesson.getScheduleItemId())
                .setGroupId(groupId)
                .setSubjectId(subjectId)
                // D-16: teacher_id is reserved in schedule.proto — no setter generated
                .setDate(lesson.getDate().toString())
                .setLessonNumber(lessonNumber)
                .setStartTime(start.toString())
                .setEndTime(end.toString())
                .setStatus(lesson.getStatus().name().toLowerCase())
                .setIsGeoBlocked(lesson.isGeoBlocked())
                .setRoom(room != null ? room : "")
                .setIsBlockedByHeadman(lesson.isBlockedByHeadman())
                .setRoomChangeState("unknown")
                .setOccurrenceId(lesson.getOccurrenceId() == null ? 0 : lesson.getOccurrenceId())
                .setAssignmentId(lesson.getAssignmentId() == null ? 0 : lesson.getAssignmentId())
                .setSemesterId(lesson.getSemesterId() == null ? 0 : lesson.getSemesterId())
                .setAssignedTeacherId(lesson.getAssignedTeacherId() == null ? 0 : lesson.getAssignedTeacherId())
                .setLessonType(lesson.getLessonType() == null ? "" : lesson.getLessonType())
                .setGeneration(lesson.getGeneration() == null ? 0 : lesson.getGeneration())
                .setRevision(lesson.getRevision() == null ? 0 : lesson.getRevision())
                .setOccurrenceRevision(occurrenceRevision == null ? 0 : occurrenceRevision)
                .setCurrent(lesson.isCurrent());
        if (transferState != null) {
            response.setTransferOperationId(transferState.operationId())
                    .setTransferState(transferState.state());
        }
        return response.build();
    }

    private Map<Long, LessonTransferWriter.TransferState> transferStates(List<Long> lessonIds) {
        return lessonTransferWriter == null ? Map.of()
                : lessonTransferWriter.pendingStatesForLessons(lessonIds);
    }

    private Map<Long, Long> occurrenceRevisions(List<Long> lessonIds) {
        return lessonTransferWriter == null ? Map.of()
                : lessonTransferWriter.occurrenceRevisionsForLessons(lessonIds);
    }

    private LessonInfo lessonInfo(Lesson lesson, ScheduleItem item) {
        Long groupId = lesson.getGroupId() != null ? lesson.getGroupId() : item.getGroupId();
        Long subjectId = lesson.getSubjectId() != null ? lesson.getSubjectId() : item.getSubjectId();
        Short number = lesson.getLessonNumber() != null ? lesson.getLessonNumber() : item.getLessonNumber();
        java.time.LocalTime start = lesson.getStartTime() != null ? lesson.getStartTime() : item.getStartTime();
        return LessonInfo.newBuilder()
                .setLessonId(lesson.getId())
                .setGroupId(groupId)
                .setSubjectId(subjectId)
                .setStartsAt(lesson.getDate() + "T" + start)
                .setLessonNumber(number == null ? 0 : number.intValue())
                .setDate(lesson.getDate().toString())
                .setOccurrenceId(lesson.getOccurrenceId() == null ? 0 : lesson.getOccurrenceId())
                .setAssignmentId(lesson.getAssignmentId() == null ? 0 : lesson.getAssignmentId())
                .setSemesterId(lesson.getSemesterId() == null ? 0 : lesson.getSemesterId())
                .setTeacherId(lesson.getAssignedTeacherId() == null ? 0 : lesson.getAssignedTeacherId())
                .setLessonType(lesson.getLessonType() == null ? "" : lesson.getLessonType())
                .setGeneration(lesson.getGeneration() == null ? 0 : lesson.getGeneration())
                .setRevision(lesson.getRevision() == null ? 0 : lesson.getRevision())
                .setStatus(lesson.getStatus().name().toLowerCase())
                .build();
    }

    /**
     * Physical V17 rows carry their own identity snapshot. A read must remain
     * valid when the mutable recurring template is inactive or when the row is
     * a one-off with no schedule_item_id; the template is only a legacy
     * fallback for pre-snapshot values.
     */
    private ScheduleItem findTemplateForRead(Lesson lesson) {
        if (lesson.getScheduleItemId() == null) {
            return null;
        }
        return scheduleItemRepository.findById(lesson.getScheduleItemId()).orElse(null);
    }
}
