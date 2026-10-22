---
name: rutcampustrack-design
description: Принятый Figma UI RutCampusTrack во Vue/PCSS.
---

Перед UI найди и прочитай нужные части `docs/design/COMPONENT_REGISTRY.md`,
`brandbook-v2.md`, `tokens-v2.json`, `BRAND_DIRECTION.md`, `A11Y_REQUIREMENTS.md`
и спеки из `docs/wireframes/`. Пока они не перенесены, не работай по памяти/выжимке.
Определи поверхность: desktop/auth или mobile PWA/TMA; учти mobile-решения 01–05.09.2026,
не распространяя старые desktop-запреты.

- Русский, «ты», Onest через токен шрифта, табличные цифры глобально.
- Компонент использует семантический/компонентный токен, не примитив/литерал;
  назначение важнее равного числа. Нужен токен — назови потребность владельцу, сам не
  создавай. PCSS/PostCSS/rem — по `frontends/AGENTS.md`; shadcn/Tailwind overlay
  отменён 05.09.2026, тени — из токенов.
- Сохраняй принятые анатомию и состояния. Реестр определяет UI-смыслы;
  технический composable/helper не равен новому продуктовому компоненту.
- Тема меняет значения, не композицию. Desktop: один акцент «сейчас» заливкой,
  focus контуром; mobile допускает несколько областей по направлению. Статус передают
  символ и слово, не один цвет. Desktop читается без тени; mobile использует принятые
  glass/gradient/glow и границы, не desktop/auth-glass. Значения — из последних токенов.
- В плотных таблицах hover не добавляй вне принятой крест-подсветки журнала.
  Не меняй данные под курсором/фокусом. Учитывай reduced motion.
- Desktop reveal — layout shift, modal — подтверждение необратимого действия;
  mobile host-action/navigation/overlay — из его спеки. Без вложенных overlays и
  неутверждённого freshness-mark. Проверь state matrix, keyboard, contrast, overflow,
  согласованные размеры/темы; старые Figma-счётчики не актуальный факт. В code-этапе
  Figma read-only; masters, новые токены и запись требуют прямого задания и Figma-правил.
