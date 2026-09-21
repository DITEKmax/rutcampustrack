package ru.rutcampustrack.attendance.marking;

import org.bson.types.Binary;
import org.bson.types.ObjectId;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import ru.rutcampustrack.attendance.exception.BadRequestException;
import ru.rutcampustrack.attendance.student.PairWriteCoordinator;
import ru.rutcampustrack.attendance.studentrequest.AttachmentState;
import ru.rutcampustrack.attendance.studentrequest.RequestAttachmentRepository;
import ru.rutcampustrack.attendance.studentrequest.entity.RequestAttachmentDocument;
import ru.rutcampustrack.attendance.contract.exception.ResourceNotFoundException;
import ru.rutcampustrack.attendance.shared.port.JournalAttachmentPort;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * Stores one manual excuse attachment using the existing request attachment
 * collection and its retention policy. The pair key is deliberately internal;
 * callers receive only a metadata projection and use the authorized stream
 * endpoint rather than a storage URL.
 */
@Service
public class AttendanceAttachmentService implements JournalAttachmentPort {

    private static final long MAX_BYTES = 10L * 1024 * 1024;
    private static final List<String> ALLOWED_TYPES =
            List.of("image/jpeg", "image/png", "application/pdf");

    private final RequestAttachmentRepository repository;
    private final Clock clock;

    public AttendanceAttachmentService(RequestAttachmentRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    public StoredAttachment replace(long lessonId, long userId, long groupId, MultipartFile file) {
        ValidatedFile validated = validate(file);
        String pairKey = PairWriteCoordinator.pairId(userId, lessonId);
        repository.deleteAll(repository.findByRequestIdAndOwnerStudentIdOrderByPositionAsc(pairKey, userId));

        Instant now = clock.instant();
        RequestAttachmentDocument document = RequestAttachmentDocument.builder()
                .id(new ObjectId().toHexString())
                .requestId(pairKey)
                .ownerStudentId(userId)
                .groupId(groupId)
                .position(0)
                .name(validated.name())
                .contentType(validated.contentType())
                .size((long) validated.bytes().length)
                .sha256(validated.sha256())
                .state(AttachmentState.ACTIVE)
                .data(new Binary(validated.bytes()))
                .uploadedAt(now)
                .expiresAt(now.atZone(ZoneOffset.UTC).plusYears(1).toInstant())
                .build();
        RequestAttachmentDocument saved = repository.save(document);
        return new StoredAttachment(saved.getId(), saved.getName(), saved.getContentType(), saved.getSize());
    }

    @Override
    public void delete(long lessonId, long userId) {
        String pairKey = PairWriteCoordinator.pairId(userId, lessonId);
        repository.deleteAll(repository.findByRequestIdAndOwnerStudentIdOrderByPositionAsc(pairKey, userId));
    }

    @Override
    public boolean isAvailable(long lessonId, long userId, String attachmentId) {
        if (attachmentId == null || attachmentId.isBlank()) {
            return false;
        }
        String pairKey = PairWriteCoordinator.pairId(userId, lessonId);
        RequestAttachmentDocument document = repository.findById(attachmentId).orElse(null);
        Instant now = clock.instant();
        return document != null
                && Objects.equals(document.getRequestId(), pairKey)
                && Objects.equals(document.getOwnerStudentId(), userId)
                && document.getState() == AttachmentState.ACTIVE
                && document.getData() != null
                && document.getExpiresAt() != null
                && now.isBefore(document.getExpiresAt());
    }

    public AttachmentDownload download(long lessonId, long userId, String attachmentId) {
        String pairKey = PairWriteCoordinator.pairId(userId, lessonId);
        RequestAttachmentDocument document = repository.findById(attachmentId)
                .orElseThrow(() -> new ResourceNotFoundException("AttendanceAttachment", "id", attachmentId));
        if (!Objects.equals(document.getRequestId(), pairKey)
                || !Objects.equals(document.getOwnerStudentId(), userId)) {
            throw new ResourceNotFoundException("AttendanceAttachment", "id", attachmentId);
        }
        Instant now = clock.instant();
        if (document.getState() == AttachmentState.EXPIRED
                || document.getExpiresAt() == null
                || !now.isBefore(document.getExpiresAt())) {
            expire(document, now);
            throw new ResponseStatusException(HttpStatus.GONE, "Вложение больше недоступно");
        }
        if (document.getData() == null) {
            throw new ResponseStatusException(HttpStatus.GONE, "Вложение больше недоступно");
        }
        return new AttachmentDownload(document.getData().getData(), document.getContentType(), document.getName());
    }

    private void expire(RequestAttachmentDocument document, Instant now) {
        document.setState(AttachmentState.EXPIRED);
        document.setExpiredAt(now);
        document.setData(null);
        repository.save(document);
    }

    private static ValidatedFile validate(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BadRequestException("Пустое вложение недопустимо");
        }
        if (file.getSize() > MAX_BYTES) {
            throw new BadRequestException("Размер вложения не должен превышать 10 МБ");
        }
        String name = safeFilename(file.getOriginalFilename());
        String declared = file.getContentType() == null
                ? "" : file.getContentType().strip().toLowerCase(Locale.ROOT);
        String extension = extension(name);
        if (!ALLOWED_TYPES.contains(declared)
                || !List.of(".jpg", ".jpeg", ".png", ".pdf").contains(extension)) {
            throw new BadRequestException("Поддерживаются только JPEG, PNG и PDF");
        }
        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException ex) {
            throw new BadRequestException("Не удалось прочитать вложение");
        }
        String detected = detectType(bytes);
        if (!declared.equals(detected) || !extensionMatches(declared, extension)) {
            throw new BadRequestException("MIME, расширение и сигнатура файла не совпадают");
        }
        return new ValidatedFile(name, detected, bytes, sha256(bytes));
    }

    private static String safeFilename(String original) {
        String value = original == null ? "attachment" : original.strip();
        value = value.replace('\\', '_').replace('/', '_').replace("..", "_");
        StringBuilder safe = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c >= 0x20 && c != 0x7f) safe.append(c);
        }
        String result = safe.toString().strip();
        if (result.isEmpty() || result.equals(".") || result.equals("..")) result = "attachment";
        return result.length() > 255 ? result.substring(0, 255) : result;
    }

    private static String extension(String filename) {
        int index = filename.lastIndexOf('.');
        return index < 0 ? "" : filename.substring(index).toLowerCase(Locale.ROOT);
    }

    private static boolean extensionMatches(String contentType, String extension) {
        return ("application/pdf".equals(contentType) && ".pdf".equals(extension))
                || ("image/png".equals(contentType) && ".png".equals(extension))
                || ("image/jpeg".equals(contentType)
                && (".jpg".equals(extension) || ".jpeg".equals(extension)));
    }

    private static String detectType(byte[] bytes) {
        if (bytes.length >= 5 && new String(bytes, 0, 5, StandardCharsets.US_ASCII).equals("%PDF-")) {
            return "application/pdf";
        }
        if (bytes.length >= 8 && (bytes[0] & 0xff) == 0x89 && bytes[1] == 0x50
                && bytes[2] == 0x4e && bytes[3] == 0x47 && bytes[4] == 0x0d
                && bytes[5] == 0x0a && bytes[6] == 0x1a && bytes[7] == 0x0a) {
            return "image/png";
        }
        if (bytes.length >= 3 && (bytes[0] & 0xff) == 0xff
                && (bytes[1] & 0xff) == 0xd8 && (bytes[2] & 0xff) == 0xff) {
            return "image/jpeg";
        }
        return "";
    }

    private static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is unavailable", ex);
        }
    }

    public record StoredAttachment(String id, String name, String contentType, Long size) {}

    public record AttachmentDownload(byte[] bytes, String contentType, String name) {}

    private record ValidatedFile(String name, String contentType, byte[] bytes, String sha256) {}
}
