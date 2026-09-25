package ru.rutcampustrack.attendance.report;

public record HeadmanStatsExportResult(String fileName, String contentType, byte[] content) {
}
