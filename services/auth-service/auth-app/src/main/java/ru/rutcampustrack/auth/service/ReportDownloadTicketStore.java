package ru.rutcampustrack.auth.service;

import java.time.Duration;
import java.util.Optional;

/** Persistence boundary for opaque report tickets and their bounded counters. */
public interface ReportDownloadTicketStore {

    boolean putIfAbsent(String ticketDigest, String serializedTicket, Duration ttl);

    Optional<String> find(String ticketDigest);

    boolean allowIssue(String sessionDigest, int maximum, Duration window);

    boolean allowRedemption(String ticketDigest, int maximum, Duration window);
}
