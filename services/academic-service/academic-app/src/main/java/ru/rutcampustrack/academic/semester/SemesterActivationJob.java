package ru.rutcampustrack.academic.semester;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import ru.rutcampustrack.academic.contract.exception.ResourceNotFoundException;
import ru.rutcampustrack.academic.exception.BadRequestException;
import ru.rutcampustrack.academic.exception.ConflictException;
import ru.rutcampustrack.academic.repository.SemesterRepository;
import ru.rutcampustrack.shared.web.time.EducationalTime;

/** Durable bounded catch-up; the service lock and re-read make concurrent ticks replay-safe. */
@Component
@Slf4j
public class SemesterActivationJob {
    private static final ZoneId MOSCOW = ZoneId.of("Europe/Moscow");
    private final SemesterRepository repository;
    private final SemesterService service;
    private final Clock educationalClock;

    @Autowired
    public SemesterActivationJob(
            SemesterRepository repository,
            SemesterService service,
            Clock clock,
            ObjectProvider<EducationalTime> educationalTime) {
        this(
                repository,
                service,
                educationalTime.getIfAvailable(() -> new EducationalTime(clock)).clock());
    }

    /** Retains direct test/helper callers without replacing the ordinary security Clock bean. */
    public SemesterActivationJob(
            SemesterRepository repository, SemesterService service, Clock clock) {
        this.repository = repository;
        this.service = service;
        this.educationalClock = clock;
    }

    @Scheduled(fixedDelayString = "${semester.activation.delay-ms:30000}")
    @SchedulerLock(
            name = "academic-semester-activation-job",
            lockAtMostFor = "PT10M",
            lockAtLeastFor = "PT1S")
    public void activateDueSemesters() {
        LocalDate today = LocalDate.ofInstant(educationalClock.instant(), MOSCOW);
        for (Long id : repository.findDueScheduledActivationIds(today)) {
            try {
                service.activateScheduledSemester(id);
            } catch (ConflictException | BadRequestException | ResourceNotFoundException deferred) {
                log.warn(
                        "Scheduled semester activation deferred semester={} reason={}",
                        id,
                        deferred.getClass().getSimpleName());
            }
        }
    }
}
