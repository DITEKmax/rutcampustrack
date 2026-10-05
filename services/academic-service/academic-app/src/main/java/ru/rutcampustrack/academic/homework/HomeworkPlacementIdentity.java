package ru.rutcampustrack.academic.homework;

import ru.rutcampustrack.academic.entity.Homework;
import ru.rutcampustrack.academic.exception.ConflictException;
import ru.rutcampustrack.schedule.grpc.HomeworkBindingResponse;

/** Validates an existing authoritative RPC result; performs no RPC or lookup. */
final class HomeworkPlacementIdentity {
    private HomeworkPlacementIdentity() { }

    static java.util.Map<String, Object> fields(HomeworkBindingResponse binding) {
        java.util.Map<String, Object> result = new java.util.LinkedHashMap<>();
        result.put("placement_occurrence_id", "DATE".equals(binding.getBindingMode()) ? null : binding.getOccurrenceId());
        result.put("placement_lesson_id", "DATE".equals(binding.getBindingMode()) ? null : binding.getCurrentLesson().getLessonId());
        result.put("placement_binding_revision", binding.getRevision());
        return result;
    }

    static void accept(Homework homework, HomeworkBindingResponse binding) {
        if (binding == null) return; // Existing fixture-only callers have no authority; keep unknown IDs.
        if (binding.getBindingId() != homework.getBindingId() || !binding.hasHomeworkId()
                || binding.getHomeworkId() != homework.getId() || binding.getGroupId() != homework.getGroupId()
                || binding.getSubjectId() != homework.getSubjectId() || binding.getSemesterId() != homework.getSemesterId()
                || binding.getRevision() <= 0) throw new ConflictException("accepted binding identity differs from Academic content");
        if (homework.getPlacementBindingRevision() != null
                && binding.getRevision() < homework.getPlacementBindingRevision()) return;
        boolean date = "DATE".equals(binding.getBindingMode());
        if (!binding.getBindingMode().equals(homework.getBindingMode().name())
                || !binding.getDate().equals(homework.getLessonDate().toString())
                || binding.getLessonNumber() != (homework.getLessonNumber() == null ? 0 : homework.getLessonNumber())
                || date && (binding.getOccurrenceId() != 0 || binding.hasCurrentLesson())
                || !date && (binding.getOccurrenceId() <= 0 || !binding.hasCurrentLesson()
                    || binding.getCurrentLesson().getLessonId() <= 0
                    || binding.getCurrentLesson().getOccurrenceId() != binding.getOccurrenceId()
                    || binding.getCurrentLesson().getGroupId() != homework.getGroupId()
                    || binding.getCurrentLesson().getSubjectId() != homework.getSubjectId()
                    || binding.getCurrentLesson().getSemesterId() != homework.getSemesterId()
                    || !binding.getCurrentLesson().getDate().equals(binding.getDate())
                    || binding.getCurrentLesson().getLessonNumber() != binding.getLessonNumber())) {
            throw new ConflictException("accepted binding placement differs from Academic content");
        }
        try {
            homework.acceptPlacementIdentity(date ? null : binding.getOccurrenceId(),
                    date ? null : binding.getCurrentLesson().getLessonId(), binding.getRevision());
        } catch (IllegalArgumentException invalid) { throw new ConflictException(invalid.getMessage()); }
    }
}
