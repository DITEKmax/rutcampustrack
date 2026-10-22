package ru.rutcampustrack.attendance.contract.dto.headman;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigInteger;

public record HeadmanLateCheckinBudgetIncreaseRequest(
        @NotNull @Positive Long semesterId,
        @NotNull @Positive Integer increase,
        @NotNull @Size(min = 16, max = 128) String requestKey) {

    /** Keep strict integer input without adding databind to the public contract module. */
    @JsonCreator
    public static HeadmanLateCheckinBudgetIncreaseRequest fromJson(
            @JsonProperty("semesterId") Long semesterId,
            @JsonProperty("increase") Object increase,
            @JsonProperty("requestKey") String requestKey) {
        Integer value = null;
        if (increase != null) {
            if (!(increase instanceof Integer
                    || increase instanceof Long
                    || increase instanceof BigInteger)) {
                throw new IllegalArgumentException("increase must be an int32 JSON integer");
            }
            value = new BigInteger(increase.toString()).intValueExact();
        }
        return new HeadmanLateCheckinBudgetIncreaseRequest(semesterId, value, requestKey);
    }
}
