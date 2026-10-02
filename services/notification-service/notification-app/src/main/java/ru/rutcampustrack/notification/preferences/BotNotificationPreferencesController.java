package ru.rutcampustrack.notification.preferences;

import com.mongodb.MongoException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.TransactionException;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import ru.rutcampustrack.notification.contract.api.BotNotificationPreferencesApi;
import ru.rutcampustrack.notification.contract.dto.preferences.BotNotificationPreferencesDto;
import ru.rutcampustrack.notification.contract.dto.preferences.UpdateBotNotificationPreferencesRequest;
import ru.rutcampustrack.shared.security.grpc.DirectedServiceCredential;

import java.security.MessageDigest;
import java.util.Base64;
import java.util.function.Supplier;

@RestController
public class BotNotificationPreferencesController implements BotNotificationPreferencesApi {
    private final BotNotificationPreferencesService service;
    private final byte[] tokenBytes;

    public BotNotificationPreferencesController(BotNotificationPreferencesService service,
            @Value("${notification.bot-preferences.token}") String token) {
        if (!DirectedServiceCredential.isCanonicalToken(token)) {
            throw new IllegalArgumentException("BOT_TO_NOTIFICATION_SERVICE_TOKEN must be canonical32byte base64url");
        }
        this.service = service;
        this.tokenBytes = Base64.getUrlDecoder().decode(token);
    }

    @Override
    public BotNotificationPreferencesDto getPreferences(String token, long userId, long telegramId, String category) {
        authenticate(token);
        return available(() -> service.get(userId, telegramId, category));
    }

    @Override
    public BotNotificationPreferencesDto updatePreferences(String token, long userId, long telegramId,
                                                           UpdateBotNotificationPreferencesRequest request) {
        authenticate(token);
        return available(() -> service.update(userId, telegramId, request));
    }

    private void authenticate(String candidate) {
        if (!DirectedServiceCredential.isCanonicalToken(candidate)
                || !MessageDigest.isEqual(tokenBytes, Base64.getUrlDecoder().decode(candidate))) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Bot credential is required");
        }
    }

    private static <T> T available(Supplier<T> operation) {
        try { return operation.get(); }
        catch (DataAccessException | MongoException | TransactionException error) {
            // No values or upstream messages in the response.
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Notification preferences are unavailable");
        }
    }
}
