package ru.rutcampustrack.notification.preferences;

import io.grpc.Metadata;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.MetadataUtils;
import net.devh.boot.grpc.client.inject.GrpcClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import ru.rutcampustrack.academic.grpc.AcademicGrpcServiceGrpc;
import ru.rutcampustrack.academic.grpc.UserByTelegramIdRequest;
import ru.rutcampustrack.academic.grpc.UserRequest;

import java.util.concurrent.TimeUnit;

/** Each private prefs operation validates both directions of the current binding. */
@Component
public class AcademicTelegramBindingClient {
    private static final Metadata.Key<String> SECRET = Metadata.Key.of("x-grpc-secret", Metadata.ASCII_STRING_MARSHALLER);
    @GrpcClient("academic-service")
    private AcademicGrpcServiceGrpc.AcademicGrpcServiceBlockingStub stub;
    private final String secret;

    public AcademicTelegramBindingClient(@Value("${grpc.auth.secret:}") String secret) { this.secret = secret; }

    public void validate(long userId, long telegramId) {
        if (userId <= 0 || telegramId <= 0) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Positive identity is required");
        if (secret == null || secret.isBlank()) throw NotificationPreferencesStore.unavailable("Binding authority is not configured", null);
        Metadata headers = new Metadata();
        headers.put(SECRET, secret);
        var client = stub.withInterceptors(MetadataUtils.newAttachHeadersInterceptor(headers));
        try {
            var byTelegram = client.withDeadlineAfter(3, TimeUnit.SECONDS).getUserByTelegramId(
                    UserByTelegramIdRequest.newBuilder().setTelegramId(telegramId).build());
            if (!byTelegram.getFound() || byTelegram.getUserId() != userId || byTelegram.getTelegramId() != telegramId) {
                throw conflict();
            }
            var byUser = client.withDeadlineAfter(3, TimeUnit.SECONDS).getUserById(
                    UserRequest.newBuilder().setUserId(userId).build());
            if (byUser.getId() != userId || byUser.getTelegramId() != telegramId) throw conflict();
        } catch (StatusRuntimeException error) {
            if (error.getStatus().getCode() == Status.Code.NOT_FOUND) throw conflict();
            throw NotificationPreferencesStore.unavailable("Binding authority is unavailable", error);
        }
    }

    private static ResponseStatusException conflict() {
        return new ResponseStatusException(HttpStatus.CONFLICT, "Telegram binding does not match");
    }
}
