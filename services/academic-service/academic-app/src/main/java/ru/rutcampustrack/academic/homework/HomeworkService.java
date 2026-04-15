package ru.rutcampustrack.academic.homework;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.rutcampustrack.academic.contract.dto.homework.CreateHomeworkRequest;
import ru.rutcampustrack.academic.contract.dto.homework.UpdateHomeworkRequest;
import ru.rutcampustrack.academic.contract.enums.UserRole;
import ru.rutcampustrack.academic.contract.exception.ResourceNotFoundException;
import ru.rutcampustrack.academic.entity.Homework;
import ru.rutcampustrack.academic.entity.HomeworkCompletion;
import ru.rutcampustrack.academic.event.HomeworkPublishedEvent;
import ru.rutcampustrack.academic.event.HomeworkUpdatedEvent;
import ru.rutcampustrack.academic.exception.AccessDeniedException;
import ru.rutcampustrack.academic.exception.ConflictException;
import ru.rutcampustrack.academic.repository.HeadmanAssistantRepository;
import ru.rutcampustrack.academic.repository.HomeworkCompletionRepository;
import ru.rutcampustrack.academic.repository.HomeworkRepository;
import ru.rutcampustrack.academic.security.RequestContext;

import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.List;

@Service
public class HomeworkService {

    private final HomeworkRepository homeworkRepository;
    private final HomeworkCompletionRepository completionRepository;
    private final HeadmanAssistantRepository assistantRepository;
    private final RequestContext requestContext;
    private final ApplicationEventPublisher eventPublisher;

    public HomeworkService(HomeworkRepository homeworkRepository,
                            HomeworkCompletionRepository completionRepository,
                            HeadmanAssistantRepository assistantRepository,
                            RequestContext requestContext,
                            ApplicationEventPublisher eventPublisher) {
        this.homeworkRepository = homeworkRepository;
        this.completionRepository = completionRepository;
        this.assistantRepository = assistantRepository;
        this.requestContext = requestContext;
        this.eventPublisher = eventPublisher;
    }

    /**
     * Checks headman or assistant with MANAGE_HOMEWORK permission (D-03, Pitfall 4).
     */
    private void requireHeadmanOrManageHomework() {
        if (requestContext.getRole() == UserRole.STUDENT) {
            if (!requestContext.isHeadman()) {
                // Check assistant delegation for MANAGE_HOMEWORK
                var assistant = assistantRepository
                        .findByGroupIdAndStudentId(requestContext.getGroupId(), requestContext.getUserId())
                        .filter(a -> a.isActive())
                        .orElseThrow(() -> new AccessDeniedException("Не является старостой или помощником"));
                boolean hasPermission = Arrays.asList(assistant.getPermissions()).contains("manage_homework");
                if (!hasPermission) {
                    throw new AccessDeniedException("Отсутствует право MANAGE_HOMEWORK");
                }
            }
        }
    }

    @Transactional
    public Homework createHomework(CreateHomeworkRequest request) {
        requireHeadmanOrManageHomework();
        Homework homework = new Homework(
                request.groupId(), request.subjectId(), request.semesterId(),
                request.title(), request.description(), request.link(),
                requestContext.getUserId(),
                request.lessonDate(), request.lessonNumber()
        );
        Homework saved = homeworkRepository.save(homework);
        eventPublisher.publishEvent(new HomeworkPublishedEvent(
                this, saved.getId(), saved.getGroupId(), saved.getSubjectId(),
                saved.getTitle(), saved.getLink() != null
        ));
        return saved;
    }

    @Transactional(readOnly = true)
    public Homework getHomework(Long id) {
        return homeworkRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Homework", "id", id));
    }

    @Transactional(readOnly = true)
    public Page<Homework> listHomeworks(Long groupId, Long semesterId, Pageable pageable) {
        List<Homework> list = homeworkRepository.findByGroupIdAndSemesterId(groupId, semesterId);
        int start = (int) pageable.getOffset();
        int end = Math.min(start + pageable.getPageSize(), list.size());
        List<Homework> page = start >= list.size() ? List.of() : list.subList(start, end);
        return new PageImpl<>(page, pageable, list.size());
    }

    /**
     * Check if a specific homework is completed by the current student.
     */
    @Transactional(readOnly = true)
    public boolean isCompleted(Long homeworkId) {
        Long studentId = requestContext.getUserId();
        return completionRepository.existsByHomeworkIdAndStudentId(homeworkId, studentId);
    }

    @Transactional
    public Homework updateHomework(Long id, UpdateHomeworkRequest request) {
        requireHeadmanOrManageHomework();
        Homework homework = getHomework(id);
        homework.setTitle(request.title());
        homework.setDescription(request.description());
        homework.setLink(request.link());
        homework.setUpdatedAt(OffsetDateTime.now());
        Homework saved = homeworkRepository.save(homework);
        eventPublisher.publishEvent(new HomeworkUpdatedEvent(
                this, saved.getId(), saved.getGroupId(), saved.getTitle()
        ));
        return saved;
    }

    @Transactional
    public void deleteHomework(Long id) {
        requireHeadmanOrManageHomework();
        Homework homework = getHomework(id);
        homeworkRepository.delete(homework);
    }

    @Transactional
    public void markComplete(Long homeworkId) {
        // Check homework exists
        getHomework(homeworkId);
        Long studentId = requestContext.getUserId();
        if (completionRepository.existsByHomeworkIdAndStudentId(homeworkId, studentId)) {
            throw new ConflictException("Домашнее задание уже отмечено как выполненное");
        }
        HomeworkCompletion completion = new HomeworkCompletion(homeworkId, studentId);
        completionRepository.save(completion);
    }

    @Transactional
    public void unmarkComplete(Long homeworkId) {
        Long studentId = requestContext.getUserId();
        HomeworkCompletion completion = completionRepository
                .findByHomeworkIdAndStudentId(homeworkId, studentId)
                .orElseThrow(() -> new ResourceNotFoundException("HomeworkCompletion", "homeworkId", homeworkId));
        completionRepository.delete(completion);
    }
}
