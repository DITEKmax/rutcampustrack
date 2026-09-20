# H74 — поправка к утверждению о текущих Academic locks

Датированное дополнение к frozen v3 PROPOSAL.md SHA836D567E86F868102819DACD283ECFFA0AC068B308C5A5643BC44A3C7109E0A0; сам reviewed artifact/sources/checks не изменены. Решение главного: `.agent/orchestration-v2/LESSONS-L5B-DESIGN-ACCEPTED-V3.md`, раздел «H74 evidence correction after frozen review».

**Отозвано** утверждение §4.2.1 frozen proposal о подтверждённом текущем assignTeacher↔addTeacher deadlock через FK subject. Статический вывод предполагал SQL FOR UPDATE, но H74 на неизменённом f59 фактически показал FOR NO KEY UPDATE для semester и subject. FK KEY SHARE совместим с этим режимом; предполагаемая цепочка ожидания в данном сценарии не возникла. Не превращать JPA PESSIMISTIC_WRITE в утверждение о конкретном SQL lock без проверки dialect/runtime.

H74: actual exit0, один реальный PostgreSQL/MockMvc test, обе REST операции HTTP201, exact2rows; test SHA B3F4A942F3EAFBF27FB56B7E95EA5B9A5EF0D8CE6EA8B25406F235D007B2E10E, SubjectService SHA377BF9B1E2F6E2B88529DD362177F0C47318D7A6C31561C4E516FA89491AFFA3, baseline f59b3b9951c971bde42265ae67df8754dc594a58. H72 был harness failure до реального semester query и не является воспроизведением deadlock. H73 post-fix отменён; продуктовая коррекция по этому предположению не нужна.

Evidence в `C:/Users/maksd/IntelliJIDEA/rutcampustrack/.agent/orchestration-v2/evidence/`:

- h74-context.json SHA DCE2AB4479620195A9E2FC6E3944AB897ACD08DC73C98F71AC6ADD7087E85AE4: команда, revision, actual exit0.
- h74-AssignmentAuthorityIT.xml SHA3787718C3807F87BB40D272852E1034338C2C4DEFC1B0A8535E564F8B90E8F5A: 1/0/0; строки115–117 содержат фактический FOR NO KEY UPDATE.
- h74-owned-cleanup.json: оба exact PostgreSQL/Ryuk IDs отсутствуют по независимой проверке главного.

Будущий общий порядок блокировок L5B и его проверки остаются design requirement: новые close/binding writers ещё не реализованы. Этот PASS не доказывает отсутствие всех возможных deadlocks и не разрешает L5B activation. Нельзя искусственно усилить test lock до FOR UPDATE ради получения ожидавшегося падения. Сохранённый meaningful concurrency test направлен на отдельное независимое test-only review.

Lead независимо прочитал context/XML/cleanup и root addendum; собственных runtime запусков не выполнял.
