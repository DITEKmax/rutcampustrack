package ru.rutcampustrack.schedule.oneoff;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.rutcampustrack.schedule.contract.dto.oneoff.CreateOneOffLessonRequest;
import ru.rutcampustrack.schedule.contract.enums.UserRole;
import ru.rutcampustrack.schedule.exception.AccessDeniedException;
import ru.rutcampustrack.schedule.exception.ResourceNotFoundException;
import ru.rutcampustrack.schedule.oneoff.entity.OneOffLesson;
import ru.rutcampustrack.schedule.oneoff.projection.OneOffCurrentLessonProjection;
import ru.rutcampustrack.schedule.oneoff.repository.OneOffLessonRepository;
import ru.rutcampustrack.schedule.security.RequestContext;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Public one-off operations; remote authorization precedes the transactional writer. */
@Service
public class OneOffLessonService {
    private final OneOffLessonRepository repository;
    private final OneOffLessonCoordinator coordinator;
    private final OneOffLessonWriter writer;
    private final RequestContext context;

    public OneOffLessonService(OneOffLessonRepository repository,
                               OneOffLessonCoordinator coordinator,
                               OneOffLessonWriter writer, RequestContext context) {
        this.repository = repository;
        this.coordinator = coordinator;
        this.writer = writer;
        this.context = context;
    }

    /** Internal callers retain the immutable origin entity; public responses use the current snapshot. */
    public OneOffLesson createOneOffLesson(CreateOneOffLessonRequest request, UUID requestKey) {
        long id = coordinator.create(request, requestKey);
        return repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("OneOffLesson", "id", id));
    }

    public OneOffCurrentLessonProjection createCurrentOneOffLesson(CreateOneOffLessonRequest request, UUID requestKey) {
        long id = coordinator.create(request, requestKey);
        return repository.findCurrentSnapshot(id)
                .orElseThrow(() -> new ResourceNotFoundException("OneOffLesson", "id", id));
    }

    @Transactional(readOnly = true)
    public List<OneOffCurrentLessonProjection> listOneOffLessons(Long groupId, LocalDate dateFrom, LocalDate dateTo) {
        if (context.getRole() != UserRole.ADMIN && context.getRole() != UserRole.TEACHER
                && !java.util.Objects.equals(context.getGroupId(), groupId)) {
            throw new AccessDeniedException("Разовая пара принадлежит другой группе");
        }
        return repository.findCurrentSnapshots(groupId, dateFrom, dateTo);
    }

    /** DELETE cancels the current physical lesson and retains its origin/history. */
    public void deleteOneOffLesson(Long id) {
        OneOffLesson origin = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("OneOffLesson", "id", id));
        coordinator.requireHeadman(origin.getGroupId());
        writer.cancelOrigin(id, "Разовая пара отменена", coordinator.requireActor());
    }
}
