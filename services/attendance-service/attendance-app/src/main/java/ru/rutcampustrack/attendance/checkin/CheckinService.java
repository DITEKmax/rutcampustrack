package ru.rutcampustrack.attendance.checkin;

import org.springframework.stereotype.Service;
import ru.rutcampustrack.attendance.contract.dto.checkin.CheckinRequest;
import ru.rutcampustrack.attendance.exception.LegacyCheckinRetiredException;

/**
 * Compatibility seam for the retired REST geo-checkin endpoint.
 *
 * <p>The canonical student gRPC command is the only writable geo-checkin path.
 * This service stays as a small bean so old controller wiring cannot
 * accidentally re-enable the former mutable implementation.</p>
 */
@Service
@Deprecated(forRemoval = false)
public class CheckinService {

    /**
     * Fails closed before any rate-limit, schedule, deduplication, persistence
     * or event operation can be performed.
     */
    public AttendanceDocument checkin(CheckinRequest request) {
        throw new LegacyCheckinRetiredException();
    }
}
