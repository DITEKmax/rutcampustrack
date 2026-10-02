package ru.rutcampustrack.auth.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.util.UUID;

/** Browser-only possession proof; approvalToken is never sufficient for exchange. */
public record QrLoginProofRequest(@NotNull QrLoginPurpose purpose, @NotNull UUID issuerId,
        @NotNull @Pattern(regexp = "[A-Za-z0-9_-]{43}") String issuerSecret,
        @NotNull UUID challengeId) {
    @Override public String toString() {
        return "QrLoginProofRequest[purpose=" + purpose + ", issuerId=" + issuerId
                + ", challengeId=" + challengeId + ", issuerSecret=<redacted>]";
    }
}
