package ru.rutcampustrack.schedule.lesson;

import org.springframework.stereotype.Service;
import ru.rutcampustrack.schedule.contract.dto.lesson.ChangeLessonRoomRequest;
import ru.rutcampustrack.schedule.contract.dto.lesson.ChangeLessonRoomResponse;
import ru.rutcampustrack.schedule.contract.enums.UserRole;
import ru.rutcampustrack.schedule.exception.AccessDeniedException;
import ru.rutcampustrack.schedule.exception.ResourceNotFoundException;
import ru.rutcampustrack.schedule.grpc.AcademicGrpcClient;
import ru.rutcampustrack.schedule.grpc.ScheduleSemesterArchiveWriteFence;
import ru.rutcampustrack.schedule.lesson.repository.LessonRepository;
import ru.rutcampustrack.schedule.security.RequestContext;
import java.util.UUID;

/** Current authority and remote archive eligibility are checked before local locks and replay. */
@Service
public class LessonRoomService {
    private final LessonRepository lessons;
    private final RequestContext context;
    private final AcademicGrpcClient academic;
    private final ScheduleSemesterArchiveWriteFence archive;
    private final LessonRoomWriter writer;

    public LessonRoomService(LessonRepository lessons, RequestContext context, AcademicGrpcClient academic,
                             ScheduleSemesterArchiveWriteFence archive, LessonRoomWriter writer) {
        this.lessons = lessons; this.context = context; this.academic = academic;
        this.archive = archive; this.writer = writer;
    }

    public ChangeLessonRoomResponse change(long lessonId, UUID key, ChangeLessonRoomRequest request) {
        var lesson = lessons.findById(lessonId)
                .orElseThrow(() -> new ResourceNotFoundException("Lesson", "id", lessonId));
        if (context.getRole() != UserRole.ADMIN && (context.getRole() != UserRole.STUDENT
                || !academic.isHeadman(context.getUserId(), lesson.getGroupId()))) {
            throw new AccessDeniedException("Только староста группы или администратор может изменить аудиторию");
        }
        var prepared = archive.prepareBusinessWrite(lesson.getSemesterId());
        return writer.change(lessonId, context.getUserId(), key, request, prepared);
    }
}
