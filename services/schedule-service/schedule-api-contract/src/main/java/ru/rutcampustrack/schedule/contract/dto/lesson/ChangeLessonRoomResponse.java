package ru.rutcampustrack.schedule.contract.dto.lesson;

/** Immutable committed receipt; decimal identifiers are safe for JavaScript clients. */
@com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.ALWAYS)
public record ChangeLessonRoomResponse(String operationId, String state, String lessonId,
        String occurrenceId, String revision, String room) { }
