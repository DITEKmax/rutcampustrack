package ru.rutcampustrack.attendance.report;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.management.OperatingSystemMXBean;

import io.pyroscope.javaagent.PyroscopeAgent;
import io.sentry.Sentry;

import ru.rutcampustrack.attendance.observability.BoundedProfilingConfiguration;
import ru.rutcampustrack.shared.observability.errors.SafeErrorEvent;

import java.lang.management.ManagementFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/** Synthetic controlled driver, actual 18-week Java report; no business DB or user data. */
public final class ObservabilityProbe {
    private static Path control;
    private static String mode;
    private static final String MARKER = "synthetic-private@example.invalid";
    private static final OperatingSystemMXBean OS =
            (OperatingSystemMXBean) ManagementFactory.getOperatingSystemMXBean();
    private static final AtomicLong PEAK = new AtomicLong();
    private static final AtomicBoolean RUNNING = new AtomicBoolean(true);

    public static void main(String[] args) throws Exception {
        if (!"isolated-ci".equals(System.getenv("RCT_OBSERVABILITY_APPS_RUNTIME")))
            throw new IllegalStateException("Isolated CI only");
        mode = args[0];
        control = Path.of(args[1]).getParent();
        Thread sampler =
                new Thread(
                        () -> {
                            while (RUNNING.get()) {
                                PEAK.accumulateAndGet(rss(), Math::max);
                                try {
                                    Thread.sleep(50);
                                } catch (InterruptedException ignored) {
                                    return;
                                }
                            }
                        });
        sampler.setDaemon(true);
        sampler.start();
        Map<String, Object> result = new LinkedHashMap<>();
        if (mode.equals("profile") || mode.equals("outage")) {
            PyroscopeAgent.start(
                    BoundedProfilingConfiguration.configuration(
                            System.getenv("RCT_PYROSCOPE_ADDRESS")));
        }
        if (mode.equals("errors") || mode.equals("outage")) {
            Sentry.init(
                    options -> {
                        options.setDsn(System.getenv("RCT_JAVA_GLITCHTIP_DSN"));
                        options.setRelease(System.getenv("GITHUB_SHA"));
                        options.setEnvironment("isolated-ci");
                        options.setBeforeSend(new SafeErrorEvent());
                        options.setSendDefaultPii(false);
                        options.setMaxBreadcrumbs(0);
                        options.setEnableUncaughtExceptionHandler(false);
                        options.setConnectionTimeoutMillis(1000);
                        options.setReadTimeoutMillis(1000);
                    });
            for (int index = 0; index < 2; index++) {
                Sentry.withScope(
                        scope -> {
                            scope.setExtra("password", MARKER);
                            scope.setTag("token", MARKER);
                            Sentry.captureException(errorForExport());
                        });
            }
        }
        if (mode.equals("baseline") || mode.equals("profile")) {
            result.put("idle", measure(false, 10000));
            result.put("operation", measure(true, 20000));
        } else {
            result.put("operation", measure(true, 2000));
        }
        if (mode.equals("errors")) Sentry.flush(3000);
        if (mode.equals("profile"))
            Thread.sleep(12000); // Let the completed profile export asynchronously.
        if (PyroscopeAgent.isStarted()) PyroscopeAgent.stop();
        Sentry.close();
        RUNNING.set(false);
        result.put("mode", mode);
        result.put("result", "PASS");
        result.put("operation_name", "DocxRenderer.render:18-weeks");
        Files.writeString(Path.of(args[1]), new ObjectMapper().writeValueAsString(result));
    }

    private static RuntimeException errorForExport() {
        return new IllegalStateException(MARKER);
    }

    private static Map<String, Object> measure(boolean operation, long durationMillis)
            throws Exception {
        DocxRenderer renderer = new DocxRenderer();
        List<HeadmanWeeklyReportModel> models = models();
        // Warm the actual operation equally before sampling both modes.
        if (operation) renderer.render(models);
        Files.writeString(control.resolve("phase"), mode + (operation ? "-operation" : "-idle"));
        PEAK.set(rss());
        long start = System.nanoTime();
        long cpu = OS.getProcessCpuTime();
        int count = 0;
        while ((System.nanoTime() - start) / 1000000 < durationMillis) {
            if (operation) {
                byte[] docx = renderer.render(models);
                if (docx.length < 1000) throw new IllegalStateException("Report result missing");
                count++;
            } else Thread.sleep(100);
        }
        long elapsed = System.nanoTime() - start;
        long consumed = OS.getProcessCpuTime() - cpu;
        Files.writeString(control.resolve("phase"), mode + "-between-phases");
        return Map.of(
                "duration_ns",
                elapsed,
                "process_cpu_ns",
                consumed,
                "peak_rss_bytes",
                PEAK.get(),
                "operations",
                count,
                "cpu_cores",
                (double) consumed / elapsed);
    }

    private static long rss() {
        try {
            for (String line : Files.readAllLines(Path.of("/proc/self/status"))) {
                if (line.startsWith("VmRSS:")) return Long.parseLong(line.split("\\s+")[1]) * 1024;
            }
        } catch (Exception ignored) {
        }
        return -1;
    }

    private static List<HeadmanWeeklyReportModel> models() {
        List<HeadmanWeeklyReportModel> result = new ArrayList<>();
        for (int week = 0; week < 18; week++) {
            LocalDate monday = LocalDate.of(2026, 2, 2).plusWeeks(week);
            List<HeadmanWeeklyReportModel.Day> days = new ArrayList<>();
            for (int day = 0; day < HeadmanWeeklyReportModel.TEMPLATE_DAYS; day++) {
                days.add(new HeadmanWeeklyReportModel.Day(monday.plusDays(day), List.of()));
            }
            result.add(
                    new HeadmanWeeklyReportModel(
                            10L,
                            "SYNTHETIC",
                            "Synthetic group",
                            1L,
                            "Synthetic term",
                            week + 1,
                            monday,
                            monday.plusDays(6),
                            days,
                            List.of(
                                    new HeadmanWeeklyReportModel.Student(
                                            1L, "Synthetic student", List.of()))));
        }
        return result;
    }
}
