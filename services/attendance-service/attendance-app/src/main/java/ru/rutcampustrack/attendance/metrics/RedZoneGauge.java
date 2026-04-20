package ru.rutcampustrack.attendance.metrics;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PostConstruct;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import ru.rutcampustrack.attendance.checkin.AttendanceRepository;
import ru.rutcampustrack.shared.observability.MetricNames;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicLong;

/**
 * M04 Группа 8 — gauge {@link MetricNames#STUDENTS_IN_RED_ZONE}.
 *
 * <p>Упрощённое определение «красной зоны» — уникальные студенты с
 * {@code status=absent} >= порога за последние {@code windowDays} дней.
 * Per-subject/per-group thresholds из academic-service в этот расчёт не
 * входят (cross-service join был бы дорогим для обычного gauge); operator
 * использует gauge как сигнал «массовые прогулы», детальный дашборд
 * строится по academic.attendance_threshold отдельно.
 *
 * <p>Пересчёт каждые 5 мин ({@link #refresh()}) — QA4 spec. Значение
 * хранится в {@link AtomicLong}, Micrometer Gauge читает его lazy при
 * scrape Prometheus'а.
 */
@Component
public class RedZoneGauge {

    private static final Logger log = LoggerFactory.getLogger(RedZoneGauge.class);

    private final AttendanceRepository repository;
    private final AtomicLong value = new AtomicLong(0);

    @Value("${attendance.red-zone.min-absences:3}")
    private long minAbsences;

    @Value("${attendance.red-zone.window-days:30}")
    private long windowDays;

    public RedZoneGauge(AttendanceRepository repository, MeterRegistry registry) {
        this.repository = repository;
        Gauge.builder(MetricNames.STUDENTS_IN_RED_ZONE, value, AtomicLong::get)
                .description("Студентов с >= N пропусков за последние windowDays дней")
                .register(registry);
    }

    @PostConstruct
    void init() {
        // Первичный расчёт при старте, чтобы gauge не висел на 0 до первого
        // scheduled прогона (5 мин — долго для smoke-тестов).
        refresh();
    }

    // NEW-28: gauge безопасен при scale-out (read-only aggregation), но
    // дублированный query на Mongo × N pods бесполезен. SchedulerLock
    // гарантирует что только один pod будет пересчитывать в 5-минутном
    // окне, остальные подхватят свежее значение gauge через Prometheus
    // remote_read (в Grafana dashboards видно единое значение).
    @Scheduled(fixedDelayString = "${attendance.red-zone.refresh-ms:300000}",
            initialDelayString = "${attendance.red-zone.refresh-ms:300000}")
    @SchedulerLock(name = "RedZoneGauge-refresh", lockAtMostFor = "4m", lockAtLeastFor = "30s")
    public void refresh() {
        try {
            Instant since = Instant.now().minus(Duration.ofDays(windowDays));
            long count = repository.countStudentsInRedZone(since, minAbsences)
                    .map(AttendanceRepository.RedZoneCount::getCount)
                    .orElse(0L);
            value.set(count);
            log.debug("RedZoneGauge refreshed: {} students (since={}, minAbsences={})",
                    count, since, minAbsences);
        } catch (Exception e) {
            // Не ронять gauge при сбое Mongo — оставляем предыдущее значение.
            log.warn("RedZoneGauge refresh failed, keeping previous value: {}", e.getMessage());
        }
    }
}
