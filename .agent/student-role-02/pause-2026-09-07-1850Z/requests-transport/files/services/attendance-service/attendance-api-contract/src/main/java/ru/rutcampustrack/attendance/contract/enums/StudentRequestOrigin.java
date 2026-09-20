package ru.rutcampustrack.attendance.contract.enums;

/** Origin is observable for late-checkin requests; excuse submissions are manual. */
public enum StudentRequestOrigin {
    MANUAL,
    AUTO_GEO_FAILURE
}
