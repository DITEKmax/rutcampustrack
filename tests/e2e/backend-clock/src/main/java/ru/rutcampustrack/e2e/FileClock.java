package ru.rutcampustrack.e2e;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.Objects;

/** External E2E fixture only; rereads the atomically replaced file at each domain operation. */
public final class FileClock extends Clock {
    private final Path file;
    private final ZoneId zone;

    public FileClock(Path file, ZoneId zone) {
        this.file = Objects.requireNonNull(file).toAbsolutePath().normalize();
        this.zone = Objects.requireNonNull(zone);
    }

    @Override
    public ZoneId getZone() {
        return zone;
    }

    @Override
    public Clock withZone(ZoneId requestedZone) {
        return new FileClock(file, requestedZone);
    }

    @Override
    public Instant instant() {
        try {
            if (!Files.isRegularFile(file) || Files.size(file) > 128) {
                throw new IllegalStateException(
                        "Teaching clock fixture requires a bounded instant file");
            }
            return Instant.parse(Files.readString(file).strip());
        } catch (IOException | DateTimeParseException error) {
            // Do not include arbitrary file contents in startup/request diagnostics.
            throw new IllegalStateException("Teaching clock fixture instant is missing or invalid");
        }
    }
}
