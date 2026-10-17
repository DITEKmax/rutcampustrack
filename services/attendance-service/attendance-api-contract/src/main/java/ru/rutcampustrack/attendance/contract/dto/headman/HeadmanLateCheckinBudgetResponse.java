package ru.rutcampustrack.attendance.contract.dto.headman;

/** Current budget, or the committed snapshot returned for an exact command replay. */
public record HeadmanLateCheckinBudgetResponse(
        long studentId, long semesterId, int limit, int used, int remaining) {}
