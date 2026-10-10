package ru.rutcampustrack.academic.semester;

import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import ru.rutcampustrack.academic.exception.BadRequestException;
import ru.rutcampustrack.academic.exception.ConflictException;
import ru.rutcampustrack.academic.contract.exception.ResourceNotFoundException;
import ru.rutcampustrack.academic.repository.SemesterRepository;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;

/** Durable bounded catch-up; the service lock and re-read make concurrent ticks replay-safe. */
@Component
@Slf4j
public class SemesterActivationJob {
    private final SemesterRepository repository;
    private final SemesterService service;
    private final Clock clock;

    public SemesterActivationJob(SemesterRepository repository, SemesterService service, Clock clock) {
        this.repository = repository;
        this.service = service;
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${semester.activation.delay-ms:30000}")
    public void activateDueSemesters() {
        LocalDate today = LocalDate.now(clock.withZone(ZoneId.of("Europe/Moscow")));
        for (Long id : repository.findDueScheduledActivationIds(today)) {
            try {
                service.activateScheduledSemester(id);
            } catch (ConflictException | BadRequestException | ResourceNotFoundException deferred) {
                log.warn("Scheduled semester activation deferred semester={} reason={}", id,
                        deferred.getClass().getSimpleName());
            }
        }
    }
}
