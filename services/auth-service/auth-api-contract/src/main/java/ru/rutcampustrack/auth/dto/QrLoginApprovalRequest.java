package ru.rutcampustrack.auth.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.util.UUID;

public record QrLoginApprovalRequest(@NotNull QrLoginPurpose purpose, @NotNull UUID challengeId,
        @NotNull @Pattern(regexp = "[A-Za-z0-9_-]{43}") String approvalToken) {
    @Override public String toString() {
        return "QrLoginApprovalRequest[purpose=" + purpose + ", challengeId=" + challengeId + ", approvalToken=<redacted>]";
    }
}
