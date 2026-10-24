# Independent security triage — 3e58 test fixtures

Вердикт: **обе находки — false positive: фиксированный тестовый UUID операции, не реальные credentials**. Подтверждённого credential exposure по этим двум местам нет. Значения `Secret`/`Match` и сами литералы здесь не воспроизводятся.

Дата: 2026-10-24. Scope: только две `generic-api-key` находки pinned Gitleaks 8.22.1 из `/tmp/pr3-root-gitleaks-roles-20261024.json`, frozen commit `3e58d981fc41f802368daf9f4fe0f92694cc78c3`, файл `frontends/web-vue/src/features/headman/recovery-flows.test.ts`, строки 403 и 425. Это классификация двух результатов, не общий security/scanner PASS. Назначение — независимое Sol6.1/high review; runtime model telemetry недоступна.

## Детерминированное происхождение и назначение

- На обеих строках стоит один и тот же hardcoded строковый литерал: 36-символьный RFC4122 UUID version 4. Это поле `input.requestKey` в JSON локальной pending-operation fixture. Равенство двух литералов, UUID format/version/variant проверены программно без вывода значений.
- Строка 403 входит в тест `releases an old saved payload that typed preflight could never transmit` (начало 393): искусственный pending payload записывается через `JSON.stringify` в тестовую storage; после mount проверяются отсутствие вызовов fetcher, освобождение pending и снятие блокировки (411–414).
- Строка 425 входит в тест `retains a syntactically valid unknown operation without giving it a new key` (начало 416): искусственный JSON сохраняется как `original`, затем проверяются отсутствие вызовов fetcher и сохранность ровно этой исходной pending operation (433–436).
- Storage полностью тестовая: `MemoryStorage` на `Map` (61–81), новый экземпляр и `vi.stubGlobal` в `beforeEach` (114–119). Ни один из двух литералов не извлекается из окружения, runtime credentials, HTTP-ответа или production storage.
- `subjectApi()` (341–370) создаёт `vi.fn<typeof fetch>().mockResolvedValue(...)`, подставляет его в `HeadmanSubjectsApi({ accessToken: () => null, fetcher })` и подменяет чтения `vi.spyOn(...).mockResolvedValue(...)`. Реального bearer/token в этих fixtures нет; сетевой fetch заменён mock и в обоих соответствующих сценариях не вызывается.
- `AssignmentReplacementInput.requestKey` — доменная идентичность replacement-команды, отдельно от `accessToken`; typed helper проверяет UUID перед передачей в запрос. Поле не предоставляет аутентификацию или доступ к API. Его имя вызвало обобщённое срабатывание `generic-api-key`, но окружение и использование подтверждают fixture provenance.

## Ровно два классифицированных fingerprints

```text
3e58d981fc41f802368daf9f4fe0f92694cc78c3:frontends/web-vue/src/features/headman/recovery-flows.test.ts:generic-api-key:403
3e58d981fc41f802368daf9f4fe0f92694cc78c3:frontends/web-vue/src/features/headman/recovery-flows.test.ts:generic-api-key:425
```

Допустима только адресная обработка этих двух fingerprints согласно принятому root workflow. Классификация не распространяется на другие `requestKey`, UUID, тестовые файлы или правило `generic-api-key` в целом. Ранее рассмотренное исключение `f45b` не пересматривалось и не расширялось.

## Ограничения и действия

Прочитаны только безопасные метаданные redacted report и релевантный frozen source; при выводе source строковые литералы скрывались, UUID сравнивались внутри read-only процесса. Сканер, тесты, builds/browser/CI не запускались. Product/allowlist/config/network/processes не менялись; единственная запись — этот внешний immutable report. Root scan exit 1 остаётся фактом исходного запуска; данный triage не заменяет последующий адресный root gate.
