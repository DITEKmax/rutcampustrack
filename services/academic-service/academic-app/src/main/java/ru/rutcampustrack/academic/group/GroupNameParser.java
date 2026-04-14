package ru.rutcampustrack.academic.group;

import org.springframework.stereotype.Component;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Парсер активного имени группы {@code ХХ(х)-<course><type><number>}.
 *
 * <p>BUG-006-6 / план 58-06. Архивный формат ({@code <active> (выпуск YYYY)}) НЕ
 * принимается парсером: архивные группы не участвуют в промоушене/создании.
 *
 * <p>Примеры:
 * <ul>
 *   <li>{@code УИТ-311} → prefix=«УИТ», course=3, type=1, number=1.</li>
 *   <li>{@code УВПв-511} → prefix=«УВПв», course=5, type=1, number=1.</li>
 *   <li>{@code УИТ-171} → prefix=«УИТ», course=1, type=7 (магистратура), number=1.</li>
 * </ul>
 */
@Component
public class GroupNameParser {

    /** 1-й префикс (1 большая + 1..3 кириллицы), затем 3 цифры: курс, тип, номер. */
    private static final Pattern NAME_RE =
            Pattern.compile("^([А-ЯЁ][А-ЯЁа-яё]{1,3})-(\\d)(\\d)(\\d)$");

    /**
     * Разобранное активное имя группы.
     */
    public record ParsedName(String prefix, int course, int type, int number) {
        /** Собрать обратно в активное имя группы. */
        public String toActive() {
            return prefix + "-" + course + type + number;
        }
    }

    /** Результат {@link #promote(String, ProgramType)}: либо переименование, либо архивация. */
    public sealed interface PromoteResult permits PromoteResult.Renamed, PromoteResult.Archived {
        record Renamed(String newName) implements PromoteResult {}
        record Archived() implements PromoteResult {}
    }

    /**
     * Распарсить активное имя группы.
     *
     * @throws IllegalArgumentException если имя не соответствует активному формату
     *                                  (в том числе для архивных имён с суффиксом).
     */
    public ParsedName parse(String name) {
        if (name == null) {
            throw new IllegalArgumentException("Group name is null");
        }
        Matcher m = NAME_RE.matcher(name);
        if (!m.matches()) {
            throw new IllegalArgumentException("Invalid active group name: " + name);
        }
        return new ParsedName(
                m.group(1),
                Integer.parseInt(m.group(2)),
                Integer.parseInt(m.group(3)),
                Integer.parseInt(m.group(4))
        );
    }

    /**
     * Вычислить результат промоушена для одиночной группы, не обращаясь к БД.
     *
     * <p>Правило: {@code course + 1} если {@code course < type.maxCourse};
     * {@link PromoteResult.Archived} если {@code course == type.maxCourse}.
     */
    public PromoteResult promote(String name, ProgramType type) {
        ParsedName p = parse(name);
        if (p.course() >= type.getMaxCourse()) {
            return new PromoteResult.Archived();
        }
        ParsedName next = new ParsedName(p.prefix(), p.course() + 1, p.type(), p.number());
        return new PromoteResult.Renamed(next.toActive());
    }
}
