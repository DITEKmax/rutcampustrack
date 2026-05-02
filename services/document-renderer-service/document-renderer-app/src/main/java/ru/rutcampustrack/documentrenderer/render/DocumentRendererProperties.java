package ru.rutcampustrack.documentrenderer.render;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "document-renderer")
public class DocumentRendererProperties {

    private String libreofficeCommand = "soffice";
    private String pdftoppmCommand = "pdftoppm";
    private long timeoutSeconds = 25;
    private int pngDpi = 200;

    public String getLibreofficeCommand() {
        return libreofficeCommand;
    }

    public void setLibreofficeCommand(String libreofficeCommand) {
        this.libreofficeCommand = libreofficeCommand;
    }

    public String getPdftoppmCommand() {
        return pdftoppmCommand;
    }

    public void setPdftoppmCommand(String pdftoppmCommand) {
        this.pdftoppmCommand = pdftoppmCommand;
    }

    public Duration timeout() {
        return Duration.ofSeconds(timeoutSeconds);
    }

    public long getTimeoutSeconds() {
        return timeoutSeconds;
    }

    public void setTimeoutSeconds(long timeoutSeconds) {
        this.timeoutSeconds = timeoutSeconds;
    }

    public int getPngDpi() {
        return pngDpi;
    }

    public void setPngDpi(int pngDpi) {
        this.pngDpi = pngDpi;
    }
}
