package ru.rutcampustrack.academic.homework;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.rutcampustrack.academic.contract.dto.homework.CreateHomeworkRequest;
import ru.rutcampustrack.academic.contract.dto.homework.UpdateHomeworkRequest;
import ru.rutcampustrack.academic.contract.enums.HomeworkPublicationState;
import ru.rutcampustrack.academic.contract.enums.UserRole;
import ru.rutcampustrack.academic.contract.exception.ResourceNotFoundException;
import ru.rutcampustrack.academic.entity.Homework;
import ru.rutcampustrack.academic.entity.HomeworkCompletion;
import ru.rutcampustrack.academic.event.HomeworkPublishedEvent;
import ru.rutcampustrack.academic.event.HomeworkUpdatedEvent;
import ru.rutcampustrack.academic.exception.AccessDeniedException;
import ru.rutcampustrack.academic.exception.BadRequestException;
import ru.rutcampustrack.academic.exception.ConflictException;
import ru.rutcampustrack.academic.exception.HomeworkPublicationPendingException;
import ru.rutcampustrack.academic.exception.ScheduleServiceUnavailableException;
import ru.rutcampustrack.academic.grpc.ScheduleGrpcClient;
import ru.rutcampustrack.academic.repository.HeadmanAssistantRepository;
import ru.rutcampustrack.academic.repository.HomeworkCompletionRepository;
import ru.rutcampustrack.academic.repository.HomeworkRepository;
import ru.rutcampustrack.academic.repository.UserRoleGrantRepository;
import ru.rutcampustrack.academic.security.RequestContext;
import ru.rutcampustrack.schedule.grpc.HomeworkBindingResponse;
import ru.rutcampustrack.schedule.grpc.HomeworkBindingState;
import ru.rutcampustrack.schedule.grpc.LessonResponse;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;

@Service
public class HomeworkService {

    private final HomeworkRepository homeworkRepository;
    private final HomeworkCompletionRepository completionRepository;
    private final HeadmanAssistantRepository assistantRepository;
    private final RequestContext requestContext;
    private final ApplicationEventPublisher eventPublisher;
    private final ScheduleGrpcClient scheduleGrpcClient;
    private final Clock clock;
    private final HomeworkPublicationPersistence publicationPersistence;
    private final UserRoleGrantRepository grantRepository;

    /** Spring constructor. The persistence bean supplies real transaction boundaries. */
    @Autowired
    public HomeworkService(HomeworkRepository homeworkRepository,
                            HomeworkCompletionRepository completionRepository,
                            HeadmanAssistantRepository assistantRepository,
                            RequestContext requestContext,
                            ApplicationEventPublisher eventPublisher,
                            ScheduleGrpcClient scheduleGrpcClient,
                            Clock clock,
                            HomeworkPublicationPersistence publicationPersistence,
                            UserRoleGrantRepository grantRepository) {
        this.homeworkRepository = homeworkRepository;
        this.completionRepository = completionRepository;
        this.assistantRepository = assistantRepository;
        this.requestContext = requestContext;
        this.eventPublisher = eventPublisher;
        this.scheduleGrpcClient = scheduleGrpcClient;
        this.clock = clock;
        this.publicationPersistence = publicationPersistence;
        this.grantRepository = grantRepository;
    }

    /** Source-compatible constructor for focused service unit tests. */
    public HomeworkService(HomeworkRepository homeworkRepository,
                           HomeworkCompletionRepository completionRepository,
                           HeadmanAssistantRepository assistantRepository,
                           RequestContext requestContext,
                           ApplicationEventPublisher eventPublisher,
                           ScheduleGrpcClient scheduleGrpcClient,
                           Clock clock) {
        this(homeworkRepository, completionRepository, assistantRepository, requestContext,
                eventPublisher, scheduleGrpcClient, clock, null, null);
    }

    /**
     * Phase 61 / D-06: homework management is limited to the headman or an
     * active assistant carrying the manage_homework grant.
     * ADMIN удалён из разрешённых ролей (CONTEXT D-06).
     * Помощник старосты с {@code manage_homework} разрешением — legacy (pre-Phase 61,
     * Phase 61 CONTEXT явно выносит помощника в deferred-ideas, но удаление доступа
     * ломает Phase 52 функционал, поэтому оставляем без поломки обратной совместимости
     * для ASSISTANT — ADMIN же блокируется однозначно).
     */
    private void requireHeadmanOrManageHomework() {
        Long actorId = requestContext.getUserId();
        Long groupId = requestContext.getGroupId();
        if (actorId == null || actorId <= 0 || groupId == null || groupId <= 0
                || grantRepository == null) {
            throw new AccessDeniedException(
                    "Управлять ДЗ может только староста или помощник с manage_homework");
        }

        boolean currentHeadman = grantRepository
                .findByUserIdAndRoleAndStatus(actorId, "headman", "active")
                .stream()
                .anyMatch(grant -> groupId.equals(grant.getGroupId()));
        if (currentHeadman) {
            return;
        }

        boolean currentStudent = grantRepository
                .findByUserIdAndRoleAndStatus(actorId, "student", "active")
                .stream()
                .anyMatch(grant -> groupId.equals(grant.getGroupId()));
        if (!currentStudent) {
            throw new AccessDeniedException("Не является старостой или помощником");
        }

        var assistant = assistantRepository
                .findByGroupIdAndStudentIdAndIsActiveTrue(groupId, actorId)
                .orElseThrow(() -> new AccessDeniedException("Не является старостой или помощником"));
        boolean hasPermission = assistant.getPermissions() != null
                && Arrays.stream(assistant.getPermissions())
                .filter(Objects::nonNull)
                .anyMatch(permission -> "manage_homework".equalsIgnoreCase(permission.trim()));
        if (!hasPermission) {
            throw new AccessDeniedException("Отсутствует право MANAGE_HOMEWORK");
        }
    }

    /**
     * Phase 61 / D-05: content editing remains author-scoped. Terminal archive
     * uses the broader publish/manage permission and is handled separately.
     */
    private void requireAuthor(Homework homework) {
        Long currentUserId = requestContext.getUserId();
        if (currentUserId == null || !currentUserId.equals(homework.getPublishedBy())) {
            throw new AccessDeniedException(
                    "Редактировать или удалять ДЗ может только автор");
        }
    }

    /**
     * M13 G9: STUDENT может видеть ДЗ только своей группы (включая headman+assistant).
     * ADMIN/TEACHER видят любое. TEACHER здесь не используется (HomeworkController
     * @RequireRole указывает STUDENT/ADMIN для list, без role для get) — но добавляем
     * TEACHER для будущего расширения, чтобы не блокировать read-only роль.
     */
    private void assertCanReadGroup(Long groupId) {
        UserRole role = requestContext.getRole();
        if (role == UserRole.ADMIN || role == UserRole.TEACHER) {
            return;
        }
        Long ownGroupId = requestContext.getGroupId();
        if (ownGroupId == null || !ownGroupId.equals(groupId)) {
            throw new AccessDeniedException("ДЗ принадлежит другой группе");
        }
    }

    public Homework createHomework(CreateHomeworkRequest request) {
        // D-06: роль-гard (HEADMAN / assistant c manage_homework)
        requireHeadmanOrManageHomework();

        Long actorId = requestContext.getUserId();
        if (actorId == null || actorId <= 0) {
            throw new AccessDeniedException("Для создания ДЗ нужен аутентифицированный автор");
        }

        // Resolve the durable replay key before the mutable natural lesson
        // slot. A missing client key receives a deterministic server key from
        // the request identity, so a transfer cannot make a committed PENDING
        // command unrecoverable.
        UUID requestKey = request.requestKey() == null
                ? defaultRequestKey(request, actorId)
                : request.requestKey();
        if (publicationPersistence != null) {
            Homework existing = publicationPersistence
                    .findByActorAndRequest(actorId, requestKey)
                    .orElse(null);
            if (existing != null) {
                return replayExistingHomework(existing, request, actorId, requestKey);
            }
        }

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
        validateLessonIdentity(request, lesson);

        byte[] payloadHash = homeworkPayloadHash(request, actorId, lesson);
        // A missing client key still gets a replayable identity. Explicit
        // keys remain available when the author intentionally wants two
        // identical content payloads on the same lesson.
        HomeworkBindingResponse reservation = scheduleGrpcClient.reserveHomeworkBinding(
                lesson.getOccurrenceId(), requestKey, lesson.getRevision(), payloadHash);
        validateBindingResponse(reservation, request, lesson, true);

        if (reservation.getState() == HomeworkBindingState.HOMEWORK_BINDING_STATE_ACTIVE) {
            if (!reservation.hasHomeworkId()) {
                throw new ConflictException("schedule returned ACTIVE binding without homework_id");
            }
            Homework existing = homeworkRepository.findById(reservation.getHomeworkId())
                    .orElseThrow(() -> new ConflictException(
                            "binding is ACTIVE but Academic content is missing; retry is unsafe"));
            if (!Arrays.equals(existing.getPayloadHash(), payloadHash)
                    || !requestKey.equals(existing.getRequestKey())
                    || !actorId.equals(existing.getActorId())) {
                throw new ConflictException("idempotency key resolves to another homework payload");
            }
            if (publicationPersistence == null) {
                if (existing.getPublicationState() == HomeworkPublicationState.PENDING) {
                    throw new IllegalStateException("homework publication persistence is not configured");
                }
                return existing;
            }
            Homework activated = publicationPersistence.activate(
                    existing.getId(), actorId, requestKey,
                    reservation.getBindingId(), payloadHash);
            return requireNotArchived(activated);
        }
        if (reservation.getState() != HomeworkBindingState.HOMEWORK_BINDING_STATE_PENDING) {
            throw new ConflictException("homework binding is not pending");
        }

        Homework homework = new Homework(
                request.groupId(), request.subjectId(), request.semesterId(),
                request.title(), request.description(), request.link(),
                requestContext.getUserId(),
                request.lessonDate(), request.lessonNumber(),
                reservation.getBindingId(), actorId, requestKey, payloadHash
        );
        Homework saved;
        if (publicationPersistence == null) {
            // Unit-test-only compatibility path. The live Spring bean always
            // uses the explicit REQUIRES_NEW boundary above.
            saved = homeworkRepository.save(homework);
            homeworkRepository.flush();
        } else {
            saved = publicationPersistence.persistPending(
                    request.groupId(), request.subjectId(), request.semesterId(),
                    request.title(), request.description(), request.link(), actorId,
                    request.lessonDate(), request.lessonNumber(), reservation.getBindingId(),
                    requestKey, payloadHash);
        }
        if (saved.getId() == null) {
            throw new IllegalStateException("Academic did not assign a homework id before confirmation");
        }

        HomeworkBindingResponse confirmation;
        try {
            confirmation = scheduleGrpcClient.confirmHomeworkBinding(
                    reservation.getBindingId(), saved.getId(), requestKey);
        } catch (ScheduleServiceUnavailableException e) {
            // PENDING content is already committed. The caller receives the
            // typed 202 payload and can retry the same key to replay Reserve
            // and finish Confirm; a generic 500 would lose that recovery path.
            throw new HomeworkPublicationPendingException(
                    saved.getId(), reservation.getBindingId(), requestKey);
        }
        validateBindingResponse(confirmation, request, lesson, false);
        if (confirmation.getState() != HomeworkBindingState.HOMEWORK_BINDING_STATE_ACTIVE
                || !confirmation.hasHomeworkId()
                || confirmation.getHomeworkId() != saved.getId()) {
            throw new ConflictException("schedule did not activate the reserved homework binding");
        }
        if (publicationPersistence == null) {
            // Unit-test-only compatibility path: keep the already saved
            // instance and avoid a second repository write.  The live path
            // activates through the separate REQUIRES_NEW persistence bean.
            saved.activatePublication();
            homeworkRepository.flush();
        } else {
            saved = publicationPersistence.activate(
                    saved.getId(), actorId, requestKey,
                    reservation.getBindingId(), payloadHash);
            return requireNotArchived(saved);
        }
        publishHomeworkPublished(saved);
        return saved;
    }

    private Homework replayExistingHomework(Homework existing,
                                             CreateHomeworkRequest request,
                                             long actorId,
                                             UUID requestKey) {
        if (!Long.valueOf(actorId).equals(existing.getActorId())) {
            throw new ConflictException("idempotency key resolves to another homework payload");
        }
        Homework current = publicationPersistence.currentPublication(
                existing.getId(), actorId, requestKey,
                existing.getBindingId(), existing.getPayloadHash());
        if (!sameRequest(current, request)) {
            throw new ConflictException("idempotency key resolves to another homework payload");
        }
        if (current.getPublicationState() == HomeworkPublicationState.ARCHIVED) {
            throw new ConflictException("archived homework cannot be recreated");
        }
        if (current.getPublicationState() == HomeworkPublicationState.ACTIVE) {
            return current;
        }

        HomeworkBindingResponse confirmation;
        try {
            confirmation = scheduleGrpcClient.confirmHomeworkBinding(
                    current.getBindingId(), current.getId(), requestKey);
        } catch (ScheduleServiceUnavailableException e) {
            throw new HomeworkPublicationPendingException(
                    current.getId(), current.getBindingId(), requestKey);
        }
        validateReplayConfirmation(confirmation, current);
        return requireNotArchived(publicationPersistence.activate(
                current.getId(), actorId, requestKey,
                current.getBindingId(), current.getPayloadHash()));
    }

    private static Homework requireNotArchived(Homework homework) {
        if (homework.getPublicationState() == HomeworkPublicationState.ARCHIVED) {
            throw new ConflictException("archived homework cannot be activated");
        }
        return homework;
    }

    private static boolean sameRequest(Homework existing, CreateHomeworkRequest request) {
        return Objects.equals(existing.getGroupId(), request.groupId())
                && Objects.equals(existing.getSubjectId(), request.subjectId())
                && Objects.equals(existing.getSemesterId(), request.semesterId())
                && Objects.equals(existing.getTitle(), request.title())
                && Objects.equals(existing.getDescription(), request.description())
                && Objects.equals(existing.getLink(), request.link())
                && Objects.equals(existing.getLessonDate(), request.lessonDate())
                && Objects.equals(existing.getLessonNumber(), request.lessonNumber());
    }

    private static void validateReplayConfirmation(HomeworkBindingResponse confirmation,
                                                   Homework existing) {
        if (confirmation.getBindingId() != existing.getBindingId()
                || !confirmation.hasHomeworkId()
                || confirmation.getHomeworkId() != existing.getId()
                || confirmation.getState() != HomeworkBindingState.HOMEWORK_BINDING_STATE_ACTIVE
                || confirmation.getCurrentLesson().getOccurrenceId() <= 0
                || confirmation.getGroupId() != existing.getGroupId()
                || confirmation.getSubjectId() != existing.getSubjectId()
                || confirmation.getSemesterId() != existing.getSemesterId()) {
            throw new ConflictException("schedule confirmation does not match the durable homework identity");
        }
        if (confirmation.getRevision() <= 0) {
            throw new ConflictException("schedule returned an invalid binding revision");
        }
    }

    private void publishHomeworkPublished(Homework saved) {
        eventPublisher.publishEvent(new HomeworkPublishedEvent(
                this, saved.getId(), saved.getGroupId(), saved.getSubjectId(),
                saved.getTitle(), saved.getDescription(), saved.getLink(),
                saved.getLessonDate().toString(), saved.getLessonNumber()
        ));
    }

    private void validateLessonIdentity(CreateHomeworkRequest request, LessonResponse lesson) {
        if (lesson.getGroupId() != request.groupId()) {
            throw new BadRequestException("groupId", "ДЗ можно задать только для указанной группы");
        }
        if (lesson.getSubjectId() != request.subjectId()) {
            throw new BadRequestException("subjectId", "ДЗ можно задать только по предмету этой пары");
        }
        if (lesson.getSemesterId() != request.semesterId()) {
            throw new BadRequestException("semesterId", "Пара принадлежит другому семестру");
        }
        if (!request.lessonDate().toString().equals(lesson.getDate())) {
            throw new BadRequestException("lessonDate", "Дата ДЗ не совпадает с датой пары");
        }
        if (lesson.getLessonNumber() != request.lessonNumber()) {
            throw new BadRequestException("lessonNumber", "Номер ДЗ не совпадает с номером пары");
        }
        if (lesson.getOccurrenceId() <= 0 || lesson.getRevision() <= 0) {
            throw new ConflictException("schedule returned a lesson without occurrence revision");
        }
    }

    private void validateBindingResponse(HomeworkBindingResponse binding,
                                         CreateHomeworkRequest request,
                                         LessonResponse lesson,
                                         boolean requireRequestedPhysical) {
        if (binding.getBindingId() <= 0 || binding.getOccurrenceId() != lesson.getOccurrenceId()) {
            throw new ConflictException("schedule returned a binding for another occurrence");
        }
        if (binding.getGroupId() != request.groupId()
                || binding.getSubjectId() != request.subjectId()
                || binding.getSemesterId() != request.semesterId()) {
            throw new ConflictException("schedule binding identity does not match homework identity");
        }
        if (binding.getCurrentLesson().getOccurrenceId() != lesson.getOccurrenceId()
                || binding.getCurrentLesson().getGroupId() != request.groupId()
                || binding.getCurrentLesson().getSubjectId() != request.subjectId()
                || binding.getCurrentLesson().getSemesterId() != request.semesterId()) {
            throw new ConflictException("schedule binding current lesson has another immutable identity");
        }
        if (requireRequestedPhysical
                && (!request.lessonDate().toString().equals(binding.getDate())
                || binding.getLessonNumber() != request.lessonNumber()
                || !request.lessonDate().toString().equals(binding.getCurrentLesson().getDate())
                || binding.getCurrentLesson().getLessonNumber() != request.lessonNumber())) {
            throw new ConflictException("schedule binding current lesson is not the requested physical lesson");
        }
        if (binding.getRevision() <= 0) {
            throw new ConflictException("schedule returned an invalid binding revision");
        }
    }

    private byte[] homeworkPayloadHash(CreateHomeworkRequest request,
                                       long actorId,
                                       LessonResponse lesson) {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream(256);
            DataOutputStream out = new DataOutputStream(bytes);
            out.writeInt(1); // versioned canonical command encoding
            out.writeLong(actorId);
            out.writeLong(request.groupId());
            out.writeLong(request.subjectId());
            out.writeLong(request.semesterId());
            writeCanonicalString(out, request.lessonDate().toString());
            out.writeInt(request.lessonNumber());
            out.writeLong(lesson.getOccurrenceId());
            out.writeLong(lesson.getRevision());
            writeCanonicalString(out, request.title());
            writeCanonicalString(out, request.description());
            writeCanonicalString(out, request.link());
            out.flush();
            return MessageDigest.getInstance("SHA-256")
                    .digest(bytes.toByteArray());
        } catch (NoSuchAlgorithmException | IOException e) {
            throw new IllegalStateException("canonical homework hash encoding is unavailable", e);
        }
    }

    private static UUID defaultRequestKey(CreateHomeworkRequest request, long actorId) {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream(192);
            DataOutputStream out = new DataOutputStream(bytes);
            out.writeInt(1); // request-key identity version
            out.writeLong(actorId);
            out.writeLong(request.groupId());
            out.writeLong(request.subjectId());
            out.writeLong(request.semesterId());
            writeCanonicalString(out, request.lessonDate().toString());
            out.writeInt(request.lessonNumber());
            writeCanonicalString(out, request.title());
            writeCanonicalString(out, request.description());
            writeCanonicalString(out, request.link());
            out.flush();
            return UUID.nameUUIDFromBytes(bytes.toByteArray());
        } catch (IOException e) {
            throw new IllegalStateException("request-key encoding failed", e);
        }
    }

    private static void writeCanonicalString(DataOutputStream out, String value) throws IOException {
        if (value == null) {
            out.writeInt(-1);
            return;
        }
        byte[] encoded = value.getBytes(StandardCharsets.UTF_8);
        out.writeInt(encoded.length);
        out.write(encoded);
    }

    @Transactional(readOnly = true)
    public Homework getHomework(Long id) {
        Homework homework = homeworkRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Homework", "id", id));
        if (homework.getPublicationState() != HomeworkPublicationState.ACTIVE) {
            throw new ResourceNotFoundException("Homework", "id", id);
        }
        // M13 G9 — STUDENT видит ДЗ только своей группы
        assertCanReadGroup(homework.getGroupId());
        return homework;
    }

    @Transactional(readOnly = true)
    public Page<Homework> listHomeworks(Long groupId, Long semesterId, Pageable pageable) {
        // M13 G9 — STUDENT не может листать ДЗ чужой группы передавая чужой groupId
        assertCanReadGroup(groupId);
        List<Homework> list = homeworkRepository.findByGroupIdAndSemesterIdAndPublicationState(
                groupId, semesterId, HomeworkPublicationState.ACTIVE);
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
                this, saved.getId(), saved.getGroupId(), saved.getSubjectId(),
                saved.getTitle(), saved.getDescription(), saved.getLink(),
                saved.getLessonDate().toString(), saved.getLessonNumber()
        ));
        return saved;
    }

    public void deleteHomework(Long id) {
        requireHeadmanOrManageHomework();
        Homework homework = homeworkRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Homework", "id", id));
        assertCanReadGroup(homework.getGroupId());
        if (publicationPersistence == null) {
            if (homework.getPublicationState() == HomeworkPublicationState.ARCHIVED) {
                return;
            }
            // Fixture-only constructor: retain content as terminally archived
            // even when no Schedule client is installed.
            homework.archivePublication();
            homeworkRepository.save(homework);
            homeworkRepository.flush();
            return;
        }
        if (homework.getBindingId() == null || homework.getRequestKey() == null) {
            throw new ConflictException("homework has no durable binding identity");
        }
        // Read publication state only after the shared binding lock, using the
        // fresh row from its own persistence transaction instead of this
        // request's possibly stale managed entity.
        homework = publicationPersistence.currentPublication(
                homework.getId(), homework.getActorId(), homework.getRequestKey(),
                homework.getBindingId(), homework.getPayloadHash());
        if (homework.getPublicationState() == HomeworkPublicationState.ARCHIVED) {
            return;
        }

        Long actorId = requestContext.getUserId();
        HomeworkBindingResponse archived = scheduleGrpcClient.archiveHomeworkBinding(
                homework.getBindingId(), homework.getId(), homework.getRequestKey());
        if (archived.getBindingId() != homework.getBindingId()
                || !archived.hasHomeworkId()
                || archived.getHomeworkId() != homework.getId()
                || archived.getState() != HomeworkBindingState.HOMEWORK_BINDING_STATE_ARCHIVED) {
            throw new ConflictException("schedule did not durably archive the homework binding");
        }
        publicationPersistence.archive(
                homework.getId(), actorId, homework.getRequestKey(), homework.getBindingId());
    }

    @Transactional
    public void markComplete(Long homeworkId) {
        // M13 G9 — getHomework делает groupId-check; нельзя отмечать чужое ДЗ
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
        // M13 G9 — getHomework делает groupId-check
        getHomework(homeworkId);
        Long studentId = requestContext.getUserId();
        HomeworkCompletion completion = completionRepository
                .findByHomeworkIdAndStudentId(homeworkId, studentId)
                .orElseThrow(() -> new ResourceNotFoundException("HomeworkCompletion", "homeworkId", homeworkId));
        completionRepository.delete(completion);
    }
}
