package ru.rutcampustrack.schedule.contract.dto.lesson;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

/** Null or blank room clears the room of this occurrence only. */
public record ChangeLessonRoomRequest(
        String room, @NotNull @Pattern(regexp = "[1-9][0-9]{0,18}") String expectedRevision) {
    /** Numeric JSON must not silently coerce into a room or a decimal revision. */
    @JsonCreator
    public static ChangeLessonRoomRequest fromJson(
            @JsonProperty(value = "room", required = true) Object room,
            @JsonProperty(value = "expectedRevision", required = true) Object revision) {
        if (room != null && !(room instanceof String) || !(revision instanceof String)) {
            throw new IllegalArgumentException("room — строка или null, expectedRevision — строка");
        }
        return new ChangeLessonRoomRequest((String) room, (String) revision);
    }
}
