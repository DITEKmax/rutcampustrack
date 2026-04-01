package ru.rutcampustrack.schedule.contract.dto.lesson;

/**
 * Response DTO for mass-cancel operation, reporting how many lessons were cancelled.
 * No Lombok — contract modules use plain Java records.
 */
public record MassCancelResponse(int cancelledCount) {}
