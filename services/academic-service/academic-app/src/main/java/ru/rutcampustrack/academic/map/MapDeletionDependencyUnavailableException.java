package ru.rutcampustrack.academic.map;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

public class MapDeletionDependencyUnavailableException extends ResponseStatusException {
    public MapDeletionDependencyUnavailableException(String message) {
        super(HttpStatus.SERVICE_UNAVAILABLE, message);
    }
}
