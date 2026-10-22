package ru.rutcampustrack.gateway.security;

import jakarta.annotation.PostConstruct;
import java.net.URI;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Fixed backend origins for the allowlisted report downloader. */
@ConfigurationProperties(prefix = "rutcampustrack.report-download")
public class ReportDownloadBackendProperties {

    private String mobileBffUrl = "http://mobile-bff:9080";
    private String attendanceServiceUrl = "http://attendance-service:9093";
    private String academicServiceUrl = "http://academic-service:9091";
    private long timeoutMillis = 30_000;

    public String getMobileBffUrl() {
        return mobileBffUrl;
    }

    public void setMobileBffUrl(String mobileBffUrl) {
        this.mobileBffUrl = mobileBffUrl;
    }

    public String getAttendanceServiceUrl() {
        return attendanceServiceUrl;
    }

    public void setAttendanceServiceUrl(String attendanceServiceUrl) {
        this.attendanceServiceUrl = attendanceServiceUrl;
    }

    public String getAcademicServiceUrl() {
        return academicServiceUrl;
    }

    public void setAcademicServiceUrl(String url) {
        academicServiceUrl = url;
    }

    public long getTimeoutMillis() {
        return timeoutMillis;
    }

    public void setTimeoutMillis(long timeoutMillis) {
        this.timeoutMillis = timeoutMillis;
    }

    @PostConstruct
    public void validate() {
        validateOrigin(mobileBffUrl, "mobile-bff-url");
        validateOrigin(attendanceServiceUrl, "attendance-service-url");
        validateOrigin(academicServiceUrl, "academic-service-url");
        if (timeoutMillis <= 0 || timeoutMillis > 60_000) {
            throw new IllegalStateException("report-download.timeout-millis must be in (0, 60000]");
        }
    }

    private static void validateOrigin(String value, String name) {
        try {
            URI uri = URI.create(value);
            if (!("http".equalsIgnoreCase(uri.getScheme())
                            || "https".equalsIgnoreCase(uri.getScheme()))
                    || uri.getHost() == null
                    || uri.getHost().isBlank()
                    || uri.getUserInfo() != null
                    || uri.getQuery() != null
                    || uri.getFragment() != null
                    || (uri.getPath() != null
                            && !uri.getPath().isEmpty()
                            && !"/".equals(uri.getPath()))) {
                throw new IllegalArgumentException("origin has non-origin parts");
            }
        } catch (RuntimeException exception) {
            throw new IllegalStateException(
                    "report-download." + name + " must be an HTTP(S) origin", exception);
        }
    }
}
