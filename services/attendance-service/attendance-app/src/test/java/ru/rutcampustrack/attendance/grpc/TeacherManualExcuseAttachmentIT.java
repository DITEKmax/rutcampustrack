package ru.rutcampustrack.attendance.grpc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.Metadata;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.grpc.ServerInterceptors;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.MetadataUtils;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import org.bson.types.Binary;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.repository.support.MongoRepositoryFactory;
import org.springframework.mock.web.MockMultipartFile;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import ru.rutcampustrack.academic.grpc.GroupMembersResponse;
import ru.rutcampustrack.academic.grpc.GroupResponse;
import ru.rutcampustrack.academic.grpc.StudentInfo;
import ru.rutcampustrack.academic.grpc.TeacherSubjectInfo;
import ru.rutcampustrack.academic.grpc.TeacherSubjectsResponse;
import ru.rutcampustrack.attendance.checkin.AttendanceDocument;
import ru.rutcampustrack.attendance.checkin.AttendanceReadPortImpl;
import ru.rutcampustrack.attendance.contract.enums.AttendanceSource;
import ru.rutcampustrack.attendance.contract.enums.AttendanceStatus;
import ru.rutcampustrack.attendance.contract.enums.ExcuseTicketStatus;
import ru.rutcampustrack.attendance.exception.AcademicServiceUnavailableException;
import ru.rutcampustrack.attendance.excuse.ExcuseRepository;
import ru.rutcampustrack.attendance.excuse.entity.ExcuseTicket;
import ru.rutcampustrack.attendance.marking.AttendanceAttachmentService;
import ru.rutcampustrack.attendance.report.ReportService;
import ru.rutcampustrack.attendance.security.RequestContext;
import ru.rutcampustrack.attendance.semester.SemesterCacheService;
import ru.rutcampustrack.attendance.student.PairWriteCoordinator;
import ru.rutcampustrack.attendance.studentrequest.AttachmentState;
import ru.rutcampustrack.attendance.studentrequest.RequestAttachmentRepository;
import ru.rutcampustrack.attendance.studentrequest.entity.RequestAttachmentDescriptorDocument;
import ru.rutcampustrack.attendance.studentrequest.entity.RequestAttachmentDocument;
import ru.rutcampustrack.schedule.grpc.LessonResponse;
import ru.rutcampustrack.shared.security.InternalJwtProperties;
import ru.rutcampustrack.shared.security.InternalJwtTestFactory;
import ru.rutcampustrack.shared.security.InternalJwtValidator;
import ru.rutcampustrack.shared.security.PublicKeyProvider;
import ru.rutcampustrack.teacher.grpc.TeacherAttendanceReadServiceGrpc;
import ru.rutcampustrack.teacher.grpc.TeacherAttendanceReadServiceGrpc.TeacherAttendanceReadServiceBlockingStub;
import ru.rutcampustrack.teacher.grpc.TeacherExcuseAttachmentRequest;
import ru.rutcampustrack.teacher.grpc.TeacherLessonRequest;
import ru.rutcampustrack.teacher.grpc.TeacherManualExcuseAttachmentRequest;
import ru.rutcampustrack.teacher.grpc.TeacherRosterEntry;

/** Signed teacher gRPC reads live roster and attachment documents in real Mongo. */
@Testcontainers
class TeacherManualExcuseAttachmentIT {
    @Container static final MongoDBContainer MONGO = new MongoDBContainer("mongo:7.0");
    private static final long TEACHER = 71L, LESSON = 101L, STUDENT = 201L, GROUP = 301L,
            SUBJECT = 401L, SEMESTER = 501L;
    private static final LocalDate DATE = LocalDate.parse("2026-09-07");
    private static final Instant NOW = Instant.parse("2026-09-08T10:00:00Z");
    private static final Instant EXPIRY = Instant.parse("2027-09-08T10:00:00Z");
    private static final String NAME = "Справка.pdf";
    private static final byte[] PDF = "%PDF-1.7\nprivate proof".getBytes(StandardCharsets.UTF_8);
    private static final TeacherSubjectsResponse GRANT = grant(GROUP, SUBJECT);

    private MongoClient client;
    private MongoTemplate mongo;
    private AcademicGrpcClient academic;
    private InternalJwtTestFactory signer;
    private Server server;
    private ManagedChannel channel;
    private TeacherAttendanceReadServiceBlockingStub teacher;
    private TeacherManualExcuseAttachmentRequest query;
    private AttendanceDocument mark;
    private RequestAttachmentDocument original;

    @BeforeEach
    void setup() throws Exception {
        client = MongoClients.create(MONGO.getReplicaSetUrl());
        mongo = new MongoTemplate(client, "teacher_manual_" + UUID.randomUUID());
        var repos = new MongoRepositoryFactory(mongo);
        var files = repos.getRepository(RequestAttachmentRepository.class);
        var excuses = repos.getRepository(ExcuseRepository.class);
        var clock = Clock.fixed(NOW, ZoneOffset.UTC);
        var journal = new AttendanceAttachmentService(files, clock);
        var file = journal.replace(SEMESTER, LESSON, STUDENT, GROUP,
                new MockMultipartFile("file", NAME, "application/pdf", PDF));
        original = files.findById(file.id()).orElseThrow();
        mark = mongo.save(AttendanceDocument.builder()
                .lessonId(LESSON).userId(STUDENT).groupId(GROUP).subjectId(SUBJECT)
                .semesterId(SEMESTER).lessonDate(DATE).lessonNumber(1)
                .status(AttendanceStatus.EXCUSED).source(AttendanceSource.HEADMAN_EXCUSE)
                .markRevision("revision-1").attachmentId(file.id()).attachmentName(file.name())
                .attachmentContentType(file.contentType()).attachmentSize(file.size()).build());
        query = TeacherManualExcuseAttachmentRequest.newBuilder()
                .setLessonId(LESSON).setStudentId(STUDENT).setAttachmentId(file.id()).build();

        var schedule = mock(ScheduleGrpcClient.class);
        when(schedule.getLessonById(LESSON)).thenReturn(LessonResponse.newBuilder()
                .setId(LESSON).setGroupId(GROUP).setSubjectId(SUBJECT).setSemesterId(SEMESTER)
                .setAssignedTeacherId(TEACHER + 1).setDate(DATE.toString())
                .setLessonType("lecture").setStatus("closed").setStartTime("09:00").build());
        when(schedule.getLessonById(LESSON + 1)).thenReturn(LessonResponse.newBuilder()
                .setId(LESSON + 1).setGroupId(GROUP).setSubjectId(SUBJECT).setSemesterId(SEMESTER)
                .setDate(DATE.toString()).setLessonType("lecture").setStatus("closed")
                .setStartTime("10:00").build());
        academic = mock(AcademicGrpcClient.class);
        when(academic.getCurrentTeacherSubjects(TEACHER)).thenReturn(GRANT);
        when(academic.getGroupMembers(GROUP, DATE, SEMESTER)).thenReturn(GroupMembersResponse.newBuilder()
                .setAsOfDate(DATE.toString()).setSemesterId(SEMESTER)
                .addStudents(StudentInfo.newBuilder().setUserId(STUDENT).setDisplayName("Student"))
                .addStudents(StudentInfo.newBuilder().setUserId(STUDENT + 1).setDisplayName("Peer"))
                .build());
        when(academic.getSubjectDetailsByIds(List.of(SUBJECT))).thenReturn(Map.of());
        when(academic.getGroup(GROUP)).thenReturn(GroupResponse.newBuilder().setId(GROUP).build());

        var report = new ReportService(new AttendanceReadPortImpl(mongo), academic, schedule,
                mock(SemesterCacheService.class), new RequestContext(), clock, journal);
        var grpc = new TeacherAttendanceReadGrpcService(report, schedule, academic, excuses, files, clock);
        signer = new InternalJwtTestFactory();
        var keys = mock(PublicKeyProvider.class);
        when(keys.getPublicKey()).thenReturn(signer.publicKey());
        var validator = new InternalJwtValidator(
                keys, new InternalJwtProperties(null, 60, 0, false, null, null, null));
        server = ServerBuilder.forPort(0).addService(ServerInterceptors.intercept(
                grpc, new TeacherAttendanceGrpcIdentityInterceptor(validator))).build().start();
        channel = ManagedChannelBuilder.forAddress("127.0.0.1", server.getPort()).usePlaintext().build();
        teacher = signed(TEACHER, "TEACHER", "ACTIVE");
    }

    @AfterEach
    void close() throws InterruptedException {
        if (channel != null) channel.shutdownNow().awaitTermination(5, TimeUnit.SECONDS);
        if (server != null) server.shutdownNow().awaitTermination(5, TimeUnit.SECONDS);
        if (client != null) client.close();
    }

    @Test
    void exactManualAttachmentReturnsActualBytesAndExpiryIncludingLegacySemester() {
        assertThat(row().getSource()).isEqualTo("NO_TICKET");
        assertThat(row().getAttachmentExpiresAt()).isEqualTo(EXPIRY.toString());
        var download = teacher.downloadTeacherManualExcuseAttachment(query);
        assertThat(download.getFileName()).isEqualTo(NAME);
        assertThat(download.getContentType()).isEqualTo("application/pdf");
        assertThat(download.getSize()).isEqualTo(PDF.length);
        assertThat(download.getData().toByteArray()).isEqualTo(PDF);

        updateBlob(blob -> blob.setSemesterId(null));
        assertThat(row().getAttachmentExpiresAt()).isEqualTo(EXPIRY.toString());
        assertThat(teacher.downloadTeacherManualExcuseAttachment(query).getData().toByteArray())
                .isEqualTo(PDF);
    }

    @Test
    void signedBoundaryAndCurrentGroupAndSubjectFailClosed() {
        deny(base(), query, Status.Code.UNAUTHENTICATED);
        deny(signed(TEACHER, "STUDENT", "ACTIVE"), query, Status.Code.PERMISSION_DENIED);
        deny(signed(TEACHER, "TEACHER", "ARCHIVED"), query, Status.Code.PERMISSION_DENIED);
        when(academic.getCurrentTeacherSubjects(TEACHER + 1))
                .thenReturn(TeacherSubjectsResponse.getDefaultInstance());
        deny(signed(TEACHER + 1, "TEACHER", "ACTIVE"), query, Status.Code.PERMISSION_DENIED);
        for (var foreign : List.of(grant(GROUP + 1, SUBJECT), grant(GROUP, SUBJECT + 1))) {
            when(academic.getCurrentTeacherSubjects(TEACHER)).thenReturn(foreign);
            deny(teacher, query, Status.Code.PERMISSION_DENIED);
        }
        when(academic.getCurrentTeacherSubjects(TEACHER))
                .thenThrow(new AcademicServiceUnavailableException("authority unavailable"));
        deny(teacher, query, Status.Code.UNAVAILABLE);
    }

    @Test
    void requiresExactRosterStudentLessonStatusAndReference() {
        deny(teacher, query.toBuilder().setStudentId(0).build(), Status.Code.INVALID_ARGUMENT);
        deny(teacher, query.toBuilder().setLessonId(0).build(), Status.Code.INVALID_ARGUMENT);
        deny(teacher, query.toBuilder().setAttachmentId(" ").build(), Status.Code.INVALID_ARGUMENT);
        deny(teacher, query.toBuilder().setStudentId(STUDENT + 1).build(), Status.Code.NOT_FOUND);
        deny(teacher, query.toBuilder().setStudentId(STUDENT + 2).build(), Status.Code.NOT_FOUND);
        deny(teacher, query.toBuilder().setLessonId(LESSON + 1).build(), Status.Code.NOT_FOUND);
        deny(teacher, query.toBuilder().setAttachmentId("unreferenced").build(), Status.Code.NOT_FOUND);
        mark.setStatus(AttendanceStatus.PRESENT);
        mongo.save(mark);
        deny(teacher, query, Status.Code.NOT_FOUND);
        assertThat(row().getAttachmentExpiresAt()).isEmpty();
    }

    @Test
    void inaccessibleBlobNeverGetsAnExpiryOrBytes() {
        List<Consumer<RequestAttachmentDocument>> corruptions = List.of(
                blob -> blob.setRequestId("ticket-binding"),
                blob -> blob.setRequestId(PairWriteCoordinator.pairId(STUDENT, LESSON + 1)),
                blob -> blob.setOwnerStudentId(STUDENT + 1),
                blob -> blob.setGroupId(GROUP + 1),
                blob -> blob.setSemesterId(SEMESTER + 1),
                blob -> blob.setState(AttachmentState.EXPIRED),
                blob -> blob.setExpiresAt(null),
                blob -> blob.setExpiresAt(NOW),
                blob -> blob.setData(null),
                blob -> blob.setData(new Binary(new byte[0])),
                blob -> blob.setData(new Binary(new byte[PDF.length + 1])),
                blob -> blob.setContentType("text/html"),
                blob -> blob.setName("different.pdf"),
                blob -> blob.setSize(11L * 1024 * 1024));
        for (var corruption : corruptions) {
            mongo.save(original);
            updateBlob(corruption);
            assertThat(row().getAttachmentExpiresAt()).isEmpty();
            deny(teacher, query, Status.Code.NOT_FOUND);
        }
        mongo.save(original);
        updateBlob(blob -> blob.setData(new Binary(new byte[PDF.length])));
        TeacherRosterEntry corrupted = row();
        assertThat(corrupted.getAttachmentId()).isEmpty();
        assertThat(corrupted.getAttachmentExpiresAt()).isEmpty();
        deny(teacher, query, Status.Code.NOT_FOUND); // Invalid PDF signature despite matching length.
        mongo.remove(original);
        assertThat(row().getAttachmentExpiresAt()).isEmpty();
        deny(teacher, query, Status.Code.NOT_FOUND);
    }

    @Test
    void detectsReplacementExpiryAndWithdrawalBetweenFirstFetchAndResponse() {
        doReturn(GRANT).doAnswer(invocation -> {
            mark.setAttachmentId("new-reference");
            mongo.save(mark);
            return GRANT;
        }).when(academic).getCurrentTeacherSubjects(TEACHER);
        deny(teacher, query, Status.Code.NOT_FOUND);
        assertThat(mongo.findById(mark.getId(), AttendanceDocument.class).getAttachmentId())
                .isEqualTo("new-reference");
        mark.setAttachmentId(original.getId());
        mongo.save(mark);

        doReturn(GRANT).doAnswer(invocation -> {
            updateBlob(blob -> blob.setExpiresAt(NOW));
            return GRANT;
        }).when(academic).getCurrentTeacherSubjects(TEACHER);
        deny(teacher, query, Status.Code.NOT_FOUND);
        assertThat(mongo.findById(original.getId(), RequestAttachmentDocument.class).getExpiresAt())
                .isEqualTo(NOW);
        mongo.save(original);

        doReturn(GRANT, TeacherSubjectsResponse.getDefaultInstance())
                .when(academic).getCurrentTeacherSubjects(TEACHER);
        deny(teacher, query, Status.Code.PERMISSION_DENIED);
    }

    @Test
    void ticketDownloadKeepsItsOwnNullExpiryAndCannotUseManualRoute() {
        updateBlob(blob -> {
            blob.setRequestId("ticket-1");
            blob.setExpiresAt(null);
        });
        mongo.save(ExcuseTicket.builder()
                .id("ticket-1").studentId(STUDENT).groupId(GROUP).lessonIds(List.of(LESSON))
                .status(ExcuseTicketStatus.APPROVED)
                .attachmentDescriptors(List.of(RequestAttachmentDescriptorDocument.builder()
                        .id(original.getId()).state(AttachmentState.ACTIVE).build()))
                .build());
        assertThat(row().getTicketId()).isEqualTo("ticket-1");
        assertThat(row().getAttachmentExpiresAt()).isEmpty();
        deny(teacher, query, Status.Code.NOT_FOUND);
        assertThat(teacher.downloadTeacherExcuseAttachment(TeacherExcuseAttachmentRequest.newBuilder()
                .setRequestId("ticket-1").setAttachmentId(original.getId()).build())
                .getData().toByteArray()).isEqualTo(PDF);
    }

    private TeacherRosterEntry row() {
        return teacher.getTeacherLesson(TeacherLessonRequest.newBuilder().setLessonId(LESSON).build())
                .getRoster(0);
    }

    private void updateBlob(Consumer<RequestAttachmentDocument> change) {
        var blob = mongo.findById(original.getId(), RequestAttachmentDocument.class);
        change.accept(blob);
        mongo.save(blob);
    }

    private TeacherAttendanceReadServiceBlockingStub base() {
        return TeacherAttendanceReadServiceGrpc.newBlockingStub(channel)
                .withDeadlineAfter(10, TimeUnit.SECONDS);
    }

    private TeacherAttendanceReadServiceBlockingStub signed(long id, String role, String status) {
        String token = signer.validToken(
                id, UUID.randomUUID(), 1, 1, role, status, null, false, "ARCHIVED".equals(status));
        Metadata headers = new Metadata();
        headers.put(Metadata.Key.of("x-internal-token", Metadata.ASCII_STRING_MARSHALLER), token);
        return base().withInterceptors(MetadataUtils.newAttachHeadersInterceptor(headers));
    }

    private static TeacherSubjectsResponse grant(long groupId, long subjectId) {
        return TeacherSubjectsResponse.newBuilder()
                .addSubjects(TeacherSubjectInfo.newBuilder().setGroupId(groupId).setSubjectId(subjectId))
                .build();
    }

    private static void deny(TeacherAttendanceReadServiceBlockingStub caller,
            TeacherManualExcuseAttachmentRequest request, Status.Code code) {
        assertThatThrownBy(() -> caller.downloadTeacherManualExcuseAttachment(request))
                .isInstanceOfSatisfying(StatusRuntimeException.class,
                        error -> assertThat(error.getStatus().getCode()).isEqualTo(code));
    }
}
