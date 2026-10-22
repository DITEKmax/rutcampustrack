package ru.rutcampustrack.attendance.grpc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.grpc.Status;
import io.grpc.StatusRuntimeException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import ru.rutcampustrack.attendance.contract.exception.ResourceNotFoundException;
import ru.rutcampustrack.attendance.exception.AccessDeniedException;
import ru.rutcampustrack.attendance.exception.ConflictException;
import ru.rutcampustrack.attendance.exception.ScheduleServiceUnavailableException;
import ru.rutcampustrack.schedule.grpc.ActiveLessonRequest;
import ru.rutcampustrack.schedule.grpc.LessonByIdRequest;
import ru.rutcampustrack.schedule.grpc.LessonResponse;
import ru.rutcampustrack.schedule.grpc.LessonsByGroupRequest;
import ru.rutcampustrack.schedule.grpc.LessonsResponse;
import ru.rutcampustrack.schedule.grpc.ScheduleGrpcServiceGrpc;

import java.lang.reflect.Field;
import java.util.List;

@ExtendWith(MockitoExtension.class)
class ScheduleGrpcClientTest {

    @Mock private ScheduleGrpcServiceGrpc.ScheduleGrpcServiceBlockingStub mockStub;

    private ScheduleGrpcClient client;

    @BeforeEach
    void setUp() throws Exception {
        client = new ScheduleGrpcClient();
        Field stubField = ScheduleGrpcClient.class.getDeclaredField("stub");
        stubField.setAccessible(true);
        stubField.set(client, mockStub);
        when(mockStub.withDeadlineAfter(anyLong(), any())).thenReturn(mockStub);
    }

    @Test
    void getActiveLesson_buildsCorrectRequest() {
        when(mockStub.getActiveLesson(any())).thenReturn(LessonResponse.getDefaultInstance());

        client.getActiveLesson(10L, "2026-04-04T10:00:00Z");

        verify(mockStub)
                .getActiveLesson(
                        ActiveLessonRequest.newBuilder()
                                .setGroupId(10L)
                                .setTimestamp("2026-04-04T10:00:00Z")
                                .build());
    }

    @Test
    void getActiveLesson_notFound_throwsResourceNotFoundException() {
        when(mockStub.getActiveLesson(any()))
                .thenThrow(new StatusRuntimeException(Status.NOT_FOUND));

        assertThrows(
                ResourceNotFoundException.class,
                () -> client.getActiveLesson(10L, "2026-04-04T10:00:00Z"));
    }

    @Test
    void getActiveLesson_unavailable_throwsScheduleServiceUnavailableException() {
        when(mockStub.getActiveLesson(any()))
                .thenThrow(new StatusRuntimeException(Status.UNAVAILABLE));

        assertThrows(
                ScheduleServiceUnavailableException.class,
                () -> client.getActiveLesson(10L, "2026-04-04T10:00:00Z"));
    }

    @Test
    void getLessonById_buildsCorrectRequest() {
        when(mockStub.getLessonById(any())).thenReturn(LessonResponse.getDefaultInstance());

        client.getLessonById(42L);

        verify(mockStub).getLessonById(LessonByIdRequest.newBuilder().setLessonId(42L).build());
    }

    @Test
    void getLessonById_notFound_throwsResourceNotFoundException() {
        when(mockStub.getLessonById(any())).thenThrow(new StatusRuntimeException(Status.NOT_FOUND));

        assertThrows(ResourceNotFoundException.class, () -> client.getLessonById(42L));
    }

    @Test
    void getLessonsByGroup_buildsCorrectRequest() {
        when(mockStub.getLessonsByGroup(any())).thenReturn(LessonsResponse.getDefaultInstance());

        client.getLessonsByGroup(5L, 1L, "2026-02-01", "2026-06-30");

        verify(mockStub)
                .getLessonsByGroup(
                        LessonsByGroupRequest.newBuilder()
                                .setGroupId(5L)
                                .setSemesterId(1L)
                                .setDateFrom("2026-02-01")
                                .setDateTo("2026-06-30")
                                .build());
    }

    @Test
    void scheduleFailureClassificationPreservesOnlySafeTransportStatusesAsRetryable() {
        for (Status status :
                List.of(
                        Status.UNAVAILABLE,
                        Status.DEADLINE_EXCEEDED,
                        Status.INVALID_ARGUMENT,
                        Status.PERMISSION_DENIED)) {
            org.mockito.Mockito.doThrow(
                            status.withDescription("SYNTHETIC_PRIVATE_DETAIL").asRuntimeException())
                    .when(mockStub)
                    .getLessonsByGroup(any());
            var failure =
                    assertThrows(
                            ScheduleServiceUnavailableException.class,
                            () -> client.getLessonsByGroup(5L, 1L, "2026-02-01", "2026-06-30"));
            org.assertj.core.api.Assertions.assertThat(failure.isRetryable())
                    .isEqualTo(
                            status.getCode() == Status.Code.UNAVAILABLE
                                    || status.getCode() == Status.Code.DEADLINE_EXCEEDED);
            org.assertj.core.api.Assertions.assertThat(failure.getMessage())
                    .doesNotContain("SYNTHETIC_PRIVATE_DETAIL");
        }
    }

    @Test
    void getLessonsByGroup_unavailable_throwsScheduleServiceUnavailableException() {
        when(mockStub.getLessonsByGroup(any()))
                .thenThrow(new StatusRuntimeException(Status.UNAVAILABLE));

        assertThrows(
                ScheduleServiceUnavailableException.class,
                () -> client.getLessonsByGroup(5L, 1L, "2026-02-01", "2026-06-30"));
    }

    @Test
    void getLessonById_unavailable_throwsScheduleServiceUnavailableException() {
        when(mockStub.getLessonById(any()))
                .thenThrow(new StatusRuntimeException(Status.UNAVAILABLE));

        assertThrows(ScheduleServiceUnavailableException.class, () -> client.getLessonById(99L));
    }

    @Test
    void scopedRequestLessonReadDoesNotTreatDeniedAccessAsAvailabilityFailure() {
        for (Status denial : List.of(Status.UNAUTHENTICATED, Status.PERMISSION_DENIED)) {
            doThrow(denial.asRuntimeException()).when(mockStub).getLessonById(any());
            assertThrows(
                    AccessDeniedException.class, () -> client.getScopedLessonById(42L, 10L, 30L));
        }
        doThrow(Status.NOT_FOUND.asRuntimeException()).when(mockStub).getLessonById(any());
        assertThrows(
                ResourceNotFoundException.class, () -> client.getScopedLessonById(42L, 10L, 30L));
        doThrow(Status.UNAVAILABLE.asRuntimeException()).when(mockStub).getLessonById(any());
        assertThrows(
                ScheduleServiceUnavailableException.class,
                () -> client.getScopedLessonById(42L, 10L, 30L));
    }

    @Test
    void scopedRequestLessonReadChecksScopeBeforePendingTransfer() {
        LessonResponse pending =
                LessonResponse.newBuilder()
                        .setId(42L)
                        .setGroupId(10L)
                        .setSemesterId(30L)
                        .setTransferState("PENDING")
                        .build();
        for (LessonResponse foreign :
                List.of(
                        pending.toBuilder().setId(43L).build(),
                        pending.toBuilder().setGroupId(11L).build(),
                        pending.toBuilder().setSemesterId(31L).build())) {
            doReturn(foreign).when(mockStub).getLessonById(any());
            assertThrows(ConflictException.class, () -> client.getScopedLessonById(42L, 10L, 30L));
        }
        doReturn(pending).when(mockStub).getLessonById(any());
        assertThrows(
                ScheduleServiceUnavailableException.class,
                () -> client.getScopedLessonById(42L, 10L, 30L));
    }

    @Test
    void getLessonByIdRetainsOnlyClosedUnavailableOrigins() {
        String privateDetail = "PRIVATE_TRANSPORT_DETAIL";
        var cases =
                new java.util.LinkedHashMap<
                        StatusRuntimeException,
                        ScheduleServiceUnavailableException.UnavailableOrigin>();
        cases.put(
                Status.UNAVAILABLE
                        .withCause(new java.net.ConnectException(privateDetail))
                        .asRuntimeException(),
                ScheduleServiceUnavailableException.UnavailableOrigin.CONNECT);
        cases.put(
                Status.UNAVAILABLE
                        .withCause(new java.net.UnknownHostException(privateDetail))
                        .asRuntimeException(),
                ScheduleServiceUnavailableException.UnavailableOrigin.DNS);
        cases.put(
                Status.UNAVAILABLE
                        .withCause(new java.nio.channels.UnresolvedAddressException())
                        .asRuntimeException(),
                ScheduleServiceUnavailableException.UnavailableOrigin.DNS);
        cases.put(
                Status.UNAVAILABLE
                        .withCause(new javax.net.ssl.SSLHandshakeException(privateDetail))
                        .asRuntimeException(),
                ScheduleServiceUnavailableException.UnavailableOrigin.TLS);
        cases.put(
                Status.UNAVAILABLE
                        .withCause(new java.security.cert.CertificateException(privateDetail))
                        .asRuntimeException(),
                ScheduleServiceUnavailableException.UnavailableOrigin.TLS);
        cases.put(
                Status.UNAVAILABLE
                        .withCause(new java.io.IOException(privateDetail))
                        .asRuntimeException(),
                ScheduleServiceUnavailableException.UnavailableOrigin.TRANSPORT_IO);
        cases.put(
                Status.UNAVAILABLE
                        .withCause(
                                new java.io.IOException(
                                        privateDetail,
                                        new javax.net.ssl.SSLException(privateDetail)))
                        .asRuntimeException(),
                ScheduleServiceUnavailableException.UnavailableOrigin.TLS);
        cases.put(
                Status.UNAVAILABLE
                        .withCause(
                                new java.io.IOException(
                                        privateDetail,
                                        new java.net.UnknownHostException(privateDetail)))
                        .asRuntimeException(),
                ScheduleServiceUnavailableException.UnavailableOrigin.DNS);
        cases.put(
                Status.UNAVAILABLE
                        .withCause(
                                new java.io.IOException(
                                        privateDetail,
                                        new java.net.ConnectException(privateDetail)))
                        .asRuntimeException(),
                ScheduleServiceUnavailableException.UnavailableOrigin.CONNECT);
        // Typed evidence takes priority over an otherwise recognized static marker.
        cases.put(
                Status.UNAVAILABLE
                        .withDescription("Channel shutdown invoked")
                        .withCause(new javax.net.ssl.SSLException(privateDetail))
                        .asRuntimeException(),
                ScheduleServiceUnavailableException.UnavailableOrigin.TLS);
        for (String description :
                java.util.List.of(
                        "Channel shutdown invoked",
                        "Channel shutdownNow invoked",
                        "Subchannel shutdown invoked")) {
            cases.put(
                    Status.UNAVAILABLE.withDescription(description).asRuntimeException(),
                    ScheduleServiceUnavailableException.UnavailableOrigin.CHANNEL_SHUTDOWN);
        }
        cases.put(
                Status.UNAVAILABLE
                        .withDescription("Server shutdownNow invoked")
                        .asRuntimeException(),
                ScheduleServiceUnavailableException.UnavailableOrigin.SERVER_UNAVAILABLE);
        for (String description :
                java.util.List.of(
                        privateDetail,
                        "server unavailable",
                        "Channel shutdown invoked ",
                        "Channel shutdown invoked\n",
                        "channel shutdown invoked",
                        "Server shutdownNow invoked " + privateDetail)) {
            cases.put(
                    Status.UNAVAILABLE.withDescription(description).asRuntimeException(),
                    ScheduleServiceUnavailableException.UnavailableOrigin.UNKNOWN);
        }
        cases.put(
                Status.UNAVAILABLE.asRuntimeException(),
                ScheduleServiceUnavailableException.UnavailableOrigin.UNKNOWN);
        var first = new IllegalStateException(privateDetail);
        var second = new IllegalStateException(privateDetail);
        first.initCause(second);
        second.initCause(first);
        cases.put(
                Status.UNAVAILABLE.withCause(first).asRuntimeException(),
                ScheduleServiceUnavailableException.UnavailableOrigin.UNKNOWN);
        Throwable inside = new java.net.ConnectException(privateDetail);
        for (int i = 0; i < 10; i++) inside = new IllegalStateException(privateDetail, inside);
        cases.put(
                Status.UNAVAILABLE.withCause(inside).asRuntimeException(),
                ScheduleServiceUnavailableException.UnavailableOrigin.CONNECT);
        cases.put(
                Status.UNAVAILABLE
                        .withCause(new IllegalStateException(privateDetail, inside))
                        .asRuntimeException(),
                ScheduleServiceUnavailableException.UnavailableOrigin.UNKNOWN);
        cases.put(
                Status.UNAVAILABLE
                        .withCause(
                                new RuntimeException(privateDetail) {
                                    @Override
                                    public synchronized Throwable getCause() {
                                        throw new IllegalStateException(privateDetail);
                                    }
                                })
                        .asRuntimeException(),
                ScheduleServiceUnavailableException.UnavailableOrigin.UNKNOWN);
        var trailers = new io.grpc.Metadata();
        trailers.put(
                io.grpc.Metadata.Key.of("private-test", io.grpc.Metadata.ASCII_STRING_MARSHALLER),
                privateDetail);
        cases.put(
                new StatusRuntimeException(
                        Status.UNAVAILABLE.withDescription(privateDetail), trailers),
                ScheduleServiceUnavailableException.UnavailableOrigin.UNKNOWN);
        for (var entry : cases.entrySet()) {
            doThrow(entry.getKey()).when(mockStub).getLessonById(any());
            var failure =
                    assertThrows(
                            ScheduleServiceUnavailableException.class,
                            () -> client.getLessonById(42L));
            assertEquals(entry.getValue(), failure.getUnavailableOrigin());
            assertEquals(Status.Code.UNAVAILABLE, failure.getStatusCode());
            assertTrue(failure.isRetryable());
            assertNull(failure.getCause());
            assertEquals(0, failure.getSuppressed().length);
            assertEquals("Schedule Service unavailable: UNAVAILABLE", failure.getMessage());
            assertFalse(failure.toString().contains(privateDetail));
        }
        verify(mockStub, org.mockito.Mockito.times(cases.size()))
                .withDeadlineAfter(3, java.util.concurrent.TimeUnit.SECONDS);
        verify(mockStub, org.mockito.Mockito.times(cases.size()))
                .getLessonById(LessonByIdRequest.newBuilder().setLessonId(42L).build());
    }

    @Test
    void otherStatusesAndLegacyConstructorsNeverAcquireAnUnavailableOrigin() {
        for (var code : Status.Code.values()) {
            if (code == Status.Code.UNAVAILABLE) continue;
            doThrow(
                            Status.fromCode(code)
                                    .withDescription("Server shutdownNow invoked")
                                    .withCause(new java.net.ConnectException("PRIVATE_DETAIL"))
                                    .asRuntimeException())
                    .when(mockStub)
                    .getLessonById(any());
            if (code == Status.Code.NOT_FOUND) {
                assertThrows(ResourceNotFoundException.class, () -> client.getLessonById(42L));
            } else {
                var failure =
                        assertThrows(
                                ScheduleServiceUnavailableException.class,
                                () -> client.getLessonById(42L));
                assertEquals(code, failure.getStatusCode());
                assertNull(failure.getUnavailableOrigin());
                assertNull(failure.getCause());
                assertEquals(code == Status.Code.DEADLINE_EXCEEDED, failure.isRetryable());
            }
        }
        assertNull(new ScheduleServiceUnavailableException("legacy").getUnavailableOrigin());
        assertNull(
                new ScheduleServiceUnavailableException("legacy", Status.Code.UNAVAILABLE)
                        .getUnavailableOrigin());
        assertNull(
                new ScheduleServiceUnavailableException(
                                "legacy",
                                Status.Code.INTERNAL,
                                ScheduleServiceUnavailableException.UnavailableOrigin.CONNECT)
                        .getUnavailableOrigin());
    }
}
