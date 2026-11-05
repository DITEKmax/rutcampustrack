package ru.rutcampustrack.schedule.contract.dto.item;

/** A closed reason code and affected logical-occurrence count; no private audit payload. */
public record ConstructorProtectedReason(String code, long count) {}
