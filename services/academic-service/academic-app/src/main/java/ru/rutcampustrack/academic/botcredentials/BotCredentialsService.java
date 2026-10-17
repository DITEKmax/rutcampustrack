package ru.rutcampustrack.academic.botcredentials;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.rutcampustrack.academic.grpc.BotCredentialMessagesRequest;
import ru.rutcampustrack.academic.grpc.BotCredentialMessagesResponse;
import ru.rutcampustrack.academic.grpc.BotOwnInitialCredentialsRequest;
import ru.rutcampustrack.academic.grpc.BotOwnInitialCredentialsResponse;

/**
 * Only this verified bot capability reaches the own credential read and durable exact-card tracker.
 */
@Service
public class BotCredentialsService {
    private final BotOwnInitialCredentialsQuery credentials;
    private final BotCredentialMessageRepository messages;

    public BotCredentialsService(
            BotOwnInitialCredentialsQuery credentials, BotCredentialMessageRepository messages) {
        this.credentials = credentials;
        this.messages = messages;
    }

    public BotOwnInitialCredentialsResponse read(BotOwnInitialCredentialsRequest request) {
        BotCredentialServiceIdentityInterceptor.requireBot();
        return credentials.read(request);
    }

    @Transactional
    public BotCredentialMessagesResponse manage(BotCredentialMessagesRequest request) {
        BotCredentialServiceIdentityInterceptor.requireBot();
        return switch (request.getCommandCase()) {
            case REGISTER -> messages.register(request.getRegister());
            case LIST_INVALIDATED -> messages.list(request.getListInvalidated());
            case COMPLETE_RENDER -> messages.completeRender(request.getCompleteRender());
            case COMPLETE_CLEANUP -> messages.completeCleanup(request.getCompleteCleanup());
            default -> throw BotOwnInitialCredentialsQuery.invalid();
        };
    }
}
