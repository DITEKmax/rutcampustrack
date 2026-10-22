# Независимое review: Gateway login body / pooled-buffer release

Вердикт: APPROVE для двух указанных исходников на базе `e9a5f99b4f36518549b0c677ccc618339e5494c0`. Блокирующих findings нет. Это review готового исходного пакета и ограниченного реального HTTP результата; интеграция и актуальные обязательные CI gates остаются у Root. Requested Sol6.1/high; actual model/effort UNKNOWN.

Прочитаны полный TASK-CONTRACT.md, CURRENT.md, decisions.md, questions.md, resources.md, orchestration-v2/RULES.md и применимые AGENTS/workflow. Автор — auth_body_bound_actual; worktree `/workspace/rct-worktrees/shared-validator-execution-20261013`; branch `codex/auth-body-bound-actual-20261013`. Review read-only: продукт, тесты и shared docs не изменялись; проверки/runtime/network/дочерние агенты не запускались. Создан только этот назначенный report.

Проверенный inventory:

| Файл | SHA256 | Изменение |
|---|---|---|
| services/api-gateway/src/main/java/ru/rutcampustrack/gateway/filter/LoginBodyExtractionFilter.java | `56a4423f20832942587be8bd85953a581279d27f1f72dac3e90ad75bbabb60a2` | Удалён только завершающий `.doOnDiscard(DataBuffer.class, DataBufferUtils::release)`. |
| services/api-gateway/src/test/java/ru/rutcampustrack/gateway/ratelimit/RateLimitIT.java | `c95fc94ba6b76902bea007c45db7576b49ac8fe17d34afa472c741c31d1527ee` | Один новый test method и его TCP/count helpers, local port и импорты. |

В worktree при чтении присутствовали только эти два изменения. Root сообщил final source/native/cleanup READY; последующий source commit с неизменными bytes не меняет вывод этого review.

## Buffer ownership и сохранённые границы

Независимо прочитаны class bytes реальных cached dependencies в RAM через Python standard-library ZIP/class parsing, без исполнения кода JAR и без новых копий. Spring core6.2.19 SHA `52146689e71a911c92bac26677d73c692512a739893a57710cc233591756bff6`; Reactor core3.7.19 SHA `50390c15320c578784f60ea9d4d3f7a3eff9ba90c6567f38c6cce5c9f1f69faa`; Spring web6.2.19 SHA `ca88e364ecebee61163e2260e8a20584138eca74e28a050d6a2ef6302bad2f2d`; Reactor Netty core1.2.18 SHA `2d1ff55147102d4284c6f9c59c06d4288e3a59b1921da01647fef24869cfefc3`.

`DataBufferUtils.join(Publisher,int)` для Flux уже устанавливает `doOnDiscard(DataBuffer.class, release)`. `MonoCollect.CollectSubscriber` discard-ит текущий элемент при ошибке accumulator и накопленную collection при error/cancel. Reactor `Operators.lambda$discardLocalAdapter$1` объединяет hooks через `Consumer.andThen`; второй такой hook добавляет второй release. `ReactorServerHttpRequest.getBody()` выполняет `receive().retain().map(bufferFactory.wrap)`; wrap не добавляет retain. `FluxReceive.onInboundNext` и `drainReceiver` вызывают `ReferenceCountUtil.release` после subscriber.onNext. Поэтому два Spring discard-release могут потребить retained reference и оставшуюся Netty owner reference. `NettyDataBuffer.isAllocated` проверяет общий refCnt>0, что не делает двойной release безопасным при refCnt2. Удаление дублирующего hook сохраняет штатную очистку join при превышении лимита/ошибке/отмене.

Успешно joined buffer по-прежнему освобождается в `finally` (filter:75–80); затем передаются скопированные bytes через свежий DefaultDataBuffer. Content-Length precheck и реальный `join(...,4096)` сохранены, JSON parsing идёт после bounded read. 413/problem-json/no-store сохранены. Replay bytes, Content-Length и удаление Transfer-Encoding не менялись. Исходный unit test проверяет точное byte equality и нормализацию login; текущие пять unit cases PASS.

Порядок filter -90 и JwtAuthenticationFilter -100 сохранён. JwtAuthenticationFilter по-прежнему удаляет клиентский X-Login до public-route admission; bounded extraction добавляет внутренний header. Ни маршрут `/api/auth/login`→StripPrefix→`/auth/login`, ни действующий IP rate limiter/лимиты/validation/Auth/password/session правила не меняются.

## Проверяемый результат и чувствительность

Новый test действительно обращается TCP через HttpURLConnection к RANDOM_PORT Gateway `/api/auth/login`, существующим route/GlobalFilters и реальному loopback HTTP upstream WireMock. Fixed-length и chunked4097 получают 413/problem-json; upstream request count не увеличивается после каждого отказа. Последующие valid JSON+whitespace4096 для обеих framings получают upstream401 и увеличивают count ровно на один каждый. `Connection: close` workaround отсутствует; исходный connection behavior восстановлен и проверен. Ответы потребляются, streams закрываются и connection.disconnect выполняется в finally; временный stub удаляется в finally.

Retained actual01 RED на неизменённом продукте проходил оба oversize отказа, затем падал на первом4096 getResponseCode с Connection reset; captured server exception — IllegalReferenceCountException/refCnt0. Та же test source SHA при единственной продуктовой правке проходит GREEN. Это существенный причинный regression witness; дополнительные mutations/replays не нужны.

Независимо разобраны текущие XML без вывода raw logs/bodies:

| Проверка | XML SHA256 | Результат |
|---|---|---|
| LoginBodyExtractionFilterTest | `9b08cc1119af51c5cc1d17a95b79665e33c5dc97b18c4ca3b505ecce7e5dedc0` | 5 tests, 0 failures/errors/skips |
| RateLimitIT.fixedAndChunkedLoginBodiesOver4096AreRejectedBeforeUpstreamHttp | `a7e9be37dc588edade1cff29b8b52a47e9b6771e72c64e111dfd77132e700321` | 1 selected test, 0 failures/errors/skips |

Captured XML system-out/system-err не содержат IllegalReferenceCountException. Author outcome и green-actual01 terminal receipt фиксируют native exit0, точные before/after source SHAs, процессы reaped, контейнеры settled, TMP removed; container receipt active_owned_ids пуст. Root отдельно подтвердил final cleanup. Форматирование и повторная formatter invocation сохранили оба source SHA.

Privacy: тест использует только синтетические credentials; count assertions избегают WireMock verify failure body dump, response bytes уходят в nullOutputStream. Новые assertions выводят только status/content-type/count. Продуктовые log/error handlers этой правкой не расширены. Review вывел только разрешённые metadata/finite outcomes, без raw XML, request/response bodies или credentials.

## Предел принятия

WireMock — настоящий HTTP upstream witness, а 401 — его контролируемый ответ. Результат доказывает Gateway rejection/forwarding и исправление указанного pooled ownership сбоя; он не доказывает genuine Auth/password/session acceptance, production/public reachability/TLS/browser или полноту P11/Q03/Q13. Прямой Auth4096 policy не принят; актуальная достижимость прямого Auth остаётся UNKNOWN. B013 frozen PLATFORM_BLOCKED не затрагивался, обходов нет. Current CI и интеграция ещё не приняты этим review.

Источник runtime evidence: `/workspace/scratch/rct-validation/auth_body_bound_actual/outcome.safe.json`, `green-actual01/terminal.safe.json`, текущие два XML в worktree `services/api-gateway/build/test-results/`. Report не заменяет сохранение достаточного redacted evidence Root в назначенной ветке/PR.
