package ru.rutcampustrack.academic.semester;

import ru.rutcampustrack.academic.contract.dto.semester.SemesterDeletionPreviewResponse;

public final class StaleSemesterDeletionPreviewException extends RuntimeException {

    private final SemesterDeletionPreviewResponse refreshedPreview;

    public StaleSemesterDeletionPreviewException(SemesterDeletionPreviewResponse refreshedPreview) {
        super("Semester deletion preview is stale");
        this.refreshedPreview = refreshedPreview;
    }

    public SemesterDeletionPreviewResponse refreshedPreview() {
        return refreshedPreview;
    }
}
