package ru.rutcampustrack.schedule.contract.dto.lesson;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.util.List;
import org.springframework.hateoas.EntityModel;
import org.springframework.hateoas.PagedModel;

/** Existing HAL page, optionally accompanied by server-owned full-semester counts. */
public class LessonListResponse extends PagedModel<EntityModel<LessonResponse>> {
    private final SemesterProgress semesterProgress;

    public LessonListResponse(
            PagedModel<EntityModel<LessonResponse>> page, SemesterProgress progress) {
        super(page.getContent(), page.getMetadata(), page.getLinks());
        semesterProgress = progress;
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public SemesterProgress getSemesterProgress() {
        return semesterProgress;
    }

    public record SemesterProgress(long semesterId, Instant generatedAt, List<TypeProgress> items) {
        public SemesterProgress {
            if (semesterId <= 0 || generatedAt == null || items == null)
                throw new IllegalArgumentException("Invalid semester progress");
            items = List.copyOf(items);
        }
    }

    public record TypeProgress(long subjectId, String lessonType, long done, long total) {
        public TypeProgress {
            if (subjectId <= 0 || lessonType == null || done < 0 || total < done)
                throw new IllegalArgumentException("Invalid subject progress");
        }
    }
}
