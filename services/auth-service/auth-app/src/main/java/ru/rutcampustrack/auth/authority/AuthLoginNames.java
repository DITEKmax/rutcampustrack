package ru.rutcampustrack.auth.authority;

import io.grpc.Status;

import java.util.function.Predicate;

/** Existing public login reservation algorithm, executed under the Auth global lock. */
final class AuthLoginNames {
    private AuthLoginNames() {}

    static String reserve(String stem, String digits, Predicate<String> occupied) {
        if (stem == null
                || (!stem.isEmpty() && !stem.matches("[a-z0-9](?:[a-z0-9-]{0,30}[a-z0-9])?"))
                || digits == null
                || !digits.matches("[0-9]{4}")) throw Status.INVALID_ARGUMENT.asRuntimeException();
        String digitPart = "-" + digits;
        String baseStem = stem.substring(0, Math.min(stem.length(), 32 - digitPart.length()));
        String base = baseStem.isEmpty() ? digits : baseStem + digitPart;
        if (!occupied.test(base)) return base;
        for (int suffix = 2; suffix < 10_000; suffix++) {
            String collision = "-" + suffix;
            int length = Math.max(1, 32 - digitPart.length() - collision.length());
            String candidateStem = baseStem.substring(0, Math.min(baseStem.length(), length));
            String candidate =
                    candidateStem.isEmpty()
                            ? digits + collision
                            : candidateStem + digitPart + collision;
            if (!occupied.test(candidate)) return candidate;
        }
        throw IdentityOperationFailure.error(Status.ALREADY_EXISTS, "LOGIN_RESERVATION_EXHAUSTED");
    }
}
