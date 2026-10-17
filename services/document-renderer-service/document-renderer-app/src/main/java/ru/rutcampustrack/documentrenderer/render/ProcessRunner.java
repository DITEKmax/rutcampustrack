package ru.rutcampustrack.documentrenderer.render;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.springframework.stereotype.Component;

@Component
public class ProcessRunner {

    public ProcessResult run(List<String> command, Path workingDirectory, Duration timeout) {
        Path logFile = workingDirectory.resolve("renderer-process.log");
        Process process = null;
        try {
            Path homeDir = workingDirectory.resolve("home");
            Path cacheDir = homeDir.resolve(".cache");
            Path configDir = homeDir.resolve(".config");
            Files.createDirectories(cacheDir);
            Files.createDirectories(configDir);

            ProcessBuilder builder =
                    new ProcessBuilder(command)
                            .directory(workingDirectory.toFile())
                            .redirectErrorStream(true)
                            .redirectOutput(logFile.toFile());
            builder.environment().put("HOME", homeDir.toString());
            builder.environment().put("XDG_CACHE_HOME", cacheDir.toString());
            builder.environment().put("XDG_CONFIG_HOME", configDir.toString());

            process = builder.start();
            boolean finished = process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS);
            if (!finished) {
                throw new DocumentConversionException(
                        "Renderer command timed out: " + command.get(0));
            }
            String output =
                    Files.exists(logFile) ? Files.readString(logFile, StandardCharsets.UTF_8) : "";
            return new ProcessResult(process.exitValue(), output);
        } catch (IOException ex) {
            throw new DocumentConversionException(
                    "Failed to start renderer command: " + command.get(0), ex);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new DocumentConversionException(
                    "Renderer command interrupted: " + command.get(0), ex);
        } finally {
            if (process != null && process.isAlive()) {
                stopProcessTree(process);
            }
        }
    }

    private static void stopProcessTree(Process process) {
        // Cleanup must finish before the converter removes its working directory.
        boolean interrupted = Thread.interrupted();
        try {
            List<ProcessHandle> descendants =
                    process.descendants()
                            .sorted(Comparator.comparingInt(ProcessRunner::processDepth).reversed())
                            .toList();
            try {
                // Keep the parent alive until it has reaped its children, avoiding orphan
                // processes.
                for (ProcessHandle child : descendants) {
                    child.destroyForcibly();
                    while (child.isAlive()) {
                        try {
                            child.onExit().get(5, TimeUnit.SECONDS);
                        } catch (InterruptedException ex) {
                            interrupted = true;
                        } catch (ExecutionException | TimeoutException ex) {
                            throw new DocumentConversionException(
                                    "Renderer child process did not stop", ex);
                        }
                    }
                }
            } finally {
                process.destroyForcibly();
                while (process.isAlive()) {
                    try {
                        if (!process.waitFor(5, TimeUnit.SECONDS)) {
                            throw new DocumentConversionException("Renderer process did not stop");
                        }
                    } catch (InterruptedException ex) {
                        interrupted = true;
                    }
                }
            }
        } finally {
            if (interrupted) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private static int processDepth(ProcessHandle process) {
        int depth = 0;
        var parent = process.parent();
        while (parent.isPresent()) {
            depth++;
            parent = parent.get().parent();
        }
        return depth;
    }

    public record ProcessResult(int exitCode, String output) {}
}
