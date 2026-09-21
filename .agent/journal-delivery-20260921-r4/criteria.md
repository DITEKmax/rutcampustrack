# Journal delivery r4 criteria

- Общий pair-scoped lifecycle удаляет journal attachment и attachment metadata
  при `PRESENT`/`ABSENT`/clear, включая geo, request decision, write-port,
  manual single mark и batch mark.
- `EXCUSED -> EXCUSED` без нового файла сохраняет только реально активный
  journal attachment; отсутствующий/expired blob очищает metadata fail-closed.
- Pair key отделяет journal bytes от request-owned ticket evidence; expiry не
  меняет request-owned retention.
- Report lesson response отдаёт attachment metadata только для доступного
  active blob; reason/status остаются видимыми после expiry.
- Frontend использует существующий `moscowDate` (`Europe/Moscow`), скрывает
  picker при `!canWrite`, повтор выбранного `EXCUSED` очищает после ACK/refetch,
  отдельный action сохраняет редактирование причины.
- Existing integration proves a manual -> decision -> geo sequence without
  resurrection/orphan and with request-owned evidence retained.
