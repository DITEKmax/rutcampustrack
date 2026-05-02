package ru.rutcampustrack.documentrenderer.render;

import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Component
public class ProcessRunner {

    public ProcessResult run(List<String> command, Path workingDirectory, Duration timeout) {
        Path logFile = workingDirectory.resolve("renderer-process.log");
        try {
            Path homeDir = workingDirectory.resolve("home");
            Path cacheDir = homeDir.resolve(".cache");
            Path configDir = homeDir.resolve(".config");
            Files.createDirectories(cacheDir);
            Files.createDirectories(configDir);

            ProcessBuilder builder = new ProcessBuilder(command)
                    .directory(workingDirectory.toFile())
                    .redirectErrorStream(true)
                    .redirectOutput(logFile.toFile());
            builder.environment().put("HOME", homeDir.toString());
            builder.environment().put("XDG_CACHE_HOME", cacheDir.toString());
            builder.environment().put("XDG_CONFIG_HOME", configDir.toString());

            Process process = builder.start();
            boolean finished = process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS);
            if (!finished) {
                process.destroyForcibly();
                throw new DocumentConversionException("Renderer command timed out: " + command.get(0));
            }
            String output = Files.exists(logFile)
                    ? Files.readString(logFile, StandardCharsets.UTF_8)
                    : "";
            return new ProcessResult(process.exitValue(), output);
        } catch (IOException ex) {
            throw new DocumentConversionException("Failed to start renderer command: " + command.get(0), ex);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new DocumentConversionException("Renderer command interrupted: " + command.get(0), ex);
        }
    }

    public record ProcessResult(int exitCode, String output) {}
}
