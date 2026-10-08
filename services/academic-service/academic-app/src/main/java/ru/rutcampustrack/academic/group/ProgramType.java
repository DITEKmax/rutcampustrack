package ru.rutcampustrack.academic.group;

import ru.rutcampustrack.academic.contract.dto.group.EducationLevel;
import ru.rutcampustrack.academic.contract.dto.group.StudyMode;

/**
 * Вторая цифра кода: 1–3 бакалавриат, 4–6 специалитет, 7–9 магистратура;
 * в каждой тройке очная, очно-заочная и заочная формы соответственно.
 * Срок задаётся явно; только исторические типы 1/7 имеют прежний fallback 4/2.
 */
public enum ProgramType {

    BACHELOR(1, 4),
    BACHELOR_PART_TIME(2, null),
    BACHELOR_EXTRAMURAL(3, null),
    SPECIALIST(4, null),
    SPECIALIST_PART_TIME(5, null),
    SPECIALIST_EXTRAMURAL(6, null),
    MASTER(7, 2),
    MASTER_PART_TIME(8, null),
    MASTER_EXTRAMURAL(9, null);

    private final int digit;
    private final Integer legacyDurationYears;

    ProgramType(int digit, Integer legacyDurationYears) {
        this.digit = digit;
        this.legacyDurationYears = legacyDurationYears;
    }

    public int getDigit() {
        return digit;
    }

    /** Compatibility duration; new types have no inferred duration. */
    public int getMaxCourse() {
        if (legacyDurationYears == null) {
            throw new GroupCodeRules.InvalidCodeException("trainingDurationYears",
                    "Для этого типа программы срок обучения должен быть задан явно");
        }
        return legacyDurationYears;
    }

    public StudyMode getStudyMode() {
        return switch ((digit - 1) % 3) {
            case 0 -> StudyMode.FULL_TIME;
            case 1 -> StudyMode.PART_TIME;
            default -> StudyMode.EXTRAMURAL;
        };
    }

    public EducationLevel getEducationLevel() {
        return digit <= 3 ? EducationLevel.BACHELOR
                : digit <= 6 ? EducationLevel.SPECIALIST : EducationLevel.MASTER;
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
