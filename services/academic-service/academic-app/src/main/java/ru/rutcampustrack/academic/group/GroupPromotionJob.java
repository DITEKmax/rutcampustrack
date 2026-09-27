package ru.rutcampustrack.academic.group;

import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import ru.rutcampustrack.academic.contract.dto.group.PromotionSummary;

@Component
@Slf4j
public class GroupPromotionJob {

    private final GroupPromotionService promotionService;

    public GroupPromotionJob(GroupPromotionService promotionService) {
        this.promotionService = promotionService;
    }

    @Scheduled(cron = "${rutcampustrack.academic.group-promotion.cron:0 0 3 * * *}",
            zone = "Europe/Moscow")
    @SchedulerLock(name = "academic-group-promotion-job",
            lockAtMostFor = "PT10M",
            lockAtLeastFor = "PT30S")
    public void checkDueSpringCycle() {
        promotionService.executeAutomaticallyIfDue().ifPresent(this::logResult);
    }

    private void logResult(PromotionSummary summary) {
        boolean hasWork = !summary.getToPromote().isEmpty()
                || !summary.getToArchive().isEmpty()
                || !summary.getConflicts().isEmpty();
        if (!hasWork) {
            return;
        }

        log.info("Automatic group promotion cycle={} promoted={} archived={} skipped={} conflicts={}",
                summary.getCycleSemesterId(), summary.getToPromote().size(), summary.getToArchive().size(),
                summary.getSkipped().size(), summary.getConflicts().size());
        summary.getConflicts().forEach(conflict -> log.warn(
                "Automatic group promotion conflict cycle={} prefix={} reason={} groupIds={}",
                summary.getCycleSemesterId(), conflict.getPrefix(), conflict.getReason(), conflict.getGroupIds()));
    }
}
