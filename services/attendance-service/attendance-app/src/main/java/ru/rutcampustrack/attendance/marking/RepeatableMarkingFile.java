package ru.rutcampustrack.attendance.marking;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import org.springframework.web.multipart.MultipartFile;
import ru.rutcampustrack.attendance.exception.BadRequestException;

/** Lazily snapshots bytes only when the existing attachment validation reads the upload. */
final class RepeatableMarkingFile implements MultipartFile {
    private final MultipartFile original;
    private final String name;
    private final String originalFilename;
    private final String contentType;
    private final long size;
    private final boolean empty;
    private byte[] bytes;

    private RepeatableMarkingFile(MultipartFile original) {
        this.original = original;
        name = original.getName();
        originalFilename = original.getOriginalFilename();
        contentType = original.getContentType();
        size = original.getSize();
        empty = original.isEmpty();
    }

    static MultipartFile of(MultipartFile file) {
        return file == null ? null : new RepeatableMarkingFile(file);
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public String getOriginalFilename() {
        return originalFilename;
    }

    @Override
    public String getContentType() {
        return contentType;
    }

    @Override
    public boolean isEmpty() {
        return empty;
    }

    @Override
    public long getSize() {
        return size;
    }

    @Override
    public byte[] getBytes() throws IOException {
        if (bytes == null) {
            // Also bound dishonest size metadata; never call the upload's unbounded getBytes().
            try (InputStream input = original.getInputStream()) {
                byte[] snapshot = input.readNBytes((int) AttendanceAttachmentService.MAX_BYTES + 1);
                if (snapshot.length > AttendanceAttachmentService.MAX_BYTES) {
                    throw new BadRequestException("Размер вложения не должен превышать 10 МБ");
                }
                bytes = snapshot;
            }
        }
        return bytes.clone();
    }

    @Override
    public InputStream getInputStream() throws IOException {
        return new ByteArrayInputStream(getBytes());
    }

    @Override
    public void transferTo(File destination) throws IOException {
        Files.write(destination.toPath(), getBytes());
    }
}
