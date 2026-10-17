package ru.rutcampustrack.attendance.studentrequest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.bson.Document;
import org.bson.types.Binary;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;
import ru.rutcampustrack.academic.grpc.HeadmanCheckResponse;
import ru.rutcampustrack.attendance.checkin.AttendanceRepository;
import ru.rutcampustrack.attendance.excuse.ExcuseEventPublisher;
import ru.rutcampustrack.attendance.excuse.ExcuseRepository;
import ru.rutcampustrack.attendance.excuse.entity.ExcuseTicket;
import ru.rutcampustrack.attendance.grpc.AcademicGrpcClient;
import ru.rutcampustrack.attendance.grpc.ScheduleGrpcClient;
import ru.rutcampustrack.attendance.latecheckin.LateCheckinEventPublisher;
import ru.rutcampustrack.attendance.latecheckin.LateCheckinRepository;
import ru.rutcampustrack.attendance.semester.SemesterCacheService;
import ru.rutcampustrack.attendance.shared.port.JournalAttachmentPort;
import ru.rutcampustrack.attendance.student.PairWriteCoordinator;
import ru.rutcampustrack.attendance.studentrequest.entity.RequestAttachmentDocument;
import ru.rutcampustrack.shared.web.time.EducationalTime;

/** Actual mixed service operations against controlled storage ports, not a DB acceptance claim. */
class EducationalRetentionClockIsolationTest {
    private static final String REQUEST = "0123456789abcdef01234567";
    private static final String ATTACHMENT = "1123456789abcdef01234567";
    private static final Instant EXPIRY = Instant.parse("2027-01-01T00:00:00Z");
    private static final byte[] BYTES = new byte[] {1, 2, 3};

    @ParameterizedTest
    @ValueSource(strings = {"2025-10-12T06:00:00Z", "2030-10-12T06:00:00Z"})
    void retainedAttachmentDownloadRemainsAvailableWhenTeachingDateCrossesExpiry(
            String teachingNow) {
        var fixture = new Fixture("2026-10-11T22:00:00Z", teachingNow);
        var download = fixture.service.fetchExcuseAttachmentForBot(200L, REQUEST, ATTACHMENT);
        assertThat(download.bytes()).containsExactly(BYTES);
        assertThat(fixture.document.getState()).isEqualTo(AttachmentState.ACTIVE);
        assertThat(fixture.document.getData().getData()).containsExactly(BYTES);
    }

    @ParameterizedTest
    @ValueSource(strings = {"2025-10-12T06:00:00Z", "2030-10-12T06:00:00Z"})
    void realExpiredAttachmentDownloadIsDeniedRegardlessOfTeachingDate(String teachingNow) {
        var fixture = new Fixture("2028-10-11T22:00:00Z", teachingNow);
        assertThatThrownBy(
                        () ->
                                fixture.service.fetchExcuseAttachmentForBot(
                                        200L, REQUEST, ATTACHMENT))
                .isInstanceOfSatisfying(
                        ResponseStatusException.class,
                        error -> assertThat(error.getStatusCode()).isEqualTo(HttpStatus.GONE));
        assertThat(fixture.document.getData().getData()).containsExactly(BYTES);
    }

    @ParameterizedTest
    @ValueSource(strings = {"2025-10-12T06:00:00Z", "2030-10-12T06:00:00Z"})
    void actualRetentionCleanupClearsOnlyRealExpiredBytes(String teachingNow) {
        var retained = new Fixture("2026-10-11T22:00:00Z", teachingNow);
        retained.document.setRequestId("100:44");
        assertThat(retained.service.expireAttachments()).isZero();
        assertThat(retained.document.getState()).isEqualTo(AttachmentState.ACTIVE);
        assertThat(retained.document.getData().getData()).containsExactly(BYTES);

        var expired = new Fixture("2028-10-11T22:00:00Z", teachingNow);
        expired.document.setRequestId("100:44");
        assertThat(expired.service.expireAttachments()).isEqualTo(1);
        assertThat(expired.document.getState()).isEqualTo(AttachmentState.EXPIRED);
        assertThat(expired.document.getData()).isNull();
        assertThat(expired.document.getExpiredAt()).isEqualTo(expired.realNow);
    }

    private static final class Fixture {
        final Instant realNow;
        final RequestAttachmentDocument document;
        final StudentRequestService service;

        Fixture(String realInstant, String teachingInstant) {
            realNow = Instant.parse(realInstant);
            document =
                    RequestAttachmentDocument.builder()
                            .id(ATTACHMENT)
                            .requestId(REQUEST)
                            .ownerStudentId(100L)
                            .groupId(10L)
                            .semesterId(1L)
                            .name("synthetic.png")
                            .contentType("image/png")
                            .state(AttachmentState.ACTIVE)
                            .data(new Binary(BYTES))
                            .expiresAt(EXPIRY)
                            .build();
            var excuses = mock(ExcuseRepository.class);
            var attachments = mock(RequestAttachmentRepository.class);
            var academic = mock(AcademicGrpcClient.class);
            var mongo = mock(MongoTemplate.class);
            var transactions = mock(TransactionTemplate.class);
            when(excuses.findById(REQUEST))
                    .thenReturn(
                            Optional.of(
                                    ExcuseTicket.builder()
                                            .id(REQUEST)
                                            .studentId(100L)
                                            .groupId(10L)
                                            .semesterId(1L)
                                            .build()));
            when(academic.isHeadman(200L, 10L))
                    .thenReturn(HeadmanCheckResponse.newBuilder().setIsHeadman(true).build());
            when(attachments.findById(ATTACHMENT)).thenReturn(Optional.of(document));
            when(attachments.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
            when(transactions.execute(any()))
                    .thenAnswer(
                            invocation -> {
                                TransactionCallback<?> callback = invocation.getArgument(0);
                                return callback.doInTransaction(null);
                            });
            // Execute the actual query's expiry predicate against this controlled persisted row.
            when(mongo.find(any(Query.class), eq(RequestAttachmentDocument.class)))
                    .thenAnswer(
                            invocation -> {
                                Query query = invocation.getArgument(0);
                                Instant cutoff =
                                        query.getQueryObject()
                                                .get("expires_at", Document.class)
                                                .get("$lte", Instant.class);
                                assertThat(cutoff).isEqualTo(realNow);
                                return document.getState() == AttachmentState.ACTIVE
                                                && !document.getExpiresAt().isAfter(cutoff)
                                        ? List.of(document)
                                        : List.of();
                            });
            service =
                    new StudentRequestService(
                            excuses,
                            mock(LateCheckinRepository.class),
                            mock(AttendanceRepository.class),
                            mock(StudentRequestReceiptRepository.class),
                            mock(StudentLateCheckinBudgetRepository.class),
                            attachments,
                            mock(ScheduleGrpcClient.class),
                            academic,
                            mock(SemesterCacheService.class),
                            mock(PairWriteCoordinator.class),
                            mock(ExcuseEventPublisher.class),
                            mock(LateCheckinEventPublisher.class),
                            mongo,
                            transactions,
                            Clock.fixed(realNow, ZoneOffset.UTC),
                            mock(JournalAttachmentPort.class),
                            new EducationalTime(
                                    Clock.fixed(Instant.parse(teachingInstant), ZoneOffset.UTC)));
        }
    }
}
