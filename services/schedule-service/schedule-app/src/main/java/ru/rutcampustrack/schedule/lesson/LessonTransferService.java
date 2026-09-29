package ru.rutcampustrack.schedule.lesson;

import org.springframework.stereotype.Service;
import ru.rutcampustrack.schedule.contract.dto.lesson.TransferLessonRequest;
import ru.rutcampustrack.schedule.contract.dto.lesson.TransferLessonResponse;
import ru.rutcampustrack.schedule.contract.enums.UserRole;
import ru.rutcampustrack.schedule.exception.AccessDeniedException;
import ru.rutcampustrack.schedule.exception.ResourceNotFoundException;
import ru.rutcampustrack.schedule.grpc.AcademicGrpcClient;
import ru.rutcampustrack.schedule.lesson.entity.Lesson;
import ru.rutcampustrack.schedule.lesson.repository.LessonRepository;
import ru.rutcampustrack.schedule.security.RequestContext;

import java.util.UUID;
import java.util.List;
import java.util.Map;

/** Authenticated application boundary for the durable recurring lesson transfer. */
@Service
public class LessonTransferService {

    private final LessonRepository lessons;
    private final RequestContext requestContext;
    private final AcademicGrpcClient academicGrpcClient;
    private final LessonTransferWriter writer;

    public LessonTransferService(LessonRepository lessons,
                                 RequestContext requestContext,
                                 AcademicGrpcClient academicGrpcClient,
                                 LessonTransferWriter writer) {
        this.lessons = lessons;
        this.requestContext = requestContext;
        this.academicGrpcClient = academicGrpcClient;
        this.writer = writer;
    }

    public TransferLessonResponse transfer(long lessonId, TransferLessonRequest request) {
        Lesson source = lessons.findById(lessonId)
                .orElseThrow(() -> new ResourceNotFoundException("Lesson", "id", lessonId));
        long groupId = source.getGroupId();
        requireHeadman(groupId);
        return writer.transfer(lessonId, requestContext.getUserId(), request);
    }

    public TransferLessonResponse status(UUID operationId) {
        long groupId = writer.groupId(operationId);
        requireHeadman(groupId);
        return writer.status(operationId);
    }

    public Map<Long, LessonTransferWriter.TransferState> statesForLessons(List<Long> lessonIds) {
        return writer.pendingStatesForLessons(lessonIds);
    }

    private void requireHeadman(long groupId) {
        if (requestContext.getRole() == UserRole.ADMIN) {
            return;
        }
        if (requestContext.getRole() != UserRole.STUDENT
                || !academicGrpcClient.isHeadman(requestContext.getUserId(), groupId)) {
            throw new AccessDeniedException("Only the group headman or an admin can transfer a lesson");
        }
    }
}
