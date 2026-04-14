package ru.rutcampustrack.academic.group;

/**
 * Тип образовательной программы, закодированный средней цифрой в имени группы
 * {@code ХХ(х)-<course><type><number>}.
 *
 * <p>BUG-006-6 / план 58-06:
 * <ul>
 *   <li>{@link #BACHELOR} — цифра 1, курсы 1..4.</li>
 *   <li>{@link #MASTER} — цифра 7, курсы 1..2.</li>
 * </ul>
 * Каталог расширяется добавлением новой константы. Неизвестный тип → {@link UnknownProgramTypeException}.
 */
public enum ProgramType {

    BACHELOR(1, 4),
    MASTER(7, 2);

    private final int digit;
    private final int maxCourse;

    ProgramType(int digit, int maxCourse) {
        this.digit = digit;
        this.maxCourse = maxCourse;
    }

    public int getDigit() {
        return digit;
    }

    public int getMaxCourse() {
        return maxCourse;
    }

    /**
     * Найти тип программы по цифре в имени группы.
     *
     * @throws UnknownProgramTypeException если цифра не соответствует ни одной константе enum.
     */
    public static ProgramType fromDigit(int digit) {
        for (ProgramType type : values()) {
            if (type.digit == digit) {
                return type;
            }
        }
        throw new UnknownProgramTypeException(digit);
    }
}
