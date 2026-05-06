package ru.rutcampustrack.gateway.filter;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.StringUtils;

@ConfigurationProperties(prefix = "rutcampustrack.pwa.version-policy")
public class PwaVersionPolicyProperties {

    private boolean enabled;
    private String latest;
    private String minimumSupported;
    private boolean force;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getLatest() {
        return latest;
    }

    public void setLatest(String latest) {
        this.latest = latest;
    }

    public String getMinimumSupported() {
        return minimumSupported;
    }

    public void setMinimumSupported(String minimumSupported) {
        this.minimumSupported = minimumSupported;
    }

    public boolean isForce() {
        return force;
    }

    public void setForce(boolean force) {
        this.force = force;
    }

    public String effectiveMinimumSupported() {
        return StringUtils.hasText(minimumSupported) ? minimumSupported : latest;
    }
}
