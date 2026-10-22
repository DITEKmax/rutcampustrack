package ru.rutcampustrack.academic.botread;

import io.grpc.Status;

import org.springframework.stereotype.Service;

import ru.rutcampustrack.academic.grpc.BotOwnHomeworksForWeekRequest;
import ru.rutcampustrack.academic.grpc.HomeworksForWeekRequest;
import ru.rutcampustrack.academic.grpc.HomeworksForWeekResponse;
import ru.rutcampustrack.authauthority.grpc.BotReadCapability;
import ru.rutcampustrack.botreads.grpc.DirectedBotIdentity;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.function.Function;

/** Live Auth checks run outside local transactions; results are never cached. */
@Service
public class AcademicBotHomeworkService {
    private final AuthBotReadAuthorityClient auth;
    private final AcademicBotHomeworkQuery query;

    public AcademicBotHomeworkService(
            AuthBotReadAuthorityClient auth, AcademicBotHomeworkQuery query) {
        this.auth = auth;
        this.query = query;
    }

    public HomeworksForWeekResponse read(
            BotOwnHomeworksForWeekRequest request,
            Function<HomeworksForWeekRequest, HomeworksForWeekResponse> compose) {
        var identity =
                DirectedBotIdentity.newBuilder()
                        .setTelegramSenderId(request.getTelegramSenderId())
                        // This existing RPC is admitted only by the private-chat Bot boundary.
                        .setPrivateChatId(request.getTelegramSenderId())
                        .setExpectedUserId(request.getExpectedUserId())
                        .setExpectedBindingVersion(request.getExpectedTelegramBindingVersion())
                        .setExpectedRolesVersion(request.getExpectedRolesVersion())
                        .build();
        AuthBotReadAuthorityClient.validate(identity);
        LocalDate from = LocalDate.parse(request.getDateFrom());
        LocalDate to = LocalDate.parse(request.getDateTo());
        if (to.isBefore(from) || ChronoUnit.DAYS.between(from, to) > 6)
            throw Status.INVALID_ARGUMENT
                    .withDescription("homework range must contain one to seven days")
                    .asRuntimeException();
        var capability = BotReadCapability.BOT_READ_CAPABILITY_HOMEWORK_READ;
        var before = auth.check(identity, capability);
        var result = query.read(identity, from, to, compose);
        if (!before.getAuthorizedGroupIdsList().contains(result.scope().groupId()))
            throw Status.PERMISSION_DENIED
                    .withDescription("own student group required")
                    .asRuntimeException();
        var after = auth.check(identity, capability);
        if (!before.equals(after))
            throw Status.FAILED_PRECONDITION
                    .withDescription("current homework authority required")
                    .asRuntimeException();
        query.requireCurrent(identity, result.scope());
        return result.response();
    }
}
