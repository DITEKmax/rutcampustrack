package ru.rutcampustrack.mobilebff.student;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import ru.rutcampustrack.attendance.grpc.StudentRequestAttachmentDownload;
import ru.rutcampustrack.attendance.grpc.StudentRequestAttachmentState;
import ru.rutcampustrack.attendance.grpc.StudentRequestDetail;
import ru.rutcampustrack.attendance.grpc.StudentRequestErrorCode;
import ru.rutcampustrack.attendance.grpc.StudentRequestKind;
import ru.rutcampustrack.attendance.grpc.StudentRequestLesson;
import ru.rutcampustrack.attendance.grpc.StudentRequestLessonOption;
import ru.rutcampustrack.attendance.grpc.StudentRequestOrigin;
import ru.rutcampustrack.attendance.grpc.StudentRequestPage;
import ru.rutcampustrack.attendance.grpc.StudentRequestPendingRef;
import ru.rutcampustrack.attendance.grpc.StudentRequestReasonOption;
import ru.rutcampustrack.attendance.grpc.StudentRequestStatus;
import ru.rutcampustrack.attendance.grpc.SubmitStudentExcuseCommand;
import ru.rutcampustrack.mobilebff.contract.model.StudentRequestApiModels;
import ru.rutcampustrack.mobilebff.contract.model.StudentApiModels;
import ru.rutcampustrack.mobilebff.contract.model.StudentRequestApiModels.Attachment;
import ru.rutcampustrack.mobilebff.contract.model.StudentRequestApiModels.AttachmentState;
import ru.rutcampustrack.mobilebff.contract.model.StudentRequestApiModels.Bucket;
import ru.rutcampustrack.mobilebff.contract.model.StudentRequestApiModels.Budget;
import ru.rutcampustrack.mobilebff.contract.model.StudentRequestApiModels.Detail;
import ru.rutcampustrack.mobilebff.contract.model.StudentRequestApiModels.ExcuseReason;
import ru.rutcampustrack.mobilebff.contract.model.StudentRequestApiModels.FileLimits;
import ru.rutcampustrack.mobilebff.contract.model.StudentRequestApiModels.Kind;
import ru.rutcampustrack.mobilebff.contract.model.StudentRequestApiModels.LateCheckinRequest;
import ru.rutcampustrack.mobilebff.contract.model.StudentRequestApiModels.Lesson;
import ru.rutcampustrack.mobilebff.contract.model.StudentRequestApiModels.LessonOption;
import ru.rutcampustrack.mobilebff.contract.model.StudentRequestApiModels.Options;
import ru.rutcampustrack.mobilebff.contract.model.StudentRequestApiModels.Page;
import ru.rutcampustrack.mobilebff.contract.model.StudentRequestApiModels.PendingRef;
import ru.rutcampustrack.mobilebff.contract.model.StudentRequestApiModels.ReasonOption;
import ru.rutcampustrack.mobilebff.contract.model.StudentRequestApiModels.Status;
import ru.rutcampustrack.mobilebff.contract.model.StudentRequestApiModels.Summary;
import ru.rutcampustrack.mobilebff.error.MobileBffException;
import ru.rutcampustrack.mobilebff.grpc.MobileAttendanceClient;
import ru.rutcampustrack.mobilebff.security.MobileRequestContext;
import ru.rutcampustrack.shared.security.InternalJwtClaims;

import java.io.IOException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

@Service
public class StudentRequestFacade {
    private final MobileAttendanceClient attendance;
    private final MobileRequestContext requestContext;

    public StudentRequestFacade(MobileAttendanceClient attendance, MobileRequestContext requestContext) {
        this.attendance = attendance;
        this.requestContext = requestContext;
    }

    public Page list(Bucket bucket, Integer page, Integer size) {
        requireStudentScope();
        StudentRequestPage response = attendance.listRequests(toProto(bucket), page, size);
        return page(response);
    }

    public Options options() {
        requireStudentScope();
        return options(attendance.requestOptions());
    }

    public Detail get(String id) {
        requireStudentScope();
        return detail(attendance.getRequest(id));
    }

    public Detail submitExcuse(String idempotencyKey,
                               StudentRequestApiModels.ExcuseRequest request,
                               List<MultipartFile> files) {
        requireMutableStudentScope();
        if (request == null) {
            throw new MobileBffException(HttpStatus.BAD_REQUEST,
                    StudentApiModels.ProblemCode.INVALID_REQUEST, "Запрос обязателен");
        }
        SubmitStudentExcuseCommand.Builder command = SubmitStudentExcuseCommand.newBuilder()
                .setIdempotencyKey(idempotencyKey == null ? "" : idempotencyKey)
                .setReason(toProto(request.reason()));
        request.lessonIds().forEach(id -> command.addLessonIds(parsePositiveLong(id, "lessonId")));
        if (request.comment() != null) command.setComment(request.comment());
        if (files != null) {
            files.forEach(file -> {
                if (file == null || file.isEmpty()) {
                    throw new MobileBffException(HttpStatus.BAD_REQUEST,
                            StudentApiModels.ProblemCode.INVALID_REQUEST, "Пустое вложение недопустимо");
                }
                try {
                    command.addAttachments(ru.rutcampustrack.attendance.grpc.StudentRequestAttachmentUpload
                            .newBuilder()
                            .setName(file.getOriginalFilename() == null ? "attachment" : file.getOriginalFilename())
                            .setDeclaredContentType(file.getContentType() == null ? "" : file.getContentType())
                            .setData(com.google.protobuf.ByteString.copyFrom(file.getBytes()))
                            .build());
                } catch (IOException error) {
                    throw new MobileBffException(HttpStatus.BAD_REQUEST,
                            StudentApiModels.ProblemCode.INVALID_REQUEST, "Не удалось прочитать вложение");
                }
            });
        }
        return detail(attendance.submitExcuse(command.build()));
    }

    public Detail submitLateCheckin(String idempotencyKey, LateCheckinRequest request) {
        requireMutableStudentScope();
        long lessonId = parsePositiveLong(request == null ? null : request.lessonId(), "lessonId");
        return detail(attendance.submitLateCheckin(lessonId, idempotencyKey));
    }

    public Detail cancel(String id) {
        requireMutableStudentScope();
        return detail(attendance.cancelRequest(id));
    }

    public Download download(String id, String attachmentId) {
        requireStudentScope();
        StudentRequestAttachmentDownload response = attendance.downloadAttachment(id, attachmentId);
        String contentType = response.getContentType().isBlank()
                ? "application/octet-stream" : response.getContentType();
        return new Download(response.getData().toByteArray(), contentType,
                response.getFilename().isBlank() ? "attachment" : response.getFilename());
    }

    private void requireStudentScope() {
        InternalJwtClaims claims = requestContext.claims();
        if (!"STUDENT".equalsIgnoreCase(claims.domainRole())) {
            throw new MobileBffException(HttpStatus.FORBIDDEN, StudentApiModels.ProblemCode.WRONG_ROLE,
                    "Мобильный student API доступен роли STUDENT");
        }
        if (claims.userId() <= 0 || claims.groupId() == null || claims.groupId() <= 0) {
            throw new MobileBffException(HttpStatus.FORBIDDEN, StudentApiModels.ProblemCode.OUT_OF_SCOPE,
                    "Не хватает student/group scope");
        }
    }

    private void requireMutableStudentScope() {
        requireStudentScope();
        if (requestContext.claims().readOnly()) {
            throw new MobileBffException(HttpStatus.FORBIDDEN, StudentApiModels.ProblemCode.ROLE_READ_ONLY,
                    "Терминальная student-сессия доступна только для чтения");
        }
    }

    private static long parsePositiveLong(String value, String field) {
        try {
            long id = Long.parseLong(value);
            if (id <= 0) throw new NumberFormatException();
            return id;
        } catch (RuntimeException error) {
            throw new MobileBffException(HttpStatus.BAD_REQUEST, StudentApiModels.ProblemCode.INVALID_REQUEST,
                    "Некорректный " + field);
        }
    }

    private static ru.rutcampustrack.attendance.grpc.StudentRequestBucket toProto(Bucket bucket) {
        if (bucket == null || bucket == Bucket.OPEN) {
            return ru.rutcampustrack.attendance.grpc.StudentRequestBucket.STUDENT_REQUEST_BUCKET_OPEN;
        }
        return ru.rutcampustrack.attendance.grpc.StudentRequestBucket.STUDENT_REQUEST_BUCKET_ARCHIVE;
    }

    private static ru.rutcampustrack.attendance.grpc.StudentExcuseReason toProto(ExcuseReason reason) {
        if (reason == null) {
            throw new MobileBffException(HttpStatus.BAD_REQUEST, StudentApiModels.ProblemCode.INVALID_REQUEST,
                    "Причина обязательна");
        }
        return ru.rutcampustrack.attendance.grpc.StudentExcuseReason.valueOf(
                "STUDENT_EXCUSE_REASON_" + reason.name());
    }

    private static Page page(StudentRequestPage page) {
        return new Page(page.getContentList().stream().map(StudentRequestFacade::summary).toList(),
                page.getPage(), page.getSize(), page.getTotalElements(), page.getTotalPages());
    }

    private static Detail detail(StudentRequestDetail detail) {
        return new Detail(summary(detail.getSummary()),
                detail.hasReason() ? ExcuseReason.valueOf(strip(detail.getReason().name(),
                        "STUDENT_EXCUSE_REASON_")) : null,
                detail.hasComment() ? detail.getComment() : null,
                detail.hasDecision() ? new StudentRequestApiModels.Decision(
                        detail.getDecision().hasComment() ? detail.getDecision().getComment() : null,
                        parseInstant(detail.getDecision().hasDecidedAt() ? detail.getDecision().getDecidedAt() : null)) : null,
                detail.getAttachmentsList().stream().map(StudentRequestFacade::attachment).toList());
    }

    private static Summary summary(ru.rutcampustrack.attendance.grpc.StudentRequestSummary summary) {
        return new Summary(summary.getId(),
                Kind.valueOf(strip(summary.getKind().name(), "STUDENT_REQUEST_KIND_")),
                Status.valueOf(strip(summary.getStatus().name(), "STUDENT_REQUEST_STATUS_")),
                StudentRequestApiModels.Origin.valueOf(strip(summary.getOrigin().name(), "STUDENT_REQUEST_ORIGIN_")),
                summary.getLessonsList().stream().map(StudentRequestFacade::lesson).toList(),
                parseInstant(summary.getCreatedAt()), parseInstant(summary.getUpdatedAt()));
    }

    private static Lesson lesson(StudentRequestLesson lesson) {
        return new Lesson(Long.toString(lesson.getId()), lesson.getLessonNumber(), lesson.getStatus(), lesson.getBlocked(),
                lesson.hasSubjectId() ? Long.toString(lesson.getSubjectId()) : null,
                lesson.hasSubjectName() ? lesson.getSubjectName() : null,
                lesson.hasSubjectType() ? lesson.getSubjectType() : null,
                lesson.hasSemesterId() ? Long.toString(lesson.getSemesterId()) : null,
                parseDate(lesson.hasDate() ? lesson.getDate() : null),
                parseTime(lesson.hasStartsAt() ? lesson.getStartsAt() : null),
                parseTime(lesson.hasEndsAt() ? lesson.getEndsAt() : null));
    }

    private static Attachment attachment(ru.rutcampustrack.attendance.grpc.StudentRequestAttachment attachment) {
        AttachmentState state = attachment.getState() == StudentRequestAttachmentState.STUDENT_REQUEST_ATTACHMENT_STATE_EXPIRED
                ? AttachmentState.EXPIRED : AttachmentState.ACTIVE;
        return new Attachment(attachment.getId(), attachment.getName(), attachment.getContentType(),
                attachment.getSizeBytes(), attachment.getSha256(), state,
                parseInstant(attachment.getUploadedAt()), parseInstant(attachment.getExpiresAt()),
                parseInstant(attachment.hasExpiredAt() ? attachment.getExpiredAt() : null));
    }

    private static Options options(ru.rutcampustrack.attendance.grpc.StudentRequestOptions options) {
        List<ReasonOption> reasons = options.getReasonsList().stream()
                .map(item -> new ReasonOption(ExcuseReason.valueOf(strip(item.getCode().name(),
                        "STUDENT_EXCUSE_REASON_")), item.getLabel(), item.getCommentRequired())).toList();
        var fileLimits = options.getFiles();
        FileLimits files = new FileLimits(fileLimits.getMaxFiles(), fileLimits.getMaxBytesPerFile(),
                fileLimits.getMaxBytesTotal(), fileLimits.getContentTypesList(), fileLimits.getExtensionsList());
        var protoBudget = options.getBudget();
        Budget budget = new Budget(Long.toString(protoBudget.getSemesterId()), protoBudget.getLimit(),
                protoBudget.getUsed(), protoBudget.getRemaining());
        List<LessonOption> lessons = options.getLessonsList().stream()
                .map(StudentRequestFacade::lessonOption).toList();
        return new Options(reasons, files, budget, lessons);
    }

    private static LessonOption lessonOption(StudentRequestLessonOption option) {
        List<PendingRef> pending = option.getPendingRequestsList().stream()
                .map(StudentRequestFacade::pendingRef).toList();
        return new LessonOption(lesson(option.getLesson()), option.getExcuseEligible(),
                option.getLateCheckinEligible(), pending);
    }

    private static PendingRef pendingRef(StudentRequestPendingRef ref) {
        return new PendingRef(ref.getId(), Kind.valueOf(strip(ref.getKind().name(), "STUDENT_REQUEST_KIND_")),
                StudentRequestApiModels.Origin.valueOf(strip(ref.getOrigin().name(), "STUDENT_REQUEST_ORIGIN_")));
    }

    private static String strip(String value, String prefix) {
        return value.startsWith(prefix) ? value.substring(prefix.length()) : value;
    }

    private static Instant parseInstant(String value) {
        return value == null || value.isBlank() ? null : Instant.parse(value);
    }

    private static LocalDate parseDate(String value) {
        return value == null || value.isBlank() ? null : LocalDate.parse(value);
    }

    private static LocalTime parseTime(String value) {
        return value == null || value.isBlank() ? null : LocalTime.parse(value);
    }

    public record Download(byte[] bytes, String contentType, String filename) {
        public Download {
            bytes = bytes == null ? new byte[0] : bytes.clone();
        }

        @Override
        public byte[] bytes() {
            return bytes.clone();
        }
    }
}
