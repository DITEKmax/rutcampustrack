package ru.rutcampustrack.documentrenderer.render;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

class ProcessRunnerTest {

    @Test
    void runProvidesWritableHomeAndXdgDirs(@TempDir Path tempDir) {
        ProcessRunner runner = new ProcessRunner();

        ProcessRunner.ProcessResult result = runner.run(envPrintCommand(), tempDir, Duration.ofSeconds(5));

        assertThat(result.exitCode()).isZero();
        assertThat(result.output())
                .contains(tempDir.resolve("home").toString())
                .contains(tempDir.resolve("home").resolve(".cache").toString())
                .contains(tempDir.resolve("home").resolve(".config").toString());
        assertThat(tempDir.resolve("home").resolve(".cache")).isDirectory();
        assertThat(tempDir.resolve("home").resolve(".config")).isDirectory();
    }

    private static List<String> envPrintCommand() {
        if (System.getProperty("os.name").toLowerCase(Locale.ROOT).contains("win")) {
            return List.of("cmd", "/c", "echo %HOME%&&echo %XDG_CACHE_HOME%&&echo %XDG_CONFIG_HOME%");
        }
        return List.of("sh", "-c", "printf '%s\\n%s\\n%s\\n' \"$HOME\" \"$XDG_CACHE_HOME\" \"$XDG_CONFIG_HOME\"");
    }
}
