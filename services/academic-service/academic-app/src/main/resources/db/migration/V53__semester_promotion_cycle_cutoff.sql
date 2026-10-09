-- Preserve the original completed spring promotion cutoff before an archived
-- semester metadata extension. Existing rows retain their current date_to fallback.
ALTER TABLE semesters ADD COLUMN promotion_cycle_date_to DATE;
