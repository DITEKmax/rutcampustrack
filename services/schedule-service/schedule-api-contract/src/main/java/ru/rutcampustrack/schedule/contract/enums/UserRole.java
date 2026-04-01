package ru.rutcampustrack.schedule.contract.enums;

/**
 * User roles propagated via X-User-Role header from API Gateway.
 * Must NOT be imported from academic-api-contract — microservice isolation.
 */
public enum UserRole {
    ADMIN,
    TEACHER,
    STUDENT
}
