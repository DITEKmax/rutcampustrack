package ru.rutcampustrack.schedule.arch;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import ru.rutcampustrack.shared.observability.GrpcDeadlineArchRules;

/**
 * M05 G9 DRY (NEW-149): правило вынесено в {@link GrpcDeadlineArchRules}, здесь только per-service
 * scoping.
 */
@AnalyzeClasses(
        packages = "ru.rutcampustrack.schedule",
        importOptions = {ImportOption.DoNotIncludeTests.class})
class GrpcClientDeadlineTest {

    @ArchTest
    static final ArchRule grpcClientsMustSetDeadline =
            GrpcDeadlineArchRules.grpcClientsMustSetDeadline();

    @org.junit.jupiter.api.Test
    void productionGrpcClientCarriesDeadlinesOnActualCallsIncludingNamedGetters() {
        var observed = new java.util.ArrayList<String>();
        io.grpc.Channel channel =
                new io.grpc.Channel() {
                    @Override
                    public String authority() {
                        return "synthetic-academic";
                    }

                    @Override
                    public <ReqT, RespT> io.grpc.ClientCall<ReqT, RespT> newCall(
                            io.grpc.MethodDescriptor<ReqT, RespT> method,
                            io.grpc.CallOptions options) {
                        GrpcDeadlineArchRules.assertCallHasDeadline(options);
                        observed.add(method.getBareMethodName());
                        return new io.grpc.ClientCall<>() {
                            private Listener<RespT> listener;

                            @Override
                            public void start(Listener<RespT> value, io.grpc.Metadata headers) {
                                listener = value;
                            }

                            @Override
                            public void request(int count) {}

                            @Override
                            public void cancel(String message, Throwable cause) {}

                            @Override
                            public void sendMessage(ReqT request) {}

                            @Override
                            public void halfClose() {
                                listener.onHeaders(new io.grpc.Metadata());
                                listener.onMessage(
                                        method.getResponseMarshaller()
                                                .parse(
                                                        new java.io.ByteArrayInputStream(
                                                                new byte[0])));
                                listener.onClose(io.grpc.Status.OK, new io.grpc.Metadata());
                            }
                        };
                    }
                };
        var client = new ru.rutcampustrack.schedule.grpc.AcademicGrpcClient();
        org.springframework.test.util.ReflectionTestUtils.setField(
                client,
                "stub",
                ru.rutcampustrack.academic.grpc.AcademicGrpcServiceGrpc.newBlockingStub(channel));
        org.springframework.test.util.ReflectionTestUtils.setField(
                client, "scheduleToAcademicToken", "A".repeat(43));
        var identity = new ru.rutcampustrack.schedule.security.RequestContext();
        identity.setInternalToken("synthetic-session");
        org.springframework.test.util.ReflectionTestUtils.setField(
                client, "requestContext", identity);
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> client.validateGroup(7L))
                .isExactlyInstanceOf(
                        ru.rutcampustrack.schedule.exception.ResourceNotFoundException.class);
        client.getActiveSemester();
        client.isHeadman(1L, 7L);
        client.hasAssistantPermission(7L, "mark_attendance");
        client.getAssignmentsByIds(java.util.List.of(501L));
        client.getPreparedAssignmentCloseOperation(java.util.UUID.randomUUID(), 501L);
        client.getPreparedAssignmentLifecycle(java.util.UUID.randomUUID(), 501L);
        client.getSemesterProgressAuthorityState(9L);
        client.getSemesterArchiveAuthorityState(9L);
        org.assertj.core.api.Assertions.assertThat(observed)
                .hasSize(9)
                .contains(
                        "GetGroup",
                        "GetActiveSemester",
                        "GetAssignmentsByIds",
                        "GetPreparedAssignmentCloseOperation",
                        "GetSemesterState");
    }
}
