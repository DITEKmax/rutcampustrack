package ru.rutcampustrack.auth.dto;

import com.fasterxml.jackson.annotation.JsonValue;
import java.util.Locale;

public enum QrLoginState {
    PENDING, CONFIRMED, REJECTED, EXPIRED;
    @JsonValue public String value() { return name().toLowerCase(Locale.ROOT); }
}
