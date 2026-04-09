# Info for GSD — v9.0 Phase Execution Guide

Рекомендации по запуску оставшихся фаз milestone v9.0 (Frontend Unification).

## Оценка фаз v9.0 по стоимости токенов

Колонки: **Discuss** — нужен ли `/gsd-discuss-phase` перед планом. **Research** — нужен ли явный `/gsd-research-phase`.

| # | Фаза | Что это | Размер | Discuss | Research | Оценка токенов |
|---|---|---|---|---|---|---|
| **50** | baseHref + /login | Рефакторинг Angular роутинга, 4 guards, login rewrite, **129 тестов** | Средняя | ✅ сделано | ✅ сделано | 💰💰 ~100-150k |
| **51** | Student Shell + Schedule + Check-in | 3 Angular маршрута, STOMP, геолокация | Средняя | ⚠️ `--auto` | ❌ нет | 💰💰💰 ~150-200k |
| **52** | Student Homework + Stats + Notifications + Profile | 4 маршрута, ng2-charts, формы | Средняя | ❌ можно без | ❌ нет | 💰💰 ~120-180k |
| **53** | Student Excuses + Late Check-in + PWA Banner | 2 маршрута + beforeinstallprompt | Малая-средняя | ⚠️ `--auto` | ❌ нет | 💰💰 ~80-120k |
| **54** | **Headman Group + Subjects + WPAN-13** | 2 Angular маршрута **+ backend AOP fix + gRPC тесты** | **Большая** | ✅ ДА, интерактивно | ✅ ДА | 💰💰💰💰 **~200-250k** |
| **55** | Headman Attendance + Stats | Journal grid (CdkVirtualScroll), excuses/late approve, threshold CRUD | **Большая** | ⚠️ `--auto` | ❌ нет | 💰💰💰💰 **~200-250k** |
| **56** | **PWA Headman Mode** | React features/headman/, BottomNav tab, **7 фич сразу**, SW кэш, **63 теста** | **Очень большая** | ✅ ДА, интерактивно | ✅ ДА | 💰💰💰💰💰 **~250-350k** |
| **57** | Landing + Docs | GSAP ScrollTrigger, HTML, **4 файла docs** | Средняя | ❌ можно без | ❌ нет | 💰💰 ~100-150k |

**Легенда:** ✅ обязательно · ⚠️ желательно в `--auto` режиме (минимум токенов) · ❌ можно пропустить

### Самые дорогие фазы (топ-3)
1. **Phase 56 (PWA Headman)** — дублирует все фичи хедмана с веба в React + тесты не должны сломаться. Много кода за раз.
2. **Phase 54 (Headman Group + WPAN-13)** — единственная фаза где и Java AOP, и Angular одновременно + gRPC контракт.
3. **Phase 55 (Headman Attendance)** — journal grid сложный компонент + несколько CRUD-экранов.

## Нужен ли research?

**Коротко: для большинства — нет, кроме 54 и 56.**

- ✅ **Phase 50** — research уже есть (`50-RESEARCH.md`), 6 планов готовы. **Сразу execute.**
- ❌ **Phases 51, 52, 53** — стек знакомый (Angular + Material + ng2-charts + STOMP), паттерны уже есть в teacher/admin cabinets и PWA. **Research не нужен, `/gsd-plan-phase` внутри сам поресёрчит что надо.**
- ✅ **Phase 54** — **research нужен**. Там WPAN-13 AOP fix в academic-service — надо понять где именно `@RequireRole` аспект, какие заголовки gateway пробрасывает (`X-Is-Headman`, `X-Group-Id`), как правильно не сломать ADMIN/TEACHER пути.
- ❌ **Phase 55** — переиспользует компоненты из teacher journal (Phase 39). Research не нужен.
- ✅ **Phase 56** — **research желателен**. Нужно посмотреть как устроен `AuthProvider.tsx`, какие 63 теста и за что они отвечают, как сейчас работает SW cache, чтобы не сломать.
- ❌ **Phase 57** — простая. GSAP skills уже установлены.

## Нужен ли discuss?

**Коротко: обязателен только для 54 и 56. Для остальных — опциональный `--auto` или можно вообще пропустить.**

`/gsd-discuss-phase` нужен когда в фазе есть **архитектурные развилки** или **неочевидные решения**, которые стоит закрепить до планирования. Если фаза типовая и похожа на уже сделанное — discuss лишний.

- ✅ **Phase 50** — discuss уже проведён (10 решений в `50-DISCUSSION-LOG.md`).
- ⚠️ **Phase 51** — `--auto` желателен. Мини-решения есть (STOMP подписка, как геолокация просит permission), но все стандартные — Клод сам справится с `--auto`.
- ❌ **Phase 52** — **можно без discuss**. Чистый CRUD: homework toggle, charts, password form, notification log. Переходи сразу к `/gsd-plan-phase 52`.
- ⚠️ **Phase 53** — `--auto` желателен. Есть решение про graceful degradation (если backend endpoints не готовы) + стратегия banner dismiss (localStorage vs cookies).
- ✅ **Phase 54** — **ОБЯЗАТЕЛЕН, интерактивно (НЕ `--auto`)**. WPAN-13 AOP fix имеет несколько подходов: (а) расширить `@RequireRole`, (б) новая аннотация `@RequireHeadman`, (в) проверка в сервисе. Это твоё решение, не Клода.
- ⚠️ **Phase 55** — `--auto` желателен. Основной вопрос — как хранить несохранённые изменения в journal grid (оптимистично vs batch save).
- ✅ **Phase 56** — **ОБЯЗАТЕЛЕН, интерактивно**. Где разместить фичи в React (`features/headman/` vs расширение `features/student/`), как шарить логику, как не сломать 63 теста — это стратегические решения.
- ❌ **Phase 57** — **можно без discuss**. Landing + docs, всё прямолинейно. Сразу `/gsd-plan-phase 57`.

### Когда discuss реально помогает
- Когда фаза меняет архитектуру (auth, роутинг, security)
- Когда есть 2+ валидных подхода и ты хочешь выбрать сам
- Когда фаза микширует backend+frontend (как 54)

### Когда discuss лишний
- Чистый CRUD по готовым паттернам (52, 57)
- Явно описанные в ROADMAP success criteria без развилок
- Когда следующая фаза копирует структуру предыдущей

## Рекомендуемый порядок команд

### Phase 50 — план готов, сразу выполнять
```
/gsd-execute-phase 50
```
После: `/gsd-verify-work 50` → `/gsd-ship` если надо PR.

### Phase 51 — discuss в --auto режиме
```
/gsd-discuss-phase 51 --auto      # авто-решения, там всё стандартно
/gsd-plan-phase 51                 # создать PLAN.md
/gsd-execute-phase 51
```
Или одной командой: `/gsd-discuss-phase 51 --chain` (discuss → plan → execute).

### Phase 52 — БЕЗ discuss, сразу plan
```
/gsd-plan-phase 52
/gsd-execute-phase 52
```
Чистый CRUD, архитектурных решений нет.

### Phase 53 — discuss в --auto режиме
```
/gsd-discuss-phase 53 --chain     # graceful degradation + banner strategy
```

### Phase 54 — НУЖЕН research для WPAN-13
```
/gsd-discuss-phase 54              # интерактивно, не --auto (WPAN-13 решения важны)
/gsd-research-phase 54             # явно, т.к. backend+frontend миксуется
/gsd-plan-phase 54
/gsd-execute-phase 54
```

### Phase 55 — discuss в --auto режиме
```
/gsd-discuss-phase 55 --chain     # batch save vs optimistic для journal grid
```

### Phase 56 — самая дорогая, разбить
```
/gsd-discuss-phase 56              # интерактивно
/gsd-research-phase 56             # AuthProvider + тесты + SW
/gsd-list-phase-assumptions 56     # проверить assumptions перед планом
/gsd-plan-phase 56
/gsd-execute-phase 56
```
**Совет:** перед 56 запустить `/clear` и начать свежий контекст — будет много файлов читать.

### Phase 57 — БЕЗ discuss, сразу plan
```
/gsd-plan-phase 57
/gsd-execute-phase 57
```
Landing + docs, всё прямолинейно.

### После всех фаз — закрыть milestone
```
/gsd-audit-milestone v9.0
/gsd-complete-milestone v9.0
```

## Экономия токенов — общие советы

1. **`/clear` между фазами** — особенно перед 54, 56. Контекст фаз не пересекается.
2. **`--auto` для 51, 53, 55** — они типовые, Клоду не нужны твои решения.
3. **Без discuss для 52 и 57** — сразу `/gsd-plan-phase`, это экономит отдельный виток токенов.
4. **`/gsd-autonomous`** есть если хочется просто «сделай всё оставшееся» — но **НЕ** запускать его для 54 и 56 (слишком рискованно, лучше контролируемо).
5. **Параллелизация невозможна** — 51→52→53 цепочка зависимостей, 54→55→56 тоже. Только 54 теоретически параллелится с 51-53 (разные feature modules), но внутри одного чата это нерелевантно.

## TL;DR

- Сейчас: `/gsd-execute-phase 50` (план готов).
- **52 и 57**: БЕЗ discuss, сразу `/gsd-plan-phase` → `/gsd-execute-phase`.
- **51, 53, 55**: `--chain --auto` дёшево и сердито (discuss в авто-режиме).
- **54 и 56**: дорогие и рискованные — полный цикл `discuss (интерактивно) → research → plan → execute`, `/clear` перед ними.
- Самая дорогая по токенам — **56 (PWA Headman)**.
