# Mobile/theme review — 79e8fdce6

**Есть два P2; полная готовность light/dark всех экранов не подтверждена.** Поздние четыре theme delta прошли source review. Семейства существующих экранов инвентаризированы; ниже отдельно обозначены source-only и непосещённые вложенные состояния. Продуктовый код не изменялся.

## Подтверждённые findings одним пакетом

1. **P2 — тёмная иллюстрация семестра теряет читаемый текст в light.** `frontends/mobile-core/src/features/headman-stats/headman-stats-mobile.pcss:515` задаёт названию обычный `--rct-color-text-primary`; даты на строке69 используют `--rct-color-text-secondary`. `HeadmanStatsMobileView.vue:135` одновременно всегда рисует фиксированный тёмный autumn/spring artwork поверх surface (PCSS:50). В HEADMAN → Ещё → Статистика, light/300/root16 название и даты семестра почти не читаются; dark читается. Нужна согласованная semantic пара для текста на artwork либо theme-appropriate artwork. Evidence: [light PNG](/workspace/rct-pr3-runtime/mobile-theme-final/headman-more-Статистика-light-300.png), [dark PNG](/workspace/rct-pr3-runtime/mobile-theme-final/headman-more-Статистика-dark-300.png). Title/date состояние одинаково; chart в light ещё загружался, в dark уже отрисован — это не функциональная приёмка chart.
2. **P2 — PWA login/OTP форма шире минимального viewport.** `frontends/pwa-vue/src/login-screen.pcss:15` оставляет intrinsic minimum grid/form, inputs на49 не имеют сжатия min-inline-size. При viewport300/root16 `.login-card` имеет left24/right320.59, document scrollWidth321; при root20 OTP scrollWidth399. Правая часть формы выходит за viewport в обеих темах. Evidence: [login light300](/workspace/rct-pr3-runtime/mobile-theme-final/auth-login-light-300.png), [OTP light300/root20](/workspace/rct-pr3-runtime/mobile-theme-final/auth-otp-root20-light-300.png); парные dark PNG есть в manifest. Это существующий mobile auth layout, обнаруженный при theme survey; происхождение не приписывается четырём поздним delta.

Неблокирующее дополнительное наблюдение: admin attendance,300/root16 имеет scrollWidth313 в обеих темах, сортировка немного выходит вправо. Таблица имеет отдельный горизонтальный rail. Evidence `admin-attendance-{light,dark}-300.png`; в два theme findings не включено.

## Source/provenance

- Целевой source: `79e8fdce620af0493287469725e82bee511b2e8f`. Runtime использовал существующие PWA15175/TMA15176 и настоящий Gateway18080, stand Academic950/Authf3. Backend responses не подменялись. Devservers не перезапускались, чужие PID не завершались. Не запускались Gradle, build, install, CI, mutation suites, Goal/PR/Git operations.
- Root HEAD во время окончания survey уже `3e58d981f`; `git diff 79e8fdce6 -- frontends/mobile-core frontends/pwa-vue frontends/tma-vue frontends/shared/foundation` пуст. Поэтому релевантные runtime source bytes точно совпадают с указанным target revision. Исторические devserver ownership records оказались stale; фактический HTTP и Chromium render подтверждены, process provenance старых PID не переобозначен как проверенный.
- Reused independent PASS: `/workspace/rct-pr3-review/theme-mobile/review-6de6fcbca-00fc23d91.md`. Новые source scopes: e92e89fe7 (единственный palette import через foundation/base), acee13767 (сняты nested dark defaults, bootstrap roles получают resolvedTheme),3e89e946b (status glyph на status-on-fill),caf3789ab (micro tracking rem). Новых P1/P2 в этих delta нет. Original commits не ancestors root: интеграция эквивалентными cherry-picks. Файлы e92/3e/caf byte-equivalent root; acee отличается только более поздними QR labels в Sessions/AccountHistory, theme changes сохранены. Полные paths и diff: `source-provenance.json`. `git diff --check 00fc23d91 79e8fdce6 -- <четыре scopes>` exit0.
- Source inventory: оба bootstrap owner используют общий controller/mirror; components не создают новые theme owners. Foundation/base импортирует canonical palette; mobile tokens остаются semantic aliases. Attendance,statistics,headman-group,notifications,Today gradients и monochrome SVG используют shared theme values. Уцелевшие locked dark controllers найдены в dev/review-only entrypoints, не в production owners.
- Reused real device HTTP evidence: `/workspace/rct-pr3-runtime/theme-api-immutable-smoke-20261024.log` (GET/PUT/readback,409 CAS,logout204; cookie accepted Chromium). Его не повторяли и не объявляли собственным новым PASS. Полную device/auth/security приёмку см. отдельные принятые root отчёты.

## Runtime family coverage

Все24 existing mobile-core feature families включены в source inventory. Runtime ниже — bounded visual survey, а не E2E всего поведения.

| Семейство | Посещённые реальные PWA состояния / предел |
|---|---|
| today | STUDENT3 Today, semester schedule; отметка ещё недоступна — без записи |
| homework | STUDENT3 day cards, HEADMAN overview; completion/swipe/attachments не менялись |
| attendance | STUDENT3 day list и semantic summary; editor/submit не выполнялись |
| statistics | STUDENT3 summary/chart/subjects; detail/ranking drawer не посещены |
| requests | inbox/type selection; excuse/late submit/upload не выполнялись |
| profile | student и teacher profile/appearance/security/sessions/history/role switch; root20 appearance; пароль/OTP/session revoke не менялись |
| map | student/teacher/headman real selectors/version/zoom controls; floor preview fixture blank, download не запускался; реальные server styles/labels видны |
| notifications | source reviewed; history/toast runtime здесь не посещены, reuse existing root evidence |
| teacher | home,attendance selection,stats controls,map,profile,calendar overlay; lesson/journal/manual-excuse/review details не посещены |
| headman-home | Today и More |
| headman-journal | Учёт/lesson roster и запрет ещё не начавшейся пары; отметки не менялись |
| headman-requests | root inbox; decision/details не выполнялись |
| headman-stats | overview/charts/semester/roster; отдельные history/trend/ticket overlays не посещены |
| headman-group | shell/search/roster read-only посещены; PNG исключены, так как delayed UI сам показывает существующие initial credentials; credential controls не нажимались |
| headman-subjects | catalog/teacher assignments; create/edit dialogs не выполнялись |
| schedule | HEADMAN constructor/parity controls; create/move/delete не выполнялись |
| headman-lesson-management | calendar/week/lesson list; editor/save/cancel не выполнялись |
| admin-dashboard | dashboard/activity chart/root nav, representative1440 |
| admin-attendance | filters/table/export control; export не запускался |
| admin-users | registry,filter overlay; account/credential/rights mutations не выполнялись |
| admin-groups | registry,create form; submit/transfer/delete не выполнялись |
| admin-semester | existing semesters/current/archive/delete controls; мутации не выполнялись |
| admin-map | inventory number inputs и statistics selector/count; privileged create/delete/version overlays не посещены |
| admin-access | source-only shared authority/access controls; access editor runtime не посещён |

Дополнительно PWA anonymous password login,OTP tab,recovery request form (без submit). Для baseline accounts использован существующий TEST_USERS helper только в памяти; runtime STUDENT screenshots относятся к baseline3 в роли STUDENT, далее тот же аккаунт HEADMAN; teacher2/admin1. Baseline4 credential selector получен позднее, но после root pause вход не выполнялся — отдельный student4 runtime отсутствует. Профили11 и actors8/13/14/15/16/17 не изменялись. Данные в карточках — реальные синтетические данные общего локального stand; presence чужих synthetic records не означает действия над ними.

## Visual/device evidence и ограничения

- Retained **140 PNG visual samples** (138 PWA +2 TMA unsigned gate),60 state IDs в manifest; часть bootstrap IDs уточнена как `admin-bootstrap-role-choice`. Это число изображений, **не число functional tests**. Основной viewport300×900/root16, выборочные1440×900 обоих themes; root20 только auth OTP/appearance на300 и1440. Full-page PNG могут иметь height>900. Все PNG оригинальные Chromium screenshots, не редактировались/не перерисовывались. Exact path,SHA256,PNG dimensions,viewport,theme,root font,revision,state и file-mtimeUTC: [PNG-MANIFEST.json](/workspace/rct-pr3-runtime/mobile-theme-final/PNG-MANIFEST.json). Не следует публиковать всю выборку как галерею без отбора.
- `survey.json` хранит безопасные DOM/theme/overflow snapshots и HTTP status metadata; screenshot render может закончить async loading после DOM snapshot. Ни токены, ни HTTP bodies/credentials не сохранялись. Два автоматически раскрывших credentials group PNG удалены и отсутствуют в manifest; raw group content также исключён.
- Ни одного page JS error в собранной survey metadata. Ни одного mismatched nested `[data-theme]` при light/dark переключении. Это не числовой аудит WCAG contrast всех text/background пар. Сами цвета и representative PNG осмотрены; P2 artwork выше остаётся.
- Новый bounded adapter observation `host-theme.json`: PWA system light→OS dark меняет resolved DOM live; manual light остаётся light после OS dark. TMA controlled host system light→themeChanged dark меняет DOM live; manual light остаётся light. Предпочтение остаётся system при source change. Ввод visual theme выполнялся через existing storage listener (controlled StorageEvent), не через обход DOM theme styles; для screenshot был проверен фактический resolved DOM.
- TMA здесь использовал **контролируемый host с пустым initData**, сохранив реальную app gate;2 PNG показывают только unsigned admission gate. Нет backend auth/domain stubs. Это не actual signed native Telegram admission и не TMA post-login route matrix. Предыдущее принятое root controlled-adapter evidence сохраняется отдельно; нативный Telegram не утверждается проверенным.
- Browser — установленный headless Chromium. Нет физических300px устройств,Firefox/WebKit/Telegram WebView,screen reader или полной keyboard приёмки. Предыдущие source/tests/device PASS не превращаются в новые runtime PASS.
- Harness navigation дважды остановился на ожидании отсутствующего exact-name Back в calendar/homework; это ошибки внешней обвязки, не UI finding. Следующий ограниченный проход перешёл через обычный reload/root nav и покрыл недостающие семьи; полного suite/retry campaign не было. Ни один mutation submit не использован.

## Checkpoint

Browser contexts/processes reviewer закрыты. По root request sampling/reads **PAUSED после текущего case**; никаких новых reads до двух исправлений. Не создавались monitors/children. Created only external review artifacts and this report; product files/refs/config/services unchanged. Дальше original sole theme author исправляет два P2, после этого достаточно адресного independent recheck этих экранов300/root16/root20 и relevant1440, без повторения всей выборки.
