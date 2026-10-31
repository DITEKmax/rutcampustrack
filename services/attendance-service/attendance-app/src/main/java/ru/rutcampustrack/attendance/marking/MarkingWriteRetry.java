package ru.rutcampustrack.attendance.marking;

import com.mongodb.MongoException;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;
import java.util.function.Supplier;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import ru.rutcampustrack.attendance.exception.AttendanceWriteBusyException;

/** Only the single-mark REST boundary may replay a confirmed aborted transaction. */
final class MarkingWriteRetry {
    private static final int MAX_ATTEMPTS = 4;
    private static final int MAX_CAUSES = 64;

    private MarkingWriteRetry() {}

    static <T> T execute(Supplier<T> transaction) {
        // The caller owns an ambient transaction; never independently commit or replay it.
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            return transaction.get();
        }
        for (int attempt = 1; ; attempt++) {
            try {
                // The existing service proxy completes commit/rollback before returning here.
                return transaction.get();
            } catch (RuntimeException failure) {
                if (TransactionSynchronizationManager.isActualTransactionActive()
                        || !isConfirmedTransientAbort(failure)) {
                    throw failure;
                }
                if (attempt == MAX_ATTEMPTS) {
                    throw new AttendanceWriteBusyException(failure);
                }
                try {
                    Thread.sleep(25L << (attempt - 1));
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    throw failure;
                }
            }
        }
    }

    static boolean isConfirmedTransientAbort(Throwable failure) {
        Set<Throwable> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        boolean confirmed = false;
        Throwable cause = failure;
        while (cause != null) {
            // Incomplete or cyclic chains fail closed: an unknown commit label may be hidden.
            if (seen.size() == MAX_CAUSES || !seen.add(cause)) return false;
            if (cause instanceof MongoException mongo) {
                if (mongo.hasErrorLabel("UnknownTransactionCommitResult")) return false;
                if (mongo.getCode() == 112 && mongo.hasErrorLabel("TransientTransactionError")) {
                    confirmed = true;
                }
            }
            cause = cause.getCause();
        }
        return confirmed;
    }
}
