package ru.rutcampustrack.academic.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;

/**
 * Study group.
 *
 * <p>BUG-006-5 / план 58-04: поля {@code name} и {@code code} слиты в одно {@code name}.
 * Активный формат: {@code ХХ(х)-NNN} (кириллица, 3 цифры) — например {@code УИТ-311}, {@code УВПв-511}.
 * Архивный формат (ставится только сервисом архивации, см. план 58-06):
 * {@code <active> (выпуск YYYY)} — например {@code УИТ-411 (выпуск 2026)}.
 * Entity-pattern разрешает оба формата.
 */
@Entity
@Table(name = "groups")
@Getter
@NoArgsConstructor
public class Group {

    /** Regex разрешает активный ИЛИ архивный формат имени группы. */
    public static final String NAME_PATTERN =
            "^[А-ЯЁ][А-ЯЁа-яё]{1,3}-\\d{3}( \\(выпуск \\d{4}\\))?$";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Setter
    @NotBlank
    @Pattern(regexp = NAME_PATTERN,
            message = "Формат имени группы: ХХ(х)-NNN (пример УИТ-311)")
    @Size(max = 32)
    @Column(name = "name", nullable = false, unique = true, length = 32)
    private String name;

    /** Split code fields introduced for the ADMIN registry. Nullable for legacy rows. */
    @Setter
    @Pattern(regexp = "^[А-ЯЁ][А-ЯЁа-яё]{1,3}$",
            message = "Буквенный код: 2–4 кириллических символа")
    @Size(max = 4)
    @Column(name = "alphabetic_code", length = 4)
    private String alphabeticCode;

    @Setter
    @Pattern(regexp = "^\\d{3}$", message = "Цифровой код должен содержать 3 цифры")
    @Column(name = "numeric_code", length = 3)
    private String numericCode;

    /** First digit of numericCode; null only for an unreadable legacy name. */
    @Setter
    @Min(1)
    @Max(9)
    @Column(name = "current_course")
    private Integer currentCourse;

    /** Null for legacy rows whose programme type cannot establish a duration. */
    @Setter
    @Min(1)
    @Column(name = "training_duration_years")
    private Integer trainingDurationYears;

    /** KNOWN for canonical/new data, LEGACY_UNKNOWN when duration is not derivable. */
    @Setter
    @Column(name = "duration_status", nullable = false, length = 24)
    private String durationStatus = "LEGACY_UNKNOWN";

    @Setter
    @Column(name = "is_active", nullable = false)
    private boolean isActive;

    @Setter
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    /**
     * Заполняется сервисом архивации (BUG-006-6 / план 58-06) одновременно с {@code is_active=false}.
     * {@code NULL} для активных групп и исторических записей, созданных до V9.
     */
    @Setter
    @Column(name = "archived_at")
    private OffsetDateTime archivedAt;
}
