package ru.rutcampustrack.auth.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.util.UUID;

public record QrLoginIssueRequest(@NotNull QrLoginPurpose purpose, UUID issuerId,
        @Pattern(regexp = "[A-Za-z0-9_-]{43}") String issuerSecret) {
    @AssertTrue(message = "issuerId и issuerSecret должны присутствовать вместе")
    public boolean isIssuerPairValid() { return (issuerId == null) == (issuerSecret == null); }
    @Override public String toString() {
        return "QrLoginIssueRequest[purpose=" + purpose + ", issuerId=" + issuerId + ", issuerSecret=<redacted>]";
    }
}
