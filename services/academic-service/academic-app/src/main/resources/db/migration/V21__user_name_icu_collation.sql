-- Cyrillic-aware ICU collation for user name sorting (BUG: students appeared
-- in heap-scan order, "Ё" landed at end with default C-locale).
-- Used by UserRepository ORDER BY ... COLLATE "ru_icu" — see
-- findByGroupId, findByRole, findByGroupIdAndRole.
--
-- ICU is bundled with postgres:16 Debian image. CREATE COLLATION is
-- DDL-but-transactional, so no -- ## marker needed.

CREATE COLLATION IF NOT EXISTS ru_icu (provider = icu, locale = 'ru-RU');
