package ru.rutcampustrack.attendance.grpc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import static ru.rutcampustrack.shared.observability.GrpcDeadlineArchRules.assertCallHasDeadline;

import io.grpc.CallOptions;
import io.grpc.Channel;
import io.grpc.ClientCall;
import io.grpc.Context;
import io.grpc.Metadata;
import io.grpc.MethodDescriptor;
import io.grpc.Status;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import ru.rutcampustrack.academic.grpc.AcademicGrpcServiceGrpc;
import ru.rutcampustrack.documentrenderer.grpc.DocumentRendererGrpcServiceGrpc;
import ru.rutcampustrack.schedule.grpc.ScheduleGrpcServiceGrpc;
import ru.rutcampustrack.teacher.grpc.TeacherAcademicReadServiceGrpc;

import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.List;

class AttendanceClientDeadlineTest {
    @Test
    void actualClientRpcOptionsCarryDeadlinesIncludingGetterAndDelegatedConversion() {
        var channel = new ObservingChannel();
        var academic = new AcademicGrpcClient();
        ReflectionTestUtils.setField(
                academic, "stub", AcademicGrpcServiceGrpc.newBlockingStub(channel));
        academic.getActiveSemester();
        var schedule = new ScheduleGrpcClient();
        ReflectionTestUtils.setField(
                schedule, "stub", ScheduleGrpcServiceGrpc.newBlockingStub(channel));
        schedule.getLessonById(42L);
        var renderer = new DocumentRendererGrpcClient();
        ReflectionTestUtils.setField(
                renderer, "stub", DocumentRendererGrpcServiceGrpc.newBlockingStub(channel));
        renderer.convertDocxToPngPagesZip(new byte[] {1});
        var teacher = new TeacherAcademicGrpcClient();
        ReflectionTestUtils.setField(
                teacher, "stub", TeacherAcademicReadServiceGrpc.newBlockingStub(channel));
        Context.current()
                .withValue(TeacherAttendanceGrpcIdentity.SIGNED_TOKEN, "synthetic-token")
                .run(() -> teacher.fullSemesterAssignments(9L));
        var admin = new AcademicAdminPermissionClient();
        ReflectionTestUtils.setField(
                admin, "stub", AcademicGrpcServiceGrpc.newBlockingStub(channel));
        assertThatThrownBy(() -> admin.requireDashboard("synthetic-token"))
                .isInstanceOf(io.grpc.StatusRuntimeException.class);
        assertThat(channel.calls).hasSize(5);
        assertThat(channel.calls).anyMatch(name -> name.endsWith("/GetActiveSemester"));
    }

    private static final class ObservingChannel extends Channel {
        private final List<String> calls = new ArrayList<>();

        @Override
        public String authority() {
            return "synthetic-authority";
        }

        @Override
        public <ReqT, RespT> ClientCall<ReqT, RespT> newCall(
                MethodDescriptor<ReqT, RespT> method, CallOptions options) {
            assertCallHasDeadline(options);
            calls.add(method.getFullMethodName());
            return new ClientCall<>() {
                private Listener<RespT> listener;

                @Override
                public void start(Listener<RespT> value, Metadata headers) {
                    listener = value;
                }

                @Override
                public void request(int count) {}

                @Override
                public void cancel(String message, Throwable cause) {}

                @Override
                public void sendMessage(ReqT message) {}

                @Override
                public void halfClose() {
                    listener.onMessage(
                            method.getResponseMarshaller()
                                    .parse(new ByteArrayInputStream(new byte[0])));
                    listener.onClose(Status.OK, new Metadata());
                }
            };
        }
    }
}
