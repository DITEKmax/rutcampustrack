package ru.rutcampustrack.auth.exception;

public class InvalidCredentialsException extends RuntimeException {

    public InvalidCredentialsException() {
        super("Invalid login or password");
    }

    public InvalidCredentialsException(String message) {
        super(message);
    }
}
