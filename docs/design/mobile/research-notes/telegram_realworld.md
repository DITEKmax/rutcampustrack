# Telegram Mini Apps: полевая практика навигации и UX

Дата среза: 28.08.2026  
Контекст: RutCampusTrack, мобильные поверхности PWA и Telegram Mini App.  
Этот файл — исследовательская записка для общей инструкции по сборке макетов. Figma не запускалась.

## Как читать выводы

- **[Доказательство]** — наблюдение прямо следует из кода живого/публичного Mini App, воспроизводимого issue клиента Telegram или отчёта автора выпущенного продукта.
- **[Направляющее свидетельство]** — несколько разработчиков описывают одинаковое поведение, но нет независимой телеметрии или исправления клиента.
- **[Проектное следствие]** — вывод для RutCampusTrack, сделанный на основе доказательств; это не цитата источника.
- **[Визуальный референс]** — Behance/Dribbble показывает композиционный приём, но не доказывает удобство, доступность или работоспособность внутри Telegram.

Шкала доверия:

- **A** — код/issue с устройством, версией, сценарием воспроизведения или измерениями;
- **B** — живой продукт или практический доклад, но данные автора нельзя независимо проверить;
- **C** — портфолио-концепт; годится только для визуального направления.

Знаки `+`/`-` уточняют силу внутри уровня: например, `B-` — полезное, но одиночное направляющее свидетельство.

## Короткое решение для RutCampusTrack

1. **Не отменять пятислотовую нижнюю панель**, но считать её навигацией только между верхнеуровневыми разделами. В Telegram она допустима: такой паттерн реализован в TelegramUI и живых open-source Mini Apps. Пятый слот «Профиль» остаётся обоснованным; для ролей с избытком разделов один из первых четырёх слотов становится «Ещё». При этом **не замораживать одинаковый состав слотов PWA и TMA без данных**: живой учебный планировщик Doday сохраняет пятислотовую форму, но меняет приоритеты вкладок между поверхностями.
2. **Не превращать Telegram `MainButton` в шестой таб или «Главную».** Это контекстная команда текущего экрана: «Сохранить посещаемость», «Отправить заявку», «Применить», «Продолжить». На обычных разделах кнопки нет.
3. **На вложенном рабочем экране скрывать нижнюю панель.** У старосты журнал пары, у студента форма заявки/тикета, редактор домашнего задания и полноэкранный фильтр получают Telegram `BackButton` и контекстный `MainButton`; после выхода возвращается панель верхнего уровня.
4. **Проектировать каждый экран для compact и expanded/fullscreen.** `requestFullscreen()` иногда молча не срабатывает, а одинаковый URL ведёт себя по-разному из разных точек запуска.
5. **Клавиатура — отдельное состояние компоновки.** Нужны явное «Готово», скрытие нижней панели, восстановление после некорректной высоты, автосохранение черновика и тесты с системной/сторонней клавиатурой.
6. **Канонический путь экрана должен быть один, точек входа — несколько.** Уведомление, карточка «Текущая пара» и бот-сообщение могут вести прямо в один и тот же маршрут журнала. Это не дублирование IA, а требование короткой Telegram-сессии.

## Реальные источники: код, issues и выпущенные продукты

### Клиенты Telegram и SDK

| Источник | Тип и признак живого опыта | Конкретная находка | Применимость к RutCampusTrack | Ограничение |
|---|---|---|---|---|
| [Telegram-iOS #1410: input fields overlapping and broken scroll](https://github.com/TelegramMessenger/Telegram-iOS/issues/1410) | **A**, открытый bug report: iPhone 13 Pro, iOS 16.0.2, Telegram 10.13, JSX и видео/сценарии воспроизведения | Поля у низа случайно перекрываются клавиатурой; фиксированное нижнее меню исчезает; прокрутка иногда блокируется; документ может «прилипнуть» со светлым/чёрным фоном вне темы. Workaround через `--tg-viewport-height`, focus/blur и scroll задерживается и нестабилен | На формах и журнале с примечанием не держать web-tabbar над клавиатурой; дать запас снизу, автопрокрутку активного поля и явное закрытие клавиатуры | Один отчёт и старые версии клиента, но семейство проблемы подтверждается более новыми issues |
| [Telegram-iOS #1385: no keyboard dismissal](https://github.com/TelegramMessenger/Telegram-iOS/issues/1385) | **A**, iPhone 11, iOS 16.7, Telegram 10.10; сравнение Mini App и обычного Telegram WebView | В Mini App исчезала iOS-кнопка Done, а тап вне поля не снимал фокус: пользователь фактически застревал в клавиатуре | В формах заявок, поиска и комментария нужна собственная видимая команда «Готово»/снятие фокуса, а не надежда на клавиатуру ОС | Не все клавиатуры и версии повторят баг; паттерн всё равно безопасен |
| [Telegram-iOS #1447: in-app swipe closes Mini App](https://github.com/TelegramMessenger/Telegram-iOS/issues/1447) | **A**, закрытый клиентский issue: iPhone 13 Pro Max, iOS 17.5.1, Telegram 10.14.1; автор проверил также Tapswap | После обновления Telegram свайп внутри swipe-heavy интерфейса закрывал Mini App, даже при попытках перехватить `touchstart/touchmove` | Не делать свайп единственным способом менять дату, статус присутствия или карточку; у каждого жеста есть кнопка/тап-эквивалент | Клиент мог исправиться, но жест остаётся конфликтным с контейнером Telegram |
| [Telegram-iOS #2235: viewport collapses to 1 px after keyboard focus](https://github.com/TelegramMessenger/Telegram-iOS/issues/2235) | **A+**, 16.07.2026: приватный используемый Mini App, iPhone 17 Pro Max, iOS 26.5.1, Telegram 12.9, сторонняя клавиатура Yandex; автор инструментировал события и получил 2 трассировки из 7, оценил частоту около 1/5 | Перед пустым экраном приходил `viewportChanged` со `stableHeight=1`; `expand()` не помогал. `blur()` + `hideKeyboard()` восстанавливали интерфейс примерно за 400 мс; автоматическое восстановление убрало последующие blank screens в тесте | Игнорировать/перехватывать явно недопустимую высоту, сохранять черновик до фокуса, показывать безопасное восстановление, тестировать сторонние клавиатуры | Один аппарат и приватный продукт; оценка частоты приблизительная, но измерение события очень конкретно |
| [Telegram-iOS #2241: `requestFullscreen()` silently ignored](https://github.com/TelegramMessenger/Telegram-iOS/issues/2241) | **A**, 20.07.2026: iOS Telegram 12.9.x / Bot API 9.6, 9 из 9 попыток, до обновления работало | Fullscreen-запрос мог быть молча проигнорирован без success/error-события | Любая критическая операция должна помещаться в compact viewport; fullscreen — улучшение, не предусловие | Один клиентский стек; не доказывает поведение Android/Desktop |
| [TelegramUI #93: safe-area inset becomes zero after Back](https://github.com/telegram-mini-apps-dev/TelegramUI/issues/93) | **A**, issue библиотеки TelegramUI с маршрутом input page → native Back → tabbar | После возврата `viewportSafeAreaInsetBottom` становился `0`, и нижняя панель теряла отступ до следующего ререндера/анимации | При возврате из формы принудительно пересчитывать layout/safe area; визуальный regression test для tabbar после клавиатуры и Back | Ошибка может быть на стыке клиента и библиотеки; важен сценарий, а не конкретный workaround |
| [tma.js #789: `theme_changed` differs by entry point/client](https://github.com/Telegram-Mini-Apps/tma.js/issues/789) | **A**, сентябрь 2025: сравнение iOS запуска через Main App и Menu Button с macOS | На iOS событие темы приходило из Main App, но не из Menu Button; базовый фон отличался между iOS и macOS | Смешанная тема: Telegram-поверхности маппить на семантические токены, иметь fallback и повторно читать тему при resume/route entry | Один SDK/версия; показывает расхождение, а не универсальную таблицу клиентов |
| [tma.js #659: wrong secondary background on Android](https://github.com/Telegram-Mini-Apps/tma.js/issues/659) | **A**, Android 15, Telegram 11.7, SDK React 3.0.10; Desktop был корректен, rollback на SDK 2.x помог | Secondary background возвращался чёрным/белым вопреки параметрам | Не принимать theme params как гарантию контраста; проверять итоговый contrast и иметь продуктовый fallback | SDK-баг закрыт; полезен как основание для защитного слоя, не для постоянного fork-а темы |
| [tma.js #423: React Router and native BackButton](https://github.com/Telegram-Mini-Apps/telegram-apps/issues/423) | **A**, конкретный код React Router v6.4 | Разработчик синхронизировал BackButton с маршрутом: на `/` скрыть, на вложенных маршрутах показать, по клику `navigate(-1)` | Именно такая модель нужна RutCampusTrack: host Back отражает вложенность, а не постоянно виден рядом с ещё одной стрелкой | Issue закрыт как not planned; пример — пользовательская интеграция, не API-гарантия |
| [tma.js #276: navigator state only in sessionStorage](https://github.com/Telegram-Mini-Apps/tma.js/issues/276) | **A**, реальный конфликт роутов с URL-параметрами и HashNavigator | История SDK жила в `sessionStorage`, из-за чего параметризованный маршрут нельзя было надёжно использовать как URL приложения | Уведомление о паре/домашке/тикете должно открывать URL-маршрут напрямую; если истории нет, Back ведёт к семантическому родителю, а не на пустую страницу | Конкретная версия навигатора; принцип глубоких ссылок шире бага |
| [Telegram Desktop #25770: viewport event missing when MainButton changes](https://github.com/telegramdesktop/tdesktop/issues/25770) | **A**, issue Desktop-клиента | При show/hide нативного `MainButton` полезная высота менялась, но `viewport_changed` мог не прийти | Появление MainButton не должно ломать последний ряд списка/формы; пересчитывать нижний inset и оставлять CSS fallback | Старый закрытый issue; проверять актуальные клиенты |
| [Telegram Desktop #31051: localStorage cleared after reopen](https://github.com/telegramdesktop/tdesktop/issues/31051) | **A**, 24.07.2026, Fedora 44, Telegram Desktop 7.0.5, живой `@TeleOTPAppBot` | После повторного открытия Mini App `localStorage` оказывался очищен | Черновик посещаемости, отправляемая заявка и несохранённые изменения не должны жить только локально; серверный черновик/CloudStorage с версией | Desktop/Linux; не означает, что storage всегда очищается |
| [Telegram Mini Apps SDK #624: crash when disabling vertical swipes](https://github.com/Telegram-Mini-Apps/tma.js/issues/624) | **A**, Android 15, Telegram 11.6.2, конкретный вызов `swipeBehavior.disableVertical` | Инициализация могла падать при попытке глобально отключить вертикальные свайпы | Отключать закрывающий свайп только на критическом несохранённом шаге, с feature detection и fallback; не делать это глобальной настройкой оболочки | Закрытый SDK-баг конкретной версии |
| [Stack Overflow: external URL leaves no usable way back](https://stackoverflow.com/questions/78978507/navigation-in-external-urls-within-tma-telegram-mini-app) | **B-/направляющее**, разработчик описал воспроизводимый переход обычным `<a>`: внешний сайт открылся в Telegram-контейнере, а обработчик Back исходного Mini App перестал работать | Обычная web-навигация на внешний URL может заменить документ приложения внутри контейнера; пользователю остаётся Close и повторный запуск | Файлы, карты и внешние материалы открывать через платформенный `openLink`/явную команду, заранее сообщать «Откроется вне приложения»; не использовать обычный anchor как переход внутри router flow | Вопрос без принятого ответа и клиентской матрицы; это warning-сценарий, который надо перепроверить на актуальных клиентах |
| [Telegram Mini Apps platform issue #62](https://github.com/Telegram-Mini-Apps/issues/issues/62) | **A-/B**, открытый с 10.01.2025 issue с сравнением fullscreen iOS/Android | Координаты Close/Menu различаются, а viewport API не сообщает геометрию этих внешних кнопок | Не выравнивать собственный заголовок «по пикселю» с Telegram chrome и не дублировать Back; жить внутри content safe area | Открытый issue без универсального client contract |
| [Platform issue #79: `viewport.mount()` blank screen](https://github.com/Telegram-Mini-Apps/issues/issues/79) | **A-/B**, подтверждения macOS, WebK и отдельных новых iPhone; maintainer передал Telegram | Даже доступный API viewport мог приводить к blank screen | Изолировать инициализацию, ставить timeout/error fallback на `window.innerHeight`; первый экран обязан рендериться без Telegram viewport API | Открытая проблема, набор клиентов меняется |
| [Platform issue #39: fixed bottom menu jumps on iOS](https://github.com/Telegram-Mini-Apps/issues/issues/39) | **A-/B**, воспроизводимый issue и обсуждение workaround; объявлен исправленным в июне 2025 | Custom fixed menu прыгало на safe-area, а `100lvh + overflow:hidden` помогало до focus/blur input, после чего ломалось | Не превращать исторический CSS workaround в архитектуру; тестировать bottom nav после input | Клиентское исправление уже выпускалось; источник показывает класс риска, не текущую частоту |
| [Platform issue #50: bottom flicker after compact → expand](https://github.com/Telegram-Mini-Apps/issues/issues/50) | **A-/B**, разные entry points, momentum scroll и Back; maintainer позже сообщил о client fix | Нижние блоки мерцали, sticky-app workaround создавал недоступный scroll offset; maintainer отдельно попросил не фиксировать глобальные `html/body` | Не фиксировать корневой документ; тестировать Menu/direct link, compact→expand, iOS overscroll и Back | Исторически исправлялось; конкретный workaround копировать нельзя |
| [Platform issue #33: keyboard cover and wrong version-gating assumption](https://github.com/Telegram-Mini-Apps/issues/issues/33) | **A-/B**, issue считали исправленным в 2025, но в феврале 2026 снова сообщили о воспроизведении; есть ответ maintainer | `tgWebAppVersion` — версия поддерживаемого API, а не версия клиента Telegram; по ней нельзя безопасно включить keyboard workaround | Использовать runtime `visualViewport`/focus state и реальные client tests, не UA/API-version guess | Повторное сообщение не даёт распространённость бага |
| [Platform issue #65: theme changes after resume](https://github.com/Telegram-Mini-Apps/issues/issues/65) | **A-/B**, открытый iOS issue | После ухода на home screen и возврата приходил неожиданный `theme_changed` с иным secondary background | На resume применять полный набор семантических токенов атомарно, а не частично перекрашивать одну поверхность | Один клиентский сценарий; нужен fallback независимо от причины |

### Публичный код Mini Apps

| Источник | Тип и признак живого опыта | Конкретная находка | Применимость | Ограничение |
|---|---|---|---|---|
| [TelegramUI](https://github.com/telegram-mini-apps-dev/TelegramUI) и [issue #78 «Right way to use Tabbar»](https://github.com/telegram-mini-apps-dev/TelegramUI/issues/78) | **A-/B**, 848★, 87 forks, 114 commits и живые issues на момент среза; библиотека спонсировалась TON Foundation | В библиотеке есть отдельный `Tabbar`, а в issue показано реальное сочетание `FixedLayout`, React Router links и outlet. То есть собственный bottom nav — распространённый, поддерживаемый сообществом паттерн, а не нарушение Telegram UX | Пятислотовая панель RutCampusTrack приемлема для верхнего уровня | Библиотека не доказывает, что конкретный состав вкладок правильный; README-заявление о поддержке всех клиентов опровергается собственными issues |
| [TelegramUI #43: custom class wipes iOS tabbar styles](https://github.com/telegram-mini-apps-dev/TelegramUI/issues/43) | **A**, issue с исправлением | Пользовательская стилизация tabbar могла затереть платформенные iOS-правила | Не стилизовать панель «с нуля»: строить над устойчивой базой, отдельно тестировать iOS safe area и активное состояние | Библиотечная деталь, но хорошо показывает цену декоративной кастомизации |
| [`my-saved-answers`](https://github.com/vkruglikov/my-saved-answers) | **B**, Telegram Mini App Contest 2023, публичный код и демо; автор перечисляет реальные тесты на iPhone 14/iOS 16.6.1/Telegram 10.1.2 и сбой Desktop | React Router ведёт list → preview; native Back используется для вложенности; `MainButton` «SEND» появляется только на preview, даёт haptic, выполняет `switchInlineQuery` и закрывает app. Логика зависит от источника запуска; на Desktop `switchInlineQuery` был нестабилен из `/setmenubutton` | Прямой образец: список домашек/заявок → detail; Back — назад, MainButton — выполнить действие; учитывать entry point и platform | Конкурсный проект, мок-данные, небольшая аудитория; паттерн кода сильнее продуктовых выводов |
| [Doday](https://github.com/SwairIt/doday), [PWA nav](https://github.com/SwairIt/doday/blob/e7bef509ccf660e6ed055b5fbcdd5955c3bc3d6a/app/templates/_partials/mobile_nav.html#L1-L28), [TMA nav](https://github.com/SwairIt/doday/blob/e7bef509ccf660e6ed055b5fbcdd5955c3bc3d6a/app/templates/miniapp/_base.html#L371-L393) | **B**, живой планировщик школьников/студентов с PWA и TMA, публичный продукт `getdoday.ru`, push 28.08.2026 | В обеих поверхностях пять слотов, но состав/порядок различаются. `MainButton` меняется по экрану и скрыт на остальных ([код](https://github.com/SwairIt/doday/blob/e7bef509ccf660e6ed055b5fbcdd5955c3bc3d6a/app/miniapp/static.py#L112-L152)). Месяц на PWA автоматически становится неделей, а TMA показывает семь day chips → список одного дня → 12-недельную heatmap ([код](https://github.com/SwairIt/doday/blob/e7bef509ccf660e6ed055b5fbcdd5955c3bc3d6a/app/templates/miniapp/calendar.html#L30-L107)) | Прямой аналог: визуально единый 5-slot shell не требует одинаковой IA; расписание/журнал преобразовывать в период → день/пара → вертикальный срез | Молодой продукт, 6★ — не массовая валидация. Глобальный `disableVerticalSwipes()` и cleanup кнопок в коде копировать нельзя |
| [Bedolaga Cabinet](https://github.com/BEDOLAGA-DEV/bedolaga-cabinet), [bottom nav](https://github.com/BEDOLAGA-DEV/bedolaga-cabinet/blob/35e5aa9e78123fdf18506a7a8a46875d268689ed/src/components/layout/AppShell/MobileBottomNav.tsx#L29-L99), [keyboard shell](https://github.com/BEDOLAGA-DEV/bedolaga-cabinet/blob/35e5aa9e78123fdf18506a7a8a46875d268689ed/src/components/layout/AppShell/AppShell.tsx#L78-L114) | **A-/B**, 283★, 131 forks, 2159 commits, push 27.08.2026; один React app для web и TMA | Слоты выбираются по критичности/feature flags; tabbar скрывается при input/textarea/contenteditable и сбрасывается на route change. Один глобальный [Back controller](https://github.com/BEDOLAGA-DEV/bedolaga-cabinet/blob/35e5aa9e78123fdf18506a7a8a46875d268689ed/src/AppWithNavigator.tsx#L30-L171) учитывает top-level, SPA-depth и deep-link fallback | У старосты прямой слот определяется критичностью; keyboard hiding и один владелец Back должны стать shell contract | VPN-продукт, не образование; сложность роутинга похожа, бизнес-сценарии нет |
| [Bedolaga issue #436: dead/looping Back](https://github.com/BEDOLAGA-DEV/bedolaga-cabinet/issues/436) и [#545](https://github.com/BEDOLAGA-DEV/bedolaga-cabinet/issues/545) | **A**, живой отказ на iOS/Desktop/Web и последующие commits | Конкурирующие handlers singleton-кнопки, неверная глубина и redirect loop делали Back «мёртвым»; auth guard воспроизводит тот же класс ошибки на `/login` | Route metadata `root/detail/modal`, один владелец Back, смысловой fallback; login/onboarding/role-switch landing считать корнями | Исправления конкретного router; архитектурный класс ошибки переносим |
| [TeleOTP](https://github.com/UselessStudio/TeleOTP), [MainButton hook](https://github.com/UselessStudio/TeleOTP/blob/af90596485e5de282798a007a7350a7486efa13d/src/hooks/telegram/useTelegramMainButton.ts#L14-L72), [Back hook](https://github.com/UselessStudio/TeleOTP/blob/af90596485e5de282798a007a7350a7486efa13d/src/hooks/telegram/useTelegramBackButton.ts#L9-L30) | **A-/B**, 125★, 22 forks, live bot, push 10.08.2026 | MainButton имеет lifecycle: text/show/hide, handler cleanup, disabled/progress; в многошаговой форме меняется Next → Create ([код](https://github.com/UselessStudio/TeleOTP/blob/af90596485e5de282798a007a7350a7486efa13d/src/pages/PasswordSetup.tsx#L40-L93)). Back подключён один раз и отписывается | Это готовый поведенческий контракт для «Сохранить журнал»/«Отправить заявку»: valid → enabled → progress → cleanup | Простое `navigate(-1)` недостаточно для deep links, guards и смены ролей RutCampusTrack |
| [Notepher](https://github.com/deptyped/notepher-bot), [notes list](https://github.com/deptyped/notepher-bot/blob/35f850c7d44127145175596e457e4776e334a6bd/apps/web-app/src/views/NotesView.vue#L1-L55), [detail/editor](https://github.com/deptyped/notepher-bot/blob/35f850c7d44127145175596e457e4776e334a6bd/apps/web-app/src/views/NoteView.vue#L96-L127) | **B**, 213★, 35 forks, публичный bot; code push март 2024 | Полезная sticky search/sync зона + viewport-aware FAB; detail использует native Back и contextual EDIT MainButton; editor включает closing confirmation. Отдельно отслеживает [`visualViewport`](https://github.com/deptyped/notepher-bot/blob/35f850c7d44127145175596e457e4776e334a6bd/apps/web-app/src/composables/useVisualViewport.ts#L7-L33) и прокручивает caret между toolbars | Хороший аналог длинного комментария/описания задания: focus-aware viewport, visible recovery, close guard | Старый SDK; TODO по iOS-height и filler из пустых абзацев показывают проблему, а не готовый код для копирования |

### Reddit и практические доклады

| Источник | Тип и признак живого опыта | Конкретная находка | Применимость | Ограничение |
|---|---|---|---|---|
| [Reddit: «I regret creating a Telegram mini app»](https://www.reddit.com/r/Telegram/comments/1ov5q1q/i_regret_creating_a_telegram_mini_app/) | **B**, автор пишет о нескольких Mini Apps и ежедневно используемом бесплатном calorie tracker `@trackit_fitness_bot`; 52 upvotes / 34 comments в срезе | Бесшовная авторизация хороша, но автору не хватало discovery/ranking и аналитики retention, engagement и user flow | Для закрытого кампусного продукта discovery почти неважен, зато нужна собственная событийная аналитика: вход → сценарий → завершение/ошибка; не ждать данных от Telegram | Самоотчёт без открытой аналитики; домен fitness, а не образование |
| [Reddit: «Are there any actual useful Mini Apps?»](https://www.reddit.com/r/Telegram/comments/1grfwoc/are_there_any_actual_useful_telegram_mini_apps/) | **B-/направляющее**, пользователи обсуждают фактическое обнаружение и доверие | Часть участников не знала, где искать каталог, или видела в основном crypto/games; полезными называли приватные утилиты | RutCampusTrack должен открываться из закреплённого меню бота, релевантного уведомления и прямой ссылки, а не рассчитывать на каталог | Небольшая выборка Reddit; не измерение всей аудитории Telegram |
| [Reddit: React Native Telegram Mini App display issue](https://www.reddit.com/r/reactnative/comments/1t5ncrh/telegram_mini_app_display_issue/) | **B-/направляющее**, автор сравнил тот же код в Expo/mobile browser и Telegram | В Telegram оставалась непокрашенная область; сворачивание/разворачивание исправляло. Автор связал это со стартом Mini App в частичной высоте и последующим slide-up | Корневой фон обязан заполнять compact и expanded viewport; первый meaningful screen не должен ждать fullscreen | Маленький тред, нет клиентского root-cause |
| [Reddit: fullscreen works from Main App but not menu](https://www.reddit.com/r/node/comments/1qg4ofb/telegram_mini_app_fullscreen_works_via_main_app/) | **B-/направляющее**, одинаковый URL, две точки запуска; второй разработчик подтвердил на своём коде | Fullscreen работал из BotFather Main App, но не из menu/inline entry | В матрице QA отдельные строки для Main App, Menu Button, inline/deep link; все должны оставаться usable без fullscreen | Малый тред и нет официального resolution; важен как противоречие идеальной модели документации |
| [Reddit: Secret Stars pivot](https://www.reddit.com/r/buildinpublic/comments/1vrqbt5/i_started_secret_stars_as_ai_tinder_now_its_an_ai/) | **B**, автор живого продукта описывает pivot и числа: home сменили с chat list/swipe deck на feed → react → chat; subscription sheet opens выросли примерно с 3/день до 11/день, next-day return немного снизился | Контекстный контент на главной лучше запускает действие, чем список конечных разделов; одновременно оптимизация одного действия может ухудшить возврат | **Проектное следствие:** главная роли должна вести в «следующую пару», «неотмеченную посещаемость», «домашку сегодня», а не быть красивым меню. Проверять по cohort metrics | Социальный AI-продукт, self-reported numbers; нельзя переносить величину эффекта |
| [YouTube: Open League UI workshop](https://www.youtube.com/watch?v=b4qomZ3p6JU) + [конспект участника](https://hackmd.io/@canfly1019/HyfB2BYUR) | **B**, практический воркшоп Open League Hackathon, 08.04.2024; Chris Cherniakov, Vladimir Alefman, Vlad Arbatov; разобраны Telebook, EVAA, Fanton | Конспект фиксирует более короткие Mini App-сессии (пока открыт Mini App, Telegram недоступен), важность разных entry points, знакомой light/dark темы и ясного CTA | Быстрый первый экран и сценарный вход важнее полноты меню; визуально быть близко к Telegram, сохраняя бренд RutCampusTrack | Это вторичный конспект на китайском, не стенограмма и не метрики; используем как направление, не как доказанный KPI |

### Источники, которые не стали основанием решений

- [TON Studio: How to Design Your Telegram Mini App](https://www.youtube.com/watch?v=WT5MZn8I_Fs) — релевантный автор (Gleb Vorontsov, Head of Telegram Mini Apps at TON Studio) и свежая дата 03.03.2025, но доступный срез не дал проверяемой стенограммы/конкретного провала; оставлен в watchlist.
- [TON Nest UX/UI workshop](https://www.youtube.com/watch?v=QbQeWDZaO-M) — заявлен как практический воркшоп, но без извлечённых конкретных фактов не используется как доказательство.
- [Turum-burum: Telegram Mini App Beyond the Standard UI](https://turumburum.com/blog/telegram-mini-app-beyond-the-standard-ui-designing-a-truly-native-experience) — формулировки про clear CTA, theme params и gestures разумны, но в материале нет названного выпущенного кейса или чисел. Это не проходит критерий владельца «живой опыт/живой продукт».
- Официальная [Telegram Mini Apps API documentation](https://core.telegram.org/bots/webapps) нужна как нормативная база, но сама по себе не считается свидетельством реального UX. В частности, идеальное описание одинакового поведения точек входа надо проверять против client issues и живых тестов выше.

## Подтверждённые выводы и проектные последствия

### 1. Bottom nav допустим, но только для верхнего уровня

**[Доказательство]** TelegramUI имеет отдельный Tabbar и реальные интеграции с React Router; Doday и Bedolaga используют пятислотовую собственную панель в живых приложениях. Нет практического основания полностью запрещать bottom nav в Telegram. Doday дополнительно показывает: одинаковая пятислотовая форма PWA/TMA может иметь разный состав вкладок.

**[Проектное следствие]** сохранить до пяти слотов, но не показывать панель на detail/editor/transaction routes. Для старосты базовый кандидат: «Главная», «Посещаемость», один часто используемый управленческий раздел, «Ещё», «Профиль». Точный третий слот и необходимость различать PWA/TMA требуют продуктовой аналитики; это не может решить внешний benchmark.

### 2. `MainButton` — контекстный commit, а не навигация

**[Доказательство]** `my-saved-answers` показывает MainButton только на preview для команды SEND; TeleOTP реализует полный lifecycle контекстной кнопки (disabled, progress, cleanup), а Doday меняет её команду по текущему экрану. Issues Desktop показывают, что появление host button меняет usable viewport и события могут быть несовершенны.

**[Проектное следствие]** допустимые подписи: «Сохранить посещаемость», «Отправить», «Применить», «Продолжить». Недопустимые: «Главная», «Меню», постоянный «Далее» без контекста. Если API/клиент не поддержал кнопку, на том же месте контента появляется inline fallback; пользователь не теряет действие.

### 3. Native Back отражает маршрут и скрыт в корне

**[Доказательство]** #423 и nextjs-template #9 показывают, что BackButton не появляется «сам»: его надо синхронизировать с router state. Bedolaga issue #436/#545 показывает production-отказы из-за нескольких владельцев кнопки, ошибочной глубины и redirect loop. Ошибки wiring встречались также в [tma.js #501](https://github.com/Telegram-Mini-Apps/tma.js/issues/501).

**[Проектное следствие]** один shell-controller владеет BackButton. Route metadata различает `root`, `detail`, `modal/edit`: на верхнеуровневом табе, login/onboarding и landing после смены роли Back скрыт; на detail/editor показан. Не рисовать вторую стрелку в собственной шапке. Для прямой ссылки без локальной истории Back ведёт к каноническому родителю: журнал пары → список занятий; тикет → заявки; домашка → предмет/список домашек.

### 4. «Один основной путь» не означает «одна точка входа»

**[Доказательство]** реальные проекты меняют логику по launch source; URL/history-конфликт #276 показывает необходимость прямого маршрута; Reddit показывает слабость каталожного discovery.

**[Проектное следствие]** у экрана один канонический маршрут и родитель, но в него разрешены contextual deep links из уведомления, карточки главной и bot message. Это сокращает частый сценарий старосты «открыть текущую пару → отметить» до одного входа, не создавая дубликаты экрана.

### 5. Экран должен быть полноценным и в compact, и в fullscreen

**[Доказательство]** iOS #2241 и разработчики Reddit наблюдали silent fullscreen failure и различия по точке запуска; React Native case показывает непокрашенную область при compact → expand.

**[Проектное следствие]** сверху в compact видны: контекст, главное состояние и первый CTA. Нельзя откладывать загрузку контента до `expand()`. Background и skeleton покрывают всю изменяемую область.

### 6. Формы и клавиатура требуют другого shell

**[Доказательство]** iOS #1410, #1385, #2235 и TelegramUI #93 независимо показывают перекрытие, отсутствие dismiss, неверную высоту и потерю safe-area после Back.

**[Проектное следствие]** Bedolaga подтверждает скрытие tabbar по focus, Notepher — отдельный `visualViewport` и ручное удержание caret между toolbars. При фокусе:

- bottom nav скрывается;
- активное поле прокручивается в безопасную область;
- есть «Готово»;
- commit не прячется только за клавиатурой;
- черновик сохранён до рискованного изменения viewport;
- после blur/back layout пересчитывается.

### 7. Критический сценарий не может зависеть от свайпа

**[Доказательство]** iOS #1447: внутренний свайп закрывал Mini App; попытки перехвата не гарантировали защиту. SDK #624 показывает, что blanket-disable также способен сломаться.

**[Проектное следствие]** смена даты пары — кнопки/календарь плюс необязательный свайп; статусы посещения — крупные chips/taps; раскрытие строки — тап, а не скрытый жест. Отключение vertical swipe допустимо временно только при несохранённой критической операции, с feature detection и closing confirmation.

### 8. Fixed bottom layout надо инвалидировать после каждого host transition

**[Доказательство]** safe-area терялась после input → Back (#93), а Desktop не всегда сообщал об изменении usable height при MainButton (#25770).

**[Проектное следствие]** пересчёт insets при `viewportChanged`, theme/resume, focus/blur, show/hide host buttons и route return. Последняя строка всегда имеет padding, позволяющий полностью попасть в поле зрения и тапнуть.

### 9. Тема должна быть гибридной, а не «слепо Telegram» и не «жёстко брендовой»

**[Доказательство]** #789 и #659 показывают пропущенные theme events и некорректные параметры на отдельных клиентах/entry points.

**[Проектное следствие]** Telegram-параметры задают начальную light/dark схему и согласуют host chrome. Фиолетовые акценты RutCampusTrack и семантика статусов остаются продуктовой системой через существующие токены. Bedolaga показывает жизнеспособный вариант приоритета сохранённой темы приложения над Telegram-схемой; issue #65 требует атомарного пересчёта на resume. Для каждого значения есть fallback и проверка контраста; hardcoded hex из референса запрещён.

### 10. Cross-client matrix — часть дизайна, а не только финальное QA

**[Доказательство]** одно и то же действие расходится на iOS, Android, macOS/Desktop, Linux и по точкам запуска.

**[Проектное следствие]** в спецификации каждого критического экрана должны быть состояния: iOS compact/expanded + keyboard, Android compact/expanded + keyboard, Desktop/Web, Main App/Menu/deep link. Макет не обязан рисовать каждую комбинацию, но обязан дать правила перестройки.

### 11. Несохранённая работа не живёт только в `localStorage`

**[Доказательство]** Desktop #31051 описывает очистку storage у живого Mini App после reopen; viewport bugs могут визуально «уронить» форму.

**[Проектное следствие]** журнал старосты и длинная заявка имеют серверный draft с версией/временем; CloudStorage годится для лёгких пользовательских предпочтений и last-used filters, но не как единственный источник истины. После восстановления показывать «Черновик восстановлен».

### 12. Главная — старт работы, не каталог возможностей

**[Направляющее свидетельство]** workshop говорит о коротких сессиях и entry points; Secret Stars приводит числа после замены list/deck на action-producing feed.

**[Проектное следствие]** первые карточки роли:

- студент: ближайшая пара, домашка сегодня/просрочено, изменение расписания;
- староста: текущая/следующая пара, незавершённый журнал, отсутствующие данные;
- преподаватель: ближайшее занятие, сводка группы, требующее внимания;
- администратор: инцидент/заявка, недавние изменения, быстрый поиск сущности.

Это гипотеза для проверки событиями, а не доказанный порядок карточек.

### 13. Собственная аналитика обязательна

**[Доказательство]** автор `@trackit_fitness_bot` прямо называет недостаточной доступную аналитику retention/engagement/user flow.

**[Проектное следствие]** сервер/фронт фиксирует: entry point, роль, route, начало и завершение сценария, отмену, recovery клавиатуры/viewport, fallback вместо host button, ошибку, время до первого полезного действия. Содержимое домашки, комментариев и персональные данные в события не писать.

### 14. Автосохранение и восстановление — видимая часть UX

**[Проектное следствие]** это синтез keyboard/viewport/storage evidence. На журнале старосты после каждого изменения статуса коротко показывать «Сохранено локально / синхронизировано»; при сбое — «Нет сети, 7 изменений ждут отправки». Перед закрытием с несинхронизированными данными включать подтверждение закрытия. Само наличие серверного draft требует согласования с бэкендом.

### 15. Внешняя ссылка не должна подменять маршрут Mini App

**[Направляющее свидетельство]** разработчик на Stack Overflow воспроизвёл ситуацию, когда обычный `<a>` открыл внешний документ в Telegram-контейнере и оставил пользователя без работающего Back исходного приложения.

**[Проектное следствие]** ссылка на файл, внешний корпус карты или справочник имеет иконку внешнего перехода и запускается платформенным способом с ожидаемым поведением. Внутренние документы по возможности показываются своим preview route. После возврата состояние списка/формы восстановлено.

## Решение по экранам RutCampusTrack

| Экран/сценарий | Верхнеуровневая навигация | Telegram host controls | Что обязательно предусмотреть |
|---|---|---|---|
| Главная любой роли | Bottom nav виден | Back скрыт, MainButton скрыт | Первый полезный блок в compact; deep links из карточек |
| Список посещаемости студента | Bottom nav виден | Back скрыт | Сводка → дата/предмет → раскрываемые детали; без горизонтального свайпа как единственного пути |
| Журнал текущей пары старосты | Bottom nav скрыт | Back = к занятиям; MainButton = «Сохранить посещаемость»; Secondary/inline = «Отменить» только когда уместно | Sticky контекст пары, крупные status chips, autosave, unsaved-close guard, keyboard state для комментария |
| Расписание/календарь | Bottom nav виден на overview, скрыт в editor | Back по вложенности; MainButton только для сохранения изменений | По примеру Doday: период → 7 day chips/выбранный день → один вертикальный список; компактная heatmap годится для сводки, но не заменяет расписание |
| Деталь домашнего задания | Bottom nav скрыт | Back = к списку; MainButton только если есть реальное действие | Deep-link-safe parent; загрузка/ошибка/нет файла; внешняя ссылка имеет явный исход |
| Создание заявки/тикета | Bottom nav скрыт | Back; MainButton = «Отправить» с disabled/loading | Явное dismiss keyboard, server draft, восстановление после viewport collapse |
| Список заявок | Bottom nav виден или доступен через «Ещё» | MainButton скрыт | Status-card list; фильтр; detail по тапу; не копировать desktop table |
| Карта | Bottom nav виден | MainButton скрыт | Карточка выбранной точки поверх карты, не swipe-only; compact viewport не закрывает controls |
| «Ещё» старосты | Верхнеуровневый sheet/page | Back по состоянию маршрута | Две сохранённые группы «Разделы» и «Управление»; не long-press и не скрытые жесты |

## Пять anti-patterns

1. **Постоянный `MainButton` как «Главная», «Меню» или пятый tab.** Он отнимает viewport, конфликтует с web-tabbar и теряет смысл контекстного commit.
2. **Двойной Back.** Собственная стрелка в шапке одновременно с Telegram BackButton либо Back на корневом табе создаёт два конкурирующих пути и риск dead/looping history.
3. **Fixed bottom nav на экране с клавиатурой.** На iOS она перекрывается, прыгает из-за safe area или оставляет поле без способа закрыть клавиатуру.
4. **Swipe-only управление журналом, датами или раскрытием.** Жест может закрыть Mini App или не сработать; критическая функция обязана иметь видимый tap target.
5. **«Идеальный единственный клиент»: hardcoded fullscreen + hardcoded Telegram theme + локальный draft.** Реальные клиенты расходятся по высоте, теме, entry point и storage; такой экран красив только в демонстрации.

## Визуальный benchmark Behance/Dribbble

### Правило использования

Behance и Dribbble здесь — **не доказательства UX**. Даже опубликованный «case study» часто является одним polished shot без клавиатуры, ошибок, safe area, loading, длинных ФИО и 30 строк журнала. Из них можно брать композиционный приём, но нельзя копировать сетку или объявлять решение проверенным.

### Реальные/заказные проекты

| Референс | Статус | Конкретный пригодный приём | Как применить | Риск копирования |
|---|---|---|---|---|
| [Frienda Brand Development](https://www.behance.net/gallery/226023327/Frienda-Brand-Development), Shuka Design и команда, 27.05.2025 | **B, запущенный Telegram Mini App**: авторы прямо называют продукт launched и заказчика/команду | Сильная продуктовая идентичность строится вокруг одного повторяемого знака/«искры», а не набора эффектов | Для RutCampusTrack выбрать один узнаваемый мотив из существующего веб-языка — например, мягкий фиолетовый световой акцент у главной карточки и active state — и повторять умеренно | Социальный продукт почти без плотных данных; декоративная айдентика не решает журнал и формы |
| [WELLDONE — Telegram Mini App для кулинарной школы](https://www.behance.net/gallery/244414709/WELLDONE-Telegram-mini-app-dlja-kulinarnoj-shkoly), Елизавета Кудина, ДАЛИ, Daria Kuzmenko и команда, 19.02.2026 | **B-**, заказная работа по готовому ТЗ: структура, прототипирование и финальный дизайн с учётом Telegram; факт публичного запуска не заявлен | Полезен как свежий benchmark того, как образовательный каталог и действия укладывают в Telegram-контейнер, сохраняя собственный бренд | Сравнить высоты карточек, плотность заголовков, длину CTA и место host chrome с будущими макетами RutCampusTrack | Портфолио не показывает keyboard, safe-area failures, empty/error и реальную телеметрию |
| [Tiimi — HR Mobile App](https://dribbble.com/shots/26304342-Tiimi-HR-Mobile-App-to-Manage-Attendance-Time-Off-Overtime), Bagus Fikri / Fikri Studio, 2026 | **B-/C+**, студийный showcase с client recommendation; deployment не подтверждён | Вместо общей таблицы продукт «дистиллирует» attendance/time off/overtime в focused views: кто работает, кто отсутствует, где накапливаются часы; контекстные сводки предшествуют деталям | Для посещаемости: верхняя сводка «присутствуют / отсутствуют / не отмечено», затем отфильтрованный список людей; для админа — summary до реестра | HR-логика не равна учебной; нельзя переносить clock-in метафору на отметку старосты |
| [B&H Omnichannel e-commerce / PWA transition](https://www.behance.net/gallery/192948481/Omichannel-e-commerce), Aaron Salley, 03.03.2024 | **B**, автор описывает шестилетнюю работу живого продукта, отказ от native Android в пользу PWA и self-reported вклад mobile suite почти в половину e-commerce transactions | Визуальную идентичность и критический journey унифицировали между mobile web/PWA и каналами, одновременно упрощая decision points, а не имитируя нативность декоративно | Для RutCampusTrack PWA может сильнее сохранять веб-бренд и структуру, но частые мобильные задачи всё равно сокращаются; визуальная близость не требует одинаковой плотности | E-commerce и self-reported outcomes; не источник для Telegram и не доказательство конкретного UI-компонента |

### Концепты, пригодные только как визуальные варианты

| Референс | Автор/дата и статус | Что взять | Что не брать без проверки |
|---|---|---|---|
| [Education Management Mobile App Design](https://dribbble.com/shots/27547618-Education-Management-Mobile-App-Design) | Victoria Grinevich, 18.08.2026, **C** | Тёмная основа, один сине-фиолетовый градиент, крупный заголовок и слоистые карточки дают быструю scanability ежедневного плана | Oversized type на каждой карточке резко уменьшит количество студентов/пар на экране; яркий градиент не должен заливать журнал |
| [Query AI — Study Dashboard](https://dribbble.com/shots/27083917-Query-AI-Study-Dashboard-Snap-Learning-App-UI-UX-Design) | Aftabul Islam Samudro / Panze, 04.03.2026, **C** | Deep purple-blue gradient + мягкие glass cards + модульная сводка хорошо подходят для главной студента и компактных progress tiles | Blur/glass поверх динамической Telegram-темы может провалить contrast и GPU; применять одной неглубокой поверхностью и только через запросы на токены |
| [Education App UI — Learning Dashboard](https://dribbble.com/shots/26965137-Education-App-UI-Learning-Dashboard) | Nixtio, 13.01.2026, **C**, Dribbble Select agency | Пастельный градиент, округлые поверхности и строгая типографическая структура; progress + duration + calendar планирование | Заявления о usability/accessibility не подкреплены тестами; не переносить пастельные оттенки мимо существующих токенов RutCampusTrack |
| [Attendance Dashboard Report — Mobile Interaction](https://dribbble.com/shots/8714837-Attendance-Dashboard-Report-Mobile-Interaction-Design) | Nitish Khagwal, interaction concept, **C** | Микроанимация active tab и фокусировка выбранного периода могут помочь понять смену контекста | Анимация не заменяет label/state; старый shot не учитывает современный Telegram viewport и reduce-motion |
| [Leave Request — Attendance Management](https://dribbble.com/shots/14736713-Leave-Request-Attendance-Management-iOS-App-Design) | Nitish Khagwal, 10.12.2020, **C** | «Modified iOS table»: строка как компактная карточка с главным статусом; новый запрос запускается коротким contextual form | Inline form рядом со списком опасна при Telegram keyboard; на телефоне лучше отдельный route/sheet с draft и Back |
| [Attendance Tracking App UI/UX](https://www.behance.net/gallery/215369893/Attendance-Tacking-app-UI-UX-Design) | Abdul Ahad Rimon, 24.12.2024, **C** | Сводка реального времени → несколько визуальных показателей → календарь истории; хорошая последовательность overview-to-detail | Это self-described showcase без пользователей; charts нельзя добавлять ради вида, если они не отвечают на решение пользователя |
| [Smart Attendance App Design](https://dribbble.com/shots/24893167-Smart-Attendance-App-Design) | Indev, **C** | Разделить историю, schedule и текущий check/status вместо одной гигантской таблицы | Generic business/school concept; нет доказательства скорости отметки группы и нет edge states |
| [Dark Bottom Navigation Bar — Battle Trading Telegram Mini-App](https://dribbble.com/shots/26660844-Dark-Bottom-Navigation-Bar-Battle-Trading-Telegram-Mini-App) | Mani Djalilzadeh, 2026, **C** | Тёмный bottom bar с ясно отделённым active state подтверждает, что панель может выглядеть нативно внутри Telegram, не копируя его буквально | Один красивый shot не проверяет safe area, длинные локализованные labels, keyboard и пять равноправных вкладок |
| [Acapella — thoughts and ideas PWA](https://www.behance.net/gallery/198468071/Acapella-thoughts-and-ideas-app-case-study) | Domingo Design / Sanchit Sharma, 13.05.2024, **C**: назван PWA, но публичный deployment и результаты не показаны | Полезен как PWA moodboard для организации большого числа коротких сущностей и сохранения собственного веб-бренда в app-like shell | Нельзя считать Behance-кадры доказательством install/offline/browser-mode UX; сравнивать только композицию списка, detail и create action |

### Визуальные правила, которые можно передать Figma-агенту

1. **Фиолетовый градиент — локальный акцент, не фон всего продукта.** Разрешён в hero/summary главной, selected progress tile, onboarding/empty illustration. Запрещён за длинным текстом, таблицей, формой и semantic status chips.
2. **«Текучая форма» должна нести функцию.** Мягкая blob/halo может связывать заголовок с единственной главной карточкой или выделять текущую пару. Не рисовать несколько плавающих пятен, которые конкурируют с CTA.
3. **Стекло — максимум один уровень.** Полупрозрачна только акцентная карточка/плавающая плашка; вложенные glass-on-glass карточки запрещены. Нужен solid fallback и contrast check при Telegram light/dark/theme mismatch.
4. **Плотный журнал строится как hierarchy, а не как dashboard collage:** sticky контекст пары → три счётчика → фильтр «Все / не отмечено / отсутствуют» → компактные person rows → detail/status по тапу. Диаграмма не заменяет список, потому что старосте надо изменить конкретного человека.
5. **Календарная сетка на телефоне превращается в управляемый срез.** Doday даёт живой кодовый benchmark: day chips → выбранный день → вертикальный список, а heatmap остаётся вторичной сводкой. Для редкого действия можно открыть bottom sheet по кнопке overflow; long press допустим только как ускоритель, не единственный вход.
6. **Статус цветом дублируется формой/иконкой/текстом.** Фиолетовый остаётся action/selection; присутствовал, отсутствовал, опоздал и не отмечено используют существующую семантику системы, а не цвета Dribbble.
7. **Крупная типографика только для одного факта на первом экране.** Например, «Пара сейчас» или «2 задания сегодня». ФИО, предметы и статусы сохраняют компактную иерархию.
8. **Скругления и тени подчинены интерактивности.** Нажимаемая карточка имеет pressed state и явный результат; статическая сводка не должна выглядеть такой же кнопкой.
9. **Любой benchmark-кадр перерисовывается в четырёх состояниях:** loading/skeleton, empty, error/retry, keyboard/compact. Если приём разваливается хотя бы в одном, он остаётся moodboard, а не компонентом.

## Минимальная матрица проверки прототипа

| Ось | Обязательные варианты |
|---|---|
| Клиент | iOS, Android, Telegram Desktop, Telegram Web |
| Вход | Main App, Menu Button, notification/deep link, повторное открытие |
| Высота | compact, expanded, fullscreen success, fullscreen ignored |
| Ввод | системная клавиатура, хотя бы одна сторонняя на iOS/Android, длинный многострочный комментарий |
| Навигация | root tab, detail, editor, direct-open detail без history, Back после keyboard |
| Тема | light, dark, theme_changed во время сессии, некорректный/неполный theme param |
| Сеть/данные | cold load, slow response, offline draft, reconnect, конфликт версий draft |
| Контент | длинные ФИО/предметы, 30+ студентов, нет данных, частичная ошибка |

## Что ещё не доказано и требует продуктовой проверки

- Какие именно три раздела должны быть прямыми табами у студента и старосты. Внешние источники подтверждают паттерн, но не частоту задач RutCampusTrack.
- Нужен ли `SecondaryButton` для «Отменить» или лучше inline/text action: зависит от поддержки клиентов и риска случайной отмены.
- Стоит ли временно блокировать vertical closing swipe в журнале старосты. Решение зависит от реальной надёжности текущих клиентов и качества autosave/closing confirmation.
- Дает ли glass/gradient измеримое улучшение распознавания текущего действия. Это визуальная гипотеза, проверяется task test и контрастом, не лайками Dribbble.
- Можно ли скрывать bottom nav только при открытой клавиатуре или на всём editor route. Исходная рекомендация — скрывать на всём editor route; проверить на прототипе, не теряют ли люди ориентацию.
