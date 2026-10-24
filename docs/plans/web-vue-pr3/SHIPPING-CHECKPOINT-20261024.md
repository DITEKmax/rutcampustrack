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
