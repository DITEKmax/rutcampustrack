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
import ru.rutcampustrack.academic.exception.BadRequestException;
import ru.rutcampustrack.academic.exception.ConflictException;
import ru.rutcampustrack.academic.grpc.ScheduleGrpcClient;
import ru.rutcampustrack.academic.repository.HeadmanAssistantRepository;
import ru.rutcampustrack.academic.repository.HomeworkCompletionRepository;
import ru.rutcampustrack.academic.repository.HomeworkRepository;
import ru.rutcampustrack.academic.security.RequestContext;
import ru.rutcampustrack.schedule.grpc.LessonResponse;

import java.time.Clock;
import java.time.LocalDate;
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
    private final ScheduleGrpcClient scheduleGrpcClient;
    private final Clock clock;

    public HomeworkService(HomeworkRepository homeworkRepository,
                            HomeworkCompletionRepository completionRepository,
                            HeadmanAssistantRepository assistantRepository,
                            RequestContext requestContext,
                            ApplicationEventPublisher eventPublisher,
                            ScheduleGrpcClient scheduleGrpcClient,
                            Clock clock) {
        this.homeworkRepository = homeworkRepository;
        this.completionRepository = completionRepository;
        this.assistantRepository = assistantRepository;
        this.requestContext = requestContext;
        this.eventPublisher = eventPublisher;
        this.scheduleGrpcClient = scheduleGrpcClient;
        this.clock = clock;
    }

    /**
     * Phase 61 / D-06: создавать/редактировать/удалять ДЗ может только сам староста.
     * ADMIN удалён из разрешённых ролей (CONTEXT D-06).
     * Помощник старосты с {@code manage_homework} разрешением — legacy (pre-Phase 61,
     * Phase 61 CONTEXT явно выносит помощника в deferred-ideas, но удаление доступа
     * ломает Phase 52 функционал, поэтому оставляем без поломки обратной совместимости
     * для ASSISTANT — ADMIN же блокируется однозначно).
     */
    private void requireHeadmanOrManageHomework() {
        if (requestContext.getRole() != UserRole.STUDENT) {
            throw new AccessDeniedException(
                    "Создавать/редактировать ДЗ может только староста группы");
        }
        if (!requestContext.isHeadman()) {
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

    /**
     * Phase 61 / D-05: автор — единственный владелец ДЗ.
     * Редактирование / удаление допускается только если {@code published_by == current user}.
     */
    private void requireAuthor(Homework homework) {
        Long currentUserId = requestContext.getUserId();
        if (currentUserId == null || !currentUserId.equals(homework.getPublishedBy())) {
            throw new AccessDeniedException(
                    "Редактировать или удалять ДЗ может только автор");
        }
    }

    @Transactional
    public Homework createHomework(CreateHomeworkRequest request) {
        // D-06: роль-гard (HEADMAN / assistant c manage_homework)
        requireHeadmanOrManageHomework();

        // D-03: дата пары не в прошлом (Moscow day, см. ClockConfig)
        if (request.lessonDate().isBefore(LocalDate.now(clock))) {
            throw new BadRequestException(
                    "lessonDate", "Нельзя создать ДЗ на прошедшую дату");
        }

        // D-04: пара существует + предмет совпадает
        LessonResponse lesson = scheduleGrpcClient
                .resolveLesson(request.groupId(), request.lessonDate(), request.lessonNumber())
                .orElseThrow(() -> new BadRequestException(
                        "lessonNumber",
                        "На эту пару нельзя назначить ДЗ — пары нет в расписании"));
        if (!Long.valueOf(lesson.getSubjectId()).equals(request.subjectId())) {
            throw new BadRequestException(
                    "subjectId",
                    "ДЗ можно задать только по предмету этой пары");
        }

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
        requireAuthor(homework); // D-05
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
        requireAuthor(homework); // D-05
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
