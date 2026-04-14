package ru.rutcampustrack.schedule.grpc;

import io.grpc.stub.StreamObserver;
import net.devh.boot.grpc.server.service.GrpcService;
import ru.rutcampustrack.schedule.exception.ResourceNotFoundException;
import ru.rutcampustrack.schedule.item.entity.ScheduleItem;
import ru.rutcampustrack.schedule.item.repository.ScheduleItemRepository;
import ru.rutcampustrack.schedule.lesson.entity.Lesson;
import ru.rutcampustrack.schedule.lesson.repository.LessonRepository;

import java.time.LocalDate;
import java.time.LocalDateTime;
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

    public ScheduleGrpcServiceImpl(LessonRepository lessonRepository,
                                   ScheduleItemRepository scheduleItemRepository) {
        this.lessonRepository = lessonRepository;
        this.scheduleItemRepository = scheduleItemRepository;
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

        ScheduleItem item = scheduleItemRepository.findById(lesson.getScheduleItemId())
                .orElseThrow(() -> new ResourceNotFoundException("ScheduleItem", "id", lesson.getScheduleItemId()));

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

        ScheduleItem item = scheduleItemRepository.findById(lesson.getScheduleItemId())
                .orElseThrow(() -> new ResourceNotFoundException("ScheduleItem", "id", lesson.getScheduleItemId()));

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

        List<ScheduleItem> items = scheduleItemRepository
                .findByGroupIdAndSemesterIdAndIsActiveTrue(request.getGroupId(), request.getSemesterId());

        if (items.isEmpty()) {
            responseObserver.onNext(LessonsResponse.newBuilder().build());
            responseObserver.onCompleted();
            return;
        }

        List<Long> itemIds = items.stream().map(ScheduleItem::getId).toList();

        List<Lesson> lessons = lessonRepository.findByScheduleItemIdInAndDateBetweenAndStatusIn(
                itemIds, from, to, List.of("planned", "active", "closed", "cancelled"));

        Map<Long, ScheduleItem> itemById = items.stream()
                .collect(Collectors.toMap(ScheduleItem::getId, i -> i));

        List<LessonResponse> responses = lessons.stream()
                .filter(l -> itemById.containsKey(l.getScheduleItemId()))
                .map(l -> buildResponse(l, itemById.get(l.getScheduleItemId())))
                .toList();

        responseObserver.onNext(LessonsResponse.newBuilder().addAllLessons(responses).build());
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

        List<Long> scheduleItemIds = lessons.stream()
                .map(Lesson::getScheduleItemId)
                .distinct()
                .toList();

        Map<Long, ScheduleItem> itemById = scheduleItemRepository.findAllById(scheduleItemIds).stream()
                .collect(Collectors.toMap(ScheduleItem::getId, i -> i));

        List<LessonInfo> infos = lessons.stream()
                .filter(l -> itemById.containsKey(l.getScheduleItemId()))
                .map(l -> {
                    ScheduleItem item = itemById.get(l.getScheduleItemId());
                    return LessonInfo.newBuilder()
                            .setLessonId(l.getId())
                            .setGroupId(item.getGroupId())
                            .setSubjectId(item.getSubjectId())
                            .setStartsAt(l.getDate().toString() + "T" + item.getStartTime().toString())
                            .build();
                })
                .toList();

        responseObserver.onNext(LessonsByIdsResponse.newBuilder().addAllLessons(infos).build());
        responseObserver.onCompleted();
    }

    private LessonResponse buildResponse(Lesson lesson, ScheduleItem item) {
        return LessonResponse.newBuilder()
                .setId(lesson.getId())
                .setScheduleItemId(lesson.getScheduleItemId())
                .setGroupId(item.getGroupId())
                .setSubjectId(item.getSubjectId())
                .setTeacherId(item.getTeacherId())
                .setDate(lesson.getDate().toString())
                .setLessonNumber(item.getLessonNumber())
                .setStartTime(item.getStartTime().toString())
                .setEndTime(item.getEndTime().toString())
                .setStatus(lesson.getStatus().name().toLowerCase())
                .setIsGeoBlocked(lesson.isGeoBlocked())
                .setRoom(item.getRoom() != null ? item.getRoom() : "")
                .build();
    }
}
