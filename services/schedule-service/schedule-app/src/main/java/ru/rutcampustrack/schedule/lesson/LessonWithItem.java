package ru.rutcampustrack.schedule.lesson;

import ru.rutcampustrack.schedule.item.entity.ScheduleItem;
import ru.rutcampustrack.schedule.lesson.entity.Lesson;

/**
 * App-internal record pairing a concrete Lesson with its parent ScheduleItem template.
 * Used by LessonService to return both entities together, avoiding multiple lookups.
 * NOT part of the API contract — stays within the app module.
 */
public record LessonWithItem(Lesson lesson, ScheduleItem scheduleItem) {}
