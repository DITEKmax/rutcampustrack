package ru.rutcampustrack.auth.qr;

import jakarta.annotation.PostConstruct;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import java.util.List;

/** Server policy; every public lifetime/poll interval is returned to clients. */
@Component
@ConfigurationProperties(prefix = "auth.qr-login")
public final class QrLoginProperties {
    private int ttlSeconds = 120;
    private int replaySeconds = 30;
    private int pollAfterSeconds = 1;
    private int issuesPerIssuerMinute = 5;
    private int issuesPerIpMinute = 60;
    private int statusesPerIssuerMinute = 60;
    private int statusesPerIpMinute = 6000;
    private int approvalsPerUserMinute = 10;
    private int exchangesPerIssuerMinute = 30;
    private List<String> trustedProxyAddresses = List.of();
    private boolean cleanupEnabled = true;
    private int cleanupBatchSize = 100;

    public int getTtlSeconds() { return ttlSeconds; }
    public void setTtlSeconds(int value) { ttlSeconds = value; }
    public int getReplaySeconds() { return replaySeconds; }
    public void setReplaySeconds(int value) { replaySeconds = value; }
    public int getPollAfterSeconds() { return pollAfterSeconds; }
    public void setPollAfterSeconds(int value) { pollAfterSeconds = value; }
    public int getIssuesPerIssuerMinute() { return issuesPerIssuerMinute; }
    public void setIssuesPerIssuerMinute(int value) { issuesPerIssuerMinute = value; }
    public int getIssuesPerIpMinute() { return issuesPerIpMinute; }
    public void setIssuesPerIpMinute(int value) { issuesPerIpMinute = value; }
    public int getStatusesPerIssuerMinute() { return statusesPerIssuerMinute; }
    public void setStatusesPerIssuerMinute(int value) { statusesPerIssuerMinute = value; }
    public int getStatusesPerIpMinute() { return statusesPerIpMinute; }
    public void setStatusesPerIpMinute(int value) { statusesPerIpMinute = value; }
    public int getApprovalsPerUserMinute() { return approvalsPerUserMinute; }
    public void setApprovalsPerUserMinute(int value) { approvalsPerUserMinute = value; }
    public int getExchangesPerIssuerMinute() { return exchangesPerIssuerMinute; }
    public void setExchangesPerIssuerMinute(int value) { exchangesPerIssuerMinute = value; }
    public List<String> getTrustedProxyAddresses() { return trustedProxyAddresses; }
    public void setTrustedProxyAddresses(List<String> value) { trustedProxyAddresses = List.copyOf(value); }
    public boolean isCleanupEnabled() { return cleanupEnabled; }
    public void setCleanupEnabled(boolean value) { cleanupEnabled = value; }
    public int getCleanupBatchSize() { return cleanupBatchSize; }
    public void setCleanupBatchSize(int value) { cleanupBatchSize = value; }

    @PostConstruct public void validate() {
        if (trustedProxyAddresses.stream().anyMatch(value -> QrLoginClientIp.literal(value) == null))
            throw new IllegalStateException("auth.qr-login.trusted-proxy-addresses requires literal IP addresses");
        if (ttlSeconds < 1 || ttlSeconds > 600 || replaySeconds < 1 || replaySeconds > 120
                || pollAfterSeconds < 1 || pollAfterSeconds > 10
                || issuesPerIssuerMinute < 1 || issuesPerIpMinute < 1
                || statusesPerIssuerMinute < 1 || statusesPerIpMinute < 1
                || approvalsPerUserMinute < 1 || exchangesPerIssuerMinute < 1
                || cleanupBatchSize < 1 || cleanupBatchSize > 1000) {
            throw new IllegalStateException("auth.qr-login requires bounded positive lifetimes and positive rate limits");
        }
    }
}
