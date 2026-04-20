package ru.rutcampustrack.schedule.lesson.projection;

import ru.rutcampustrack.schedule.contract.enums.LessonStatus;

import java.time.LocalDate;
import java.time.LocalTime;

/**
 * M05 Группа 2 (reference pattern для NEW-143).
 *
 * <p>Spring Data interface-based projection: вместо loading {@code Lesson} +
 * отдельный {@code findById(scheduleItemId)} для ScheduleItem (2 round-trips),
 * repository делает один JPQL/native JOIN и возвращает тонкий subset полей.
 *
 * <p>Используется для mobile single-detail экрана, где клиенту нужно
 * отобразить карточку пары без всего entity. Экономит bytes на проводе и
 * один roundtrip к БД.
 *
 * <p>Convention: proj interface = suffix {@code Projection} + лежит в пакете
 * {@code {domain}/projection/}. Все getter'ы primitive / value types, без
 * связанных entity — чтобы Spring Data не триггерил lazy loads.
 *
 * <p>NEW-143 ArchUnit rule whitelist'ит repository-методы, возвращающие
 * тип с именем, заканчивающимся на {@code Projection}.
 */
public interface LessonDetailsProjection {

    Long getId();

    LocalDate getDate();

    LessonStatus getStatus();

    boolean getIsBlockedByHeadman();

    Long getGroupId();

    Long getSubjectId();

    Short getLessonNumber();

    LocalTime getStartTime();

    LocalTime getEndTime();

    String getRoom();
}
