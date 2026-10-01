# Вложения заявок — source-ready, 2026-10-01

Назначенный sole writer: admin_recovery_sol61, Sol6.1/high, S3 access/lifecycle. WT admin-group-promotion-20260927; перед изменениями normal merge main a6fd83e8 выполнен в 2bba9585. Собственные и чужие evidence сохранены. Backend и API/права не изменены.

Принятые источники: JS-STUDENT-27 и JS-STUDENT-WEB-07 в `docs/research/reference-rutcampustrack-design/knowledge/job-stories.md`; AttachmentList в `docs/design/COMPONENT_REGISTRY.md`: имя/размер/«Открыть», ошибка внутри списка без исчезновения карточки. Авторский picker/preview/remove, проверка metadata limits, authenticated open и lifecycle уже существуют; их не переписывали.

## Конкретное исправление

Старосте доступны «Открыть» (существующий изолированный popup helper) и «Скачать» через существующий authenticated blob API. Поздний blob не публикуется после collapse, изменения списка/API/offline/permissions или unmount. Все созданные object URL имеют таймер 60 секунд и немедленную очистку при смене контекста/ошибке/teardown; ожидающий popup закрывается. Ошибки 401/403 запрещают повторные действия с файлами текущей заявки до обновления списка, 404/410 делают конкретное вложение недоступным. 5xx/сеть допускают повтор. EXPIRED/прошедший expiresAt показывает текст об истёкшем сроке. Ошибка details видна также в архиве. Cached details очищаются при загрузке нового списка; поздние details не могут восстановить их.

Права остаются серверными: client GET `/api/attendance/requests/{requestId}/attachments/{attachmentId}`; generation-bound client проверяет session owner после blob response. Headman authority на сервере — текущий староста своей группы либо помощник с MANAGE_EXCUSES. Frontend повторяет существующее условие MANAGE_EXCUSES для локального отключения и очистки; дополнительных прав не вводит.

Фрагмент download helper:

```ts
const blob = await deps.download()
if (!isCurrent()) return
objectUrl = deps.createObjectUrl(blob)
if (!isCurrent()) return
deps.save(objectUrl)
// URL либо передан владельцу bounded cleanup, либо освобождён в finally.
```

## Точный inventory

Изменены:
- `frontends/mobile-core/src/features/headman-requests/HeadmanRequestsScreen.vue`
- `frontends/mobile-core/src/features/requests/request-attachment-action.ts`
- `frontends/mobile-core/src/features/requests/request-attachment-action.test.ts`

Создан: `.agent/evidence/request-attachments-20261001.md`. Удалённых файлов нет. StudentFeatureOwner/App/auth/SW/offline/maps/contracts/generated/config/lockfiles/backend не изменены.

## Проверки

Из `frontends`:
- `node node_modules/vitest/vitest.mjs run mobile-core/src/features/requests/request-attachment-action.test.ts mobile-core/src/features/requests/request-attachment-preview.test.ts mobile-core/src/features/requests/attachment-validation.test.ts` — exit0, 43/43 PASS. Новые meaningful download cases: owner/generation/dispose при позднем ответе, invalidate при allocation, exception при save и release, 403/404/410/503, поздний denial. Существующие open/preview/validation cases PASS.
- `npm run typecheck --workspace @rct/pwa-vue` — exit0 после итогового изменения Vue.
- scoped ESLint всех трёх файлов с `--max-warnings=0` и отключением только трёх форматных правил `vue/max-attributes-per-line`, `vue/singleline-html-element-content-newline`, `vue/html-indent` — exit0. Полный строгий ESLint этих файлов FAIL: 141 warnings, 0 errors, включая старое форматирование всего HeadmanRequestsScreen. Форматирование всего экрана не входит в функциональную правку; semantic rules не отключались.
- `git diff --check -- <три продуктовых файла>` — exit0.

Docker/Gradle/общий build и серверные тесты не запускались. Независимое S3 review и browser/runtime acceptance выполняет root на общем стенде; source checks не считаются runtime PASS.

## Простой browser acceptance path

1. На disposable fixture студент открывает «Заявки» → новая уважительная заявка для доступной пары → JPEG/PNG/PDF. Проверить имя/размер/preview, удалить и снова выбрать файл; отправить. Автор раскрывает созданную заявку и открывает вложение; сравнить содержимое.
2. Текущий староста той же группы открывает «Заявки группы» → созданная заявка → детали → «Открыть», затем «Скачать». Содержимое и имя скачанного файла соответствуют выбранному студентом. Повторить через помощника с MANAGE_EXCUSES; помощник без права не получает файл.
3. При замедленном ответе нажать действие и сразу скрыть детали, сменить вкладку/страницу, выйти с экрана либо сменить API/session owner: старый response не открывает/не скачивает файл; ожидающее окно закрывается. На смене permissions/offline старые details/URL очищаются.
4. Для expired descriptor увидеть текст истёкшего срока без действий. Для реального 404/410/403 при получении файла увидеть понятный alert в его строке, старый URL очищен, карточка не исчезает; 403 блокирует файлы заявки до refresh. Для сети/5xx остаётся повторная попытка. После refresh результат перечитывается через API.

Для негативных HTTP UI состояний допустим bounded browser response interception; это не доказательство серверной авторизации. Серверное denial проверять настоящим пользователем/правами общего disposable fixture. Нельзя менять пароли/права реальных учётных записей. Никакой ticket/token/credential/file payload не хранить в evidence.
