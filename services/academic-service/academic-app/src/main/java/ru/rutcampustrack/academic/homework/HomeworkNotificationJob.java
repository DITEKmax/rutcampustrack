package ru.rutcampustrack.academic.homework;

import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import ru.rutcampustrack.academic.contract.enums.UserRole;
import ru.rutcampustrack.academic.entity.Group;
import ru.rutcampustrack.academic.entity.Homework;
import ru.rutcampustrack.academic.entity.HomeworkCompletion;
import ru.rutcampustrack.academic.entity.HomeworkWeeklyDigestRun;
import ru.rutcampustrack.academic.entity.Semester;
import ru.rutcampustrack.academic.entity.Subject;
import ru.rutcampustrack.academic.entity.User;
import ru.rutcampustrack.academic.event.HomeworkDueReminderEvent;
import ru.rutcampustrack.academic.event.HomeworkNotificationItem;
import ru.rutcampustrack.academic.event.HomeworkWeeklyDigestEvent;
import ru.rutcampustrack.academic.repository.GroupRepository;
import ru.rutcampustrack.academic.repository.HomeworkCompletionRepository;
import ru.rutcampustrack.academic.repository.HomeworkRepository;
import ru.rutcampustrack.academic.repository.HomeworkWeeklyDigestRunRepository;
import ru.rutcampustrack.academic.repository.SemesterRepository;
import ru.rutcampustrack.academic.repository.SubjectRepository;
import ru.rutcampustrack.academic.repository.UserRepository;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.temporal.TemporalAdjusters;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
@Slf4j
public class HomeworkNotificationJob {

    private static final int DAYS_BEFORE_DUE = 2;

    private final HomeworkRepository homeworkRepository;
    private final HomeworkCompletionRepository completionRepository;
    private final HomeworkWeeklyDigestRunRepository digestRunRepository;
    private final SemesterRepository semesterRepository;
    private final GroupRepository groupRepository;
    private final UserRepository userRepository;
    private final SubjectRepository subjectRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;

    public HomeworkNotificationJob(HomeworkRepository homeworkRepository,
                                   HomeworkCompletionRepository completionRepository,
                                   HomeworkWeeklyDigestRunRepository digestRunRepository,
                                   SemesterRepository semesterRepository,
                                   GroupRepository groupRepository,
                                   UserRepository userRepository,
                                   SubjectRepository subjectRepository,
                                   ApplicationEventPublisher eventPublisher,
                                   Clock clock) {
        this.homeworkRepository = homeworkRepository;
        this.completionRepository = completionRepository;
        this.digestRunRepository = digestRunRepository;
        this.semesterRepository = semesterRepository;
        this.groupRepository = groupRepository;
        this.userRepository = userRepository;
        this.subjectRepository = subjectRepository;
        this.eventPublisher = eventPublisher;
        this.clock = clock;
    }

    @Scheduled(cron = "${rutcampustrack.homework.weekly-digest.cron:0 0 9 * * SUN}",
            zone = "${rutcampustrack.homework.scheduler-zone:Europe/Moscow}")
    @SchedulerLock(name = "homework-weekly-digest-job",
            lockAtMostFor = "PT10M",
            lockAtLeastFor = "PT30S")
    @Transactional
    public void publishWeeklyDigest() {
        LocalDate today = LocalDate.now(clock);
        Optional<Semester> maybeSemester = activeSemester(today);
        if (maybeSemester.isEmpty()) {
            return;
        }

        Semester semester = maybeSemester.get();
        LocalDate weekStart = today.with(TemporalAdjusters.next(DayOfWeek.MONDAY));
        if (weekStart.isAfter(semester.getDateTo())) {
            return;
        }
        LocalDate weekEnd = min(weekStart.plusDays(6), semester.getDateTo());
        OffsetDateTime now = OffsetDateTime.now(clock);

        int groupsProcessed = 0;
        int eventsPublished = 0;
        for (Group group : groupRepository.findAllByIsActiveTrue()) {
            if (digestRunRepository.existsByGroupIdAndWeekStart(group.getId(), weekStart)) {
                continue;
            }

            List<Homework> weekHomeworks = homeworkRepository
                    .findByGroupIdAndSemesterIdAndLessonDateBetweenOrderByLessonDateAscLessonNumberAscIdAsc(
                            group.getId(), semester.getId(), weekStart, weekEnd);
            Map<Long, String> subjectNames = subjectNames(weekHomeworks);

            for (User student : activeStudents(group.getId())) {
                List<HomeworkNotificationItem> items = incompleteItemsForStudent(
                        weekHomeworks, student.getId(), subjectNames);
                eventPublisher.publishEvent(new HomeworkWeeklyDigestEvent(
                        this,
                        student.getId(),
                        group.getId(),
                        semester.getId(),
                        weekStart.toString(),
                        weekEnd.toString(),
                        items));
                eventsPublished++;
            }

            digestRunRepository.save(new HomeworkWeeklyDigestRun(group.getId(), semester.getId(), weekStart, now));
            groupsProcessed++;
        }

        log.info("Homework weekly digests published: groups={}, events={}, weekStart={}, weekEnd={}",
                groupsProcessed, eventsPublished, weekStart, weekEnd);
    }

    @Scheduled(cron = "${rutcampustrack.homework.due-reminder.cron:0 0 9 * * *}",
            zone = "${rutcampustrack.homework.scheduler-zone:Europe/Moscow}")
    @SchedulerLock(name = "homework-due-reminder-job",
            lockAtMostFor = "PT10M",
            lockAtLeastFor = "PT30S")
    @Transactional
    public void publishDueReminders() {
        LocalDate today = LocalDate.now(clock);
        Optional<Semester> maybeSemester = activeSemester(today);
        if (maybeSemester.isEmpty()) {
            return;
        }

        Semester semester = maybeSemester.get();
        LocalDate dueDate = today.plusDays(DAYS_BEFORE_DUE);
        if (dueDate.isAfter(semester.getDateTo())) {
            return;
        }

        Set<Long> activeGroupIds = groupRepository.findAllByIsActiveTrue().stream()
                .map(Group::getId)
                .collect(Collectors.toSet());
        List<Homework> dueHomeworks = homeworkRepository
                .findBySemesterIdAndLessonDateAndDueReminderSentAtIsNullOrderByGroupIdAscLessonNumberAscIdAsc(
                        semester.getId(), dueDate).stream()
                .filter(hw -> activeGroupIds.contains(hw.getGroupId()))
                .toList();
        if (dueHomeworks.isEmpty()) {
            return;
        }

        Map<Long, String> subjectNames = subjectNames(dueHomeworks);
        int eventsPublished = 0;
        for (Homework homework : dueHomeworks) {
            Set<Long> completedStudentIds = completionRepository.findByHomeworkId(homework.getId()).stream()
                    .map(HomeworkCompletion::getStudentId)
                    .collect(Collectors.toSet());

            HomeworkNotificationItem item = item(homework, subjectNames);
            for (User student : activeStudents(homework.getGroupId())) {
                if (completedStudentIds.contains(student.getId())) {
                    continue;
                }
                eventPublisher.publishEvent(new HomeworkDueReminderEvent(
                        this,
                        student.getId(),
                        homework.getGroupId(),
                        semester.getId(),
                        DAYS_BEFORE_DUE,
                        dueDate.toString(),
                        item));
                eventsPublished++;
            }
            homework.setDueReminderSentAt(OffsetDateTime.now(clock));
        }

        homeworkRepository.saveAll(dueHomeworks);
        log.info("Homework due reminders published: homeworks={}, events={}, dueDate={}",
                dueHomeworks.size(), eventsPublished, dueDate);
    }

    private Optional<Semester> activeSemester(LocalDate today) {
        return semesterRepository.findByIsActiveTrue()
                .filter(s -> !today.isBefore(s.getDateFrom()) && !today.isAfter(s.getDateTo()));
    }

    private List<User> activeStudents(Long groupId) {
        return userRepository.findByGroupId(groupId).stream()
                .filter(u -> u.getRole() == UserRole.STUDENT)
                .toList();
    }

    private List<HomeworkNotificationItem> incompleteItemsForStudent(List<Homework> homeworks,
                                                                     Long studentId,
                                                                     Map<Long, String> subjectNames) {
        if (homeworks.isEmpty()) {
            return List.of();
        }
        List<Long> homeworkIds = homeworks.stream().map(Homework::getId).toList();
        Set<Long> completedHomeworkIds = completionRepository
                .findByHomeworkIdInAndStudentId(homeworkIds, studentId).stream()
                .map(HomeworkCompletion::getHomeworkId)
                .collect(Collectors.toCollection(HashSet::new));
        return homeworks.stream()
                .filter(hw -> !completedHomeworkIds.contains(hw.getId()))
                .map(hw -> item(hw, subjectNames))
                .toList();
    }

    private Map<Long, String> subjectNames(Collection<Homework> homeworks) {
        Set<Long> subjectIds = homeworks.stream()
                .map(Homework::getSubjectId)
                .collect(Collectors.toSet());
        if (subjectIds.isEmpty()) {
            return Map.of();
        }
        return subjectRepository.findAllById(subjectIds).stream()
                .collect(Collectors.toMap(Subject::getId, Subject::getName, (left, right) -> left));
    }

    private HomeworkNotificationItem item(Homework homework, Map<Long, String> subjectNames) {
        String link = homework.getLink();
        return new HomeworkNotificationItem(
                homework.getId(),
                homework.getSubjectId(),
                subjectNames.getOrDefault(homework.getSubjectId(), "Предмет"),
                homework.getTitle(),
                homework.getDescription(),
                link,
                link != null && !link.isBlank(),
                homework.getLessonDate().toString(),
                homework.getLessonNumber());
    }

    private static LocalDate min(LocalDate a, LocalDate b) {
        return a.isBefore(b) ? a : b;
    }
}
