# Решение источников заявок — 07.09.2026

Fresh Sol xhigh `excuse_source_decision`; root принял решение для текущего student PWA+TMA contract. Это source resolution, не implementation review/PASS. Оригиналы ниже открыты root; поздний финальный board имеет приоритет по решению владельца.

## Причины и механизмы

Final `4781-164.txt:121–145` отменяет старый перечень в canonical job-stories:142–150,714–717 для текущего student flow. Причина отдельна от механизма и есть только у EXCUSE:

| Код | Подпись |
|---|---|
| ILLNESS | Болезнь |
| MEDICAL_EXAMINATION | Медицинское обследование |
| COMPETITION_PARTICIPATION | Участие в соревнованиях |
| FAMILY_CIRCUMSTANCES | Семейные обстоятельства |
| OTHER | Другое |

SUMMONS, UNIVERSITY_ORDER, EXEMPTION, FREE_ATTENDANCE не принимаются новым student contract; нельзя молча переименовать их в новые причины. Это не разрешение удалять данные или менять другие роли. Для OTHER сервер требует непустой `comment.strip()`; для остальных комментарий необязателен. Существующий предел1000 символов сохраняется. Нет требования обязательного файла или документов по конкретной причине.

`EXCUSE` — пакет на у; `LATE_CHECKIN` — одиночное н→+. У late-checkin origin=MANUAL либо AUTO_GEO_FAILURE. Автоматический запрос не становится ручным тикетом. Оригиналы: final4610-848937:23–51, 4610-849007:42–145, 4610-849072:26–81; существующий LateCheckinRequestOrigin.

## Eligibility и атомарность

Для у будущие/текущие PLANNED/ACTIVE разрешены без PRESENT, EXCUSED и активного EXCUSE; CLOSED требует эффективный ABSENT. CANCELLED запрещён. Пустая attendance допустима для PLANNED/ACTIVE; для CLOSED это нарушение закрытия пары, не молчаливый ABSENT. Blockage не препятствует у. Уже достигнутый EXCUSED/PRESENT нельзя отправить как новый у. Появившийся после submit PRESENT не заменяется при approval у.

Активный manual/auto late-checkin не запрещает EXCUSE. **Это консультантский вывод**, а не дословное решение владельца: R2 разделяет механизмы, источники не задают взаимного исключения, приоритет PRESENT разрешает гонку. Следствие: pending передаётся вместе с kind; один boolean недостаточен.

Пакет у неделим: непустые уникальные lessonIds своей группы, атомарная повторная валидация всех ID при submit. Любой невалидный ID отклоняет весь пакет с причиной по паре.

Ручной н→+: ровно одна CLOSED/ABSENT пара, без blockage/активного late-checkin, с положительным бюджетом. Пять подач за семестр, расход при submit атомарно, включая повтор после отказа. Отказ и отмена не возвращают попытку. AUTO и EXCUSE счётчик не меняют.

Основания: canonical job-stories:723 (future/past у),795–800 (неделимость),152,1071 (cancelled); backend-conflicts в `docs/architecture/reference-rutcampustrack-design/`:93–113 (R2),186–193,558 (R11),550–555 (приоритет +).

## Проверки будущей реализации

Parameterized eligibility states, OTHER null/blank/whitespace, unknown enum, foreign scope, package atomicity, duplicate submission, повтор после reject/cancel, пять списаний, unlimited auto/у, blockage и гонка pending late-checkin→PRESENT. Текущий ExcuseService/LateCheckinService не считается доказательством требований.

## Вложения — принятое дополнение Sol xhigh

Final hint4610-849007:139: «До 10 МБ · не более 2 файлов». Root принял bounded interpretation: 0–2 необязательных файла, каждый не более10MiB, сумма20MiB следует из этих двух границ. Registry FileUploadField:203 разделяет size/formats/count как server-provided limits; текущее application.yml:11–14 задаёт max-file-size10MiB и max-request-size12MB. Поэтому 10МБ трактуется на файл, а transport limit должен покрывать20MiB плюс multipart overhead (практический минимум21MB).

JPEG/PNG/PDF (`image/jpeg`, `image/png`, `application/pdf`; `.jpg/.jpeg/.png/.pdf`) — **консультантский технический минимум**, не дословный whitelist владельца. PDF подтверждён существующим studentRequestsApi test:108–110; shared ValidFile:24–31 уже задаёт точныеMIME/10MiB. Сервер сверяет MIME, extension и сигнатуру; клиент использует возвращённые limits для accept/prevalidation, не зашивает их. HEIC/WebP/DOCX не включаются без поддерживаемой проверки/preview и обновлённых server limits. Вложения хранятся год по R9 независимо от жизни тикета/пользователя; явное expired state.

Проверки: 2×10MiB проходят весь transport; третий/oversize/spoofedMIME/unsupported format отклоняются. Mandatory attachment по причинам не вводится. Вопрос владельцу не требуется.
