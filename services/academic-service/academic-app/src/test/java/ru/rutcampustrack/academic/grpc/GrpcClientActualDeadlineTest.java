package ru.rutcampustrack.academic.grpc;

import static org.assertj.core.api.Assertions.assertThat;

import io.grpc.*;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import ru.rutcampustrack.academic.security.RequestContext;
import ru.rutcampustrack.attendance.grpc.AttendanceSemesterDeletionGrpcServiceGrpc;
import ru.rutcampustrack.schedule.grpc.ScheduleGrpcServiceGrpc;
import ru.rutcampustrack.shared.observability.GrpcDeadlineArchRules;

import java.io.ByteArrayInputStream;
import java.time.LocalDate;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

class GrpcClientActualDeadlineTest {
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
