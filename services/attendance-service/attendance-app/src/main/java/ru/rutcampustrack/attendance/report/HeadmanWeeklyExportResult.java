package ru.rutcampustrack.attendance.report;

public record HeadmanWeeklyExportResult(
        String fileName,
        String contentType,
        byte[] content
) {}
