package ru.rutcampustrack.academic.group;

/**
 * Brought when the middle digit of a group name encodes a program type not registered in {@link
 * ProgramType}.
 *
 * <p>BUG-006-6 / план 58-06: расширяем каталог типов добавлением новой константы в enum {@link
 * ProgramType}. До расширения — тип «не принят», и каждый использующий сервис решает как
 * реагировать:
 *
 * <ul>
 *   <li>{@code GroupService.createGroup} → {@link BadRequestException} 400.
 *   <li>{@code GroupPromotionService} → префикс идёт в {@code conflicts[]} с {@code
 *       reason="unknown_type"} и не ломает остальные префиксы.
 * </ul>
 */
public class UnknownProgramTypeException extends RuntimeException {

    private final int digit;

    public UnknownProgramTypeException(int digit) {
        super("Unknown program type digit: " + digit);
        this.digit = digit;
    }

    public int getDigit() {
        return digit;
    }
}
