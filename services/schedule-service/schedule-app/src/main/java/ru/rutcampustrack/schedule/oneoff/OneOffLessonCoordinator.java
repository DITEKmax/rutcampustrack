package ru.rutcampustrack.schedule.oneoff;

import org.springframework.stereotype.Service;
import ru.rutcampustrack.academic.grpc.AssignmentInfo;
import ru.rutcampustrack.academic.grpc.GroupResponse;
import ru.rutcampustrack.academic.grpc.SemesterResponse;
import ru.rutcampustrack.schedule.contract.dto.oneoff.CreateOneOffLessonRequest;
import ru.rutcampustrack.schedule.contract.enums.UserRole;
import ru.rutcampustrack.schedule.exception.AccessDeniedException;
import ru.rutcampustrack.schedule.exception.ConflictException;
import ru.rutcampustrack.schedule.exception.OneOffCreateRejectedException;
import ru.rutcampustrack.schedule.grpc.AcademicGrpcClient;
import ru.rutcampustrack.schedule.recurring.RecurringAssignmentAuthority;
import ru.rutcampustrack.schedule.security.RequestContext;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Validates current actor and remote assignment authority before the local writer. */
@Service
public class OneOffLessonCoordinator {
    private final AcademicGrpcClient academic;
    private final RequestContext context;
    private final OneOffLessonWriter writer;

    public OneOffLessonCoordinator(AcademicGrpcClient academic, RequestContext context,
                                   OneOffLessonWriter writer) {
        this.academic = academic;
        this.context = context;
        this.writer = writer;
    }

    public long create(CreateOneOffLessonRequest request, UUID requestKey) {
        validateRequest(request, requestKey);
        requireHeadman(request.groupId());
        long actor = requireActor();
        OneOffLessonWriter.Creation replay = writer.findReplay(request, requestKey, actor);
        if (replay != null) return replay.oneOffLessonId();

        GroupResponse group = academic.validateGroup(request.groupId());
        if (group == null || group.getId() != request.groupId() || !group.getIsActive()) {
            throw new OneOffCreateRejectedException("Группа неактивна или ответ Academic не соответствует запросу");
        }
        SemesterResponse semester = academic.getActiveSemester();
        if (semester == null || semester.getId() <= 0) {
            throw new OneOffCreateRejectedException("Нет активного семестра для разовой пары");
        }
        List<AssignmentInfo> assignments = academic.getAssignmentsByIds(List.of(request.assignmentId()));
        if (assignments == null || assignments.size() != 1 || assignments.get(0) == null
                || assignments.get(0).getId() != request.assignmentId()) {
            throw new OneOffCreateRejectedException("Academic не вернул точное назначение преподавателя");
        }
        AssignmentInfo raw = assignments.get(0);
        RecurringAssignmentAuthority authority;
        try {
            authority = new RecurringAssignmentAuthority(raw.getId(), raw.getTeacherId(), raw.getSubjectId(),
                    raw.getGroupId(), raw.getSemesterId(), raw.getLessonType(),
                    LocalDate.parse(raw.getValidFrom()), LocalDate.parse(raw.getValidUntilExclusive()));
        } catch (RuntimeException error) {
            throw new OneOffCreateRejectedException("Назначение преподавателя содержит некорректные данные");
        }
        LocalDate from = parseDate(semester.getDateFrom());
        LocalDate to = parseDate(semester.getDateTo());
        if (authority.groupId() != request.groupId() || authority.subjectId() != request.subjectId()
                || authority.semesterId() != semester.getId() || from.isAfter(to)
                || request.date().isBefore(from) || request.date().isAfter(to)
                || request.date().isBefore(authority.validFrom())
                || !request.date().isBefore(authority.validUntilExclusive())) {
            throw new OneOffCreateRejectedException("Группа, предмет, семестр или дата не входят в назначение преподавателя");
        }
        return writer.create(request, requestKey, actor, authority).oneOffLessonId();
    }

    /** Returns null for the existing recurring route; all remote checks precede the one-off writer transaction. */
    public Long restoreIfOneOff(long lessonId) {
        OneOffLessonWriter.RestorePlan plan = writer.planRestore(lessonId);
        if (plan == null) return null;
        long actor = requireActor();
        if (context.getRole() != UserRole.ADMIN) {
            if (context.isHeadman()) {
                if (!academic.isHeadman(actor, plan.groupId())) throw new AccessDeniedException("Ты не староста этой группы");
            } else if (!java.util.Objects.equals(context.getGroupId(), plan.groupId())
                    || !academic.hasAssistantPermission(plan.groupId(), "CANCEL_LESSONS")) {
                throw new AccessDeniedException("Нет права восстановить пару этой группы");
            }
        }
        RecurringAssignmentAuthority authority = null;
        if (!plan.date().isBefore(writer.today())) {
            List<AssignmentInfo> assignments = academic.getAssignmentsByIds(List.of(plan.targetAssignmentId()));
            if (assignments == null || assignments.size() != 1 || assignments.get(0) == null
                    || assignments.get(0).getId() != plan.targetAssignmentId()) {
                throw new ConflictException("Academic не вернул текущее назначение преподавателя");
            }
            AssignmentInfo raw = assignments.get(0);
            try {
                authority = new RecurringAssignmentAuthority(raw.getId(), raw.getTeacherId(), raw.getSubjectId(),
                        raw.getGroupId(), raw.getSemesterId(), raw.getLessonType(),
                        LocalDate.parse(raw.getValidFrom()), LocalDate.parse(raw.getValidUntilExclusive()));
            } catch (RuntimeException error) {
                throw new ConflictException("Текущее назначение содержит некорректные данные");
            }
        }
        return writer.restore(plan, authority, actor);
    }

    public void requireHeadman(long groupId) {
        if (context.getRole() == UserRole.ADMIN) return;
        if (!context.isHeadman() || !academic.isHeadman(requireActor(), groupId)) {
            throw new AccessDeniedException("Только староста своей группы или администратор может изменить разовую пару");
        }
    }

    public long requireActor() {
        Long actor = context.getUserId();
        if (actor == null || actor <= 0) throw new AccessDeniedException("Требуется авторизованный пользователь");
        return actor;
    }

    private static void validateRequest(CreateOneOffLessonRequest request, UUID requestKey) {
        if (request == null || requestKey == null || request.groupId() == null || request.groupId() <= 0
                || request.subjectId() == null || request.subjectId() <= 0
                || request.assignmentId() == null || request.assignmentId() <= 0 || request.date() == null
                || request.lessonNumber() == null || request.lessonNumber() < 1 || request.lessonNumber() > 8
                || request.startTime() == null || request.endTime() == null
                || !request.endTime().isAfter(request.startTime())
                || request.classroom() != null && request.classroom().length() > 64) {
            throw new OneOffCreateRejectedException("Для разовой пары нужны request key, назначение, дата, номер и корректное время");
        }
    }

    private static LocalDate parseDate(String value) {
        try {
            return LocalDate.parse(value);
        } catch (RuntimeException error) {
            throw new OneOffCreateRejectedException("Academic вернул некорректные границы семестра");
        }
    }
}
