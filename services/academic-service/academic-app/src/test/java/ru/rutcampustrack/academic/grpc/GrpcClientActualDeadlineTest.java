package ru.rutcampustrack.academic.grpc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import io.grpc.*;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.mock.web.MockHttpServletRequest;

import ru.rutcampustrack.academic.security.RequestContext;
import ru.rutcampustrack.attendance.grpc.AttendanceSemesterDeletionGrpcServiceGrpc;
import ru.rutcampustrack.schedule.grpc.ScheduleGrpcServiceGrpc;
import ru.rutcampustrack.shared.observability.GrpcDeadlineArchRules;

import java.io.ByteArrayInputStream;
import java.time.LocalDate;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.UUID;

class GrpcClientActualDeadlineTest {
    @Test
    void bindingInvalidArgumentIsBadRequestWhileAvailabilityAndAuthorityMappingsRemain() {
        for (Status status : new Status[] {Status.INVALID_ARGUMENT, Status.UNAVAILABLE,
                Status.DEADLINE_EXCEEDED, Status.PERMISSION_DENIED, Status.UNAUTHENTICATED,
                Status.ABORTED, Status.FAILED_PRECONDITION, Status.ALREADY_EXISTS}) {
            Channel channel = new Channel() {
                public String authority() { return "synthetic-binding-error"; }
                public <Req, Resp> ClientCall<Req, Resp> newCall(MethodDescriptor<Req, Resp> method, CallOptions options) {
                    GrpcDeadlineArchRules.assertCallHasDeadline(options);
                    return new ClientCall<>() {
                        private Listener<Resp> listener;
                        public void start(Listener<Resp> value, Metadata headers) { listener = value; }
                        public void request(int messages) {}
                        public void cancel(String message, Throwable cause) {}
                        public void sendMessage(Req message) {}
                        public void halfClose() { listener.onClose(status.withDescription("SYNTHETIC_RPC_PRIVATE"), new Metadata()); }
                    };
                }
            };
            var context = new RequestContext();
            context.setUserId(1L);
            context.setInternalToken("synthetic-observer-token");
            var schedule = new ScheduleGrpcClient(context, "A".repeat(43), null);
            ReflectionTestUtils.setField(schedule, "stub", ScheduleGrpcServiceGrpc.newBlockingStub(channel));
            Throwable error = catchThrowable(() -> schedule.reserveDateHomeworkBinding(10L, 20L, 30L,
                    LocalDate.of(2026, 10, 22), UUID.randomUUID(), new byte[32]));
            if (status == Status.INVALID_ARGUMENT) {
                assertThat(error).isInstanceOf(ru.rutcampustrack.academic.exception.BadRequestException.class)
                        .hasMessageNotContaining("SYNTHETIC_RPC_PRIVATE");
                var response = new ru.rutcampustrack.academic.exception.GlobalExceptionHandler().handleBadRequest(
                        (ru.rutcampustrack.academic.exception.BadRequestException) error, new MockHttpServletRequest());
                assertThat(response.getStatusCode().value()).isEqualTo(400);
            } else if (status == Status.UNAVAILABLE || status == Status.DEADLINE_EXCEEDED) {
                assertThat(error).isInstanceOf(ru.rutcampustrack.academic.exception.ScheduleServiceUnavailableException.class);
                var response = new ru.rutcampustrack.academic.exception.GlobalExceptionHandler().handleScheduleUnavailable(
                        (ru.rutcampustrack.academic.exception.ScheduleServiceUnavailableException) error, new MockHttpServletRequest());
                assertThat(response.getStatusCode().value()).isEqualTo(503);
            } else if (status == Status.PERMISSION_DENIED || status == Status.UNAUTHENTICATED) {
                assertThat(error).isInstanceOf(ru.rutcampustrack.academic.exception.AccessDeniedException.class);
            } else assertThat(error).isInstanceOf(ru.rutcampustrack.academic.exception.ConflictException.class);
        }
    }
    @Test
    void productionClientsPassDeadlineOnActualCallsIncludingGetterLookup() {
        var observed = new AtomicInteger();
        Channel channel =
                new Channel() {
                    @Override
                    public String authority() {
                        return "synthetic-deadline-observer";
                    }

                    @Override
                    public <Req, Resp> ClientCall<Req, Resp> newCall(
                            MethodDescriptor<Req, Resp> method, CallOptions options) {
                        GrpcDeadlineArchRules.assertCallHasDeadline(options);
                        assertThat(options.getDeadline().timeRemaining(TimeUnit.MILLISECONDS))
                                .isBetween(1L, 3000L);
                        observed.incrementAndGet();
                        return new ClientCall<>() {
                            private Listener<Resp> listener;

                            @Override
                            public void start(Listener<Resp> value, Metadata headers) {
                                listener = value;
                            }

                            @Override
                            public void request(int messages) {}

                            @Override
                            public void cancel(String message, Throwable cause) {}

                            @Override
                            public void sendMessage(Req message) {}

                            @Override
                            public void halfClose() {
                                listener.onMessage(
                                        method.getResponseMarshaller()
                                                .parse(new ByteArrayInputStream(new byte[0])));
                                listener.onClose(Status.OK, new Metadata());
                            }
                        };
                    }
                };
        var context = new RequestContext();
        context.setUserId(1L);
        context.setInternalToken("synthetic-observer-token");
        var schedule = new ScheduleGrpcClient(context, "A".repeat(43), null);
        ReflectionTestUtils.setField(
                schedule, "stub", ScheduleGrpcServiceGrpc.newBlockingStub(channel));
        schedule.resolveLesson(7L, LocalDate.of(2026, 9, 1), 1);
        schedule.countSubjectReferences(11L);
        schedule.getHomeworkBinding(17L);
        var attendance = new AttendanceSemesterDeletionGrpcClient();
        ReflectionTestUtils.setField(
                attendance,
                "stub",
                AttendanceSemesterDeletionGrpcServiceGrpc.newBlockingStub(channel));
        attendance.preview(31L);
        assertThat(observed.get()).isEqualTo(4);
    }
}
