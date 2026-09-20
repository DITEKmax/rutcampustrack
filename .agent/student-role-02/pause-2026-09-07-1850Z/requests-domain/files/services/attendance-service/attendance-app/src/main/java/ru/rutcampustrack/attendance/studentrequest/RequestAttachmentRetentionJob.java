package ru.rutcampustrack.attendance.studentrequest;

import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Clears expired attachment bytes while retaining the audit descriptor. */
@Component
@Profile("!test")
public class RequestAttachmentRetentionJob {

    private final StudentRequestService service;

    public RequestAttachmentRetentionJob(StudentRequestService service) {
        this.service = service;
    }

    @Scheduled(cron = "${attendance.request-attachments.expiry-cron:0 15 2 * * *}", zone = "UTC")
    @SchedulerLock(name = "RequestAttachmentRetentionJob-expire",
            lockAtMostFor = "PT10M", lockAtLeastFor = "PT30S")
    public void expire() {
        service.expireAttachments();
    }
}
