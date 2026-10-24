# Shipping preparation — actual image checkpoint

Source `a81394473a19a72cca574d7d63ce79b053aaaa11`. Функциональные показатели
остаются115/117 implemented и110/117 locally verified; сборка образа и число
кадров не увеличивают117 действий. Angular ещё активен: это подготовка Vue
поставки и проверка её изменённых входов, а не завершение замены.

- Exact preset catalog вынесен в shared без изменения12 IDs/labels/metadata;
  Vue больше не импортирует Angular source. Старая копия остаётся до удаления.
- WEB image использует canonical workspace lock/manifests/shared sources,
  строгие Vue types/Vite и root static nginx. PWA/TMA Docker COPY дополнены
  четвёртым workspace manifest/shared foundation. Existing Push source/worker/
  registration/logout и prefixes `/app/`, `/mini-app/` сохранены.
- Optional BuildKit CA-secret доступен только network RUN/npm ci с включённым
  TLS. При отсутствии secret используется обычное TLS-verified npm ci. CA не
  копируется в слои и не сохраняется runtime ENV.
- Все три настоящих образа собраны локально. WEB12 static checks PASS:
  shell/deep links no-store, missingJS/CSS/worker404, MIME и byte identity
  build/container/HTTP, original FigmaPNG200 с точным hash. PWA6/TMA4 адресных
  static checks PASS, nginx -t PASS; PWA sw/sw-assets/version no-store,
  version.latest=`0.1.0-pr3.a81394473`, TMA worker не добавлен.
- Нормализация чтения только public dist внутри конечного nginx image
  исправляет настоящий initial403: исходный восстановленный PNG имел0600.
  Байтам/хешу соответствовал оригинал; worker не мог читать файл. Первоначальная
  ошибка сохранена, проверки HTTP/hash/cache не ослаблены.

[Actual receipts](../../validation/web-vue-pr3/checkpoints/shipping-20261024/)
и [independent source reviews](../../validation/web-vue-pr3/review/manifest.json)
содержат точные image IDs, source revisions и пределы. Изолированные static
контейнеры остановлены; compiled images сохранены. Outer production API/PWA/TMA
routing и Angular image/oracle/CI/deploy wiring этим smoke не переключались.
Реальная WebPush delivery/установка PWA/signed Telegram не заявлены этим тестом.

## Инцидент места и восстановление

Сборки через текущий Docker `vfs` израсходовали свободное место: полные копии
промежуточных snapshots значительно больше логических404MB build-cache stats.
`/workspace` достиг0 доступных байт; четыре main healthcheck стали unhealthy.
Root недооценил этот расход. Clock stage1 остановился дисковым guard **до**
Gradle/prepare/containers; это не automatic approval rejection и не runtime PASS.

Независимо проверен manifest31 собственных unused/unshared shipping cache IDs
с точными CreatedAt/descriptions/родительскими связями и saved build logs.
Удалены30 индивидуальными exact-ID cache filters, child→parent; один сохранён.
Никаких global prune, image/container/volume/source/WIP удалений. Внешние cache
refs и все IDs/images/mountsets контейнеров подтверждены неизменными. Main13
восстановились healthy без restart после первого освобождения места. Финально
доступно10,12GiB, вместо0. Receipts/ограничения — [disk-recovery](../../validation/web-vue-pr3/checkpoints/shipping-20261024/disk-recovery.json).

На будущие VFS-сборки нужен запас под полные копии и адресное освобождение
только созданных этой задачей unused cache IDs после верификации. Логический
Docker system df не заменяет проверку реального statvfs свободного места.

## Следующие глобальные gates

Удаление Angular после замены оставшихся возможностей, переключение compose/
artifact oracle/активных browser controls и проверка настоящего outer edge.
CI/Coverage/Security/OpenAPI остаются только workflow_dispatch/PAUSED;
восстановление trigger/run, merge/deploy/production не выполнялись.

## Clock stage1 после освобождения места

Offline fixtureJar/control native0, immutable preparation0, official recursive
five-input isolation0 и actual Compose config0. Infra startup затем native1:
новый PostgreSQL не прочитал bound init-script0600, Mongo early exit пока без
доказанного fatal (invalid base64 RS-key отдельно подтверждён), Rabbit health
CLI timeout при0.1CPU. Main13 остаются SAMECID/healthy/no-clock. Snapshot,
restore, apps и browser не запускались. Подготовляется bounded source correction
и независимое review; это stage FAIL, не положительная GEO64 и не blocker,
требующий нового продуктового решения.

## Test edge source wiring

Original05e08a2e3 → root3fc1ec247 adds internal Vue web-nginx to e2e Compose
and proxies the origin root/full URI to it. Existing API/WS/upload/privacy/TLS
and mobile prefix blocks are byte-identical. Actual compose config--quiet0
and independent source review PASS; no imagebuild/up/CI/outeredge runtime was
run for this delta. Legacy Angular browser controls and production wiring remain
explicit global delivery gates.

## Current clock readiness / permission boundary

Own5 infra genuinely healthy at0b941667 after reviewed corrections. Fixture/control,
prepare, official recursive isolation and actual Compose config native0. Final
MongoCPU-only change retains the same CID/memory/env/healthtest/5s timeout; probes
now1.0–1.6s. Main13 SAMECID/images/mountsets healthy and unmodified.
Phase2 stop/privateDBsnapshot was rejected by automatic approval BEFORE execution;
no main stop/dump/import/apps/browser. Q-ENV-005 asks explicit permission for the
exact local script; snapshotflagFALSE, no alternate executor. This does not
change115/117 implemented or110/117 verified.


Served-artifact oracle original8b8f3c7ac→root4851387b7 now requires exact Vue
#app and a single hashed root/assets module. Revision/image pin, status/MIME,
HTML/module bytes and path bounds remain strict. Author8 unittest PASS;
independent source review PASS. No new outeredge/container/deploy execution is
claimed. Existing actual innerstatic12checks at UIa813 remain separate evidence.


Vue browser adapters original0d30f35ce+correction6ff8874ad→root0d37bc982/
576faf38b: four bounded fixtures now use current auth/role/nav/landmark and six
ADMIN/TEACHER data→DOM oracles. Unsupported legacy intent explicitly throws
PENDING_VUE before browser actions, never a green skip. Existing finitecontrol
ran once native0 (10positive/22rejected, browserProoffalse); scopedstrictTS0.
Independent initialreview found2P2 (nestedpasswordlabel/compactrolecarrier),
correction-onlyauth.ts passednarrowrecheck. Both reports retained. No browser,
backendwrites, rolegrants or productchange; accepted115/110counts unchanged.
Further existingusers/role/authspec migration is separately owned and pending;
admincreate/studentexcuse/retired410carriers remain explicit later scope.


Current acceptance update05:47: nativeGEO passed in naturalMAIN window and
independentpostguard; QENV005stop/privateDBsnapshot no longer required and was
neverexecuted. Ownclock5infrahealthyidle. TinyStudentTrend wording43b7 source
andreadonlyruntimePASS are newer thanimagesa813; no newimagebuild claimed.
Users/role/auth9filepacket e7bb→rootc19 reviewedPASS: nonemptyprivateconfig
selection only3testMatchchanges, wrappers/privacyguards retained; finitecontrol
19positive/47rejected andscopedTS0. --list collection isnotbrowserPASS. No runtime/
backendwrites. Remainingadmincreate/studentexcuselegacy testmigration separate.


Final source-only adapter checkpoint: bb7074+c1ec+eb682→root03759/adbec/3068
passed independent paired review after a PNG CRC finding. Two current specs
preserve private/disposable guards, exactownedcreate201/ephemeralcredential
admission and currentEXCUSE200/PENDING/file/readback. Seven relative has scopes
retain ownedouter boundaries. Finite19/47 and scopedTS PASS; staticaboutblank
probe7 old0/newowned1/foreign0/network0 is explicitlynotSUT/Vueproof. Diagnostic
sandbox/temporarytablefixture failures retained. Borrowed identicalmobilePNG CRC
fixed4042→eca7, independentlyRootverified allchunks/pixels. No mobilefeature/API
change. Actual2scenario runtime andexactlegacyMultipart410IT remain unclaimed.
See final source receipt; all115/111actioncounts unchanged by test migration.
