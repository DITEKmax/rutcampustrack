package ru.rutcampustrack.academic.exportjobs;

/** Fixed report producer, never accepts a caller URL or arbitrary dispatch action. */
public interface ReportJobRenderer {
    ExportJobService.GeneratedFile render(ExportJobRepository.Job job);
}
