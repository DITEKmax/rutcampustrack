-- M05 Группа 1 (P2-10/1, DECISION D2).
--
-- Composite indexes на реальные hot queries с парой (group_id, semester_id):
--
--   1. TeacherSubjectGroupRepository.findByGroupIdAndSemesterId
--      — teacher-dashboard «все мои группы в семестре».
--      До M05: используется idx_tsg_group + in-memory filter по semester
--      (2/3 rows отбрасывается на 4 семестрах).
--
--   2. HomeworkRepository.findByGroupIdAndSemesterId
--      — homework-list «все ДЗ группы в семестре».
--      До M05: используется idx_homeworks_group_date (V13, для
--      недельных выборок), но planner падает на filter по semester.
--
-- Почему НЕ композит на users (group_id, semester_id):
--   таблица user_groups (из OWNER-ANSWERS 3695) не существует;
--   users.group_id уже покрыт idx_users_group (V1:41); UserRepository.
--   findByGroupId не фильтрует по semester. См. DECISIONS.md D2.
--
-- EXPLAIN baseline — см. docs/performance-indexes.md, Q2 и Q3.

CREATE INDEX IF NOT EXISTS idx_tsg_group_semester
    ON teacher_subject_groups (group_id, semester_id);

CREATE INDEX IF NOT EXISTS idx_hw_group_semester
    ON homeworks (group_id, semester_id);
