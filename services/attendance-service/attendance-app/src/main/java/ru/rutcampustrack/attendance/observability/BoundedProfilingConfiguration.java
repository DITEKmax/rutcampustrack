package ru.rutcampustrack.attendance.observability;

import io.pyroscope.http.Format;
import io.pyroscope.javaagent.EventType;
import io.pyroscope.javaagent.PyroscopeAgent;
import io.pyroscope.javaagent.config.Config;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;
import java.util.Map;

/** Explicit opt-in for one JVM; no allocator, lock events, user or request labels. */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "rct.profiling.enabled", havingValue = "true")
public class BoundedProfilingConfiguration {
    private static final Logger LOG = LoggerFactory.getLogger(BoundedProfilingConfiguration.class);
    private final String address;

    public BoundedProfilingConfiguration(
            @Value("${rct.profiling.address:http://pyroscope:4040}") String address) {
        this.address = address;
    }

    @PostConstruct
    public void start() {
        try {
            PyroscopeAgent.start(configuration(address));
        } catch (RuntimeException | LinkageError failure) {
            LOG.warn("Optional CPU profiling unavailable; business operations remain enabled");
        }
    }

    public static Config configuration(String address) {
        return new Config.Builder()
                .setApplicationName("rct.attendance")
                .setServerAddress(address)
                .setProfilingEvent(EventType.ITIMER)
                .setProfilingInterval(Duration.ofMillis(10))
                .setFormat(Format.JFR)
                .setProfilingAlloc("")
                .setProfilingLock("")
                .setUploadInterval(Duration.ofSeconds(10))
                .setProfileExportTimeout(Duration.ofSeconds(2))
                .setJavaStackDepthMax(256)
                .setPushQueueCapacity(2)
                .setLabels(Map.of("operation_scope", "attendance-report"))
                .build();
    }

    @PreDestroy
    public void stop() {
        try {
            PyroscopeAgent.stop();
        } catch (RuntimeException | LinkageError failure) {
            LOG.debug("Optional CPU profiling shutdown unavailable");
        }
    }
}
