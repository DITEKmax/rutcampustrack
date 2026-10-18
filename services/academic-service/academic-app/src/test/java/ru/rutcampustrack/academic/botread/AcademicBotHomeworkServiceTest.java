package ru.rutcampustrack.academic.botread;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import io.grpc.Status;

import org.junit.jupiter.api.Test;

import ru.rutcampustrack.academic.grpc.*;
import ru.rutcampustrack.authauthority.grpc.*;

import java.util.function.Function;

class AcademicBotHomeworkServiceTest {
    private final AuthBotReadAuthorityClient auth = mock(AuthBotReadAuthorityClient.class);
    private final AcademicBotHomeworkQuery query = mock(AcademicBotHomeworkQuery.class);
    private final AcademicBotHomeworkService service = new AcademicBotHomeworkService(auth, query);
    private final Function<HomeworksForWeekRequest, HomeworksForWeekResponse> compose =
            ignored -> {
                throw new AssertionError("query must control composition transaction");
            };
    private final AcademicBotHomeworkQuery.Scope scope =
            new AcademicBotHomeworkQuery.Scope(8, 5, 12, 4);
    private final HomeworksForWeekResponse response =
            HomeworksForWeekResponse.newBuilder()
                    .addHomeworks(
                            HomeworkInfo.newBuilder()
                                    .setHomeworkId(91)
                                    .setCompleted(true)
                                    .setCompletedAt("2026-10-18T09:00:00Z"))
                    .build();

    @Test
    void positiveStudentReadPreservesResponseAndRechecksLocalEligibility() {
        when(auth.check(any(), any())).thenReturn(authority());
        when(query.read(any(), any(), any(), any()))
                .thenReturn(new AcademicBotHomeworkQuery.Result(scope, response));
        assertThat(service.read(request(), compose)).isEqualTo(response);
        verify(query).requireCurrent(any(), eq(scope));
    }

    @Test
    void bindingAndRolesVersionAreMandatoryBeforeAnyAuthorityOrDataRead() {
        for (var invalid :
                new BotOwnHomeworksForWeekRequest[] {
                    request().toBuilder().setExpectedTelegramBindingVersion(0).build(),
                    request().toBuilder().setExpectedRolesVersion(0).build(),
                    request().toBuilder().setTelegramSenderId(0).build()
                }) {
            assertCode(Status.Code.INVALID_ARGUMENT, () -> service.read(invalid, compose));
        }
        verifyNoInteractions(auth, query);
    }

    @Test
    void invalidRangeDoesNotReadData() {
        assertCode(
                Status.Code.INVALID_ARGUMENT,
                () -> service.read(request().toBuilder().setDateTo("2026-10-25").build(), compose));
        verifyNoInteractions(auth, query);
    }

    @Test
    void teacherOrAdminOnlyDeniedByLiveAuthCannotReadHomework() {
        when(auth.check(any(), any())).thenThrow(Status.PERMISSION_DENIED.asRuntimeException());
        assertCode(Status.Code.PERMISSION_DENIED, () -> service.read(request(), compose));
        verifyNoInteractions(query);
    }

    @Test
    void revokedBindingAfterCompositionReturnsNoData() {
        when(auth.check(any(), any()))
                .thenReturn(authority())
                .thenThrow(Status.FAILED_PRECONDITION.asRuntimeException());
        when(query.read(any(), any(), any(), any()))
                .thenReturn(new AcademicBotHomeworkQuery.Result(scope, response));
        assertCode(Status.Code.FAILED_PRECONDITION, () -> service.read(request(), compose));
    }

    @Test
    void localGroupOutsideCurrentAuthGrantCannotReturnData() {
        when(auth.check(any(), any()))
                .thenReturn(
                        authority().toBuilder()
                                .clearAuthorizedGroupIds()
                                .addAuthorizedGroupIds(13)
                                .build());
        when(query.read(any(), any(), any(), any()))
                .thenReturn(new AcademicBotHomeworkQuery.Result(scope, response));
        assertCode(Status.Code.PERMISSION_DENIED, () -> service.read(request(), compose));
    }

    @Test
    void changedAuthScopeAfterCompositionCannotReturnData() {
        when(auth.check(any(), any()))
                .thenReturn(authority())
                .thenReturn(authority().toBuilder().addAuthorizedGroupIds(13).build());
        when(query.read(any(), any(), any(), any()))
                .thenReturn(new AcademicBotHomeworkQuery.Result(scope, response));
        assertCode(Status.Code.FAILED_PRECONDITION, () -> service.read(request(), compose));
    }

    @Test
    void pendingPublicationOrTransferAfterCompositionCannotReturnData() {
        when(auth.check(any(), any())).thenReturn(authority());
        when(query.read(any(), any(), any(), any()))
                .thenReturn(new AcademicBotHomeworkQuery.Result(scope, response));
        doThrow(Status.UNAVAILABLE.asRuntimeException()).when(query).requireCurrent(any(), any());
        assertCode(Status.Code.UNAVAILABLE, () -> service.read(request(), compose));
    }

    private static CheckBotOwnAuthorityResponse authority() {
        return CheckBotOwnAuthorityResponse.newBuilder()
                .setUserId(7)
                .setRolesVersion(5)
                .setTelegramBindingVersion(3)
                .setCapability(BotReadCapability.BOT_READ_CAPABILITY_HOMEWORK_READ)
                .addAuthorizedGroupIds(12)
                .build();
    }

    private static BotOwnHomeworksForWeekRequest request() {
        return BotOwnHomeworksForWeekRequest.newBuilder()
                .setTelegramSenderId(42)
                .setExpectedUserId(7)
                .setExpectedTelegramBindingVersion(3)
                .setExpectedRolesVersion(5)
                .setDateFrom("2026-10-18")
                .setDateTo("2026-10-24")
                .build();
    }

    private static void assertCode(
            Status.Code code, org.assertj.core.api.ThrowableAssert.ThrowingCallable action) {
        assertThatThrownBy(action)
                .satisfies(
                        error -> assertThat(Status.fromThrowable(error).getCode()).isEqualTo(code));
    }
}
