package ru.rutcampustrack.documentrenderer.render;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ProcessRunnerLifecycleTest {

    @Test
    void interruptionStopsTheOwnedProcessAndItsChild(@TempDir Path directory) throws Exception {
        AtomicReference<Throwable> failure = new AtomicReference<>();
        AtomicReference<Boolean> interrupted = new AtomicReference<>(false);
        Thread worker =
                new Thread(
                        () -> {
                            try {
                                new ProcessRunner()
                                        .run(command(directory), directory, Duration.ofSeconds(30));
                            } catch (Throwable error) {
                                failure.set(error);
                                interrupted.set(Thread.currentThread().isInterrupted());
                            }
                        });
        worker.start();
        List<Long> processes = List.of();
        try {
            processes = waitForProcesses(directory);
            worker.interrupt();
            worker.join(5000);
            assertThat(worker.isAlive()).isFalse();
            assertThat(failure.get())
                    .isInstanceOf(DocumentConversionException.class)
                    .hasMessageContaining("interrupted");
            assertThat(interrupted.get()).isTrue();
            assertStopped(processes);
        } finally {
            worker.interrupt();
            stopOwned(processes);
            stopOwned(readProcesses(directory));
            worker.join(5000);
        }
    }

    @Test
    void timeoutStopsTheOwnedProcessAndItsChild(@TempDir Path directory) throws Exception {
        // Wait is deliberately shorter than helper work; it is a cancellation test, not an SLA.
        try {
            assertThatThrownBy(
                            () ->
                                    new ProcessRunner()
                                            .run(
                                                    command(directory),
                                                    directory,
                                                    Duration.ofSeconds(3)))
                    .isInstanceOf(DocumentConversionException.class)
                    .hasMessageContaining("timed out");
            assertStopped(waitForProcesses(directory));
        } finally {
            stopOwned(readProcesses(directory));
        }
    }

    static List<String> command(Path directory) {
        String executable =
                System.getProperty("os.name").startsWith("Windows") ? "java.exe" : "java";
        return List.of(
                Path.of(System.getProperty("java.home"), "bin", executable).toString(),
                "-cp",
                Path.of(
                                java.net.URI.create(
                                        ProcessFixture.class
                                                .getProtectionDomain()
                                                .getCodeSource()
                                                .getLocation()
                                                .toExternalForm()))
                        .toString(),
                ProcessFixture.class.getName(),
                directory.toString());
    }

    static List<Long> waitForProcesses(Path directory) throws Exception {
        long deadline = System.nanoTime() + Duration.ofSeconds(5).toNanos();
        while (!Files.exists(directory.resolve("ready")) && System.nanoTime() < deadline) {
            Thread.sleep(10);
        }
        assertThat(directory.resolve("ready")).as("helper started both processes").exists();
        return readProcesses(directory);
    }

    static List<Long> readProcesses(Path directory) throws Exception {
        if (!Files.exists(directory.resolve("pids"))) return List.of();
        return Files.readAllLines(directory.resolve("pids")).stream().map(Long::parseLong).toList();
    }

    static void assertStopped(List<Long> pids) {
        assertThat(pids)
                .hasSize(3)
                .allSatisfy(
                        pid ->
                                assertThat(
                                                ProcessHandle.of(pid)
                                                        .map(ProcessHandle::isAlive)
                                                        .orElse(false))
                                        .as("owned helper process %s has stopped", pid)
                                        .isFalse());
    }

    static void stopOwned(List<Long> pids) {
        for (Long pid : pids.reversed()) {
            ProcessHandle.of(pid)
                    .ifPresent(
                            process -> {
                                process.destroyForcibly();
                                try {
                                    process.onExit().get(5, java.util.concurrent.TimeUnit.SECONDS);
                                } catch (Exception ignored) {
                                    // A baseline leak can already be orphaned; still clean the
                                    // remaining owned PIDs.
                                }
                            });
        }
    }

    public static final class ProcessFixture {
        public static void main(String[] arguments) throws Exception {
            if (arguments.length == 0) {
                Thread.sleep(60000);
                return;
            }
            Path directory = Path.of(arguments[0]);
            List<String> parentCommand = command(directory);
            if (arguments.length > 1) {
                Process grandchild =
                        new ProcessBuilder(parentCommand.subList(0, parentCommand.size() - 1))
                                .start();
                Files.writeString(
                        directory.resolve("grandchild-pid"), Long.toString(grandchild.pid()));
                grandchild.waitFor();
                return;
            }
            List<String> childCommand = new java.util.ArrayList<>(parentCommand);
            childCommand.add("child");
            Process child = new ProcessBuilder(childCommand).start();
            while (!Files.exists(directory.resolve("grandchild-pid"))) {
                Thread.sleep(10);
            }
            Files.writeString(
                    directory.resolve("pids"),
                    ProcessHandle.current().pid()
                            + "\n"
                            + child.pid()
                            + "\n"
                            + Files.readString(directory.resolve("grandchild-pid")));
            Files.writeString(directory.resolve("ready"), "ready");
            child.waitFor();
        }
    }
}
