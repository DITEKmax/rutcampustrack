package ru.rutcampustrack.academic.identityoperation;

import ru.rutcampustrack.academic.contract.dto.IdentityOperationHttpModels;

public final class AcademicIdentityPendingException extends RuntimeException {
    private final IdentityOperationHttpModels.Operation operation;

    public AcademicIdentityPendingException(IdentityOperationHttpModels.Operation operation) {
        super("identity operation pending", null, false, false);
        this.operation = operation;
    }

    public IdentityOperationHttpModels.Operation operation() {
        return operation;
    }
}
