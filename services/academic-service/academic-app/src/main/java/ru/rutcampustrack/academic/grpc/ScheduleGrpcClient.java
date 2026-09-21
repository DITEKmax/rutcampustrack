package ru.rutcampustrack.academic.grpc;

import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.Metadata;
import io.grpc.stub.MetadataUtils;
import net.devh.boot.grpc.client.inject.GrpcClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import ru.rutcampustrack.academic.exception.ConflictException;
import ru.rutcampustrack.academic.exception.AccessDeniedException;
import ru.rutcampustrack.academic.exception.ScheduleServiceUnavailableException;
import ru.rutcampustrack.academic.security.RequestContext;
import ru.rutcampustrack.schedule.grpc.CountSubjectReferencesRequest;
import ru.rutcampustrack.schedule.grpc.CountSubjectReferencesResponse;
import ru.rutcampustrack.schedule.grpc.ConfirmHomeworkBindingRequest;
import ru.rutcampustrack.schedule.grpc.ArchiveHomeworkBindingRequest;
import ru.rutcampustrack.schedule.grpc.HomeworkBindingResponse;
import ru.rutcampustrack.schedule.grpc.HomeworkBindingsRequest;
import ru.rutcampustrack.schedule.grpc.HomeworkBindingsResponse;
import ru.rutcampustrack.schedule.grpc.LessonResponse;
import ru.rutcampustrack.schedule.grpc.ResolveLessonRequest;
import ru.rutcampustrack.schedule.grpc.ReserveHomeworkBindingRequest;
import ru.rutcampustrack.schedule.grpc.ScheduleGrpcServiceGrpc;
import ru.rutcampustrack.shared.security.grpc.DirectedServiceCredential;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Phase 61 / D-04: gRPC клиент academic→schedule. Используется в {@code HomeworkService}
 * для валидации: пара {@code (groupId, date, lessonNumber)} существует и её subject
 * совпадает с {@code subjectId} ДЗ.
 *
 * <p>3-секундный deadline — mitigation T-61-05 (DoS через hanging gRPC call).
 * NOT_FOUND транслируется в {@link Optional#empty()} — вызывающий сервис сам решает,
 * как его интерпретировать (для D-04 — это 400 «пары нет в расписании»).
 * Любая другая ошибка gRPC → {@link ScheduleServiceUnavailableException} → 503.
 */
@Component
public class ScheduleGrpcClient {

    private static final Metadata.Key<String> INTERNAL_TOKEN =
            Metadata.Key.of("x-internal-token", Metadata.ASCII_STRING_MARSHALLER);

    private final RequestContext requestContext;
    private final String academicToScheduleToken;

    /** Source-compatible constructor for focused unit tests. */
    public ScheduleGrpcClient(RequestContext requestContext) {
        this(requestContext, "");
    }

    @Autowired
    public ScheduleGrpcClient(RequestContext requestContext,
                              @Value("${grpc.service-identity.academic-to-schedule-token:}")
                              String academicToScheduleToken) {
        this.requestContext = requestContext;
        this.academicToScheduleToken = academicToScheduleToken == null
                ? "" : academicToScheduleToken.trim();
    }

    @GrpcClient("schedule-service")
    private ScheduleGrpcServiceGrpc.ScheduleGrpcServiceBlockingStub stub;

    public Optional<LessonResponse> resolveLesson(Long groupId, LocalDate date, int lessonNumber) {
        try {
            LessonResponse response = stub.withDeadlineAfter(3, TimeUnit.SECONDS)
                    .resolveLesson(ResolveLessonRequest.newBuilder()
                            .setGroupId(groupId)
                            .setDate(date.toString())
                            .setLessonNumber(lessonNumber)
                            .build());
            return Optional.of(response);
        } catch (StatusRuntimeException e) {
            if (e.getStatus().getCode() == Status.Code.NOT_FOUND) {
                return Optional.empty();
            }
            throw new ScheduleServiceUnavailableException(
                    "Не удалось связаться с schedule-service: " + e.getStatus(), e);
        }
    }

    /**
     * Pre-check перед удалением Subject: узнаём, сколько объектов расписания
     * ссылаются на него. Если {@code non_planned_lessons_count > 0} — есть
     * посещаемость, SubjectService вернёт 409 без force=true.
     *
     * <p>Любая ошибка gRPC → {@link ScheduleServiceUnavailableException} (503).
     */
    public CountSubjectReferencesResponse countSubjectReferences(Long subjectId) {
        try {
            return stub.withDeadlineAfter(3, TimeUnit.SECONDS)
                    .countSubjectReferences(CountSubjectReferencesRequest.newBuilder()
                            .setSubjectId(subjectId)
                            .build());
        } catch (StatusRuntimeException e) {
            throw new ScheduleServiceUnavailableException(
                    "Не удалось связаться с schedule-service: " + e.getStatus(), e);
        }
    }

    public HomeworkBindingResponse reserveHomeworkBinding(long occurrenceId,
                                                          UUID requestKey,
                                                          long expectedRevision,
                                                          byte[] payloadHash) {
        try {
            return bindingStub().reserveHomeworkBinding(ReserveHomeworkBindingRequest.newBuilder()
                    .setOccurrenceId(occurrenceId)
                    .setRequestKey(requestKey.toString())
                    .setExpectedRevision(expectedRevision)
                    .setPayloadHash(com.google.protobuf.ByteString.copyFrom(payloadHash))
                    .build());
        } catch (StatusRuntimeException e) {
            throw mapBindingError(e, "зарезервировать привязку домашней работы");
        }
    }

    public HomeworkBindingResponse confirmHomeworkBinding(long bindingId,
                                                           long homeworkId,
                                                           UUID requestKey) {
        try {
            return bindingStub().confirmHomeworkBinding(ConfirmHomeworkBindingRequest.newBuilder()
                    .setBindingId(bindingId)
                    .setHomeworkId(homeworkId)
                    .setRequestKey(requestKey.toString())
                    .build());
        } catch (StatusRuntimeException e) {
            throw mapBindingError(e, "подтвердить привязку домашней работы");
        }
    }

    public HomeworkBindingResponse archiveHomeworkBinding(long bindingId,
                                                          long homeworkId,
                                                          UUID requestKey) {
        try {
            return bindingStub().archiveHomeworkBinding(ArchiveHomeworkBindingRequest.newBuilder()
                    .setBindingId(bindingId)
                    .setHomeworkId(homeworkId)
                    .setRequestKey(requestKey.toString())
                    .build());
        } catch (StatusRuntimeException e) {
            throw mapBindingError(e, "архивировать привязку домашней работы");
        }
    }

    public HomeworkBindingsResponse getHomeworkBindings(Iterable<Long> occurrenceIds) {
        try {
            HomeworkBindingsRequest.Builder request = HomeworkBindingsRequest.newBuilder();
            occurrenceIds.forEach(request::addOccurrenceIds);
            return bindingStub().getHomeworkBindings(request.build());
        } catch (StatusRuntimeException e) {
            throw mapBindingError(e, "прочитать привязки домашних работ");
        }
    }

    private ScheduleGrpcServiceGrpc.ScheduleGrpcServiceBlockingStub bindingStub() {
        Long actorId = requestContext.getUserId();
        String internalToken = requestContext.getInternalToken();
        if (actorId == null || actorId <= 0
                || internalToken == null || internalToken.isBlank()) {
            throw new ScheduleServiceUnavailableException(
                    "Нельзя вызвать binding API без подписанного аутентифицированного пользователя");
        }
        if (!DirectedServiceCredential.isCanonicalToken(academicToScheduleToken)) {
            throw new ScheduleServiceUnavailableException(
                    "Нельзя вызвать binding API без directed Academic service identity");
        }
        Metadata headers = new Metadata();
        headers.put(INTERNAL_TOKEN, internalToken);
        headers.put(DirectedServiceCredential.TOKEN_METADATA_KEY, academicToScheduleToken);
        return stub.withInterceptors(MetadataUtils.newAttachHeadersInterceptor(headers))
                .withDeadlineAfter(3, TimeUnit.SECONDS);
    }

    private RuntimeException mapBindingError(StatusRuntimeException error, String action) {
        Status.Code code = error.getStatus().getCode();
        if (code == Status.Code.UNAUTHENTICATED || code == Status.Code.PERMISSION_DENIED) {
            return new AccessDeniedException(error.getStatus().getDescription() == null
                    ? "Нет права на binding API"
                    : error.getStatus().getDescription());
        }
        if (code == Status.Code.ABORTED || code == Status.Code.FAILED_PRECONDITION
                || code == Status.Code.ALREADY_EXISTS) {
            return new ConflictException(error.getStatus().getDescription() == null
                    ? "Конфликт: не удалось " + action
                    : error.getStatus().getDescription());
        }
        return new ScheduleServiceUnavailableException(
                "Не удалось " + action + " в schedule-service: " + error.getStatus(), error);
    }
}
