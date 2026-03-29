package ru.rutcampustrack.auth.exception;

public class OtpExpiredException extends RuntimeException {

    public OtpExpiredException() {
        super("OTP code has expired or is invalid");
    }
}
