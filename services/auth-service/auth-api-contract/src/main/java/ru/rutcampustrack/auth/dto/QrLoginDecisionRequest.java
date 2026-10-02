package ru.rutcampustrack.auth.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.util.UUID;

public record QrLoginDecisionRequest(@NotNull QrLoginPurpose purpose, @NotNull UUID challengeId,
        @NotNull @Pattern(regexp = "[A-Za-z0-9_-]{43}") String approvalToken,
        @NotNull Decision decision) {
    public enum Decision { CONFIRM, REJECT }
    public QrLoginApprovalRequest approval() { return new QrLoginApprovalRequest(purpose, challengeId, approvalToken); }
    @Override public String toString() {
        return "QrLoginDecisionRequest[purpose=" + purpose + ", challengeId=" + challengeId
                + ", approvalToken=<redacted>, decision=" + decision + ']';
    }
}
