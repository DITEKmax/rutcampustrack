# Правила процесса

Прочитай [RULES.md](../.agent/orchestration-v2/RULES.md) и CURRENT.md, решение владельца 2026-09-20. Главный чат — единственная точка вопросов/ответов/STOP владельца. Проверки только по изменённому поведению; модели и ownership по RULES. Старые packets не расширяют проверки вопреки новой политике.

# Vue / PCSS

- Vue и strict TypeScript: страницы компонуют features; transport, состояние и
  бизнес-логика имеют отдельных владельцев. Typed props/emits, публичный feature API;
  не складывай всё в App.vue и не вводи формальный лимит строк.
- Все авторские стили — отдельные `.pcss` / `.module.pcss`, обработанные PostCSS.
  PostCSS — постпроцессор CSS, не UI framework. Tailwind, shadcn/shadcn-vue,
  utility CSS frameworks, Sass/SCSS/Less и CSS-in-JS не добавляй.
- Все размерные авторские PCSS-длины — rem (включая границы, outline, blur, тени и
  breakpoints), без неустановленного px-to-rem plugin; ноль/коэффициенты без единицы,
  время ms/s, проценты/fr/intrinsic — для доли и раскладки. Для pixel-макета фиксируй
  базу: при 16 CSS px — px / 16; не меняй html на 16px/62.5%, проверь обычный и
  увеличенный root font. Screenshot/viewport API и SVG-координаты — не PCSS-длины.
- Перед UI-правкой используй `rutcampustrack-design`. Токены примитив → семантика
  → компонент; значения длин в rem живут в токенах, компоненты используют var().
  Выбирай токен по смыслу; отсутствие нужного токена назови владельцу.
- PWA и TMA делят подходящие features/UI, но имеют свои bootstrap/adapters; Telegram
  API не попадает в домен, frontend не копируется по ролям. У server response один
  cache/query owner, без дублирования в store/query cache; библиотека — по ADR.
  PWA student offline: расписание и ДЗ; TMA online. Offline-запись, вложения,
  хранение и истёкшая сессия требуют решения.
- UI: Figma → извлечение → существующий код → один flow → реализация → запуск →
  browser/Playwright → screenshot → исправления. Перед Figma-инструментом используй
  design-to-code skill; Tailwind в его выводе не разрешает Tailwind в проекте.
  Проверь затронутые состояния, keyboard, контраст и согласованный viewport/overflow;
  отсутствие mobile-спеки не заменяй desktop resize.
