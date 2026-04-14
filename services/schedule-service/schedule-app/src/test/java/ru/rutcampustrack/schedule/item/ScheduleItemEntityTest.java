package ru.rutcampustrack.schedule.item;

import org.junit.jupiter.api.Test;
import ru.rutcampustrack.schedule.item.entity.ScheduleItem;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit test (no Spring context) verifying D-16:
 * ScheduleItem entity must NOT contain a teacherId field. The teacher-to-lesson
 * link is resolved at read-time via JOIN ScheduleItem × TeacherSubjectGroup.
 */
class ScheduleItemEntityTest {

    @Test
    void scheduleItem_hasNoTeacherIdField() {
        Set<String> fieldNames = Arrays.stream(ScheduleItem.class.getDeclaredFields())
                .map(Field::getName)
                .collect(Collectors.toSet());

        assertThat(fieldNames)
                .as("ScheduleItem must not have teacherId field after D-16")
                .doesNotContain("teacherId");
    }

    @Test
    void scheduleItem_stillHasCoreSlotFields() {
        Set<String> fieldNames = Arrays.stream(ScheduleItem.class.getDeclaredFields())
                .map(Field::getName)
                .collect(Collectors.toSet());

        // Sanity: the remaining core slot fields per D-16 are still present.
        assertThat(fieldNames).contains(
                "id", "groupId", "subjectId", "semesterId",
                "dayOfWeek", "lessonNumber", "startTime", "endTime",
                "weekType", "room"
        );
    }
}
