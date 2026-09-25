ALTER TABLE semesters
    ADD COLUMN semester_type VARCHAR(8);

ALTER TABLE semesters
    ADD COLUMN academic_year INTEGER;

ALTER TABLE semesters
    ADD CONSTRAINT semesters_semester_type_check
        CHECK (semester_type IS NULL OR semester_type IN ('AUTUMN', 'SPRING'));

ALTER TABLE semesters
    ADD CONSTRAINT semesters_academic_year_check
        CHECK (
            (semester_type IS NULL AND academic_year IS NULL)
            OR (semester_type IS NOT NULL AND academic_year IS NOT NULL AND academic_year BETWEEN 1 AND 9998)
        );
