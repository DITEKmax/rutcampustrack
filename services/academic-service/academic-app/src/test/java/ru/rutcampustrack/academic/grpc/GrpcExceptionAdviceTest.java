package ru.rutcampustrack.academic.grpc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.grpc.Metadata;
import io.grpc.ServerCall;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;

import net.devh.boot.grpc.server.advice.GrpcAdviceDiscoverer;
import net.devh.boot.grpc.server.advice.GrpcAdviceExceptionHandler;
import net.devh.boot.grpc.server.advice.GrpcExceptionHandlerMethodResolver;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;

import ru.rutcampustrack.academic.contract.exception.ResourceNotFoundException;
import ru.rutcampustrack.academic.exception.UnknownAssignmentCodeException;
import ru.rutcampustrack.academic.history.HistoricalMembershipException;
import ru.rutcampustrack.academic.identityoperation.AcademicIdentityReadFence;

import java.util.List;

class GrpcExceptionAdviceTest {
    private static final String PRIVATE_MARKER = "private-fence-control-not-for-response";
    private static final String SAFE_DESCRIPTION =
            "Academic identity read is temporarily unavailable";
    private AnnotationConfigApplicationContext context;
    private GrpcExceptionHandlerMethodResolver resolver;
    private GrpcAdviceExceptionHandler handler;

    @BeforeEach
    void initializeRealAdviceDispatcher() throws Exception {
        context = new AnnotationConfigApplicationContext();
        context.registerBean(GrpcExceptionAdvice.class);
        context.refresh();
        GrpcAdviceDiscoverer discoverer = new GrpcAdviceDiscoverer();
        discoverer.setApplicationContext(context);
        discoverer.afterPropertiesSet();
        resolver = new GrpcExceptionHandlerMethodResolver(discoverer);
        resolver.afterPropertiesSet();
        handler = new GrpcAdviceExceptionHandler(resolver);
    }

    @AfterEach
    void closeContext() {
        context.close();
    }

    @Test
    void actualIdentityFencesRemainUnavailableThroughFrameworkAdvice() throws Exception {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForObject(anyString(), eq(Boolean.class), eq(17L), eq(17L)))
                .thenReturn(true);
        when(jdbc.queryForObject(anyString(), eq(Boolean.class))).thenReturn(true);
        AcademicIdentityReadFence fence = new AcademicIdentityReadFence(jdbc);
        List<StatusRuntimeException> failures =
                List.of(
                        assertThrows(
                                StatusRuntimeException.class,
                                () -> fence.requireGroupReadable(17L)),
                        assertThrows(
                                StatusRuntimeException.class, () -> fence.requireUserReadable(17L)),
                        assertThrows(StatusRuntimeException.class, fence::requireRegistryReadable));
        for (StatusRuntimeException failure : failures) {
            assertThat(failure.getStatus().getCode()).isEqualTo(Status.Code.UNAVAILABLE);
            Status response = dispatch(failure);
            assertThat(response.getCode()).isEqualTo(Status.Code.UNAVAILABLE);
            assertThat(response.getDescription()).isEqualTo(SAFE_DESCRIPTION);
            assertThat(response.getCause()).isNull();
        }
    }

    @Test
    void recognizedFenceDiscardsPrivateCauseAndTrailers() {
        for (String description :
                List.of(
                        "group identity operation is pending",
                        "identity operation publication is pending")) {
            Metadata trailers = new Metadata();
            trailers.put(
                    Metadata.Key.of("private-control", Metadata.ASCII_STRING_MARSHALLER),
                    PRIVATE_MARKER);
            StatusRuntimeException failure =
                    Status.UNAVAILABLE
                            .withDescription(description)
                            .withCause(new IllegalStateException(PRIVATE_MARKER))
                            .asRuntimeException(trailers);
            Status response = dispatch(failure);
            assertThat(response.getCode()).isEqualTo(Status.Code.UNAVAILABLE);
            assertThat(response.getDescription()).isEqualTo(SAFE_DESCRIPTION);
            assertThat(response.getCause()).isNull();
            assertThat(response.toString()).doesNotContain(PRIVATE_MARKER);
        }
    }

    @Test
    void unrecognizedDescriptionsOtherStatusesAndWrappedFencesKeepInternalPolicy() {
        for (String description :
                new String[] {
                    null,
                    "",
                    "group identity operation is pending " + PRIVATE_MARKER,
                    "identity operation publication is pending\n" + PRIVATE_MARKER,
                    "GROUP IDENTITY OPERATION IS PENDING"
                }) {
            assertInternal(
                    dispatch(Status.UNAVAILABLE.withDescription(description).asRuntimeException()));
        }
        for (Status.Code code : Status.Code.values()) {
            if (code == Status.Code.UNAVAILABLE) continue;
            assertInternal(
                    dispatch(
                            Status.fromCode(code)
                                    .withDescription("group identity operation is pending")
                                    .asRuntimeException()));
        }
        assertInternal(
                dispatch(
                        new RuntimeException(
                                Status.UNAVAILABLE
                                        .withDescription("group identity operation is pending")
                                        .asRuntimeException())));
        assertInternal(
                dispatch(
                        Status.UNAVAILABLE
                                .withDescription("group identity operation is pending")
                                .asException()));
        assertInternal(dispatch(new IllegalStateException(PRIVATE_MARKER)));
    }

    @Test
    void existingSpecificHandlersAndUnknownAssignmentPriorityRemainUnchanged() {
        assertThat(
                        dispatch(
                                        HistoricalMembershipException.precondition(
                                                "managed history required"))
                                .getCode())
                .isEqualTo(Status.Code.FAILED_PRECONDITION);
        assertThat(dispatch(new ResourceNotFoundException("Group", "id", 17L)).getCode())
                .isEqualTo(Status.Code.NOT_FOUND);
        assertThat(dispatch(new IllegalArgumentException("invalid control")).getCode())
                .isEqualTo(Status.Code.INVALID_ARGUMENT);
        assertThat(
                        dispatch(
                                        Status.UNAVAILABLE
                                                .withDescription(
                                                        "group identity operation is pending")
                                                .withCause(new UnknownAssignmentCodeException())
                                                .asRuntimeException())
                                .getCode())
                .isEqualTo(Status.Code.FAILED_PRECONDITION);
    }

    private static void assertInternal(Status response) {
        assertThat(response.getCode()).isEqualTo(Status.Code.INTERNAL);
        assertThat(response.getDescription()).isEqualTo("Internal server error");
    }

    @SuppressWarnings("unchecked")
    private Status dispatch(Throwable failure) {
        ServerCall<Object, Object> call = mock(ServerCall.class);
        handler.handleError(call, failure);
        ArgumentCaptor<Status> status = ArgumentCaptor.forClass(Status.class);
        ArgumentCaptor<Metadata> trailers = ArgumentCaptor.forClass(Metadata.class);
        verify(call).close(status.capture(), trailers.capture());
        assertThat(trailers.getValue()).isNotNull();
        assertThat(trailers.getValue().keys()).isEmpty();
        return status.getValue();
    }
}
