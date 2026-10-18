package ru.rutcampustrack.academic.identityoperation;

import io.grpc.Status;
import io.grpc.StatusRuntimeException;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import ru.rutcampustrack.academic.contract.dto.IdentityOperationHttpModels;

import java.util.UUID;
import java.util.function.Function;

/** User-operation HTTP retry/status behavior; commands and effects remain in the coordinator. */
@Component
public class AcademicIdentityHttpOperations {
    private final HttpServletRequest request;
    private final HttpServletResponse response;
    private final JdbcAcademicIdentityOperationStore store;
    private final AcademicIdentityCoordinator coordinator;

    public AcademicIdentityHttpOperations(
            HttpServletRequest request,
            HttpServletResponse response,
            JdbcAcademicIdentityOperationStore store,
            AcademicIdentityCoordinator coordinator) {
        this.request = request;
        this.response = response;
        this.store = store;
        this.coordinator = coordinator;
    }

    public AcademicIdentityCoordinator.Operation execute(
            UUID operation,
            AcademicIdentityLocalCommand.ApiIntent intent,
            Function<UUID, AcademicIdentityCoordinator.AcceptedCommand> build) {
        if (operation == null) throw Status.INVALID_ARGUMENT.asRuntimeException();
        String supplied = request.getHeader("Idempotency-Key");
        Object current = request.getAttribute(AcademicIdentityHttpOperations.class.getName());
        if (supplied != null && !supplied.equals(operation.toString())
                || current instanceof UUID id && !id.equals(operation))
            throw Status.ALREADY_EXISTS
                    .withDescription("operation id differs from request key")
                    .asRuntimeException();
        request.setAttribute(AcademicIdentityHttpOperations.class.getName(), operation);
        return execute(intent, build);
    }

    public AcademicIdentityCoordinator.Operation execute(
            AcademicIdentityLocalCommand.ApiIntent intent,
            Function<UUID, AcademicIdentityCoordinator.AcceptedCommand> build) {
        if (TransactionSynchronizationManager.isActualTransactionActive())
            throw Status.UNAVAILABLE.asRuntimeException();
        UUID id = key();
        headers(id);
        var accepted = store.acceptedForActor(id.toString(), intent);
        boolean fresh = accepted == null;
        if (fresh) {
            try {
                accepted = build.apply(id);
            } catch (LocalPreflightRejection rejected) {
                if (store.rejectPreflight(id.toString(), intent)) throw rejected.original;
                // A concurrent exact positive acceptance wins; report/recover its real outcome.
                var winner = store.acceptedForActor(id.toString(), intent);
                if (winner == null) throw Status.UNAVAILABLE.asRuntimeException();
                return complete(executeStored(winner));
            } catch (ru.rutcampustrack.academic.exception.BadRequestException
                    | ru.rutcampustrack.academic.exception.ConflictException rejected) {
                if (store.rejectPreflight(id.toString(), intent)) throw rejected;
                var winner = store.acceptedForActor(id.toString(), intent);
                if (winner == null) throw Status.UNAVAILABLE.asRuntimeException();
                return complete(executeStored(winner));
            }
        }
        AcademicIdentityCoordinator.Operation operation;
        try {
            operation = coordinator.execute(accepted);
        } catch (StatusRuntimeException failure) {
            if (fresh && failure.getStatus().getCode() == Status.Code.ALREADY_EXISTS) {
                // Two same-key callers can both miss the initial local lookup. Replay the
                // winner's exact bytes only after checking the bound nonsecret API intent.
                var winner = store.acceptedForActor(id.toString(), intent);
                if (winner == null) throw failure;
                operation = executeStored(winner);
            } else if (uncertain(failure.getStatus().getCode())) {
                operation = knownOutcome(id.toString(), failure);
            } else throw failure;
        } catch (DataAccessException unknownLocalOutcome) {
            operation = knownOutcome(id.toString(), unknownLocalOutcome);
        }
        return complete(operation);
    }

    public void checkGroupAttempt(UUID id, AcademicIdentityLocalCommand.ApiIntent intent) {
        store.checkLocalRejection(id.toString(), intent);
    }

    /**
     * Group-bound local admission uses the same durable UUID negative receipt as user mutations.
     */
    public ru.rutcampustrack.academic.groupavailability.GroupAvailabilityStore.Operation
            groupPreflight(
                    UUID id,
                    AcademicIdentityLocalCommand.ApiIntent intent,
                    java.util.function.Supplier<
                                    ru.rutcampustrack.academic.groupavailability
                                            .GroupAvailabilityStore.Operation>
                            local,
                    java.util.function.Supplier<
                                    ru.rutcampustrack.academic.groupavailability
                                            .GroupAvailabilityStore.Operation>
                            positiveWinner) {
        try {
            return localPreflight(local);
        } catch (LocalPreflightRejection failure) {
            if (store.rejectPreflight(id.toString(), intent)) throw failure.original;
            return positiveWinner.get();
        } catch (ru.rutcampustrack.academic.exception.BadRequestException
                | ru.rutcampustrack.academic.exception.ConflictException failure) {
            if (store.rejectPreflight(id.toString(), intent)) throw failure;
            return positiveWinner.get();
        }
    }

    /** Invoke only around own local preflight reads/validation, never a remote RPC. */
    public static <T> T localPreflight(java.util.function.Supplier<T> work) {
        try {
            return work.get();
        } catch (StatusRuntimeException failure) {
            switch (failure.getStatus().getCode()) {
                case INVALID_ARGUMENT, FAILED_PRECONDITION, ALREADY_EXISTS, NOT_FOUND, ABORTED ->
                        throw new LocalPreflightRejection(failure);
                default -> throw failure;
            }
        } catch (org.springframework.web.ErrorResponseException failure) {
            int code = failure.getStatusCode().value();
            if (code == 400 || code == 404 || code == 409 || code == 422)
                throw new LocalPreflightRejection(failure);
            throw failure;
        }
    }

    private static final class LocalPreflightRejection extends RuntimeException {
        private final RuntimeException original;

        private LocalPreflightRejection(RuntimeException original) {
            super(null, original, false, false);
            this.original = original;
        }
    }

    private AcademicIdentityCoordinator.Operation executeStored(
            AcademicIdentityCoordinator.AcceptedCommand accepted) {
        try {
            return coordinator.execute(accepted);
        } catch (StatusRuntimeException failure) {
            if (!uncertain(failure.getStatus().getCode())) throw failure;
            return knownOutcome(accepted.authCommand().getOperationId(), failure);
        } catch (DataAccessException unknownLocalOutcome) {
            return knownOutcome(accepted.authCommand().getOperationId(), unknownLocalOutcome);
        }
    }

    private AcademicIdentityCoordinator.Operation knownOutcome(
            String id, RuntimeException original) {
        try {
            return store.findForActor(id);
        } catch (StatusRuntimeException missing) {
            if (missing.getStatus().getCode() == Status.Code.NOT_FOUND) throw original;
            throw missing;
        }
    }

    private AcademicIdentityCoordinator.Operation complete(
            AcademicIdentityCoordinator.Operation operation) {
        var status = publicStatus(operation);
        if (status.status() == IdentityOperationHttpModels.Status.PENDING)
            throw new AcademicIdentityPendingException(status);
        if (status.status() == IdentityOperationHttpModels.Status.REJECTED) {
            boolean denied =
                    operation.prepared() != null
                            && "PREPARE_REJECTED_ACTOR_DENIED"
                                    .equals(operation.prepared().getErrorCode());
            throw (denied ? Status.PERMISSION_DENIED : Status.FAILED_PRECONDITION)
                    .asRuntimeException();
        }
        if (status.status() == IdentityOperationHttpModels.Status.FAILED_SAFE)
            throw Status.FAILED_PRECONDITION
                    .withDescription("fresh authorized reconciliation required")
                    .asRuntimeException();
        return operation;
    }

    public static IdentityOperationHttpModels.Operation publicStatus(
            AcademicIdentityCoordinator.Operation operation) {
        var state =
                switch (operation.phase()) {
                    case SUCCEEDED -> IdentityOperationHttpModels.Status.SUCCEEDED;
                    case REJECTED -> IdentityOperationHttpModels.Status.REJECTED;
                    case FAILED_SAFE -> IdentityOperationHttpModels.Status.FAILED_SAFE;
                    default -> IdentityOperationHttpModels.Status.PENDING;
                };
        return new IdentityOperationHttpModels.Operation(
                UUID.fromString(operation.id()),
                state,
                state == IdentityOperationHttpModels.Status.PENDING ? 2 : 0);
    }

    public static String location(UUID id) {
        return "/academic/identity-operations/" + id;
    }

    public UUID beginOperation() {
        if (TransactionSynchronizationManager.isActualTransactionActive())
            throw Status.UNAVAILABLE.asRuntimeException();
        UUID id = key();
        headers(id);
        return id;
    }

    private UUID key() {
        var existing = request.getAttribute(AcademicIdentityHttpOperations.class.getName());
        if (existing instanceof UUID id) return id;
        String supplied = request.getHeader("Idempotency-Key");
        UUID id;
        try {
            id = supplied == null ? UUID.randomUUID() : UUID.fromString(supplied);
            if (supplied != null && !id.toString().equals(supplied))
                throw new IllegalArgumentException();
        } catch (IllegalArgumentException invalid) {
            throw Status.INVALID_ARGUMENT
                    .withDescription("canonical Idempotency-Key UUID required")
                    .asRuntimeException();
        }
        request.setAttribute(AcademicIdentityHttpOperations.class.getName(), id);
        return id;
    }

    private void headers(UUID id) {
        response.setHeader("X-Operation-Id", id.toString());
        response.setHeader("Location", location(id));
        response.setHeader("Cache-Control", "no-store");
    }

    private static boolean uncertain(Status.Code code) {
        return switch (code) {
            case ABORTED, UNAVAILABLE, DEADLINE_EXCEEDED, CANCELLED, UNKNOWN, INTERNAL -> true;
            default -> false;
        };
    }
}
