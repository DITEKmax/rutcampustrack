package ru.rutcampustrack.mobilebff.teacher;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.SerializationFeature;
import com.google.protobuf.ByteString;
import io.grpc.ManagedChannelBuilder;
import io.grpc.Metadata;
import io.grpc.ServerBuilder;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptor;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.converter.ByteArrayHttpMessageConverter;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import ru.rutcampustrack.mobilebff.error.MobileProblemHandler;
import ru.rutcampustrack.mobilebff.grpc.MobileAcademicClient;
import ru.rutcampustrack.mobilebff.grpc.MobileAttendanceClient;
import ru.rutcampustrack.mobilebff.grpc.MobileGrpcAuth;
import ru.rutcampustrack.mobilebff.grpc.MobileScheduleClient;
import ru.rutcampustrack.mobilebff.security.MobileIdentityFilter;
import ru.rutcampustrack.mobilebff.security.MobileRequestContext;
import ru.rutcampustrack.shared.security.InternalJwtException;
import ru.rutcampustrack.shared.security.InternalJwtProperties;
import ru.rutcampustrack.shared.security.InternalJwtTestFactory;
import ru.rutcampustrack.shared.security.InternalJwtValidator;
import ru.rutcampustrack.shared.security.PublicKeyProvider;
import ru.rutcampustrack.teacher.grpc.TeacherAttachmentDownload;
import ru.rutcampustrack.teacher.grpc.TeacherAttendanceReadServiceGrpc;
import ru.rutcampustrack.teacher.grpc.TeacherLessonRequest;
import ru.rutcampustrack.teacher.grpc.TeacherLessonResponse;
import ru.rutcampustrack.teacher.grpc.TeacherLessonSummary;
import ru.rutcampustrack.teacher.grpc.TeacherManualExcuseAttachmentRequest;
import ru.rutcampustrack.teacher.grpc.TeacherRosterEntry;

class TeacherManualExcuseAttachmentHttpIT {
    private static final String LESSON = "/api/v1/teacher/lessons/101";
    private static final String FILE = LESSON + "/students/201/excuse/attachments/";
    private static final String EXPIRY = "2027-09-08T10:00:00Z";
    private static final String NAME = "Справка.pdf";
    private static final byte[] PDF = "%PDF-1.7\nprivate proof".getBytes(StandardCharsets.UTF_8);

    @Test
    void signedHttpReturnsPrivateBinaryAndNullableExpiryAndHidesDownstreamErrorDetail()
            throws Exception {
        var signer = new InternalJwtTestFactory();
        var keys = mock(PublicKeyProvider.class);
        when(keys.getPublicKey()).thenReturn(signer.publicKey());
        var properties = new InternalJwtProperties(null, 60, 0, false, null, null, null);
        var validator = new InternalJwtValidator(keys, properties);
        var forwardedTeacher = new AtomicLong();
        var tokenHeader = Metadata.Key.of("x-internal-token", Metadata.ASCII_STRING_MARSHALLER);
        var upstream = new TeacherAttendanceReadServiceGrpc.TeacherAttendanceReadServiceImplBase() {
            @Override
            public void downloadTeacherManualExcuseAttachment(
                    TeacherManualExcuseAttachmentRequest request,
                    StreamObserver<TeacherAttachmentDownload> observer) {
                if (request.getLessonId() != 101L || request.getStudentId() != 201L
                        || !"proof".equals(request.getAttachmentId())) {
                    observer.onError(Status.NOT_FOUND.withDescription("private blob detail")
                            .asRuntimeException());
                    return;
                }
                observer.onNext(TeacherAttachmentDownload.newBuilder()
                        .setFileName(NAME).setContentType("application/pdf").setSize(PDF.length)
                        .setData(ByteString.copyFrom(PDF)).build());
                observer.onCompleted();
            }

            @Override
            public void getTeacherLesson(TeacherLessonRequest request,
                    StreamObserver<TeacherLessonResponse> observer) {
                observer.onNext(TeacherLessonResponse.newBuilder()
                        .setLesson(TeacherLessonSummary.newBuilder().setLessonId(101L)
                                .setLessonDate("2026-09-07"))
                        .addRoster(TeacherRosterEntry.newBuilder().setStudentId(201L)
                                .setSource("NO_TICKET").setAttachmentId("proof")
                                .setAttachmentExpiresAt(EXPIRY))
                        .addRoster(TeacherRosterEntry.newBuilder().setStudentId(202L)
                                .setTicketId("ticket"))
                        .build());
                observer.onCompleted();
            }
        };
        var grpc = ServerBuilder.forPort(0).addService(upstream)
                .intercept(new ServerInterceptor() {
                    @Override
                    public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(
                            ServerCall<ReqT, RespT> call, Metadata headers,
                            ServerCallHandler<ReqT, RespT> next) {
                        try {
                            forwardedTeacher.set(validator.validate(headers.get(tokenHeader)).userId());
                            return next.startCall(call, headers);
                        } catch (InternalJwtException error) {
                            call.close(Status.UNAUTHENTICATED, new Metadata());
                            return new ServerCall.Listener<>() {};
                        }
                    }
                }).build().start();
        var channel = ManagedChannelBuilder.forAddress("127.0.0.1", grpc.getPort())
                .usePlaintext().build();
        try {
            var context = new MobileRequestContext();
            var attendance = new MobileAttendanceClient(new MobileGrpcAuth(context, ""));
            ReflectionTestUtils.setField(attendance, "teacherStub",
                    TeacherAttendanceReadServiceGrpc.newBlockingStub(channel));
            var facade = new TeacherReadFacade(context, mock(MobileAcademicClient.class),
                    mock(MobileScheduleClient.class), attendance);
            var json = Jackson2ObjectMapperBuilder.json()
                    .featuresToDisable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS).build();
            var mvc = MockMvcBuilders.standaloneSetup(new TeacherApiController(facade))
                    .setControllerAdvice(new MobileProblemHandler(Clock.systemUTC()))
                    .setMessageConverters(new ByteArrayHttpMessageConverter(),
                            new MappingJackson2HttpMessageConverter(json))
                    .addFilters(new MobileIdentityFilter(
                            validator, properties, context, json, Clock.systemUTC()))
                    .build();
            String teacher = signer.validToken(
                    71L, UUID.randomUUID(), 1L, 1L, "TEACHER", "ACTIVE", null, false, false);
            var response = mvc.perform(get(FILE + "proof").header(properties.headerName(), teacher)
                            .accept("application/octet-stream"))
                    .andExpect(status().isOk())
                    .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
                    .andExpect(header().longValue(HttpHeaders.CONTENT_LENGTH, PDF.length))
                    .andExpect(content().contentType("application/pdf"))
                    .andExpect(content().bytes(PDF))
                    .andReturn().getResponse();
            assertThat(ContentDisposition.parse(response.getHeader(HttpHeaders.CONTENT_DISPOSITION))
                    .getFilename()).isEqualTo(NAME);
            assertThat(forwardedTeacher.get()).isEqualTo(71L);
            mvc.perform(get(LESSON).header(properties.headerName(), teacher))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.roster[0].attachmentExpiresAt").value(EXPIRY))
                    .andExpect(jsonPath("$.roster[1].attachmentExpiresAt")
                            .value(org.hamcrest.Matchers.nullValue()));
            mvc.perform(get(FILE + "foreign").header(properties.headerName(), teacher))
                    .andExpect(status().isNotFound())
                    .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
                    .andExpect(header().doesNotExist(HttpHeaders.CONTENT_DISPOSITION))
                    .andExpect(jsonPath("$.code").value("ATTACHMENT_NOT_FOUND"))
                    .andExpect(content().string(org.hamcrest.Matchers.not(
                            org.hamcrest.Matchers.containsString("private blob detail"))));
            mvc.perform(get(FILE + "proof")).andExpect(status().isUnauthorized());
        } finally {
            channel.shutdownNow().awaitTermination(5, TimeUnit.SECONDS);
            grpc.shutdownNow().awaitTermination(5, TimeUnit.SECONDS);
        }
    }
}
