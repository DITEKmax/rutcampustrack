# Independent security classification — native GEO intent digest

Вердикт: **одна находка false positive — публичный SHA256 digest идентичности команды, не сырой key/credential/capability**. Generic rule следует сохранить. Это классификация указанного finding, не scanner/security/runtime PASS.

Дата: 2026-10-24. Исходный pinned Gitleaks8.22.1 staged scan FAIL1 и metadata переданы root; reviewer сканер не повторял. Finding: `generic-api-key`, staged/Commit empty, `docs/validation/web-vue-pr3/checkpoints/native-geo-20261024/native-ui-summary.json:28`, поле `commands[0].keySha256`, entropy3.8889. Exact staged fingerprint:

```text
docs/validation/web-vue-pr3/checkpoints/native-geo-20261024/native-ui-summary.json:generic-api-key:28
```

## Детерминированное происхождение

- `/tmp/student-natural-geo64.cjs:37` захватывает `Idempotency-Key` из header реального owned command в private capture, для записи которого source задаёт `mode:0o600`. Затем public `report.commands` строится явным `map` только из path/method/parsed body/**keySha256**/capturedAt; raw `key` и исходный command object туда не spread-копируются.
- Это поле вычисляется именно `crypto.createHash('sha256').update(c.key ?? '').digest('hex')`. Алгоритм, hex encoding, источник header, явная public projection и запись mode0600 независимо проверены статически без вывода литералов или содержимого key. SHA256 reviewed source script: `a4175420594b16f6c2204be6100178e8065c2fb7a126025178e50c5363a66c69`.
- Значение finding независимо проверено как ровно64 lowercase hex chars. Staged public JSON и `/tmp/student-web-natural-geo64/summary.json` во время проверки побайтно совпадали; их общий source SHA256: `c6aff338fc4c6af6e9064a149b1f319e55aeb0f0ddde6a82b976f3a2f1f832dc`. Parsed JSON и конкретный digest тоже совпали. Оба исходника тогда не имели final LF.
- Source использует digest как диагностическое свидетельство равенства intent, не отправляет его как Authorization, request key или download URL. Это output криптографического хеша, а не оригинальный header. Private raw key/capture не читался, поэтому reviewer не заявляет независимый пересчёт SHA256 из сырого key или фактическую проверку permissions private файла. Доказательство здесь — точный producer expression, artifact identity и format/projection.

## Рекомендуемая публичная проекция

Предпочтителен семантически явный structured carrier, например `commands[].idempotencyDigest = { algorithm: "SHA-256", encoding: "hex", value: <тот же digest> }`, без raw key. Сохранить все прочие допустимые public summary данные и добавить provenance envelope с SHA256 **исходных неизменённых bytes** summary. Указать projection version и применённое преобразование; EOF normalization относится к новой публичной проекции и не меняет source hash оригинала.

Это документирует тип значения и устраняет вводящее в заблуждение key-поле, сохраняя смысл и возможность проверить соответствие original source. Root может отдельно сверить, что digest value после преобразования точно равен исходному, остальные semantic поля равны и original private artifact hash не изменился. Исходный finding value в этом отчёте не печатается; raw credential/key добавлять в envelope нельзя. Само переименование/структура не считаются новым scanner PASS: после конкретной root projection нужен адресный обычный gate.

Альтернатива при необходимости сохранить exact public schema — адресное исключение **только указанного одного fingerprint** после привязки к этим artifact/source hashes и данному producer expression. Его недостаток: staged fingerprint зависит от строки и Commit empty, поэтому перенос/изменение carrier требует новой проверки; исключение не выражает тип digest читателю. Broad path ignore, отключение `generic-api-key`, entropy threshold relaxation или исключение всех `keySha256`/64hex недопустимы и этим review не рекомендуются.

## Пределы

Никакие private raw-key/capture files, credentials или secret contents не читались и не печатались. Reviewer не выполнял actual GEO/runtime, HTTP/browser/DB, scanner, rules/ignore/product edits. Сохранённый первый FAIL остаётся evidence исходного запуска; root самостоятельно владеет public projection/EOF envelope и последующим scan. Единственная запись reviewer — этот внешний immutable classification report. Назначение — независимое Sol6.1/high review; runtime model telemetry недоступна.
